package com.cheshire.wallpaperswitcher.ui.viewmodel

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

class TestContentProvider : ContentProvider() {
    companion object {
        val mockCursors = mutableMapOf<Uri, Cursor>()
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = mockCursors[uri]

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WallpaperViewModelTest {
    private lateinit var viewModel: WallpaperViewModel
    private lateinit var repository: WallpaperRepository
    private lateinit var context: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private val baseTestUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3APictures")

    @Before
    fun setup() =
        runTest {
            Dispatchers.setMain(testDispatcher)
            context = ApplicationProvider.getApplicationContext()
            testDataStore =
                PreferenceDataStoreFactory.create(
                    scope = testScope,
                    produceFile = { context.preferencesDataStoreFile("test_datastore_vm") },
                )
            // Inject the test dispatcher into the repository
            repository = WallpaperRepository(context, testDataStore, testDispatcher)

            // Clear DataStore and files
            testDataStore.edit { it.clear() }
            val baseDir = repository.baseDir
            baseDir.listFiles()?.forEach { it.delete() }

            TestContentProvider.mockCursors.clear()
            Robolectric.setupContentProvider(TestContentProvider::class.java, "com.android.externalstorage.documents")
        }

    @After
    fun tearDown() {
        shadowOf(Looper.getMainLooper()).idle()
        Dispatchers.resetMain()
        testScope.cancel()
    }

    private fun TestScope.waitTasks() {
        advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `initialization loads data from repository`() =
        runTest {
            val folderUri = baseTestUri
            mockDirectoryLastModified(folderUri, 1000L)

            repository.saveFolderUri(folderUri)
            repository.updateCurrentWallpaper(Uri.parse("content://curr"), "current.jpg")
            repository.saveFavorites(setOf("fav.jpg"))

            viewModel = WallpaperViewModel(repository)
            waitTasks()

            assertEquals(folderUri, viewModel.folderUri)
            assertEquals("current.jpg", viewModel.currentWallpaperName)
            assertTrue(viewModel.favoriteNames.contains("fav.jpg"))
        }

    @Test
    fun `updateFolderUri saves to repository`() =
        runTest {
            viewModel = WallpaperViewModel(repository)
            waitTasks()

            val newUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANewFolder")
            mockDirectoryLastModified(newUri, 1000L)

            viewModel.updateFolderUri(newUri)
            waitTasks()

            assertEquals(newUri, repository.getFolderUri().first())
            assertEquals(newUri, viewModel.folderUri)
        }

    @Test
    fun `toggleFavorite updates state and repository`() =
        runTest {
            repository.updateCurrentWallpaper(Uri.parse("content://img"), "img.jpg")
            viewModel = WallpaperViewModel(repository)
            waitTasks()

            viewModel.toggleFavorite()
            waitTasks()
            assertTrue(viewModel.favoriteNames.contains("img.jpg"))
            assertTrue(repository.getFavoriteImages().contains("img.jpg"))

            viewModel.toggleFavorite()
            waitTasks()
            assertFalse(viewModel.favoriteNames.contains("img.jpg"))
            assertFalse(repository.getFavoriteImages().contains("img.jpg"))
        }

    @Test
    fun `toggleToRemove updates state and repository`() =
        runTest {
            repository.updateCurrentWallpaper(Uri.parse("content://bad"), "bad_img.jpg")
            viewModel = WallpaperViewModel(repository)
            waitTasks()

            viewModel.toggleToRemove()
            waitTasks()
            assertTrue(viewModel.toRemoveNames.contains("bad_img.jpg"))
            assertTrue(repository.getToRemoveImages().contains("bad_img.jpg"))

            viewModel.toggleToRemove()
            waitTasks()
            assertFalse(viewModel.toRemoveNames.contains("bad_img.jpg"))
            assertFalse(repository.getToRemoveImages().contains("bad_img.jpg"))
        }

    @Test
    fun `resetSeen clears state and repository`() =
        runTest {
            repository.saveSeenImages(setOf("seen.jpg"))
            viewModel = WallpaperViewModel(repository)
            waitTasks()

            viewModel.resetSeen()
            waitTasks()

            assertTrue(viewModel.seenImageNames.isEmpty())
            assertTrue(repository.getSeenImages().isEmpty())
        }

    private fun mockDirectoryLastModified(
        uri: Uri,
        lastModified: Long,
    ) {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

        val cursor = MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
        cursor.addRow(arrayOf(lastModified))

        TestContentProvider.mockCursors[documentUri] = cursor

        Robolectric.setupContentProvider(TestContentProvider::class.java, uri.authority!!)
    }

    @Test
    fun `cache is valid when folder and modification date match`() =
        runTest {
            val folderUri = baseTestUri
            val lastModified = 1000L
            repository.saveFolderUri(folderUri)
            mockDirectoryLastModified(folderUri, lastModified)

            val cacheFile = File(repository.baseDir, "image_cache.txt")
            cacheFile.writeText("# dir=$folderUri\n# lastModified=$lastModified\ncontent://uri1|image1.jpg")

            viewModel = WallpaperViewModel(repository)
            waitTasks()

            assertEquals(1, viewModel.cachedImages.size)
            assertEquals("image1.jpg", viewModel.cachedImages[0].second)
        }

    @Test
    fun `cache is invalid if folder uri changes`() =
        runTest {
            val oldFolder = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AOld")
            val newFolder = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANew")
            repository.saveFolderUri(newFolder)
            mockDirectoryLastModified(newFolder, 1000L)

            val cacheFile = File(repository.baseDir, "image_cache.txt")
            cacheFile.writeText("# dir=$oldFolder\n# lastModified=1000\ncontent://uri1|image1.jpg")

            viewModel = WallpaperViewModel(repository)
            waitTasks()

            assertTrue(viewModel.cachedImages.isEmpty())
        }

    @Test
    fun `cache is invalid if last modified date changes`() =
        runTest {
            val folderUri = baseTestUri
            repository.saveFolderUri(folderUri)
            mockDirectoryLastModified(folderUri, 2000L) // Current is 2000

            val cacheFile = File(repository.baseDir, "image_cache.txt")
            cacheFile.writeText("# dir=$folderUri\n# lastModified=1000\ncontent://uri1|image1.jpg") // Cache is 1000

            viewModel = WallpaperViewModel(repository)
            waitTasks()

            assertTrue(viewModel.cachedImages.isEmpty())
        }
}
