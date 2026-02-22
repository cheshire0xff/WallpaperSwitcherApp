package com.cheshire.wallpaperswitcher.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WallpaperRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: WallpaperRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = WallpaperRepository(context)
        
        // Clear files before each test to ensure isolation
        val baseDir = context.externalCacheDir ?: context.cacheDir
        File(baseDir, "seen_images.txt").delete()
        File(baseDir, "favorites.txt").delete()
        File(baseDir, "to_remove.txt").delete()
        File(baseDir, "image_cache.txt").delete()
        
        // Clear SharedPreferences
        context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `save and get folder uri`() {
        val uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")
        repository.saveFolderUri(uri)
        assertEquals(uri, repository.getFolderUri())
    }

    @Test
    fun `save and get seen images`() {
        val seen = setOf("image1.jpg", "image2.png")
        repository.saveSeenImages(seen)
        val retrieved = repository.getSeenImages()
        assertEquals(seen, retrieved)
    }

    @Test
    fun `save and get favorites`() {
        val favorites = setOf("fav1.webp", "fav2.jpg")
        repository.saveFavorites(favorites)
        val retrieved = repository.getFavoriteImages()
        assertEquals(favorites, retrieved)
    }

    @Test
    fun `save and get to remove images`() {
        val toRemove = setOf("bad1.jpg", "bad2.png")
        repository.saveToRemoveImages(toRemove)
        val retrieved = repository.getToRemoveImages()
        assertEquals(toRemove, retrieved)
    }

    @Test
    fun `update and get current wallpaper`() {
        val uri = Uri.parse("content://media/external/images/media/1")
        val name = "wallpaper.jpg"
        repository.updateCurrentWallpaper(uri, name)
        assertEquals(uri, repository.getCurrentWallpaperUri())
        assertEquals(name, repository.getCurrentWallpaperName())
    }

    @Test
    fun `loadCache returns empty list when file does not exist`() = runTest {
        val result = repository.loadCache()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadCache returns list from file`() = runTest {
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val cacheFile = File(baseDir, "image_cache.txt")
        cacheFile.writeText("content://uri1|image1.jpg\ncontent://uri2|image2.png")
        
        val result = repository.loadCache()
        assertEquals(2, result.size)
        assertEquals("content://uri1", result[0].first.toString())
        assertEquals("image1.jpg", result[0].second)
        assertEquals("content://uri2", result[1].first.toString())
        assertEquals("image2.png", result[1].second)
    }
    
    @Test
    fun `getSeenImages handles empty file`() {
        val baseDir = context.externalCacheDir ?: context.cacheDir
        File(baseDir, "seen_images.txt").writeText("")
        val retrieved = repository.getSeenImages()
        assertTrue(retrieved.isEmpty())
    }
}
