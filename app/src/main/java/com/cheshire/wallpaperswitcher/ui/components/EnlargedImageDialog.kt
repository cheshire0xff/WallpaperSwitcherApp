package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 */
@Composable
fun EnlargedImageDialog(
    imagePair: Pair<Uri, String>,
    viewModel: WallpaperViewModel,
    onDismiss: () -> Unit,
    onSetWallpaper: () -> Unit
) {
    val context = LocalContext.current
    var metadata by remember { mutableStateOf<WallpaperMetadata?>(null) }

    val name = imagePair.second
    val isFavorite = name in viewModel.favoriteNames
    val isToRemove = name in viewModel.toRemoveNames

    LaunchedEffect(imagePair.first) {
        metadata = viewModel.fetchMetadata(imagePair.first)
    }

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
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() },
                    contentScale = ContentScale.Fit
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = imagePair.second,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        metadata?.let { meta ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .alpha(0.8f)
                                    .padding(top = 4.dp)
                            ) {
                                Text(
                                    text = meta.fileSizeMb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = meta.dimensions,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Favorite Button
                            WallpaperActionButton(
                                icon = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                label = "Fav",
                                onClick = {
                                    viewModel.toggleFavorite(name)
                                },
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White
                            )

                            // Home Button
                            WallpaperActionButton(
                                icon = Icons.Default.Wallpaper,
                                label = "Home",
                                onClick = { onSetWallpaper() }
                            )

                            // Lock Button
                            WallpaperActionButton(
                                icon = Icons.Default.Lock,
                                label = "Lock",
                                onClick = {
                                    viewModel.setLockScreenOnly(imagePair.first)
                                    Toast.makeText(
                                        context,
                                        "Lock screen updated! 🔒",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            // Remove Button
                            WallpaperActionButton(
                                icon = if (isToRemove) Icons.Default.DeleteSweep else Icons.Outlined.DeleteOutline,
                                label = "Trash",
                                onClick = { viewModel.toggleToRemove(name) },
                                tint = if (isToRemove) MaterialTheme.colorScheme.error else Color.White
                            )
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
    tint: Color = Color.White
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
