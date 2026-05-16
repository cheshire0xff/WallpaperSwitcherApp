package com.cheshire.wallpaperswitcher.util

import android.graphics.Path
import android.graphics.RectF

object NotchPathUtil {
    /**
     * Updates the given [Path] with the notch drawing logic.
     * The notch is drawn as a bar at the top with optional inverted rounded corners at the bottom.
     *
     * @param path The path to update.
     * @param left The left boundary of the notch.
     * @param right The right boundary of the notch.
     * @param notchHeight The height of the notch bar.
     * @param cornerRadius The radius of the inverted corners at the bottom.
     * @param rect A reusable [RectF] to avoid allocations.
     */
    fun updateNotchPath(
        path: Path,
        left: Float,
        right: Float,
        notchHeight: Float,
        cornerRadius: Float,
        rect: RectF = RectF(),
    ) {
        path.reset()
        if (right <= left) return

        if (cornerRadius > 0) {
            // Start from top-left
            path.moveTo(left, 0f)
            // To top-right
            path.lineTo(right, 0f)
            // Down to bottom-right corner start
            path.lineTo(right, notchHeight + cornerRadius)
            // Curve back into the notch bar to make wallpaper look rounded
            rect.set(right - 2 * cornerRadius, notchHeight, right, notchHeight + 2 * cornerRadius)
            path.arcTo(rect, 0f, -90f, false)
            // Across to bottom-left corner
            path.lineTo(left + cornerRadius, notchHeight)
            // Curve for bottom-left
            rect.set(left, notchHeight, left + 2 * cornerRadius, notchHeight + 2 * cornerRadius)
            path.arcTo(rect, 270f, -90f, false)
            // Close the path
            path.close()
        } else {
            path.addRect(left, 0f, right, notchHeight, Path.Direction.CW)
        }
    }
}
