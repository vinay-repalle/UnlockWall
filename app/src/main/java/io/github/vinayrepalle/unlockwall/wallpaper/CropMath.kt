package io.github.vinayrepalle.unlockwall.wallpaper

import kotlin.math.max

// Plain Kotlin, so that the arithmetic can be tested without a phone.

/**
 * How the user placed a photo in the screen frame.
 *
 * @property rotation clockwise degrees: 0, 90, 180 or 270.
 * @property zoom 1 means the photo just covers the frame.
 * @property panX how far the photo's centre is from the frame's centre, in frame widths.
 * @property panY the same in frame heights.
 */
data class Adjustment(
    val rotation: Int = 0,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
) {
    fun encode() = "$rotation $zoom $panX $panY"

    companion object {
        /** Anything unreadable gives the starting position. */
        fun decode(text: String): Adjustment {
            val parts = text.trim().split(' ')
            if (parts.size != 4) return Adjustment()
            val rotation = parts[0].toIntOrNull()?.takeIf { it in listOf(0, 90, 180, 270) }
            val zoom = parts[1].toFloatOrNull()?.takeIf { it.isFinite() }
            val panX = parts[2].toFloatOrNull()?.takeIf { it.isFinite() }
            val panY = parts[3].toFloatOrNull()?.takeIf { it.isFinite() }
            return if (rotation == null || zoom == null || panX == null || panY == null) {
                Adjustment()
            } else {
                Adjustment(rotation, zoom, panX, panY)
            }
        }
    }
}

/** The part of the (already rotated) photo that fills the frame, in photo pixels. */
data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

object CropMath {
    const val MAX_ZOOM = 5f

    fun rotatedSize(width: Int, height: Int, rotation: Int): Pair<Int, Int> =
        if (rotation % 180 == 0) width to height else height to width

    private fun coverScale(width: Int, height: Int, frameWidth: Float, frameHeight: Float) =
        max(frameWidth / width, frameHeight / height)

    /** Keeps zoom in range and the photo covering the whole frame. */
    fun clamp(
        adjustment: Adjustment,
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
    ): Adjustment {
        val (width, height) = rotatedSize(imageWidth, imageHeight, adjustment.rotation)
        val zoom = adjustment.zoom.coerceIn(1f, MAX_ZOOM)
        val scale = coverScale(width, height, frameWidth, frameHeight) * zoom
        val limitX = max(0f, (width * scale - frameWidth) / 2f / frameWidth)
        val limitY = max(0f, (height * scale - frameHeight) / 2f / frameHeight)
        return adjustment.copy(
            zoom = zoom,
            panX = adjustment.panX.coerceIn(-limitX, limitX),
            panY = adjustment.panY.coerceIn(-limitY, limitY),
        )
    }

    /**
     * Applies one pinch or drag step. [centroidX] and [centroidY] are the point between the
     * fingers, in frame pixels from the top left; it stays over the same spot of the photo.
     */
    fun gesture(
        adjustment: Adjustment,
        zoomChange: Float,
        dragX: Float,
        dragY: Float,
        centroidX: Float,
        centroidY: Float,
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
    ): Adjustment {
        val zoom = (adjustment.zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
        val ratio = zoom / adjustment.zoom
        val fromCentreX = centroidX - frameWidth / 2f
        val fromCentreY = centroidY - frameHeight / 2f
        val x = fromCentreX - (fromCentreX - adjustment.panX * frameWidth) * ratio + dragX
        val y = fromCentreY - (fromCentreY - adjustment.panY * frameHeight) * ratio + dragY
        return clamp(
            adjustment.copy(zoom = zoom, panX = x / frameWidth, panY = y / frameHeight),
            imageWidth,
            imageHeight,
            frameWidth,
            frameHeight,
        )
    }

    /** A quarter turn clockwise, starting again from a centred, unzoomed photo. */
    fun rotate(adjustment: Adjustment) = Adjustment(rotation = (adjustment.rotation + 90) % 360)

    fun cropRect(
        adjustment: Adjustment,
        imageWidth: Int,
        imageHeight: Int,
        frameWidth: Float,
        frameHeight: Float,
    ): CropRect {
        val safe = clamp(adjustment, imageWidth, imageHeight, frameWidth, frameHeight)
        val (width, height) = rotatedSize(imageWidth, imageHeight, safe.rotation)
        val scale = coverScale(width, height, frameWidth, frameHeight) * safe.zoom
        return CropRect(
            left = (width * scale / 2f - frameWidth / 2f - safe.panX * frameWidth) / scale,
            top = (height * scale / 2f - frameHeight / 2f - safe.panY * frameHeight) / scale,
            width = frameWidth / scale,
            height = frameHeight / scale,
        )
    }
}
