package com.cheshire.wallpaperswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import com.cheshire.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = WallpaperRepository(applicationContext)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WallpaperViewModel(repository) as T
            }
        }

        setContent {
            WallpaperSwitcherTheme {
                val viewModel: WallpaperViewModel = viewModel(factory = factory)
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainAppShell(viewModel)
                }
            }
        }
    }
}
