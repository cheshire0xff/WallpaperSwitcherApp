package com.cheshire.wallpaperswitcher.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WallpaperViewModelTest {

    private lateinit var viewModel: WallpaperViewModel
    private lateinit var repository: WallpaperRepository
    private lateinit var context: Context
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        repository = WallpaperRepository(context)
        
        // Clear SharedPreferences and files
        context.getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE).edit().clear().commit()
        val baseDir = context.externalCacheDir ?: context.cacheDir
        baseDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initialization loads data from repository`() = runTest {
        val folderUri = Uri.parse("content://folder")
        repository.saveFolderUri(folderUri)
        repository.updateCurrentWallpaper(Uri.parse("content://curr"), "current.jpg")
        repository.saveFavorites(setOf("fav.jpg"))
        
        viewModel = WallpaperViewModel(repository)

        
        assertEquals(folderUri, viewModel.folderUri)
        assertEquals("current.jpg", viewModel.currentWallpaperName)
        assertTrue(viewModel.favoriteNames.contains("fav.jpg"))
    }

    @Test
    fun `updateFolderUri saves to repository`() = runTest {
        viewModel = WallpaperViewModel(repository)

        val newUri = Uri.parse("content://new_folder")
        
        viewModel.updateFolderUri(newUri)

        
        assertEquals(newUri, repository.getFolderUri())
        assertEquals(newUri, viewModel.folderUri)
    }

    @Test
    fun `toggleFavorite updates state and repository`() = runTest {
        repository.updateCurrentWallpaper(Uri.parse("content://img"), "img.jpg")
        viewModel = WallpaperViewModel(repository)
        advanceUntilIdle()
        
        // Add to favorite
        viewModel.toggleFavorite()
        assertTrue(viewModel.favoriteNames.contains("img.jpg"))
        assertTrue(repository.getFavoriteImages().contains("img.jpg"))
        
        // Remove from favorite
        viewModel.toggleFavorite()
        assertFalse(viewModel.favoriteNames.contains("img.jpg"))
        assertFalse(repository.getFavoriteImages().contains("img.jpg"))
    }

    @Test
    fun `toggleToRemove updates state and repository`() = runTest {
        repository.updateCurrentWallpaper(Uri.parse("content://bad"), "bad_img.jpg")
        viewModel = WallpaperViewModel(repository)

        
        viewModel.toggleToRemove()
        assertTrue(viewModel.toRemoveNames.contains("bad_img.jpg"))
        assertTrue(repository.getToRemoveImages().contains("bad_img.jpg"))
        
        viewModel.toggleToRemove()
        assertFalse(viewModel.toRemoveNames.contains("bad_img.jpg"))
        assertFalse(repository.getToRemoveImages().contains("bad_img.jpg"))
    }

    @Test
    fun `resetSeen clears state and repository`() = runTest {
        repository.saveSeenImages(setOf("seen.jpg"))
        viewModel = WallpaperViewModel(repository)

        
        viewModel.resetSeen()
        
        assertTrue(viewModel.seenImageNames.isEmpty())
        assertTrue(repository.getSeenImages().isEmpty())
    }

}
