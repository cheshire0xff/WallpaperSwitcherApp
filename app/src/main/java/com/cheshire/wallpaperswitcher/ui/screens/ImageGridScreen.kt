package com.cheshire.wallpaperswitcher.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.components.EnlargedImageDialog
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
import kotlinx.coroutines.launch

/**
 * Screen displaying a lazy-loaded grid of images.
 */
@Composable
fun ImageGridScreen(
    images: List<Pair<Uri, String>>,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    var selectedImage by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val gridState = rememberLazyGridState()

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        if (images.isEmpty()) {
            Text(
                "No images to display",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(images) { imagePair ->
                    AsyncImage(
                        model = imagePair.first,
                        contentDescription = imagePair.second,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { selectedImage = imagePair },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            VerticalGridScrollbar(
                gridState = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 8.dp, bottom = 48.dp, end = 12.dp) // More bottom padding and moved from edge
            )
        }
    }

    selectedImage?.let { image ->
        EnlargedImageDialog(
            imagePair = image,
            onDismiss = { selectedImage = null },
            viewModel = viewModel,
            onSetWallpaper = {
                viewModel.setWallpaper(image)
                selectedImage = null
                onBack()
            }
        )
    }
}

@Composable
private fun VerticalGridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (gridState.isScrollInProgress || isDragging) 1f else 0f,
        animationSpec = if (gridState.isScrollInProgress || isDragging) {
            snap()
        } else {
            tween(durationMillis = 500)
        },
        label = "scrollbarAlpha"
    )

    // Keep it composed if it's being dragged even if grid thinks it's not scrolling
    if (scrollbarAlpha <= 0f && !isDragging) return

    val layoutInfo = gridState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return

    val firstItem = gridState.firstVisibleItemIndex
    val totalVisibleItems = visibleItems.size
    
    if (totalVisibleItems >= totalItems) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp) // Large touch target width
            .alpha(scrollbarAlpha)
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbSize = 40.dp
        val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
        
        // Calculate the max index we can scroll to (first visible item)
        val maxScrollIndex = (totalItems - totalVisibleItems).coerceAtLeast(1)
        val offsetFraction = (firstItem.toFloat() / maxScrollIndex).coerceIn(0f, 1f)
        
        val availableTrack = trackHeightPx - thumbSizePx
        var thumbOffsetPx by remember { mutableStateOf(offsetFraction * availableTrack) }
        
        // Sync with grid scroll when NOT dragging
        LaunchedEffect(firstItem, trackHeightPx) {
            if (!isDragging) {
                thumbOffsetPx = offsetFraction * availableTrack
            }
        }

        Box(
            modifier = Modifier
                .offset(y = with(LocalDensity.current) { thumbOffsetPx.toDp() })
                .size(thumbSize)
                .align(Alignment.TopCenter)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f))
                .pointerInput(totalItems, trackHeightPx) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            thumbOffsetPx = (thumbOffsetPx + dragAmount.y).coerceIn(0f, availableTrack)
                            
                            val newFraction = if (availableTrack > 0) thumbOffsetPx / availableTrack else 0f
                            val targetItem = (newFraction * maxScrollIndex).toInt()
                            
                            coroutineScope.launch {
                                gridState.scrollToItem(targetItem.coerceIn(0, totalItems - 1))
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
