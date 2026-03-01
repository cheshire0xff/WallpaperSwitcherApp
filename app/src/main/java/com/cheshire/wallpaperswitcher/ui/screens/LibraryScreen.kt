package com.cheshire.wallpaperswitcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

@Composable
fun LibraryScreen(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
) {
    // Holds the currently selected tab index for the UI.
    // Initialized from the ViewModel when the Library screen enters composition.
    // Updating this variable triggers recomposition, so the TabRow will reflect the latest selected tab.
    var selectedTabIndex by remember { mutableIntStateOf(viewModel.libraryTabIndex) }

    // Creates a stable reference to the latest selectedTabIndex for use in side effects (e.g., onDispose).
    // Unlike selectedTabIndex, changing this value does NOT trigger recomposition.
    // Ensures that callbacks always see the most recent tab, even if the user switched tabs rapidly.
    val currentTabIndex by rememberUpdatedState(selectedTabIndex)
    val tabs = listOf("Favorites", "To Remove")

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveLibraryTabIndex(currentTabIndex)
        }
    }

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
