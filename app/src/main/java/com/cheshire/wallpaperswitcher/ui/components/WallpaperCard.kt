package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    onClick: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Thumbnail Card containing Image and Icons
        ElevatedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clickable { onClick() },
            shape = RoundedCornerShape(28.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: The Image
                AsyncImage(
                    model = uri,
                    contentDescription = "Current Wallpaper",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Layer 2: Action Buttons (Bottom Layer inside Thumb)
                Row(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Favorite Toggle Button
                    FilledTonalIconButton(
                        onClick = onToggleFavorite,
                        colors =
                            if (isFavorite) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.4f),
                                    contentColor = Color.White,
                                )
                            },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                        )
                    }

                    // Remove Toggle Button
                    FilledTonalIconButton(
                        onClick = onToggleToRemove,
                        colors =
                            if (isToRemove) {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.4f),
                                    contentColor = Color.White,
                                )
                            },
                    ) {
                        Icon(
                            if (isToRemove) Icons.Default.DeleteSweep else Icons.Outlined.DeleteOutline,
                            contentDescription = "To Remove",
                        )
                    }
                }
            }
        }

        // Information Section (Outside the card, below the thumbnail)
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = name ?: "Unknown Wallpaper",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(0.7f),
            ) {
                Text(
                    text = metadata.fileSizeMb,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = metadata.dimensions,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
