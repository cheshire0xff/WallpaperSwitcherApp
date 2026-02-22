package com.cheshire.wallpaperswitcher.ui.screens.grid

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheshire.wallpaperswitcher.ui.components.EnlargedImageDialog
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
/**
 * Screen displaying a lazy-loaded grid of images.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen(
    title: String,
    images: List<Pair<Uri, String>>,
    viewModel: WallpaperViewModel,
    onBack: () -> Unit
) {
    var selectedImage by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (images.isEmpty()) {
                Text(
                    "No images to display",
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyVerticalGrid(
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
            }
        }
    }

    if (selectedImage != null) {
        EnlargedImageDialog(
            imagePair = selectedImage!!,
            onDismiss = { selectedImage = null },
            onSetWallpaper = {
                viewModel.setWallpaper(selectedImage!!)
                selectedImage = null
                onBack() 
            }
        )
    }
}
