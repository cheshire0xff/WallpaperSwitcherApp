package com.cheshire.wallpaperswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import com.cheshire.wallpaperswitcher.ui.screens.dashboard.DashboardScreen
import com.cheshire.wallpaperswitcher.ui.screens.dashboard.DashboardViewModel
import com.cheshire.wallpaperswitcher.ui.screens.grid.ImageGridScreen
import com.cheshire.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme

/**
 * Available screens in the app for navigation.
 */
enum class Screen {
    Dashboard,
    Queue,
    Favorites,
    History
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = WallpaperRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(repository) as T
            }
        }

        setContent {
            WallpaperSwitcherTheme {
                val viewModel: DashboardViewModel = viewModel(factory = factory)
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        WallpaperSwitcherApp(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WallpaperSwitcherApp(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel
) {
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    when (currentScreen) {
        Screen.Dashboard -> {
            DashboardScreen(
                modifier = modifier,
                viewModel = viewModel,
                onNavigate = { currentScreen = it }
            )
        }
        Screen.Queue -> {
            ImageGridScreen(
                title = "Upcoming Queue",
                images = viewModel.shuffledQueue,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.Favorites -> {
            val favImages = viewModel.cachedImages.filter { it.first.toString() in viewModel.favorites }
            ImageGridScreen(
                title = "Favorites",
                images = favImages,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
        Screen.History -> {
            val historyImages = viewModel.cachedImages.filter { it.first.toString() in viewModel.seenImageUris }
            ImageGridScreen(
                title = "History (Already Seen)",
                images = historyImages,
                viewModel = viewModel,
                onBack = { currentScreen = Screen.Dashboard }
            )
        }
    }
}
