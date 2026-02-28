package com.cheshire.wallpaperswitcher.data

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cheshire.wallpaperswitcher.service.ScrollingWallpaperService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WallpaperRepository"
private const val CACHE_FILE_NAME = "image_cache.txt"
private const val SEEN_IMAGES_FILE = "seen_images.txt"
private const val FAVORITES_FILE = "favorites.txt"
private const val TO_REMOVE_FILE = "to_remove.txt"

@Singleton
class WallpaperRepository @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
    private val dataStore: DataStore<Preferences>
) {

    private object PreferenceKeys {
        val FOLDER_URI = stringPreferencesKey("folder_uri")
        val CURRENT_WALLPAPER_NAME = stringPreferencesKey("current_wallpaper_name")
        val CURRENT_WALLPAPER_URI = stringPreferencesKey("current_wallpaper_uri")
        val LAST_MODIFIED = longPreferencesKey("last_modified")
    }

    private val fileMutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(fileName: String) = fileMutexes.computeIfAbsent(fileName) { Mutex() }

    val baseDir: File
        get() = context.getExternalFilesDir("wallpapers") ?: context.externalCacheDir
        ?: context.cacheDir

    suspend fun getFolderUri(): Uri? = dataStore.data
        .map { preferences -> preferences[PreferenceKeys.FOLDER_URI]?.toUri() }
        .first()

    suspend fun saveFolderUri(uri: Uri) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.FOLDER_URI] = uri.toString()
        }
    }

    suspend fun getSeenImages(): Set<String> = readSetFromFile(SEEN_IMAGES_FILE)

    suspend fun getFavoriteImages(): Set<String> = readSetFromFile(FAVORITES_FILE)

    suspend fun getToRemoveImages(): Set<String> = readSetFromFile(TO_REMOVE_FILE)

    suspend fun getCurrentWallpaperName(): String? = dataStore.data
        .map { preferences -> preferences[PreferenceKeys.CURRENT_WALLPAPER_NAME] }
        .first()

    suspend fun getCurrentWallpaperUri(): Uri? = dataStore.data
        .map { preferences -> preferences[PreferenceKeys.CURRENT_WALLPAPER_URI]?.toUri() }
        .first()

    suspend fun getLastModified(): Long = dataStore.data
        .map { preferences -> preferences[PreferenceKeys.LAST_MODIFIED] ?: -1L }
        .first()

    private suspend fun readSetFromFile(fileName: String): Set<String> =
        withContext(Dispatchers.IO) {
            mutexFor(fileName).withLock {
                val file = File(baseDir, fileName)
                if (!file.exists()) return@withLock emptySet()
                try {
                    file.readLines().filter { it.isNotBlank() }.toSet()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading $fileName: ${e.message}")
                    emptySet()
                }
            }
        }

    private suspend fun saveSetToFile(fileName: String, set: Set<String>) =
        withContext(Dispatchers.IO) {
            mutexFor(fileName).withLock {
                try {
                    val file = File(baseDir, fileName)
                    file.writeText(set.joinToString("\n"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving $fileName: ${e.message}")
                }
            }
        }

    suspend fun getDirectoryLastModified(uri: Uri): Long = withContext(Dispatchers.IO) {
        try {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else -1L
            } ?: -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last modified: ${e.message}")
            -1L
        }
    }

    suspend fun loadCache(): List<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        mutexFor(CACHE_FILE_NAME).withLock {
            val list = mutableListOf<Pair<Uri, String>>()
            try {
                val file = File(baseDir, CACHE_FILE_NAME)
                if (file.exists()) {
                    file.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            val parts = line.split("|", limit = 2)
                            if (parts.size == 2) {
                                list.add(parts[0].toUri() to parts[1])
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading cache: ${e.message}")
            }
            list
        }
    }

    suspend fun refreshCache(uri: Uri): List<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        val newList = mutableListOf<Pair<Uri, String>>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                uri,
                DocumentsContract.getTreeDocumentId(uri)
            )
            val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif")

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                val idCol =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol =
                    cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: ""
                    if (imageExtensions.any { name.endsWith(it, ignoreCase = true) }) {
                        val docId = cursor.getString(idCol)
                        newList.add(DocumentsContract.buildDocumentUriUsingTree(uri, docId) to name)
                    }
                }
            }

            val currentModified = getDirectoryLastModified(uri)
            saveCacheToFile(newList)
            dataStore.edit { preferences ->
                preferences[PreferenceKeys.LAST_MODIFIED] = currentModified
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing cache: ${e.message}")
        }
        newList
    }

    private suspend fun saveCacheToFile(list: List<Pair<Uri, String>>) =
        withContext(Dispatchers.IO) {
            mutexFor(CACHE_FILE_NAME).withLock {
                try {
                    val file = File(baseDir, CACHE_FILE_NAME)
                    file.bufferedWriter().use { writer ->
                        list.forEach { (uri, name) ->
                            writer.write("${uri}|${name}")
                            writer.newLine()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving cache to file: ${e.message}")
                }
            }
        }

    suspend fun getImageResolution(uri: Uri): String = withContext(Dispatchers.IO) {
        var size = "Unknown"
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                size = "${options.outWidth}x${options.outHeight}"
            }
        } catch (_: Exception) {
        }
        size
    }

    suspend fun getImageSize(uri: Uri): String = withContext(Dispatchers.IO) {
        var sizeMb = "0.0 MB"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex != -1) {
                    val sizeBytes = cursor.getLong(sizeIndex)
                    sizeMb =
                        String.format(Locale.US, "%.2f MB", sizeBytes.toDouble() / (1024 * 1024))
                }
            }
        } catch (_: Exception) {
        }
        sizeMb
    }

    suspend fun isManagingLockScreen(): Boolean = withContext(Dispatchers.IO) {
        val wm = WallpaperManager.getInstance(context)
        val packageName = context.packageName
        val serviceName = ScrollingWallpaperService::class.java.name

        // 1. Check if our service is explicitly set as a Live Wallpaper for either screen
        val systemInfo = wm.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM) ?: wm.wallpaperInfo
        val lockInfo = wm.getWallpaperInfo(WallpaperManager.FLAG_LOCK)

        val isOurSystem =
            systemInfo?.let { it.packageName == packageName && it.serviceName == serviceName }
                ?: false
        val isOurLock =
            lockInfo?.let { it.packageName == packageName && it.serviceName == serviceName }
                ?: false

        // 2. Check if the lock screen is currently "Inheriting" from the system wallpaper.
        // getWallpaperId(FLAG_LOCK) returns -1 if no specific wallpaper (static or live) is set for the lock screen.
        val hasSeparateLockWallpaper = wm.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0

        // We manage the lock screen if:
        // - It's explicitly set to our Live Wallpaper service.
        // - OR it's inherited from the system screen AND our service is set on the system screen.
        isOurLock || (isOurSystem && !hasSeparateLockWallpaper)
    }

    suspend fun updateCurrentWallpaper(uri: Uri, name: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.CURRENT_WALLPAPER_URI] = uri.toString()
            preferences[PreferenceKeys.CURRENT_WALLPAPER_NAME] = name
        }

        val updateIntent = Intent("com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER")
        updateIntent.setPackage(context.packageName)
        updateIntent.putExtra("uri", uri.toString())
        context.sendBroadcast(updateIntent)
    }

    suspend fun setLockScreen(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                wallpaperManager.setStream(inputStream, null, true, WallpaperManager.FLAG_LOCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting lock screen: ${e.message}")
        }
    }

    suspend fun saveSeenImages(seenNames: Set<String>) {
        saveSetToFile(SEEN_IMAGES_FILE, seenNames)
    }

    suspend fun saveFavorites(favNames: Set<String>) {
        saveSetToFile(FAVORITES_FILE, favNames)
    }

    suspend fun saveToRemoveImages(names: Set<String>) {
        saveSetToFile(TO_REMOVE_FILE, names)
    }
}
