package com.cheshire.wallpaperswitcher.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
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
 * 
 * INTERACTION WITH THE APP:
 * 1. Communication: Uses a BroadcastReceiver to listen for 'UPDATE_WALLPAPER' intents
 *    sent by the main app when a user selects a new wallpaper.
 * 2. Persistence: Reads the 'current_wallpaper_uri' from DataStore via WallpaperRepository
 *    on startup to restore the last used wallpaper after a reboot or service restart.
 */
@AndroidEntryPoint
class ScrollingWallpaperService : WallpaperService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Inject
    lateinit var repository: WallpaperRepository

    override fun onCreate() {
        super.onCreate()
    }

    override fun onCreateEngine(): Engine {
        DLog.v("WallpaperService", "Service: onCreateEngine called")
        return ScrollingEngine()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    inner class ScrollingEngine : Engine(), Choreographer.FrameCallback {
        private var wallpaperBitmap: Bitmap? = null
        private var xOffset = 0.5f
        private val paint = Paint().apply { isFilterBitmap = true }

        // Cache calculations
        private val drawMatrix = Matrix()
        private var finalScale = 1f
        private var maxScroll = 0f
        private var ty = 0f
        private var viewWidth = 0f
        private var viewHeight = 0f

        private var isFramePending = false

        /**
         * BroadcastReceiver for handling "App -> Service" communication.
         * When the user clicks "Next" or selects a specific image in the app, 
         * the app broadcasts an intent that this receiver picks up to trigger a live update.
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

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            DLog.v("WallpaperService", "Engine onCreate (Preview: $isPreview)")

            val metrics = resources.displayMetrics
            val wm = getSystemService(WALLPAPER_SERVICE) as WallpaperManager
            // Suggest double width for scrolling
            wm.suggestDesiredDimensions(metrics.widthPixels * 2, metrics.heightPixels)

            // Register the receiver to listen for wallpaper update requests from the app
            val filter = IntentFilter("com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER")
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)

            /**
             * RESTORE STATE:
             * On initialization (like after a phone reboot), we read the same preference file 
             * written by the main app to ensure we are displaying the correct wallpaper immediately.
             */
            serviceScope.launch {
                val uri = repository.getCurrentWallpaperUri()
                uri?.let {
                    loadWallpaper(it)
                }
            }
        }

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

        override fun doFrame(frameTimeNanos: Long) {
            isFramePending = false // Reset the gatekeeper

            if (isVisible) {
                draw()
            }
        }

        private fun scheduleDraw() {
            // If a frame is already scheduled, we don't need to do anything.
            // The upcoming doFrame will pick up the latest xOffset anyway.
            if (!isFramePending) {
                isFramePending = true
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        override fun onDestroy() {
            DLog.v("WallpaperService", "Engine onDestroy (Preview: $isPreview)")
            super.onDestroy()
            // Cleanup: unregister receiver to prevent memory leaks
            unregisterReceiver(receiver)
            isFramePending = false
            Choreographer.getInstance().removeFrameCallback(this)
            wallpaperBitmap?.recycle()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            // Listen to launcher scroll events
            if (this.xOffset != xOffset) {
                this.xOffset = xOffset
                scheduleDraw()
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            DLog.v("WallpaperService", "Surface changed: ${width}x${height}")
            viewWidth = width.toFloat()
            viewHeight = height.toFloat()
            recalculateDimensions()
            scheduleDraw()
        }

        private fun loadWallpaper(uri: Uri) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val newBitmap = BitmapFactory.decodeStream(stream)
                    if (newBitmap != null) {
                        wallpaperBitmap?.recycle()
                        wallpaperBitmap = newBitmap
                        recalculateDimensions()
                        scheduleDraw()
                    }
                }
            } catch (e: Exception) {
                Log.e("WallpaperService", "Load error", e)
            }
        }

        private fun recalculateDimensions() {
            val bitmap = wallpaperBitmap ?: return
            if (viewWidth <= 0 || viewHeight <= 0) return

            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            // Just fit to height, don't upscale to force scrolling if thin
            finalScale = viewHeight / bitmapHeight

            val finalScaledWidth = bitmapWidth * finalScale
            maxScroll = (finalScaledWidth - viewWidth).coerceAtLeast(0f)
            ty = (viewHeight - bitmapHeight * finalScale) / 2f
        }

        private fun draw() {
            val holder = surfaceHolder
            // Use lockHardwareCanvas() for GPU acceleration if on API 26+
            val canvas = try {
                holder.lockHardwareCanvas()
            } catch (e: Exception) {
                Log.e("WallpaperService", "Canvas lock error", e)
                null
            } ?: return

            try {
                val bitmap = wallpaperBitmap
                if (bitmap != null) {
                    val tx = if (maxScroll > 0) {
                        -xOffset * maxScroll
                    } else {
                        (viewWidth - bitmap.width * finalScale) / 2f
                    }

                    drawMatrix.reset()
                    drawMatrix.postScale(finalScale, finalScale)
                    drawMatrix.postTranslate(tx, ty)

                    // GPU handles this drawBitmap call much faster
                    canvas.drawColor(Color.BLACK)
                    canvas.drawBitmap(bitmap, drawMatrix, paint)
                } else {
                    canvas.drawColor(Color.BLACK)
                }
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
