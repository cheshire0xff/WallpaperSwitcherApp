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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

private const val TAG = "WallpaperSwitcher"

class MainActivity : ComponentActivity() {
    private var folderUri by mutableStateOf<Uri?>(null)
    private var cachedImages by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
    private var isCaching by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedPreferences = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
        folderUri = sharedPreferences.getString("folder_uri", null)?.toUri()

        // Cache the images asynchronously at application start if a folder is already selected
        folderUri?.let { uri ->
            lifecycleScope.launch {
                refreshImageCache(this@MainActivity, uri)
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
                            isCaching = isCaching,
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
                            }
                        )
                    }
                }
            }
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
                cachedImages = newList
                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Cache refreshed in ${totalTime}ms, found ${newList.size} images.")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing cache: ${e.message}")
            } finally {
                isCaching = false
            }
        }
    }

    private suspend fun applyNextWallpaper() {
        if (cachedImages.isEmpty()) {
            if (!isCaching) {
                Toast.makeText(this, "No images found in cache", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val (randomUri, name) = cachedImages.random()
        val startTime = System.currentTimeMillis()

        withContext(Dispatchers.IO) {
            try {
                // Get resolution efficiently
                var resolution = "Unknown"
                contentResolver.openInputStream(randomUri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(input, null, options)
                    resolution = "${options.outWidth}x${options.outHeight}"
                }

                Log.i(TAG, "Selected Wallpaper: $name ($resolution)")

                getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit {
                    putString("current_wallpaper_uri", randomUri.toString())
                }

                val updateIntent = Intent("com.example.wallpaperswitcher.UPDATE_WALLPAPER")
                updateIntent.setPackage(packageName)
                updateIntent.putExtra("uri", randomUri.toString())
                sendBroadcast(updateIntent)

                val totalTime = System.currentTimeMillis() - startTime
                Log.d(TAG, "Wallpaper application triggered in ${totalTime}ms.")

                withContext(Dispatchers.Main) {
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
    isCaching: Boolean,
    onFolderSelected: (Uri) -> Unit,
    onNextWallpaper: () -> Unit
) {
    val context = LocalContext.current
    var isEngineEnabled by remember { mutableStateOf(isWallpaperEngineActive(context)) }

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
            if (!isEngineEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("One-time setup required", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "To enable scrolling, you must set this app as your live wallpaper.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(onClick = {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, 
                                ComponentName(context, ScrollingWallpaperService::class.java))
                            context.startActivity(intent)
                        }) {
                            Text("Enable Scrolling Engine")
                        }
                    }
                }
            } else {
                Text(
                    "Wallpaper Engine is Active",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isCaching) {
                    CircularProgressIndicator()
                    Text("Scanning folder...", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("$cachedImagesCount images found", style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = { onNextWallpaper() },
                    enabled = !isCaching && cachedImagesCount > 0,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Next Wallpaper")
                }
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
