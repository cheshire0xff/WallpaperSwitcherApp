package com.cheshire.wallpaperswitcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

@Composable
fun LibraryScreen(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Favorites", "To Remove")

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) },
                )
            }
        }

        when (selectedTabIndex) {
            0 -> {
                ImageGridScreen(
                    images = viewModel.favoriteImages,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }

            1 -> {
                ImageGridScreen(
                    images = viewModel.toRemoveImages,
                    viewModel = viewModel,
                    onBack = onBack,
                )
            }
        }
    }
}
