package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperMetadata

@Composable
fun CurrentWallpaperCard(
    modifier: Modifier = Modifier,
    name: String?,
    uri: Uri?,
    metadata: WallpaperMetadata,
    isFavorite: Boolean,
    isToRemove: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleToRemove: () -> Unit,
    onClick: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Thumbnail Card containing Image and Icons
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clickable { onClick() },
            shape = RoundedCornerShape(28.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: The Image
                AsyncImage(
                    model = uri,
                    contentDescription = "Current Wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Layer 2: Status Badges (Top Layer)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Favorite Badge (Left)
                    if (isFavorite) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.size(32.dp))
                    }

                    // Removal Badge (Right)
                    if (isToRemove) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Layer 3: Action Buttons (Bottom Layer inside Thumb)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Favorite Toggle Button
                    FilledTonalIconButton(
                        onClick = onToggleFavorite,
                        colors = if (isFavorite) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f),
                                contentColor = Color.White
                            )
                        }
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite"
                        )
                    }

                    // Remove Toggle Button
                    FilledTonalIconButton(
                        onClick = onToggleToRemove,
                        colors = if (isToRemove) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.4f),
                                contentColor = Color.White
                            )
                        }
                    ) {
                        Icon(
                            if (isToRemove) Icons.Default.DeleteSweep else Icons.Outlined.DeleteOutline,
                            contentDescription = "To Remove"
                        )
                    }
                }
            }
        }

        // Information Section (Outside the card, below the thumbnail)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name ?: "Unknown Wallpaper",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(0.7f)
            ) {
                Text(
                    text = metadata.fileSizeMb,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = metadata.dimensions,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
