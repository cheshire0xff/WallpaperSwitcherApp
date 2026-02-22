package com.cheshire.wallpaperswitcher.service

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import androidx.core.net.toUri

class ScrollingWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        Log.v("WallpaperService", "Service: onCreateEngine called")
        return ScrollingEngine()
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
        
        private var needsDraw = false

        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER") {
                    val uriString = intent.getStringExtra("uri")
                    Log.v("WallpaperService", "Update received (Preview: $isPreview): $uriString")
                    if (uriString != null) {
                        loadWallpaper(uriString.toUri())
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.v("WallpaperService", "Engine onCreate (Preview: $isPreview)")
            
            val metrics = resources.displayMetrics
            val wm = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            // Suggest double width for scrolling
            wm.suggestDesiredDimensions(metrics.widthPixels * 2, metrics.heightPixels)
            
            val filter = IntentFilter("com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER")
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)

            val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            prefs.getString("current_wallpaper_uri", null)?.let {
                loadWallpaper(it.toUri())
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.v("WallpaperService", "Visibility changed to $visible (Preview: $isPreview)")
            if (visible) {
                scheduleDraw()
            } else {
                Choreographer.getInstance().removeFrameCallback(this)
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (needsDraw) {
                draw()
                needsDraw = false
            }
            if (isVisible) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        private fun scheduleDraw() {
            needsDraw = true
            Choreographer.getInstance().removeFrameCallback(this)
            Choreographer.getInstance().postFrameCallback(this)
        }

        override fun onDestroy() {
            Log.v("WallpaperService", "Engine onDestroy (Preview: $isPreview)")
            super.onDestroy()
            unregisterReceiver(receiver)
            Choreographer.getInstance().removeFrameCallback(this)
            wallpaperBitmap?.recycle()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            if (this.xOffset != xOffset) {
                this.xOffset = xOffset
                needsDraw = true
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.v("WallpaperService", "Surface changed: ${width}x${height}")
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
