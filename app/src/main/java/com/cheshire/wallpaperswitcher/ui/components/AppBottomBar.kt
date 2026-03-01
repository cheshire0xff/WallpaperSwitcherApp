package com.cheshire.wallpaperswitcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cheshire.wallpaperswitcher.ui.Screen
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

@Composable
fun AppBottomBar(
    currentScreen: Screen,
    viewModel: WallpaperViewModel,
    onNavigate: (Screen) -> Unit,
) {
    if (viewModel.folderUri != null) {
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                label = { Text("Dash") },
                selected = currentScreen == Screen.Dashboard,
                onClick = { onNavigate(Screen.Dashboard) },
            )
            NavigationBarItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = "Queue",
                    )
                },
                label = { Text("Queue") },
                selected = currentScreen == Screen.Queue,
                onClick = { onNavigate(Screen.Queue) },
                enabled = viewModel.cachedImages.isNotEmpty(),
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Library") },
                label = { Text("Library") },
                selected = currentScreen == Screen.Library,
                onClick = { onNavigate(Screen.Library) },
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                label = { Text("History") },
                selected = currentScreen == Screen.History,
                onClick = { onNavigate(Screen.History) },
                enabled = viewModel.seenImageNames.isNotEmpty(),
            )
        }
    }
}
