package com.example.wallpaperswitcher

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.example.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val TAG = "WallpaperSwitcher"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WallpaperSwitcherTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    WallpaperSwitcherScreen(
                        modifier = Modifier.padding(innerPadding),
                        onSetWallpaper = { uri ->
                            lifecycleScope.launch {
                                setNextWallpaper(this@MainActivity, uri)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WallpaperSwitcherScreen(
    modifier: Modifier = Modifier,
    onSetWallpaper: (Uri) -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE) }
    
    var folderUri by remember { 
        mutableStateOf(sharedPreferences.getString("folder_uri", null)?.toUri()) 
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
                folderUri = uri
                sharedPreferences.edit { putString("folder_uri", uri.toString()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable permission", e)
                Toast.makeText(context, "Permission failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (folderUri == null) {
            Button(onClick = { launcher.launch(null) }) {
                Text("Select Wallpaper Directory")
            }
        } else {
            Text("Folder Selected", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSetWallpaper(folderUri!!) }) {
                Text("Next Wallpaper")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { launcher.launch(null) }) {
                Text("Change Directory")
            }
        }
    }
}

suspend fun setNextWallpaper(context: Context, folderUri: Uri) {
    withContext(Dispatchers.IO) {
        try {
            val root = DocumentFile.fromTreeUri(context, folderUri)
            if (root == null || !root.isDirectory) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Invalid directory", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val files = root.listFiles().filter { 
                it.type?.startsWith("image/") == true 
            }

            if (files.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No images found in directory", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            val randomFile = files.random()
            Log.d(TAG, "Setting wallpaper to: ${randomFile.uri}")
            
            val inputStream: InputStream? = context.contentResolver.openInputStream(randomFile.uri)
            if (inputStream != null) {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap != null) {
                    val wallpaperManager = WallpaperManager.getInstance(context)
                    
                    val metrics = context.resources.displayMetrics
                    val screenWidth = metrics.widthPixels
                    val screenHeight = metrics.heightPixels
                    
                    // Suggesting a wider width enables scrolling
                    wallpaperManager.suggestDesiredDimensions(screenWidth * 2, screenHeight)
                    
                    wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Wallpaper changed!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Failed to decode bitmap from ${randomFile.uri}")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to decode image", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: Permission denied while accessing files or setting wallpaper", e)
            withContext(Dispatchers.Main) {
                // Show a shorter message but log the full one
                Toast.makeText(context, "Permission Denied. Check Logcat for details.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting wallpaper", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
