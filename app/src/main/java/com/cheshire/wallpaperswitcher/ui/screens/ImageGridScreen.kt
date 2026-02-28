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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.cheshire.wallpaperswitcher.ui.components.EnlargedImageDialog
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
import com.cheshire.wallpaperswitcher.util.DLog
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Screen displaying a lazy-loaded grid of images.
 * Heavily optimized for huge datasets (10k+ images).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    images: List<Pair<Uri, String>>,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
) {
    var selectedImage by remember { mutableStateOf<Pair<Uri, String>?>(null) }
    val gridState = rememberLazyGridState()

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredImages =
        remember(images, searchQuery) {
            if (searchQuery.isBlank()) {
                images
            } else {
                images.filter { it.second.contains(searchQuery, ignoreCase = true) }
            }
        }

    // Handle back button: collapse search first if active
    BackHandler(onBack = {
        if (isSearchActive) {
            isSearchActive = false
        } else {
            onBack()
        }
    })

    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSearchActive) 0.dp else 16.dp)
                    .padding(bottom = if (isSearchActive) 0.dp else 8.dp),
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { isSearchActive = false },
                    expanded = isSearchActive,
                    onExpandedChange = { isSearchActive = it },
                    placeholder = { Text("Search wallpapers...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                )
            },
            expanded = isSearchActive,
            onExpandedChange = { isSearchActive = it },
        ) {
            // Live results in the search bar drop-down
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filteredImages.take(20)) { imagePair ->
                    ListItem(
                        headlineContent = {
                            Text(
                                imagePair.second,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(LocalContext.current)
                                        .data(imagePair.first)
                                        .size(100, 100)
                                        .precision(Precision.INEXACT)
                                        .build(),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        },
                        modifier =
                            Modifier.clickable {
                                selectedImage = imagePair
                                isSearchActive = false
                            },
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (filteredImages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No matching wallpapers",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Optimization: Grid content is isolated to prevent recomposing
                // the whole grid when the 'selectedImage' dialog state changes.
                WallpaperGrid(
                    images = filteredImages,
                    gridState = gridState,
                    onImageClick = { selectedImage = it },
                )

                VerticalGridScrollbar(
                    gridState = gridState,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(top = 8.dp, bottom = 48.dp, end = 12.dp),
                )
            }
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
            },
        )
    }
}

@Composable
private fun WallpaperGrid(
    images: List<Pair<Uri, String>>,
    gridState: LazyGridState,
    onImageClick: (Pair<Uri, String>) -> Unit,
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = images,
            key = { it.second },
            contentType = { "wallpaper_item" },
        ) { imagePair ->
            val request =
                remember(imagePair.first) {
                    ImageRequest
                        .Builder(context)
                        .data(imagePair.first)
                        .size(300, 500)
                        .precision(Precision.INEXACT)
                        // Optimization: Use RGB_565 for thumbnails.
                        // This uses 50% less memory than ARGB_8888 and is perfect for wallpapers.
                        .bitmapConfig(Bitmap.Config.RGB_565)
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
                modifier =
                    Modifier
                        .aspectRatio(0.6f)
                        .clickable { onImageClick(imagePair) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun VerticalGridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (gridState.isScrollInProgress || isDragging) 1f else 0f,
        animationSpec =
            if (gridState.isScrollInProgress || isDragging) {
                snap()
            } else {
                tween(durationMillis = 500)
            },
        label = "scrollbarAlpha",
    )

    if (scrollbarAlpha <= 0f && !isDragging) return

    val thumbSize = 40.dp

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxHeight()
                .width(48.dp)
                .graphicsLayer { alpha = scrollbarAlpha },
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }
        val availableTrack = trackHeightPx - thumbSizePx

        val scrollPosition by remember {
            derivedStateOf {
                val layoutInfo = gridState.layoutInfo
                val layoutDetails =
                    calculateLayoutDetails(layoutInfo)
                        ?: return@derivedStateOf 0f
                val currentPosition = layoutDetails.getCurrentPosition()
                DLog.d(
                    "VerticalGridScrollbar",
                    "pos: ${currentPosition.px}/${layoutDetails.maxScrollPositionPx} " +
                        "ratio: ${currentPosition.ratio} fvi: ${layoutDetails.firstItem.index} " +
                        " fvo: ${layoutDetails.firstItem.offset.y}",
                )
                currentPosition.ratio
            }
        }

        var dragOffsetPx by remember { mutableFloatStateOf(0f) }

        Box(
            modifier =
                Modifier
                    .size(thumbSize)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetPx else scrollPosition * availableTrack
                    }.clip(CircleShape)
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
                                dragOffsetPx =
                                    (dragOffsetPx + dragAmount.y).coerceIn(0f, availableTrack)

                                val layoutInfo = gridState.layoutInfo
                                val layoutDetails =
                                    calculateLayoutDetails(layoutInfo)
                                        ?: return@detectDragGestures

                                val newFraction =
                                    if (availableTrack > 0) {
                                        dragOffsetPx / availableTrack
                                    } else {
                                        0f
                                    }
                                val gridPosition = layoutDetails.calcPositionInGrid(newFraction)
                                DLog.d(
                                    "VerticalGridScrollbar",
                                    "dragOffsetPx: $dragOffsetPx/$availableTrack " +
                                        "fraction: $newFraction " +
                                        "targetScrollPx: ${gridPosition.gridPositionPx}/${layoutDetails.maxScrollPositionPx} " +
                                        "targetItem: ${gridPosition.itemIndex}/${layoutDetails.totalItems} " +
                                        "targetOffset: ${gridPosition.itemIndex}",
                                )

                                coroutineScope.launch {
                                    gridState.scrollToItem(
                                        gridPosition.itemIndex,
                                        gridPosition.itemOffset,
                                    )
                                }
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private data class ScrollPosition(
    val ratio: Float,
    val px: Int,
)

private data class GridPosition(
    val itemIndex: Int,
    val itemOffset: Int,
    val gridPositionPx: Int,
)

private data class LayoutDetails(
    val columns: Int,
    val rows: Int,
    val itemHeightPx: Int,
    val maxScrollPositionPx: Int,
    val totalItems: Int,
    val firstItem: LazyGridItemInfo,
    val lastItem: LazyGridItemInfo,
) {
    fun getCurrentPosition(): ScrollPosition {
        val calculatedPos = getCurrentScrollPx()
        val ratio = (calculatedPos.toFloat() / maxScrollPositionPx.toFloat()).coerceIn(0f, 1f)
        return ScrollPosition(ratio = ratio, px = calculatedPos)
    }

    fun calcPositionInGrid(ratio: Float): GridPosition {
        val targetScrollPx = ratio * maxScrollPositionPx

        val targetRowFloat = targetScrollPx / itemHeightPx
        val targetRow = floor(targetRowFloat).toInt()
        val targetItemFloat = targetRowFloat * columns
        val targetItem =
            targetItemFloat.toInt().coerceIn(0, totalItems)
        val targetOffsetRatio = targetRowFloat - targetRow
        val targetOffsetFloat =
            targetOffsetRatio * itemHeightPx
        val targetOffset = targetOffsetFloat.toInt()

        return GridPosition(
            itemIndex = targetItem,
            itemOffset = targetOffset,
            gridPositionPx = targetScrollPx.toInt(),
        )
    }

    fun getCurrentScrollPx(): Int {
        val offset = firstItem.offset.y
        val calculatedPos = itemHeightPx * firstItem.row - offset
        return calculatedPos
    }
}

// To properly work requires at least two rows ot items.
// Returns null otherwise.
private fun calculateLayoutDetails(layoutInfo: LazyGridLayoutInfo): LayoutDetails? {
    val visibleItems = layoutInfo.visibleItemsInfo
    // No items case.
    if (visibleItems.isEmpty()) {
        return null
    }
    val lastItem = visibleItems.last()
    if (lastItem.row == 0) {
        return null
    }
    val firstItem = visibleItems.first()
    // For example with 5 items like:
    // 0 1 2
    // 3 4
    // It will be 4 - 1 = 3
    // So we correctly get 3.
    val firstItemInLastRowIndex = lastItem.index - lastItem.column
    // For example with 5 items like:
    // 0 1 2 row 0
    // 3 4 5 row 1
    // 6     row 2
    // It will be 6 / 2 == 3
    val columns = firstItemInLastRowIndex / lastItem.row
    if (columns == 0) {
        // Just a sanity check.
        return null
    }
    val totalItems = layoutInfo.totalItemsCount
    val maxRows = totalItems / columns
    // True height with padding.
    val itemHeight = firstItem.size.height + layoutInfo.afterContentPadding
    val viewPortEndOffset = layoutInfo.viewportEndOffset
    // We can only scroll to grid size subtracted by what we can see.
    val maxScrollPositionPx = maxRows * itemHeight - viewPortEndOffset
    return LayoutDetails(
        columns = columns,
        rows = maxRows,
        itemHeightPx = itemHeight,
        maxScrollPositionPx = maxScrollPositionPx,
        totalItems = totalItems,
        firstItem = firstItem,
        lastItem = lastItem,
    )
}
