package com.example.wallpaperswitcher

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class WallpaperViewModel(private val repository: WallpaperRepository) : ViewModel() {
    var folderUri by mutableStateOf<Uri?>(null)
        private set
    var cachedImages by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
        private set
    var isCaching by mutableStateOf(false)
        private set
    var currentWallpaperName by mutableStateOf<String?>(null)
        private set
    var currentWallpaperUri by mutableStateOf<Uri?>(null)
        private set
    var seenImageUris by mutableStateOf<Set<String>>(emptySet())
        private set
    var favorites by mutableStateOf<Set<String>>(emptySet())
        private set

    init {
        folderUri = repository.getFolderUri()
        currentWallpaperName = repository.getCurrentWallpaperName()
        currentWallpaperUri = repository.getCurrentWallpaperUri()
        seenImageUris = repository.getSeenImages()
        favorites = repository.getFavoriteImages()

        folderUri?.let { refreshCache() }
    }

    fun updateFolderUri(uri: Uri) {
        folderUri = uri
        refreshCache()
    }

    private fun refreshCache() {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isCaching = true
            val lastStoredModified = repository.getLastModified()
            val currentModified = repository.getDirectoryLastModified(uri)
            val loadedCache = repository.loadCache()

            if (currentModified != -1L && currentModified == lastStoredModified && loadedCache.isNotEmpty()) {
                cachedImages = loadedCache
                if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                    setInitialWallpaper()
                }
                isCaching = false
            } else {
                cachedImages = repository.refreshCache(uri)
                if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                    setInitialWallpaper()
                }
                isCaching = false
            }
        }
    }

    private fun setInitialWallpaper() {
        val pair = cachedImages.random()
        currentWallpaperUri = pair.first
        currentWallpaperName = pair.second
        repository.updateCurrentWallpaper(pair.first, pair.second)
    }

    fun nextWallpaper() {
        if (cachedImages.isEmpty()) return

        viewModelScope.launch {
            val unseenImages = cachedImages.filter { it.first.toString() !in seenImageUris }
            val targetList = if (unseenImages.isEmpty()) {
                seenImageUris = emptySet()
                repository.saveSeenImages(emptySet())
                cachedImages
            } else {
                unseenImages
            }

            val (randomUri, name) = targetList.random()
            
            // Resolution check
            repository.getImageResolution(randomUri)
            
            val newSeen = seenImageUris + randomUri.toString()
            seenImageUris = newSeen
            repository.saveSeenImages(newSeen)

            currentWallpaperUri = randomUri
            currentWallpaperName = name
            repository.updateCurrentWallpaper(randomUri, name)
        }
    }

    fun toggleFavorite() {
        val uriStr = currentWallpaperUri?.toString() ?: return
        val newFavorites = if (uriStr in favorites) {
            favorites - uriStr
        } else {
            favorites + uriStr
        }
        favorites = newFavorites
        repository.saveFavorites(newFavorites)
    }

    fun resetSeen() {
        seenImageUris = emptySet()
        repository.saveSeenImages(emptySet())
    }
}
