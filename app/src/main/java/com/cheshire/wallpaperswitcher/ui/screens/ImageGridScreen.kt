package com.cheshire.wallpaperswitcher.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.cheshire.wallpaperswitcher.ui.components.EnlargedImageDialog
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
import kotlinx.coroutines.launch

/**
 * Screen displaying a lazy-loaded grid of images.
 * Heavily optimized for huge datasets (10k+ images).
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
            // Optimization: Grid content is isolated to prevent recomposing 
            // the whole grid when the 'selectedImage' dialog state changes.
            WallpaperGrid(
                images = images,
                gridState = gridState,
                onImageClick = { selectedImage = it }
            )

            VerticalGridScrollbar(
                gridState = gridState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 8.dp, bottom = 48.dp, end = 12.dp)
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
private fun WallpaperGrid(
    images: List<Pair<Uri, String>>,
    gridState: LazyGridState,
    onImageClick: (Pair<Uri, String>) -> Unit
) {
    val context = LocalContext.current
    
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = images,
            key = { it.second },
            contentType = { "wallpaper_item" }
        ) { imagePair ->
            val request = remember(imagePair.first) {
                ImageRequest.Builder(context)
                    .data(imagePair.first)
                    .size(300, 500)
                    .precision(Precision.INEXACT)
                    .allowHardware(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    // Short crossfade is snappier for fast scrolling
                    .crossfade(150)
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = imagePair.second,
                modifier = Modifier
                    .aspectRatio(0.6f)
                    .clickable { onImageClick(imagePair) },
                contentScale = ContentScale.Crop
            )
        }
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

    if (scrollbarAlpha <= 0f && !isDragging) return

    val thumbSize = 40.dp
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .graphicsLayer { alpha = scrollbarAlpha }
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
        val availableTrack = trackHeightPx - thumbSizePx

        val scrollPosition by remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val visibleItems = layoutInfo.visibleItemsInfo
                
                if (totalItems == 0 || visibleItems.isEmpty()) 0f
                else {
                    val firstItem = gridState.firstVisibleItemIndex
                    val totalVisibleItems = visibleItems.size
                    val maxScrollIndex = (totalItems - totalVisibleItems).coerceAtLeast(1)
                    (firstItem.toFloat() / maxScrollIndex).coerceIn(0f, 1f)
                }
            }
        }

        var dragOffsetPx by remember { mutableFloatStateOf(0f) }

        Box(
            modifier = Modifier
                .size(thumbSize)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffsetPx else scrollPosition * availableTrack
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.8f))
                .pointerInput(trackHeightPx) {
                    detectDragGestures(
                        onDragStart = { 
                            isDragging = true
                            dragOffsetPx = scrollPosition * availableTrack
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx = (dragOffsetPx + dragAmount.y).coerceIn(0f, availableTrack)
                            
                            val newFraction = if (availableTrack > 0) dragOffsetPx / availableTrack else 0f
                            val currentTotal = gridState.layoutInfo.totalItemsCount
                            val currentVisible = gridState.layoutInfo.visibleItemsInfo.size
                            val maxScrollIdx = (currentTotal - currentVisible).coerceAtLeast(1)
                            val targetItem = (newFraction * maxScrollIdx).toInt()

                            coroutineScope.launch {
                                gridState.scrollToItem(targetItem.coerceIn(0, currentTotal - 1))
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
