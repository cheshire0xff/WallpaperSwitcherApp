package com.cheshire.wallpaperswitcher.ui.components

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.WallpaperManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cheshire.wallpaperswitcher.service.ScrollingWallpaperService

/**
 * Displays the status of the Live Wallpaper engine and an enable button if needed.
 */
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
