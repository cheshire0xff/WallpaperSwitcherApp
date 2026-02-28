package com.cheshire.wallpaperswitcher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cheshire.wallpaperswitcher.ui.theme.WallpaperSwitcherTheme
import com.cheshire.wallpaperswitcher.ui.viewmodel.WallpaperViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WallpaperSwitcherTheme {
                val viewModel: WallpaperViewModel = viewModel()
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainAppShell(viewModel)
                }
            }
        }
    }
}
