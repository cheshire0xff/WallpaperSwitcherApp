package com.cheshire.wallpaperswitcher.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WallpaperRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: WallpaperRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = WallpaperRepository(context)

        // Clear files before each test to ensure isolation
        val baseDir = repository.baseDir
        File(baseDir, "seen_images.txt").delete()
        File(baseDir, "favorites.txt").delete()
        File(baseDir, "to_remove.txt").delete()
        File(baseDir, "image_cache.txt").delete()

        // Clear DataStore
        runTest {
            context.dataStore.edit { it.clear() }
        }
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun `save and get folder uri`() = runTest {
        val uri =
            Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")
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
    fun `update and get current wallpaper`() = runTest {
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
        val baseDir = repository.baseDir
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
        val baseDir = repository.baseDir
        File(baseDir, "seen_images.txt").writeText("")
        val retrieved = repository.getSeenImages()
        assertTrue(retrieved.isEmpty())
    }
}
