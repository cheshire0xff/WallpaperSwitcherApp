package com.cheshire.wallpaperswitcher.ui.screens.dashboard

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cheshire.wallpaperswitcher.service.ScrollingWallpaperService
import com.cheshire.wallpaperswitcher.ui.Screen
import com.cheshire.wallpaperswitcher.ui.components.CurrentWallpaperCard
import com.cheshire.wallpaperswitcher.ui.components.EngineStatusSection
import com.cheshire.wallpaperswitcher.ui.components.FolderDetailsDialog
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel


/**
 * Main dashboard screen composable.
 */
@Composable
fun DashboardScreen(
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
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateFolderUri(uri)
        }
    }

    if (showDetails) {
        FolderDetailsDialog(
            totalImages = viewModel.cachedImages.size,
            seenCount = viewModel.seenImageUris.size,
            favoritesCount = viewModel.favorites.size,
            folderUri = viewModel.folderUri,
            onResetSeen = { viewModel.resetSeen() },
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
            CurrentWallpaperCard(
                name = viewModel.currentWallpaperName,
                uri = viewModel.currentWallpaperUri,
                isFavorite = viewModel.currentWallpaperUri?.toString() in viewModel.favorites,
                isCaching = viewModel.isCaching,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onEnlarge = { _, _ -> /* enlargement handled inside CurrentWallpaperCard or passed up */ }
            )

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigate(Screen.Queue) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.cachedImages.isNotEmpty()
                ) {
                    Text("Queue", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { onNavigate(Screen.Favorites) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.favorites.isNotEmpty()
                ) {
                    Text("Favs", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { onNavigate(Screen.History) },
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.seenImageUris.isNotEmpty()
                ) {
                    Text("History", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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

private fun isWallpaperEngineActive(context: Context): Boolean {
    val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    val info: WallpaperInfo? = wm.wallpaperInfo
    return info != null && info.packageName == context.packageName && 
           info.serviceName == ScrollingWallpaperService::class.java.name
}
