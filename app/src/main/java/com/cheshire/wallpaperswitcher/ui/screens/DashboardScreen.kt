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
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (viewModel.folderUri == null) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Button(onClick = onSelectFolder) {
                    Text("Select Wallpaper Folder")
                }
            }
        } else {
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
            }

            EngineStatusSection(context = context, isEngineEnabled = isEngineEnabled)
            
            // Extra space at bottom
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
