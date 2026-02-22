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

    // A pre-shuffled list of images that haven't been seen yet.
    private var shuffledUnseenImages = mutableListOf<Pair<Uri, String>>()

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
                prepareShuffledList()
                if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                    setInitialWallpaper()
                }
                isCaching = false
            } else {
                cachedImages = repository.refreshCache(uri)
                prepareShuffledList()
                if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                    setInitialWallpaper()
                }
                isCaching = false
            }
        }
    }

    /**
     * Filters the cached images to find those not yet seen, shuffles them,
     * and stores them in shuffledUnseenImages.
     */
    private fun prepareShuffledList() {
        val unseen = cachedImages.filter { it.first.toString() !in seenImageUris }
        shuffledUnseenImages = unseen.shuffled().toMutableList()
    }

    private fun setInitialWallpaper() {
        if (cachedImages.isEmpty()) return
        
        // Use the shuffled list if available, otherwise fallback to cache
        val pair = if (shuffledUnseenImages.isNotEmpty()) {
            shuffledUnseenImages.removeAt(0)
        } else {
            cachedImages.random()
        }
        
        currentWallpaperUri = pair.first
        currentWallpaperName = pair.second
        
        // If we picked from shuffled, mark it as seen
        if (pair.first.toString() !in seenImageUris) {
            val newSeen = seenImageUris + pair.first.toString()
            seenImageUris = newSeen
            repository.saveSeenImages(newSeen)
        }
        
        repository.updateCurrentWallpaper(pair.first, pair.second)
    }

    fun nextWallpaper() {
        if (cachedImages.isEmpty()) return

        viewModelScope.launch {
            // If our pre-shuffled list is empty, regenerate it
            if (shuffledUnseenImages.isEmpty()) {
                val unseen = cachedImages.filter { it.first.toString() !in seenImageUris }
                if (unseen.isEmpty()) {
                    // All images seen, reset history and shuffle everything
                    resetSeen()
                    shuffledUnseenImages = cachedImages.shuffled().toMutableList()
                } else {
                    shuffledUnseenImages = unseen.shuffled().toMutableList()
                }
            }

            // Pick the next one from the top of the shuffled list
            val (randomUri, name) = shuffledUnseenImages.removeAt(0)
            
            // Log resolution (as requested previously)
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
        // Immediately regen the shuffled list since we reset the seen history
        prepareShuffledList()
    }
}
