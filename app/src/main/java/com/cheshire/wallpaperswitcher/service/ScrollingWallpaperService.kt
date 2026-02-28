package com.cheshire.wallpaperswitcher.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.net.toUri
import com.cheshire.wallpaperswitcher.data.WallpaperRepository
import com.cheshire.wallpaperswitcher.util.DLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Live Wallpaper Service that handles image rendering and horizontal scrolling.
 * This service manages the lifecycle of the wallpaper engine and coordinates
 * wallpaper updates via broadcasts.
 */
@AndroidEntryPoint
class ScrollingWallpaperService : WallpaperService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject
    lateinit var repository: WallpaperRepository

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Creates and returns a new [ScrollingEngine] instance.
     */
    override fun onCreateEngine(): Engine {
        DLog.v("WallpaperService", "Service: onCreateEngine called")
        return ScrollingEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * The Engine class responsible for the actual rendering logic and event handling
     * for the live wallpaper.
     */
    inner class ScrollingEngine : Engine(), Choreographer.FrameCallback {

        private var xOffset = 0.5f
        private val renderer = ScrollingWallpaperRenderer()
        private var isFramePending = false

        /**
         * Listens for "UPDATE_WALLPAPER" broadcasts to refresh the current wallpaper image.
         */
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER") {
                    val uriString = intent.getStringExtra("uri")
                    DLog.i("WallpaperService", "Update received (Preview: $isPreview): $uriString")
                    if (uriString != null) {
                        loadWallpaper(uriString.toUri())
                    }
                }
            }
        }

        /**
         * Initializes the engine, sets up the broadcast receiver, and loads the initial wallpaper.
         */
        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            DLog.v("WallpaperService", "Engine onCreate (Preview: $isPreview)")

            val metrics = resources.displayMetrics
            val wm = getSystemService(WALLPAPER_SERVICE) as WallpaperManager
            // Suggest double width to encourage launchers to provide scroll offsets.
            wm.suggestDesiredDimensions(metrics.widthPixels * 2, metrics.heightPixels)

            val filter = IntentFilter("com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER")
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)

            serviceScope.launch {
                val uri = repository.getCurrentWallpaperUri()
                uri?.let {
                    loadWallpaper(it)
                }
            }
        }

        /**
         * Handles visibility changes. Starts/stops the drawing loop based on visibility.
         */
        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            DLog.v("WallpaperService", "Visibility changed to $visible (Preview: $isPreview)")
            if (visible) {
                scheduleDraw()
            } else {
                isFramePending = false
                Choreographer.getInstance().removeFrameCallback(this)
            }
        }

        /**
         * Called by [Choreographer] when it's time to draw a new frame.
         */
        override fun doFrame(frameTimeNanos: Long) {
            isFramePending = false
            if (isVisible) {
                draw()
            }
        }

        /**
         * Schedules a new frame to be drawn via the [Choreographer].
         */
        private fun scheduleDraw() {
            if (!isFramePending) {
                isFramePending = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun onDestroy() {
            DLog.v("WallpaperService", "Engine onDestroy (Preview: $isPreview)")
            super.onDestroy()
            unregisterReceiver(receiver)
            isFramePending = false
            Choreographer.getInstance().removeFrameCallback(this)
            renderer.recycle()
        }

        /**
         * Updates the horizontal offset when the user scrolls through launcher pages.
         */
        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            if (this.xOffset != xOffset) {
                this.xOffset = xOffset
                scheduleDraw()
            }
        }

        /**
         * Updates the renderer's viewport when the surface dimensions change.
         */
        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            DLog.v("WallpaperService", "Surface changed: ${width}x${height}")
            renderer.updateViewport(width.toFloat(), height.toFloat())
            scheduleDraw()
        }

        /**
         * Loads a bitmap from the specified URI and prepares the renderer.
         *
         * @param uri The URI of the image to load.
         */
        private fun loadWallpaper(uri: Uri) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val newBitmap = BitmapFactory.decodeStream(stream)
                    if (newBitmap != null) {
                        renderer.prepareBitmap(newBitmap)
                        scheduleDraw()
                    }
                }
            } catch (e: Exception) {
                Log.e("WallpaperService", "Load error", e)
            }
        }

        /**
         * Renders the wallpaper to the surface canvas.
         */
        private fun draw() {
            val holder = surfaceHolder
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                Log.e("WallpaperService", "Canvas lock error", e)
                null
            } ?: return

            try {
                renderer.draw(canvas, xOffset)
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    Log.e("WallpaperService", "unlockCanvasAndPost error", e)
                }
            }
        }
    }
}
