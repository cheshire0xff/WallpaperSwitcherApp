package com.cheshire.wallpaperswitcher.ui.viewmodel

import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import kotlinx.coroutines.launch

data class WallpaperMetadata(
    val fileSizeMb: String = "0.0 MB",
    val dimensions: String = "0x0"
)

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
    var currentMetadata by mutableStateOf(WallpaperMetadata())
        private set
    var seenImageNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var favoriteNames by mutableStateOf<Set<String>>(emptySet())
        private set
    var toRemoveNames by mutableStateOf<Set<String>>(emptySet())
        private set

    // Map of filename -> Uri for quick lookup
    private var imageMap = emptyMap<String, Uri>()

    // Derived states for library screens to avoid re-mapping on every recomposition
    val favoriteImages by derivedStateOf {
        favoriteNames.mapNotNull { name -> imageMap[name]?.let { it to name } }
    }
    
    val historyImages by derivedStateOf {
        seenImageNames.mapNotNull { name -> imageMap[name]?.let { it to name } }
    }
    
    val toRemoveImages by derivedStateOf {
        toRemoveNames.mapNotNull { name -> imageMap[name]?.let { it to name } }
    }

    // Exposed for the images screen
    var shuffledQueue by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
        private set

    // Manages the shuffling and non-repeating queue logic
    private val playlist = WallpaperPlaylist()

    init {
        folderUri = repository.getFolderUri()
        currentWallpaperName = repository.getCurrentWallpaperName()
        currentWallpaperUri = repository.getCurrentWallpaperUri()
        seenImageNames = repository.getSeenImages()
        favoriteNames = repository.getFavoriteImages()
        toRemoveNames = repository.getToRemoveImages()

        folderUri?.let { refreshCache() }
        currentWallpaperUri?.let { updateMetadata(it) }
    }

    fun updateFolderUri(uri: Uri) {
        folderUri = uri
        repository.saveFolderUri(uri)
        refreshCache()
    }

    private fun refreshCache() {
        val uri = folderUri ?: return
        viewModelScope.launch {
            isCaching = true
            val lastStoredModified = repository.getLastModified()
            val currentModified = repository.getDirectoryLastModified(uri)
            val loadedCache = repository.loadCache()

            cachedImages = if (currentModified != -1L && currentModified == lastStoredModified && loadedCache.isNotEmpty()) {
                loadedCache
            } else {
                repository.refreshCache(uri)
            }
            
            // Build the map: filename -> Uri
            imageMap = cachedImages.associate { it.second to it.first }
            
            // Sync the playlist with the new image list and current history
            playlist.updateData(cachedImages, seenImageNames)
            shuffledQueue = playlist.getQueue()
            
            if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                setInitialWallpaper()
            }
            isCaching = false
        }
    }

    private fun setInitialWallpaper() {
        val pair = playlist.getNext() ?: return
        shuffledQueue = playlist.getQueue()
        currentWallpaperUri = pair.first
        currentWallpaperName = pair.second
        
        markAsSeen(pair.second)
        repository.updateCurrentWallpaper(pair.first, pair.second)
        updateMetadata(pair.first)
    }

    fun nextWallpaper() {
        if (cachedImages.isEmpty()) return

        viewModelScope.launch {
            var pair = playlist.getNext()
            
            if (pair == null) {
                // All images in the current cycle seen, reset history and start over
                resetSeen()
                pair = playlist.getNext() ?: return@launch
            }

            shuffledQueue = playlist.getQueue()

            currentWallpaperUri = pair.first
            currentWallpaperName = pair.second
            
            markAsSeen(pair.second)
            repository.updateCurrentWallpaper(pair.first, pair.second)
            updateMetadata(pair.first)
        }
    }

    fun setWallpaper(pair: Pair<Uri, String>) {
        viewModelScope.launch {
            currentWallpaperUri = pair.first
            currentWallpaperName = pair.second
            
            markAsSeen(pair.second)
            repository.updateCurrentWallpaper(pair.first, pair.second)
            updateMetadata(pair.first)
            
            // Remove from queue if it was there
            playlist.removeFromQueue(pair.first)
            shuffledQueue = playlist.getQueue()
        }
    }

    private fun updateMetadata(uri: Uri) {
        viewModelScope.launch {
            val res = repository.getImageResolution(uri)
            // Get size from content provider
            var sizeMb = "0.0 MB"
            try {
                repository.context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst() && sizeIndex != -1) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        sizeMb = String.format("%.2f MB", sizeBytes.toDouble() / (1024 * 1024))
                    }
                }
            } catch (_: Exception) {}
            
            currentMetadata = WallpaperMetadata(sizeMb, res)
        }
    }
    
    private fun markAsSeen(name: String) {
        if (name !in seenImageNames) {
            val newSeen = seenImageNames + name
            seenImageNames = newSeen
            repository.saveSeenImages(newSeen)
            playlist.updateSeenHistory(newSeen)
        }
    }

    fun toggleFavorite() {
        val name = currentWallpaperName ?: return
        val newFavorites = if (name in favoriteNames) {
            favoriteNames - name
        } else {
            favoriteNames + name
        }
        favoriteNames = newFavorites
        repository.saveFavorites(newFavorites)
    }

    fun toggleToRemove() {
        val name = currentWallpaperName ?: return
        val newToRemove = if (name in toRemoveNames) {
            toRemoveNames - name
        } else {
            toRemoveNames + name
        }
        toRemoveNames = newToRemove
        repository.saveToRemoveImages(newToRemove)
    }

    fun resetSeen() {
        seenImageNames = emptySet()
        repository.saveSeenImages(emptySet())
        playlist.updateSeenHistory(emptySet())
        shuffledQueue = playlist.getQueue()
    }

}

/**
 * Internal helper class that manages a shuffled queue of images.
 * It automatically regens the queue when empty based on the unseen subset.
 */
private class WallpaperPlaylist {
    private var allImages: List<Pair<Uri, String>> = emptyList()
    private var seenNames: Set<String> = emptySet()
    private val queue = mutableListOf<Pair<Uri, String>>()

    /**
     * Updates the master list and history, and regenerates the queue.
     */
    fun updateData(all: List<Pair<Uri, String>>, seen: Set<String>) {
        allImages = all
        seenNames = seen
        regenerateQueue()
    }

    /**
     * Updates the history set. If the history is cleared, regens the queue immediately.
     */
    fun updateSeenHistory(seen: Set<String>) {
        seenNames = seen
        if (seen.isEmpty()) regenerateQueue()
    }

    private fun regenerateQueue() {
        queue.clear()
        queue.addAll(allImages.filter { it.second !in seenNames }.shuffled())
    }

    /**
     * Returns the next image from the shuffled queue. 
     * If the queue is empty, it attempts to regenerate from unseen images.
     * Returns null only if NO unseen images exist.
     */
    fun getNext(): Pair<Uri, String>? {
        if (queue.isEmpty()) {
            regenerateQueue()
        }
        return if (queue.isNotEmpty()) queue.removeAt(0) else null
    }

    fun getQueue(): List<Pair<Uri, String>> = queue.toList()

    fun removeFromQueue(uri: Uri) {
        queue.removeAll { it.first == uri }
    }
}
