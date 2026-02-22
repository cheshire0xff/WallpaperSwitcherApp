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
import android.os.Build
import android.os.Bundle
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder

class ScrollingWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        Log.v("WallpaperService", "Service: onCreateEngine called")
        return ScrollingEngine()
    }

    inner class ScrollingEngine : Engine() {
        private var wallpaperBitmap: Bitmap? = null
        private var xOffset = 0.5f 
        private val paint = Paint().apply { isFilterBitmap = true }
        
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER") {
                    val uriString = intent.getStringExtra("uri")
                    Log.v("WallpaperService", "Update received (Preview: $isPreview): $uriString")
                    if (uriString != null) {
                        loadWallpaper(Uri.parse(uriString))
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            Log.v("WallpaperService", "Engine onCreate (Preview: $isPreview)")
            
            val metrics = resources.displayMetrics
            val wm = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
            wm.suggestDesiredDimensions(metrics.widthPixels * 2, metrics.heightPixels)
            
            val filter = IntentFilter("com.cheshire.wallpaperswitcher.UPDATE_WALLPAPER")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            
            val prefs = getSharedPreferences("WallpaperPrefs", Context.MODE_PRIVATE)
            prefs.getString("current_wallpaper_uri", null)?.let {
                loadWallpaper(Uri.parse(it))
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            Log.v("WallpaperService", "Visibility changed to $visible (Preview: $isPreview)")
            if (visible) draw()
        }

        override fun onDestroy() {
            Log.v("WallpaperService", "Engine onDestroy (Preview: $isPreview)")
            super.onDestroy()
            unregisterReceiver(receiver)
            wallpaperBitmap?.recycle()
        }

        override fun onOffsetsChanged(
            xOffset: Float, yOffset: Float,
            xOffsetStep: Float, yOffsetStep: Float,
            xPixelOffset: Int, yPixelOffset: Int
        ) {
            Log.v("WallpaperService", "Offset: x=$xOffset (Preview: $isPreview)")
            
            if (this.xOffset != xOffset) {
                this.xOffset = xOffset
                draw()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            Log.v("WallpaperService", "Surface changed: ${width}x${height}")
            draw()
        }

        private fun loadWallpaper(uri: Uri) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val newBitmap = BitmapFactory.decodeStream(stream)
                    if (newBitmap != null) {
                        wallpaperBitmap?.recycle()
                        wallpaperBitmap = newBitmap
                        draw()
                    }
                }
            } catch (e: Exception) {
                Log.e("WallpaperService", "Load error", e)
            }
        }

        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    val bitmap = wallpaperBitmap
                    if (bitmap != null) {
                        drawBitmap(canvas, bitmap)
                    } else {
                        canvas.drawColor(Color.BLACK)
                    }
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawBitmap(canvas: Canvas, bitmap: Bitmap) {
            val viewWidth = canvas.width.toFloat()
            val viewHeight = canvas.height.toFloat()
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()

            val scale = viewHeight / bitmapHeight
            val minWidth = viewWidth * 1.5f
            val finalScale = if (bitmapWidth * scale < minWidth) minWidth / bitmapWidth else scale
            
            val finalScaledWidth = bitmapWidth * finalScale
            val matrix = Matrix()
            matrix.postScale(finalScale, finalScale)

            val maxScroll = (finalScaledWidth - viewWidth).coerceAtLeast(0f)
            val tx = -xOffset * maxScroll
            val ty = (viewHeight - bitmapHeight * finalScale) / 2f
            
            matrix.postTranslate(tx, ty)
            
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, matrix, paint)
        }
    }
}
