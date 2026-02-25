package com.cheshire.wallpaperswitcher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cheshire.wallpaperswitcher.ui.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    currentScreen: Screen,
    onShowInformation: () -> Unit,
    onChangeFolder: () -> Unit,
    onRestartEngine: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(currentScreen.title) },
        actions = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Information") },
                    onClick = {
                        showMenu = false
                        onShowInformation()
                    },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Change Folder") },
                    onClick = {
                        showMenu = false
                        onChangeFolder()
                    },
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Restart Engine") },
                    onClick = {
                        showMenu = false
                        onRestartEngine()
                    },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                )
            }
        }
    )
}
