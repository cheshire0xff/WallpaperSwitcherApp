package com.cheshire.wallpaperswitcher.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheshire.wallpaperswitcher.data.SetImageRequest
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WallpaperMetadata(
    val fileSizeMb: String = "0.0 MB",
    val dimensions: String = "0x0",
)

enum class SortOption(
    val displayName: String,
) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    TIME_ADDED_ASC("Oldest First"),
    TIME_ADDED_DESC("Newest First"),
}

@HiltViewModel
class WallpaperViewModel
    @Inject
    constructor(
        private val repository: WallpaperRepository,
    ) : ViewModel() {
        val appDataDir: String = repository.baseDir.absolutePath

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
        var currentWallpaperFlipped by mutableStateOf(false)
            private set
        var currentMetadata by mutableStateOf(WallpaperMetadata())
            private set
        var seenImageNames by mutableStateOf<Set<String>>(emptySet())
            private set
        var favoriteNames by mutableStateOf<Set<String>>(emptySet())
            private set
        var toRemoveNames by mutableStateOf<Set<String>>(emptySet())
            private set

        var managesLockScreen by mutableStateOf(false)
            private set

        var libraryTabIndex by mutableIntStateOf(0)
            private set

        var allImagesSortOption by mutableStateOf(SortOption.TIME_ADDED_DESC)
            private set
        var favoritesSortOption by mutableStateOf(SortOption.TIME_ADDED_DESC)
            private set
        var historySortOption by mutableStateOf(SortOption.TIME_ADDED_DESC)
            private set
        var toRemoveSortOption by mutableStateOf(SortOption.TIME_ADDED_DESC)
            private set

        // Map of filename -> Uri for quick lookup, derived from cachedImages
        private val imageMap by derivedStateOf {
            cachedImages.associate { it.second to it.first }
        }

        private fun List<Pair<Uri, String>>.applySort(option: SortOption): List<Pair<Uri, String>> =
            when (option) {
                SortOption.NAME_ASC -> sortedBy { it.second }
                SortOption.NAME_DESC -> sortedByDescending { it.second }
                SortOption.TIME_ADDED_ASC -> this
                SortOption.TIME_ADDED_DESC -> reversed()
            }

        val allImagesSorted by derivedStateOf {
            cachedImages.applySort(allImagesSortOption)
        }

        // Derived states for library screens to avoid re-mapping on every recomposition
        val favoriteImages by derivedStateOf {
            favoriteNames.mapNotNull { name -> imageMap[name]?.let { it to name } }.applySort(favoritesSortOption)
        }

        val historyImages by derivedStateOf {
            seenImageNames.mapNotNull { name -> imageMap[name]?.let { it to name } }.applySort(historySortOption)
        }

        val toRemoveImages by derivedStateOf {
            toRemoveNames.mapNotNull { name -> imageMap[name]?.let { it to name } }.applySort(toRemoveSortOption)
        }

        // Exposed for the images screen
        var shuffledQueue by mutableStateOf<List<Pair<Uri, String>>>(emptyList())
            private set

        // Manages the shuffling and non-repeating queue logic
        private val playlist = WallpaperPlaylist()

        init {
            viewModelScope.launch {
                folderUri = repository.getFolderUri().first()
                currentWallpaperName = repository.getCurrentWallpaperName().first()
                currentWallpaperUri = repository.getCurrentWallpaperUri().first()
                currentWallpaperFlipped = repository.isCurrentWallpaperFlipped().first()
                seenImageNames = repository.getSeenImages()
                favoriteNames = repository.getFavoriteImages()
                toRemoveNames = repository.getToRemoveImages()
                libraryTabIndex = repository.getLibraryTabIndex().first()

                folderUri?.let { refreshCache() }
                currentWallpaperUri?.let { updateMetadata(it) }
                updateLockScreenStatus()
            }
        }

        fun updateLockScreenStatus() {
            viewModelScope.launch {
                managesLockScreen = repository.isManagingLockScreen()
            }
        }

        fun updateFolderUri(uri: Uri) {
            folderUri = uri
            viewModelScope.launch {
                repository.saveFolderUri(uri)
                refreshCache()
            }
        }

        private fun refreshCache() {
            val uri = folderUri ?: return
            viewModelScope.launch {
                isCaching = true
                val cache = repository.loadCache()
                val currentModified = repository.getDirectoryLastModified(uri)

                val isCacheValid =
                    cache.folderUri != null &&
                        cache.folderUri == uri &&
                        cache.lastModified != null &&
                        currentModified != null &&
                        currentModified == cache.lastModified &&
                        cache.images.isNotEmpty()

                cachedImages =
                    if (isCacheValid) {
                        cache.images
                    } else {
                        repository.refreshCache(uri)
                    }

                // Sync the playlist with the new image list and current history
                playlist.updateData(cachedImages, seenImageNames, toRemoveNames)
                shuffledQueue = playlist.getQueue()

                if (currentWallpaperUri == null && cachedImages.isNotEmpty()) {
                    setInitialWallpaper()
                }
                isCaching = false
            }
        }

        private suspend fun setInitialWallpaper() {
            val pair = playlist.getNext() ?: return
            shuffledQueue = playlist.getQueue()
            currentWallpaperUri = pair.first
            currentWallpaperName = pair.second
            currentWallpaperFlipped = false

            markAsSeen(pair.second)
            repository.updateCurrentWallpaper(pair.first, pair.second, false)
            updateMetadata(pair.first)
        }

        fun nextWallpaper() {
            if (cachedImages.isEmpty()) return
            viewModelScope.launch {
                val pair =
                    playlist.getNext() ?: run {
                        resetSeen()
                        playlist.getNext() ?: return@launch
                    }
                markAsSeen(pair.second)
                repository.updateCurrentWallpaper(pair.first, pair.second, false)
                updateMetadata(pair.first)
                shuffledQueue = playlist.getQueue()
                currentWallpaperUri = pair.first
                currentWallpaperName = pair.second
                currentWallpaperFlipped = false
            }
        }

        fun setWallpaper(
            request: SetImageRequest,
            name: String,
        ) {
            viewModelScope.launch {
                currentWallpaperUri = request.uri
                currentWallpaperName = name
                currentWallpaperFlipped = request.isFlipped

                markAsSeen(name)
                repository.updateCurrentWallpaper(request.uri, name, request.isFlipped)
                updateMetadata(request.uri)

                // Remove from queue if it was there
                playlist.removeFromQueue(request.uri)
                shuffledQueue = playlist.getQueue()
            }
        }

        fun setLockScreenOnly(request: SetImageRequest) {
            viewModelScope.launch {
                repository.setLockScreen(request)
                updateLockScreenStatus()
            }
        }

        private suspend fun updateMetadata(uri: Uri) {
            currentMetadata = fetchMetadata(uri)
        }

        suspend fun fetchMetadata(uri: Uri): WallpaperMetadata =
            coroutineScope {
                val res = async { repository.getImageResolution(uri) }
                val sizeMb = async { repository.getImageSize(uri) }
                WallpaperMetadata(sizeMb.await(), res.await())
            }

        private suspend fun markAsSeen(name: String) {
            if (name !in seenImageNames) {
                val newSeen = seenImageNames + name
                seenImageNames = newSeen
                repository.saveSeenImages(newSeen)
                playlist.updateSeenHistory(newSeen)
            }
        }

        fun toggleFavorite(name: String? = null) {
            val targetName = name ?: currentWallpaperName ?: return
            val newFavorites =
                if (targetName in favoriteNames) {
                    favoriteNames - targetName
                } else {
                    favoriteNames + targetName
                }
            favoriteNames = newFavorites
            viewModelScope.launch {
                repository.saveFavorites(newFavorites)
            }
        }

        fun toggleToRemove(name: String? = null) {
            val targetName = name ?: currentWallpaperName ?: return
            val newToRemove =
                if (targetName in toRemoveNames) {
                    toRemoveNames - targetName
                } else {
                    toRemoveNames + targetName
                }
            toRemoveNames = newToRemove
            viewModelScope.launch {
                repository.saveToRemoveImages(newToRemove)
            }
            playlist.updateToRemoveNames(newToRemove)
            shuffledQueue = playlist.getQueue()
        }

        fun resetSeen() {
            seenImageNames = emptySet()
            viewModelScope.launch {
                repository.saveSeenImages(emptySet())
            }
            playlist.updateSeenHistory(emptySet())
            shuffledQueue = playlist.getQueue()
        }

        fun saveLibraryTabIndex(index: Int) {
            libraryTabIndex = index
            viewModelScope.launch {
                repository.saveLibraryTabIndex(index)
            }
        }

        fun updateAllImagesSortOption(option: SortOption) {
            allImagesSortOption = option
        }

        fun updateFavoritesSortOption(option: SortOption) {
            favoritesSortOption = option
        }

        fun updateHistorySortOption(option: SortOption) {
            historySortOption = option
        }

        fun updateToRemoveSortOption(option: SortOption) {
            toRemoveSortOption = option
        }
    }

/**
 * Internal helper class that manages a shuffled queue of images.
 * It automatically regens the queue when empty based on the unseen subset.
 */
private class WallpaperPlaylist {
    private var allImages: List<Pair<Uri, String>> = emptyList()
    private var seenNames: Set<String> = emptySet()
    private var toRemoveNames: Set<String> = emptySet()
    private val queue = mutableListOf<Pair<Uri, String>>()

    /**
     * Updates the master list and history, and regenerates the queue.
     */
    fun updateData(
        all: List<Pair<Uri, String>>,
        seen: Set<String>,
        toRemove: Set<String>,
    ) {
        allImages = all
        seenNames = seen
        toRemoveNames = toRemove
        regenerateQueue()
    }

    /**
     * Updates the history set. If the history is cleared, regens the queue immediately.
     */
    fun updateSeenHistory(seen: Set<String>) {
        seenNames = seen
        if (seen.isEmpty()) regenerateQueue()
    }

    fun updateToRemoveNames(toRemove: Set<String>) {
        toRemoveNames = toRemove
        // Remove any newly added 'toRemove' items from the active queue
        queue.removeAll { it.second in toRemoveNames }
    }

    private fun regenerateQueue() {
        queue.clear()
        queue.addAll(allImages.filter { it.second !in seenNames && it.second !in toRemoveNames }.shuffled())
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
