package com.cheshire.wallpaperswitcher.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Displays information about the current wallpaper and its thumbnail.
 */
@Composable
fun CurrentWallpaperCard(
    name: String?,
    uri: Uri?,
    isFavorite: Boolean,
    isCaching: Boolean,
    onToggleFavorite: () -> Unit,
    onEnlarge: (Uri, String) -> Unit
) {
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
                name?.let { fileName ->
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
                                text = fileName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Star else Icons.Outlined.Star,
                                contentDescription = "Toggle Favorite",
                                tint = if (isFavorite) Color.White else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.3f)
                            )
                        }
                    }

                    if (uri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = uri,
                            contentDescription = "Current wallpaper thumbnail",
                            modifier = Modifier
                                .height(320.dp)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onEnlarge(uri, fileName) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}
