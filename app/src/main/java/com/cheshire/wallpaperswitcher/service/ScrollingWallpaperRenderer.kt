package com.cheshire.wallpaperswitcher.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/**
 * Helper class to handle wallpaper dimension calculations and matrix transformations.
 * It manages the scaling and positioning of the wallpaper bitmap to support horizontal scrolling.
 */
class ScrollingWallpaperRenderer {
    private val paint = Paint().apply { isFilterBitmap = true }

    // Original bitmap.
    private var wallpaperBitmap: Bitmap? = null

    // Bitmap scaled to viewport.
    private var cachedBitmap: Bitmap? = null

    private val drawMatrix = Matrix()

    private var finalScale = 1f
    private var finalScaledWidth = 1f
    private var maxScroll = 0f
    private var ty = 0f
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var isFlipped = false

    /**
     * Updates the dimensions of the viewable area (the surface) and triggers a recalculation.
     *
     * @param width The width of the viewport.
     * @param height The height of the viewport.
     */
    fun updateViewport(
        width: Float,
        height: Float,
    ) {
        viewWidth = width
        viewHeight = height
        recalculate()
        prepareCachedBitmap()
    }

    /**
     * Sets a new bitmap to be used as the wallpaper.
     * Handles recycling of the old bitmap and triggers a recalculation of dimensions.
     *
     * @param bitmap The new wallpaper bitmap.
     */
    fun prepareBitmap(
        bitmap: Bitmap,
        flipped: Boolean = false,
    ) {
        recycle()
        wallpaperBitmap = bitmap
        isFlipped = flipped
        recalculate()
        prepareCachedBitmap()
    }

    /**
     * Draws the wallpaper onto the provided canvas, applying the correct horizontal scroll offset.
     *
     * @param canvas The canvas to draw on.
     * @param xOffset The horizontal scroll offset (0.0 to 1.0).
     */
    fun draw(
        canvas: Canvas,
        xOffset: Float,
    ) {
        val bitmap = cachedBitmap
        if (bitmap == null) {
            canvas.drawColor(Color.BLACK)
            return
        }
        val matrix = getTransformationMatrix(bitmap, xOffset)
        canvas.drawColor(Color.BLACK)
        if (matrix != null) {
            canvas.drawBitmap(bitmap, matrix, paint)
        }
    }

    /**
     * Recycles the current wallpaper bitmap and clears the reference.
     */
    fun recycle() {
        maybeRecycleCachedBitmap()
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
    }

    private fun maybeRecycleCachedBitmap() {
        if (cachedBitmap !== wallpaperBitmap) {
            cachedBitmap?.recycle()
        }
        cachedBitmap = null
    }

    /**
     * Recalculates scaling factors and scroll limits based on the current bitmap and viewport.
     */
    private fun recalculate() {
        val b = wallpaperBitmap ?: return
        if (viewWidth <= 0 || viewHeight <= 0) return

        val bitmapWidth = b.width.toFloat()
        val bitmapHeight = b.height.toFloat()

        // Scale the bitmap to fit the height of the viewport.
        finalScale = viewHeight / bitmapHeight

        finalScaledWidth = bitmapWidth * finalScale
        // Calculate the maximum amount the wallpaper can be scrolled horizontally.
        maxScroll = (finalScaledWidth - viewWidth).coerceAtLeast(0f)
        // Center the bitmap vertically if it doesn't perfectly match the height.
        ty = (viewHeight - bitmapHeight * finalScale) / 2f
    }

    private fun prepareCachedBitmap() {
        maybeRecycleCachedBitmap()
        val original = wallpaperBitmap ?: return
        val matrix = Matrix()
        matrix.postScale(finalScale, finalScale)
        if (isFlipped) {
            matrix.postScale(-1f, 1f, (original.width * finalScale) / 2f, (original.height * finalScale) / 2f)
        }
        cachedBitmap =
            Bitmap.createBitmap(
                original,
                0,
                0,
                original.width,
                original.height,
                matrix,
                true,
            )
    }

    /**
     * Generates the transformation matrix for drawing the wallpaper based on the current horizontal offset.
     *
     * @param bitmap The wallpaper bitmap to be drawn.
     * @param xOffset A value between 0.0 and 1.0 representing the horizontal scroll position.
     * @return A [Matrix] containing the scale and translation, or null if the bitmap is null.
     */
    private fun getTransformationMatrix(
        bitmap: Bitmap?,
        xOffset: Float,
    ): Matrix? {
        val b = bitmap ?: return null

        // Calculate the horizontal translation.
        // If the scaled bitmap is wider than the view, use the xOffset to determine the shift.
        // Otherwise, center it horizontally.
        val tx =
            if (maxScroll > 0) {
                -xOffset * maxScroll
            } else {
                (viewWidth - b.width) / 2f
            }

        drawMatrix.reset()
        drawMatrix.postTranslate(tx, ty)
        return drawMatrix
    }
}
