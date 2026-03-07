package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperMetadata
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

/**
 * Dialog displaying an enlarged version of the selected thumbnail.
 * Supports pinch-to-zoom and panning with bounded constraints.
 */
@Composable
fun EnlargedImageDialog(
    imagePair: Pair<Uri, String>,
    viewModel: WallpaperViewModel,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit,
) {
    val context = LocalContext.current
    var metadata by remember { mutableStateOf<WallpaperMetadata?>(null) }

    val name = imagePair.second
    val isFavorite = name in viewModel.favoriteNames
    val isToRemove = name in viewModel.toRemoveNames

    // Transformation state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isFlipped by remember { mutableStateOf(false) }
    var showUI by remember { mutableStateOf(true) }
    var showUseAsMenu by remember { mutableStateOf(false) }

    LaunchedEffect(imagePair.first) {
        metadata = viewModel.fetchMetadata(imagePair.first)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                val state =
                    rememberTransformableState { zoomChange, offsetChange, _ ->
                        val newScale = (scale * zoomChange).coerceIn(1f, 5f)

                        // Calculate the actual image size as displayed by ContentScale.Fit
                        val dimensions = metadata?.dimensions?.split("x")
                        val iw = dimensions?.getOrNull(0)?.toFloatOrNull() ?: 0f
                        val ih = dimensions?.getOrNull(1)?.toFloatOrNull() ?: 0f
                        val (imgW, imgH) =
                            if (iw > 0f && ih > 0f) {
                                // Fitting calculations.
                                val imgAspectRatio = iw / ih
                                val screenAspectRatio = screenWidth / screenHeight
                                if (imgAspectRatio > screenAspectRatio) {
                                    screenWidth to (screenWidth / imgAspectRatio)
                                } else {
                                    (screenHeight * imgAspectRatio) to screenHeight
                                }
                            } else {
                                screenWidth to screenHeight
                            }
                        // Bounds: image can't be panned further than its edges.
                        // We allow a small extra margin (10%) for better feel, but keep it tight.
                        val maxX = maxOf(0f, (imgW * newScale - screenWidth) / 2f)
                        val maxY = maxOf(0f, (imgH * newScale - screenHeight) / 2f)

                        // Speed up panning when zoomed in.
                        val scaledOffsetChangeX = offsetChange.x * newScale
                        val scaledOffsetChangeY = offsetChange.y * newScale

                        scale = newScale
                        offset =
                            if (scale > 1.01f) {
                                Offset(
                                    x = (offset.x + scaledOffsetChangeX).coerceIn(-maxX, maxX),
                                    y = (offset.y + scaledOffsetChangeY).coerceIn(-maxY, maxY),
                                )
                            } else {
                                Offset.Zero
                            }
                    }

                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = imagePair.first,
                        contentDescription = imagePair.second,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = if (isFlipped) -scale else scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ).transformable(state = state)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            showUI = !showUI
                                        },
                                        onDoubleTap = {
                                            if (scale > 1.05f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 3f
                                            }
                                        },
                                    )
                                },
                        contentScale = ContentScale.Fit,
                    )
                    if (showUI.not()) {
                        return@Box
                    }
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SelectionContainer {
                                Text(
                                    text = imagePair.second,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            metadata?.let { meta ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier =
                                        Modifier
                                            .alpha(0.8f)
                                            .padding(top = 4.dp),
                                ) {
                                    Text(
                                        text = meta.fileSizeMb,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = meta.dimensions,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                    )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 24.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                WallpaperActionButton(
                                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    label = "Fav",
                                    onClick = { viewModel.toggleFavorite(name) },
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                                )

                                WallpaperActionButton(
                                    icon = Icons.Default.Flip,
                                    label = "Flip",
                                    onClick = { isFlipped = !isFlipped },
                                )

                                Box {
                                    WallpaperActionButton(
                                        icon = Icons.Default.Wallpaper,
                                        label = "Use as",
                                        onClick = { showUseAsMenu = true },
                                    )
                                    DropdownMenu(
                                        expanded = showUseAsMenu,
                                        onDismissRequest = { showUseAsMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Wallpaper") },
                                            onClick = {
                                                onSetWallpaper()
                                                showUseAsMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Lock screen") },
                                            onClick = {
                                                viewModel.setLockScreenOnly(imagePair.first)
                                                Toast
                                                    .makeText(
                                                        context,
                                                        "Lock screen updated! 🔒",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                                showUseAsMenu = false
                                            },
                                        )
                                    }
                                }

                                WallpaperActionButton(
                                    icon = if (isToRemove) Icons.Default.DeleteSweep else Icons.Outlined.DeleteOutline,
                                    label = "Trash",
                                    onClick = { viewModel.toggleToRemove(name) },
                                    tint = if (isToRemove) MaterialTheme.colorScheme.error else Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}
