package com.cheshire.wallpaperswitcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.cheshire.wallpaperswitcher.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    currentScreen: Screen,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(currentScreen.title) },
        navigationIcon = {
            if (currentScreen == Screen.Settings || currentScreen == Screen.Notch) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (currentScreen != Screen.Settings && currentScreen != Screen.Notch) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        },
    )
}
