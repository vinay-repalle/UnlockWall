package io.github.vinayrepalle.unlockwall.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object Pictures {
    /** Large enough to stay sharp when zoomed, small enough not to run out of memory. */
    private const val MAX_SIDE = 4096

    /**
     * Reads a photo the right way up (EXIF is applied). Returns null if it cannot be read.
     * Blocking: call off the main thread.
     */
    fun decode(source: ImageDecoder.Source): Bitmap? = try {
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt()),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: IOException) {
        null
    } catch (e: RuntimeException) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws [picture] as the user adjusted it into a [width] x [height] area. The preview
     * and the saved file both use this, so what you see is what is stored.
     */
    fun draw(canvas: Canvas, picture: Bitmap, adjustment: Adjustment, width: Float, height: Float) {
        val crop = CropMath.cropRect(adjustment, picture.width, picture.height, width, height)
        val matrix = Matrix()
        matrix.postRotate(adjustment.rotation.toFloat())
        // Rotation turns around the corner; move the picture back into view.
        when (adjustment.rotation) {
            90 -> matrix.postTranslate(picture.height.toFloat(), 0f)
            180 -> matrix.postTranslate(picture.width.toFloat(), picture.height.toFloat())
            270 -> matrix.postTranslate(0f, picture.width.toFloat())
        }
        matrix.postTranslate(-crop.left, -crop.top)
        matrix.postScale(width / crop.width, height / crop.height)
        canvas.drawBitmap(picture, matrix, paint)
    }
}
