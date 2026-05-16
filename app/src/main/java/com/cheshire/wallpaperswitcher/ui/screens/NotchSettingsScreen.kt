package com.cheshire.wallpaperswitcher.ui.screens

import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
import com.cheshire.wallpaperswitcher.util.NotchPathUtil

@Composable
fun NotchSettingsScreen(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
) {
    val settings = viewModel.notchSettings
    var showUI by remember { mutableStateOf(true) }
    val density = LocalDensity.current

    BackHandler(onBack = onBack)

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showUI = !showUI })
                },
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        // Reproduce exactly the ScrollingWallpaperRenderer scaling and positioning logic
        val metadata = viewModel.currentMetadata
        val dimensions = metadata.dimensions.split("x")
        val iw = dimensions.getOrNull(0)?.toFloatOrNull() ?: 0f
        val ih = dimensions.getOrNull(1)?.toFloatOrNull() ?: 0f

        val (scale, tx) =
            if (iw > 0f && ih > 0f) {
                val scale = screenHeight / ih
                val sw = iw * scale
                val maxScroll = (sw - screenWidth).coerceAtLeast(0f)
                // Use default 0.5 offset for preview to match typical service start state (centered horizontally)
                val t = if (maxScroll > 0) -0.5f * maxScroll else (screenWidth - sw) / 2f
                scale to t
            } else {
                1f to 0f
            }

        // Full Screen Background Wallpaper (positioned exactly like the renderer draws it: scaled to fit height)
        AsyncImage(
            model = viewModel.currentWallpaperUri,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = if (viewModel.currentWallpaperFlipped) -scale else scale
                        scaleY = scale
                    },
            contentScale = ContentScale.Crop,
        )

        // Notch Overlay - Exactly matching image width and position, supporting rounded edges
        if (settings.enabled) {
            val notchColor = Color(settings.color)
            val notchHeight = settings.height.toFloat()
            val cornerRadius = settings.cornerRadius.toFloat()

            // For preview, we match the logic where notch width matches image width (or screen if wider)
            val sw = if (iw > 0f && ih > 0f) iw * (screenHeight / ih) else screenWidth
            val left = tx.coerceAtLeast(0f)
            val right = (tx + sw).coerceAtMost(screenWidth)

            val notchPath = remember { Path() }
            val androidPath = remember(notchPath) { notchPath.asAndroidPath() }
            val rect = remember { RectF() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (right > left) {
                    NotchPathUtil.updateNotchPath(
                        path = androidPath,
                        left = left,
                        right = right,
                        notchHeight = notchHeight,
                        cornerRadius = cornerRadius,
                        rect = rect,
                    )
                    drawPath(notchPath, notchColor)
                }
            }
        }

        if (showUI) {
            // Immersive Overlay Controls
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp)
                        .fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Notch Settings",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    ListItem(
                        headlineContent = { Text("Enable Notch", color = Color.White) },
                        trailingContent = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { viewModel.updateNotchSettings(settings.copy(enabled = it)) },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (settings.enabled) {
                        Text(
                            text = "Height: ${settings.height}px",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Slider(
                            value = settings.height.toFloat(),
                            onValueChange = { viewModel.updateNotchSettings(settings.copy(height = it.toInt())) },
                            valueRange = 10f..600f,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        Text(
                            text = "Rounding: ${settings.cornerRadius}px",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Slider(
                            value = settings.cornerRadius.toFloat(),
                            onValueChange = { viewModel.updateNotchSettings(settings.copy(cornerRadius = it.toInt())) },
                            valueRange = 0f..200f,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val colors =
                                listOf(
                                    Color.Black,
                                    Color.DarkGray,
                                    Color(0xFF1A1A1A),
                                    Color(0xFF333333),
                                    MaterialTheme.colorScheme.primary,
                                )

                            colors.forEach { color ->
                                val isSelected = settings.color == color.toArgb()
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable { viewModel.updateNotchSettings(settings.copy(color = color.toArgb())) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(16.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.8f)),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap wallpaper to hide UI",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}
