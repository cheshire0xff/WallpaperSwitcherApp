package com.example.wallpaperswitcher

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "WallpaperSwitcher"
private const val CACHE_FILE_NAME = "image_cache.txt"

class MainActivity : ComponentActivity() {
    private var folderUri by mutableStateOf<Uri?>(null)
    private var cachedImages by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
    private var isCaching by mutableStateOf(false)
    private var currentWallpaperName by mutableStateOf<String?>(null)
    private var currentWallpaperUri by mutableStateOf<Uri?>(null)
    private var seenImageUris by mutableStateOf<Set<String>>(emptySet())
    private var favorites by mutableStateOf<Set<String>>(emptySet())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        folderUri = sharedPreferences.getString("folder_uri", null)?.toUri()
        currentWallpaperName = sharedPreferences.getString("current_wallpaper_name", null)
        currentWallpaperUri = sharedPreferences.getString("current_wallpaper_uri", null)?.toUri()
        seenImageUris = sharedPreferences.getStringSet("seen_images", emptySet()) ?: emptySet()
        favorites = sharedPreferences.getStringSet("favorite_images", emptySet()) ?: emptySet()

        // Asynchronously check and load/refresh cache
        folderUri?.let { uri ->
            lifecycleScope.launch {
                checkAndRefreshCache(this@MainActivity, uri)
            }
        }

        setContent {
            WallpaperSwitcherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        WallpaperSwitcherScreen(
                            modifier = Modifier.padding(innerPadding),
                            folderUri = folderUri,
                            cachedImagesCount = cachedImages.size,
                            seenImagesCount = seenImageUris.size,
                            favoritesCount = favorites.size,
                            isCaching = isCaching,
                            currentWallpaperName = currentWallpaperName,
                            isFavorite = currentWallpaperUri?.toString()?.let { it in favorites } ?: false,
                            onFolderSelected = { uri ->
                                folderUri = uri
                                lifecycleScope.launch {
                                    refreshImageCache(this@MainActivity, uri)
                                }
                            },
                            onNextWallpaper = {
                                lifecycleScope.launch {
                                    applyNextWallpaper()
                                }
                            },
                            onToggleFavorite = {
                                toggleFavorite()
                            },
                            onResetSeen = {
                                seenImageUris = emptySet()
                                getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
                                    putStringSet("seen_images", emptySet())
                                }
                                Toast.makeText(this@MainActivity, "History reset", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun toggleFavorite() {
        val uriStr = currentWallpaperUri?.toString() ?: return
        val newFavorites = if (uriStr in favorites) {
            favorites - uriStr
        } else {
            favorites + uriStr
        }
        favorites = newFavorites
        getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
            putStringSet("favorite_images", newFavorites)
        }
    }

    private suspend fun checkAndRefreshCache(context: Context, uri: Uri) {
        isCaching = true
        withContext(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            val lastStoredModified = sharedPreferences.getLong("last_modified", -1L)
            val currentModified = getDirectoryLastModified(context, uri)

            val loadedCache = loadCacheFromFile(context)
            if (currentModified != -1L && currentModified == lastStoredModified && loadedCache.isNotEmpty()) {
                Log.d(TAG, "Cache is up to date. Loaded ${loadedCache.size} images.")
                withContext(Dispatchers.Main) {
                    cachedImages = loadedCache
                    // If we have a cache but no wallpaper is set yet, pick one
                    if (currentWallpaperUri == null && loadedCache.isNotEmpty()) {
                        setInitialWallpaperInfo(context, loadedCache.random())
                    }
                    isCaching = false
                }
            } else {
                Log.d(TAG, "Cache is outdated or missing. Refreshing...")
                refreshImageCache(context, uri)
            }
        }
    }

    private fun getDirectoryLastModified(context: Context, uri: Uri): Long {
        return try {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last modified: ${e.message}")
            -1L
        }
    }

    private suspend fun refreshImageCache(context: Context, uri: Uri) {
        isCaching = true
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "refreshImageCache started for: $uri")

        withContext(Dispatchers.IO) {
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
                )

                val newList = mutableListOf<Pair<Uri, String>>()
                val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")

                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: ""
                        if (imageExtensions.any { name.endsWith(it, ignoreCase = true) }) {
                            val docId = cursor.getString(idCol)
                            newList.add(DocumentsContract.buildDocumentUriUsingTree(uri, docId) to name)
                        }
                    }
                }

                val currentModified = getDirectoryLastModified(context, uri)
                saveCacheToFile(context, newList)
                context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
                    putLong("last_modified", currentModified)
                }

                withContext(Dispatchers.Main) {
                    cachedImages = newList
                    if (currentWallpaperUri == null && newList.isNotEmpty()) {
                        setInitialWallpaperInfo(context, newList.random())
                    }
                }
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Cache refreshed and saved in ${totalTime}ms, found ${newList.size} images.")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing cache: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    isCaching = false
                }
            }
        }
    }

    private fun setInitialWallpaperInfo(context: Context, pair: Pair<Uri, String>) {
        val (uri, name) = pair
        currentWallpaperName = name
        currentWallpaperUri = uri
        context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
            putString("current_wallpaper_uri", uri.toString())
            putString("current_wallpaper_name", name)
        }
        Log.d(TAG, "Initial wallpaper info set to: $name")
    }

    private fun saveCacheToFile(context: Context, list: List<Pair<Uri, String>>) {
        try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            file.bufferedWriter().use { writer ->
                list.forEach { (uri, name) ->
                    writer.write("${uri}|${name}")
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cache to file: ${e.message}")
        }
    }

    private fun loadCacheFromFile(context: Context): List<Pair<Uri, String>> {
        val list = mutableListOf<Pair<Uri, String>>()
        try {
            val file = File(context.cacheDir, CACHE_FILE_NAME)
            if (file.exists()) {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split("|", limit = 2)
                        if (parts.size == 2) {
                            list.add(parts[0].toUri() to parts[1])
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cache from file: ${e.message}")
        }
        return list
    }

    private suspend fun applyNextWallpaper() {
        if (cachedImages.isEmpty()) {
            if (!isCaching) {
                Toast.makeText(this, "No images found in cache", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val unseenImages = cachedImages.filter { it.first.toString() !in seenImageUris }
        
        val targetList = if (unseenImages.isEmpty()) {
            Toast.makeText(this, "All images seen! Starting over.", Toast.LENGTH_SHORT).show()
            seenImageUris = emptySet()
            cachedImages
        } else {
            unseenImages
        }

        val (randomUri, name) = targetList.random()
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            try {
                var resolution = "Unknown"
                contentResolver.openInputStream(randomUri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    resolution = "${options.outWidth}x${options.outHeight}"
                }

                Log.i(TAG, "Selected Wallpaper: $name ($resolution)")

                val newSeen = seenImageUris + randomUri.toString()
                
                getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
                    putString("current_wallpaper_uri", randomUri.toString())
                    putString("current_wallpaper_name", name)
                    putStringSet("seen_images", newSeen)
                }

                val updateIntent = Intent("com.example.wallpaperswitcher.UPDATE_WALLPAPER")
                updateIntent.setPackage(packageName)
                updateIntent.putExtra("uri", randomUri.toString())
                sendBroadcast(updateIntent)

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Wallpaper application triggered in ${totalTime}ms.")

                withContext(Dispatchers.Main) {
                    currentWallpaperName = name
                    currentWallpaperUri = randomUri
                    seenImageUris = newSeen
                    Toast.makeText(this@MainActivity, "Wallpaper Updated!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error applying wallpaper: ${e.message}")
            }
        }
    }
}

@Composable
fun WallpaperSwitcherScreen(
    modifier: Modifier = Modifier,
    folderUri: Uri?,
    cachedImagesCount: Int,
    seenImagesCount: Int,
    favoritesCount: Int,
    isCaching: Boolean,
    currentWallpaperName: String?,
    isFavorite: Boolean,
    onFolderSelected: (Uri) -> Unit,
    onNextWallpaper: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResetSeen: () -> Unit
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onFolderSelected(uri)
                context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
                    putString("folder_uri", uri.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable permission", e)
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Folder Details") },
            text = {
                Column {
                    Text("Total Images: $cachedImagesCount", fontWeight = FontWeight.Bold)
                    Text("Seen: $seenImagesCount")
                    Text("New: ${cachedImagesCount - seenImagesCount}")
                    Text("Favorites: $favoritesCount")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Path:", fontWeight = FontWeight.Bold)
                    Text(
                        text = folderUri?.let { Uri.decode(it.toString()) } ?: "None",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onResetSeen() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Seen History")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (folderUri == null) {
            Button(onClick = { launcher.launch(null) }) {
                Text("Select Wallpaper Folder")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isCaching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Scanning folder...", style = MaterialTheme.typography.bodySmall)
                    } else {
                        currentWallpaperName?.let {
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
                                        text = it,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onToggleFavorite() }) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Toggle Favorite",
                                        tint = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                    color = Color(0xFF4CAF50) // Green
                )
            }

            Button(
                onClick = { 
                    if (!isEngineEnabled) {
                        Toast.makeText(context, "Please enable the engine first", Toast.LENGTH_SHORT).show()
                    }
                    onNextWallpaper() 
                },
                enabled = !isCaching && cachedImagesCount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Next Wallpaper")
            }

            Spacer(modifier = Modifier.weight(1f))

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
    val wm = WallpaperManager.getInstance(context)
    val info: WallpaperInfo? = wm.wallpaperInfo
    return info != null && info.packageName == context.packageName && 
           info.serviceName == ScrollingWallpaperService::class.java.name
}
