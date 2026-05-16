package com.cheshire.wallpaperswitcher.ui

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cheshire.wallpaperswitcher.service.ScrollingWallpaperService
import com.cheshire.wallpaperswitcher.ui.components.AppBottomBar
import com.cheshire.wallpaperswitcher.ui.components.AppTopBar
import com.cheshire.wallpaperswitcher.ui.screens.DashboardScreen
import com.cheshire.wallpaperswitcher.ui.screens.ImageGridScreen
import com.cheshire.wallpaperswitcher.ui.screens.LibraryScreen
import com.cheshire.wallpaperswitcher.ui.screens.NotchSettingsScreen
import com.cheshire.wallpaperswitcher.ui.screens.SettingsScreen
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

/**
 * Available screens in the app for navigation.
 */
enum class Screen(
    val title: String,
) {
    Dashboard("Wallpaper Switcher"),
    Queue("Upcoming Queue"),
    Library("Library"),
    History("History"),
    Settings("Settings"),
    Notch("Notch Settings"),
}

@Composable
fun MainAppShell(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var isEngineEnabled by remember { mutableStateOf(isWallpaperEngineActive(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isEngineEnabled = isWallpaperEngineActive(context)
                    viewModel.updateLockScreenStatus()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                viewModel.updateFolderUri(uri)
            }
        }

    Scaffold(
        topBar = {
            if (currentScreen != Screen.Notch) {
                AppTopBar(
                    currentScreen = currentScreen,
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onBack = {
                        if (currentScreen == Screen.Notch) {
                            currentScreen = Screen.Settings
                        } else {
                            currentScreen = Screen.Dashboard
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (currentScreen != Screen.Settings && currentScreen != Screen.Notch) {
                AppBottomBar(
                    currentScreen = currentScreen,
                    viewModel = viewModel,
                    onNavigate = { currentScreen = it },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (currentScreen == Screen.Dashboard && viewModel.folderUri != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isEngineEnabled) {
                            Toast
                                .makeText(
                                    context,
                                    "Please enable the engine first",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                        viewModel.nextWallpaper()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(text = "Next Wallpaper")
                }
            }
        },
    ) { innerPadding ->
        val contentPadding = if (currentScreen == Screen.Notch) PaddingValues(0.dp) else innerPadding
        Box(modifier = Modifier.padding(contentPadding)) {
            NavigationHost(
                currentScreen = currentScreen,
                viewModel = viewModel,
                isEngineEnabled = isEngineEnabled,
                onSelectFolder = { launcher.launch(null) },
                onNavigate = { currentScreen = it },
            )
        }
    }
}

@Composable
fun NavigationHost(
    currentScreen: Screen,
    viewModel: WallpaperViewModel,
    isEngineEnabled: Boolean,
    onSelectFolder: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val context = LocalContext.current
    when (currentScreen) {
        Screen.Dashboard -> {
            DashboardScreen(
                viewModel = viewModel,
                isEngineEnabled = isEngineEnabled,
                onSelectFolder = onSelectFolder,
            )
        }

        Screen.Queue -> {
            ImageGridScreen(
                images = viewModel.shuffledQueue,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) },
                showSort = false,
                showHideToRemoveOption = false,
            )
        }

        Screen.Library -> {
            LibraryScreen(
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) },
            )
        }

        Screen.History -> {
            ImageGridScreen(
                images = viewModel.historyImages,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) },
                initialHideToRemove = false,
            )
        }

        Screen.Settings -> {
            SettingsScreen(
                viewModel = viewModel,
                onSelectFolder = onSelectFolder,
                onRestartEngine = {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(context, ScrollingWallpaperService::class.java),
                    )
                    context.startActivity(intent)
                },
                onOpenNotchSettings = { onNavigate(Screen.Notch) },
                onBack = { onNavigate(Screen.Dashboard) },
            )
        }

        Screen.Notch -> {
            NotchSettingsScreen(
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Settings) },
            )
        }
    }
}

private fun isWallpaperEngineActive(context: Context): Boolean {
    val wm = context.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    val info: WallpaperInfo? = wm.wallpaperInfo
    return info != null && info.packageName == context.packageName &&
        info.serviceName == ScrollingWallpaperService::class.java.name
}
