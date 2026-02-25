package com.cheshire.wallpaperswitcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cheshire.wallpaperswitcher.ui.components.CurrentWallpaperCard
import com.cheshire.wallpaperswitcher.ui.components.EngineEnableCard
import com.cheshire.wallpaperswitcher.ui.components.EngineStatusSection
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel


/**
 * Main dashboard screen content.
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel,
    isEngineEnabled: Boolean,
    onSelectFolder: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Engine Status (Active label OR Enable Engine Card) at the top
        if (isEngineEnabled) {
            EngineStatusSection(
                managesLockScreen = viewModel.managesLockScreen
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (viewModel.folderUri == null) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = onSelectFolder) {
                    Text("Select Wallpaper Folder")
                }
            }
            return
        }
        if (isEngineEnabled) {
            CurrentWallpaperCard(
                name = viewModel.currentWallpaperName,
                uri = viewModel.currentWallpaperUri,
                metadata = viewModel.currentMetadata,
                isFavorite = viewModel.currentWallpaperName in viewModel.favoriteNames,
                isToRemove = viewModel.currentWallpaperName in viewModel.toRemoveNames,
                onToggleFavorite = { viewModel.toggleFavorite() },
                onToggleToRemove = { viewModel.toggleToRemove() }
            )
        } else {
            EngineEnableCard(
                context = LocalContext.current
            )
        }


        // Extra space at bottom to prevent FAB overlap
        Spacer(modifier = Modifier.height(80.dp))

    }
}
