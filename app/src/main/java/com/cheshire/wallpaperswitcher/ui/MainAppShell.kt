package com.cheshire.wallpaperswitcher.ui

import android.app.WallpaperInfo
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cheshire.wallpaperswitcher.service.ScrollingWallpaperService
import com.cheshire.wallpaperswitcher.ui.components.AppBottomBar
import com.cheshire.wallpaperswitcher.ui.components.AppTopBar
import com.cheshire.wallpaperswitcher.ui.components.InformationDialog
import com.cheshire.wallpaperswitcher.ui.screens.DashboardScreen
import com.cheshire.wallpaperswitcher.ui.screens.ImageGridScreen
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

/**
 * Available screens in the app for navigation.
 */
enum class Screen(val title: String) {
    Dashboard("Wallpaper Switcher"),
    Queue("Upcoming Queue"),
    Favorites("Favorites"),
    History("History"),
    ToRemove("To Remove")
}

@Composable
fun MainAppShell(viewModel: WallpaperViewModel) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }
    var isEngineEnabled by remember { mutableStateOf(isWallpaperEngineActive(context)) }
    var showInformation by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEngineEnabled = isWallpaperEngineActive(context)
                viewModel.updateLockScreenStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.updateFolderUri(uri)
        }
    }

    if (showInformation) {
        InformationDialog(
            totalImages = viewModel.cachedImages.size,
            availableSeenCount = viewModel.historyImages.size,
            totalSeenCount = viewModel.seenImageNames.size,
            availableFavoritesCount = viewModel.favoriteImages.size,
            totalFavoritesCount = viewModel.favoriteNames.size,
            availableToRemoveCount = viewModel.toRemoveImages.size,
            totalToRemoveCount = viewModel.toRemoveNames.size,
            appDataPath = viewModel.appDataDir,
            folderUri = viewModel.folderUri,
            onResetSeen = { viewModel.resetSeen() },
            onDismiss = { showInformation = false }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                currentScreen = currentScreen,
                onShowInformation = { showInformation = true },
                onChangeFolder = { launcher.launch(null) },
                onRestartEngine = {
                    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(context, ScrollingWallpaperService::class.java)
                    )
                    context.startActivity(intent)
                }
            )
        },
        bottomBar = {
            AppBottomBar(
                currentScreen = currentScreen,
                viewModel = viewModel,
                onNavigate = { currentScreen = it }
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            if (currentScreen == Screen.Dashboard && viewModel.folderUri != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!isEngineEnabled) {
                            Toast.makeText(
                                context,
                                "Please enable the engine first",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        viewModel.nextWallpaper()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(text = "Next Wallpaper")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavigationHost(
                currentScreen = currentScreen,
                viewModel = viewModel,
                isEngineEnabled = isEngineEnabled,
                onSelectFolder = { launcher.launch(null) },
                onNavigate = { currentScreen = it }
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
    onNavigate: (Screen) -> Unit
) {
    when (currentScreen) {
        Screen.Dashboard -> {
            DashboardScreen(
                viewModel = viewModel,
                isEngineEnabled = isEngineEnabled,
                onSelectFolder = onSelectFolder
            )
        }

        Screen.Queue -> {
            ImageGridScreen(
                images = viewModel.shuffledQueue,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) }
            )
        }

        Screen.Favorites -> {
            ImageGridScreen(
                images = viewModel.favoriteImages,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) }
            )
        }

        Screen.History -> {
            ImageGridScreen(
                images = viewModel.historyImages,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) }
            )
        }

        Screen.ToRemove -> {
            ImageGridScreen(
                images = viewModel.toRemoveImages,
                viewModel = viewModel,
                onBack = { onNavigate(Screen.Dashboard) }
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
