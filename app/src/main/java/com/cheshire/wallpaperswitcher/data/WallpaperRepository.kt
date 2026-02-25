package com.cheshire.wallpaperswitcher.data

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "WallpaperRepository"
private const val CACHE_FILE_NAME = "image_cache.txt"
private const val SEEN_IMAGES_FILE = "seen_images.txt"
private const val FAVORITES_FILE = "favorites.txt"
private const val TO_REMOVE_FILE = "to_remove.txt"
private const val PREFS_NAME = "WallpaperPrefs"

class WallpaperRepository(val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val baseDir: File
        get() = context.externalCacheDir ?: context.cacheDir

    fun getFolderUri(): Uri? = prefs.getString("folder_uri", null)?.toUri()

    fun saveFolderUri(uri: Uri) {
        prefs.edit { putString("folder_uri", uri.toString()) }
    }

    fun getSeenImages(): Set<String> = readSetFromFile(SEEN_IMAGES_FILE)

    fun getFavoriteImages(): Set<String> = readSetFromFile(FAVORITES_FILE)

    fun getToRemoveImages(): Set<String> = readSetFromFile(TO_REMOVE_FILE)

    fun getCurrentWallpaperName(): String? = prefs.getString("current_wallpaper_name", null)

    fun getCurrentWallpaperUri(): Uri? = prefs.getString("current_wallpaper_uri", null)?.toUri()

    fun getLastModified(): Long = prefs.getLong("last_modified", -1L)

    private fun readSetFromFile(fileName: String): Set<String> {
        val file = File(baseDir, fileName)
        if (!file.exists()) return emptySet()
        return try {
            file.readLines().filter { it.isNotBlank() }.toSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading $fileName: ${e.message}")
            emptySet()
        }
    }

    private fun saveSetToFile(fileName: String, set: Set<String>) {
        try {
            val file = File(baseDir, fileName)
            file.writeText(set.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving $fileName: ${e.message}")
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
            prefs.edit { putLong("last_modified", currentModified) }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing cache: ${e.message}")
        }
        newList
    }

    private fun saveCacheToFile(list: List<Pair<Uri, String>>) {
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

    suspend fun getImageResolution(uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                "${options.outWidth}x${options.outHeight}"
            } ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun updateCurrentWallpaper(uri: Uri, name: String) {
        prefs.edit {
            putString("current_wallpaper_uri", uri.toString())
            putString("current_wallpaper_name", name)
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

    fun saveSeenImages(seenNames: Set<String>) {
        saveSetToFile(SEEN_IMAGES_FILE, seenNames)
    }

    fun saveFavorites(favNames: Set<String>) {
        saveSetToFile(FAVORITES_FILE, favNames)
    }

    fun saveToRemoveImages(names: Set<String>) {
        saveSetToFile(TO_REMOVE_FILE, names)
    }
}
