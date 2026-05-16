package com.cheshire.wallpaperswitcher.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cheshire.wallpaperswitcher.BuildConfig
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

@Composable
fun SettingsScreen(
    viewModel: WallpaperViewModel,
    onSelectFolder: () -> Unit,
    onRestartEngine: () -> Unit,
    onBack: () -> Unit,
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = packageInfo.versionName ?: "1.0.4-stable"
    val gitHash = BuildConfig.GIT_HASH

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        // Section: Library
        SectionHeader("Library")
        ListItem(
            headlineContent = { Text("Image Folder") },
            supportingContent = {
                Text(viewModel.folderUri?.toString()?.let { Uri.decode(it) } ?: "No folder selected")
            },
            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
            modifier = Modifier.clickable { onSelectFolder() },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Section: Stats
        SectionHeader("Stats")
        StatItem(
            label = "Total Images",
            value = viewModel.cachedImages.size.toString(),
        )
        StatItem(
            label = "New (Unseen)",
            value = (viewModel.cachedImages.size - viewModel.historyImages.size).toString(),
        )
        StatItem(
            label = "History Size",
            value = "${viewModel.historyImages.size} available",
            supportingText = "(${viewModel.seenImageNames.size} total seen)",
        )
        StatItem(
            label = "Favourites",
            value = "${viewModel.favoriteImages.size} available",
            supportingText = "(${viewModel.favoriteNames.size} total favorites)",
        )
        StatItem(
            label = "Marked for deletion",
            value = "${viewModel.toRemoveImages.size} available",
            supportingText = "(${viewModel.toRemoveNames.size} total to remove)",
        )
        StatItem(
            label = "Queue Size",
            value = viewModel.shuffledQueue.size.toString(),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Section: Advanced
        SectionHeader("Advanced")
        ListItem(
            headlineContent = { Text("App Data Path") },
            supportingContent = { Text(viewModel.appDataDir) },
        )
        ListItem(
            headlineContent = {
                Text("Reset Seen History", color = MaterialTheme.colorScheme.error)
            },
            supportingContent = { Text("Destructive action - start over") },
            leadingContent = {
                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            modifier = Modifier.clickable { showResetDialog = true },
        )
        ListItem(
            headlineContent = { Text("Restart Engine") },
            supportingContent = { Text("Service refresh") },
            leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
            modifier = Modifier.clickable { showRestartDialog = true },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Section: About
        SectionHeader("About")
        ListItem(
            headlineContent = { Text("Version") },
            supportingContent = { Text("$versionName ($gitHash)") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset History?") },
            text = { Text("Are you sure? This will clear your entire seen history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetSeen()
                        showResetDialog = false
                    },
                ) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text("Restart Engine?") },
            text = { Text("This will open the wallpaper selection screen to refresh the service.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRestartEngine()
                        showRestartDialog = false
                    },
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    supportingText: String? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = supportingText?.let { { Text(it) } },
    )
}
