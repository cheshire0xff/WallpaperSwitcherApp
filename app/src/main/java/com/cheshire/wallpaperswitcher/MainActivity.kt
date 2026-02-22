package com.cheshire.wallpaperswitcher

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme

/**
 * Available screens in the app for navigation.
 */
enum class Screen {
    Dashboard,
    Queue,
    Favorites,
    History
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = WallpaperRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WallpaperViewModel(repository) as T
            }
        }

        setContent {
            WallpaperSwitcherTheme {
                val viewModel: WallpaperViewModel = viewModel(factory = factory)
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        WallpaperSwitcherApp(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Main application container that handles navigation between different screens.
 */
@Composable
fun WallpaperSwitcherApp(
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel
) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    // System back button handling to return to Dashboard from other screens
    if (currentScreen != Screen.Dashboard) {
        BackHandler {
            currentScreen = Screen.Dashboard
        }
    }

    when (currentScreen) {
        Screen.Dashboard -> {
            WallpaperSwitcherScreen(
                modifier = modifier,
                viewModel = viewModel,
                onNavigate = { currentScreen = it }
            )
        }
        Screen.Queue -> {
            ImageGridScreen(
                title = "Upcoming Queue",
                images = viewModel.shuffledQueue,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.Favorites -> {
            val favImages = viewModel.cachedImages.filter { it.first.toString() in viewModel.favorites }
            ImageGridScreen(
                title = "Favorites",
                images = favImages,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.History -> {
            val historyImages = viewModel.cachedImages.filter { it.first.toString() in viewModel.seenImageUris }
            ImageGridScreen(
                title = "History (Already Seen)",
                images = historyImages,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}

/**
 * Main dashboard screen composable.
 */
@Composable
fun WallpaperSwitcherScreen(
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel,
    onNavigate: (Screen) -> Unit
) {
    val context = LocalContext.current
    var isEngineEnabled by remember { mutableStateOf(isWallpaperEngineActive(context)) }
    var showDetails by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEngineEnabled = isWallpaperEngineActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateFolderUri(uri)
        }
    }

    if (showDetails) {
        FolderDetailsDialog(
            viewModel = viewModel,
            onDismiss = { showDetails = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (viewModel.folderUri == null) {
            Button(onClick = { launcher.launch(null) }) {
                Text("Select Wallpaper Folder")
            }
        } else {
            CurrentWallpaperCard(viewModel = viewModel)

            EngineStatusSection(context = context, isEngineEnabled = isEngineEnabled)

            Button(
                onClick = { 
                    if (!isEngineEnabled) {
                        Toast.makeText(context, "Please enable the engine first", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.nextWallpaper() 
                },
                enabled = !viewModel.isCaching && viewModel.cachedImages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Next Wallpaper")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation Row for different image lists
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate(Screen.Queue) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.cachedImages.isNotEmpty()
                ) {
                    Text("Queue", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { onNavigate(Screen.Favorites) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.favorites.isNotEmpty()
                ) {
                    Text("Favs", textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { onNavigate(Screen.History) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.seenImageUris.isNotEmpty()
                ) {
                    Text("History", textAlign = TextAlign.Center)
                }
            }

            OutlinedButton(
                onClick = { showDetails = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Folder Details")
            }

            OutlinedButton(
                onClick = { launcher.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Folder")
            }
        }
    }
}

/**
 * Screen displaying a lazy-loaded grid of images.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    title: String,
    images: List<Pair<Uri, String>>,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    var selectedImage by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (images.isEmpty()) {
                Text(
                    "No images to display",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images) { imagePair ->
                        AsyncImage(
                            model = imagePair.first,
                            contentDescription = imagePair.second,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { selectedImage = imagePair },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (selectedImage != null) {
        EnlargedImageDialog(
            imagePair = selectedImage!!,
            onDismiss = { selectedImage = null },
            onSetWallpaper = {
                viewModel.setWallpaper(selectedImage!!)
                selectedImage = null
                onBack() 
            }
        )
    }
}

/**
 * Dialog displaying an enlarged version of the selected thumbnail.
 */
@Composable
fun EnlargedImageDialog(
    imagePair: Pair<Uri, String>,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imagePair.first,
                    contentDescription = imagePair.second,
                    modifier = Modifier.fillMaxSize().clickable { onDismiss() },
                    contentScale = ContentScale.Fit
                )
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = imagePair.second,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onSetWallpaper) {
                        Text("Set as Wallpaper")
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWallpaperCard(viewModel: WallpaperViewModel) {
    var enlargedImagePair by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.isCaching) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text("Scanning folder...", style = MaterialTheme.typography.bodySmall)
            } else {
                viewModel.currentWallpaperName?.let { name ->
                    val uri = viewModel.currentWallpaperUri
                    val isFavorite = uri?.toString() in viewModel.favorites
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Current Wallpaper:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                contentDescription = "Toggle Favorite",
                                tint = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                            )
                        }
                    }

                    // Current wallpaper thumbnail with phone aspect ratio
                    if (uri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = uri,
                            contentDescription = "Current wallpaper thumbnail",
                            modifier = Modifier
                                .height(320.dp)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { enlargedImagePair = uri to name },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }

    if (enlargedImagePair != null) {
        EnlargedImageDialog(
            imagePair = enlargedImagePair!!,
            onDismiss = { enlargedImagePair = null },
            onSetWallpaper = {
                viewModel.setWallpaper(enlargedImagePair!!)
                enlargedImagePair = null
            }
        )
    }
}

@Composable
fun EngineStatusSection(context: Context, isEngineEnabled: Boolean) {
    if (!isEngineEnabled) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Engine Disabled", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scrolling won't work until you set this as your Live Wallpaper.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(onClick = {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, 
                        ComponentName(context, ScrollingWallpaperService::class.java))
                    context.startActivity(intent)
                }) {
                    Text("Enable Engine Now")
                }
            }
        }
    } else {
        Text(
            "Wallpaper Engine is Active",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF4CAF50)
        )
    }
}

@Composable
fun FolderDetailsDialog(
    viewModel: WallpaperViewModel,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folder Details") },
        text = {
            Column {
                Text("Total Images: ${viewModel.cachedImages.size}", fontWeight = FontWeight.Bold)
                Text("Seen: ${viewModel.seenImageUris.size}")
                Text("New: ${viewModel.cachedImages.size - viewModel.seenImageUris.size}")
                Text("Favorites: ${viewModel.favorites.size}")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Path:", fontWeight = FontWeight.Bold)
                Text(
                    text = viewModel.folderUri?.let { Uri.decode(it.toString()) } ?: "None",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.resetSeen() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Seen History")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun isWallpaperEngineActive(context: Context): Boolean {
    val wm = WallpaperManager.getInstance(context)
    val info: WallpaperInfo? = wm.wallpaperInfo
    return info != null && info.packageName == context.packageName && 
           info.serviceName == ScrollingWallpaperService::class.java.name
}
