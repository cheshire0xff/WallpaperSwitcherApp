package com.cheshire.wallpaperswitcher.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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

        private var xOffset = 0.5f
        private val renderer = ScrollingWallpaperRenderer()
        private var isFramePending = false

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
            isFramePending = false
            if (isVisible) {
                draw()
            }
        }

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
