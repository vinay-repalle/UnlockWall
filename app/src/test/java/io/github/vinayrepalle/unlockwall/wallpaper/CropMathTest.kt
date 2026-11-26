package io.github.vinayrepalle.unlockwall.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Test

/** Frame 100 x 200 (tall, like a phone); the photo is 400 x 200 (landscape). */
class CropMathTest {

    private fun crop(adjustment: Adjustment) = CropMath.cropRect(adjustment, 400, 200, 100f, 200f)

    private fun assertRect(left: Float, top: Float, width: Float, height: Float, actual: CropRect) {
        assertEquals(left, actual.left, 0.01f)
        assertEquals(top, actual.top, 0.01f)
        assertEquals(width, actual.width, 0.01f)
        assertEquals(height, actual.height, 0.01f)
    }

    @Test
    fun startsWithTheMiddleOfThePhoto() {
        assertRect(150f, 0f, 100f, 200f, crop(Adjustment()))
    }

    @Test
    fun zoomShowsLessAroundTheSameCentre() {
        assertRect(175f, 50f, 50f, 100f, crop(Adjustment(zoom = 2f)))
    }

    @Test
    fun draggingCannotLeaveEmptySpace() {
        // Pulled far to the right: the left edge of the photo, no further.
        assertRect(0f, 0f, 100f, 200f, crop(Adjustment(panX = 99f)))
        assertRect(300f, 0f, 100f, 200f, crop(Adjustment(panX = -99f)))
        // There is no spare height, so it cannot move up or down.
        assertRect(150f, 0f, 100f, 200f, crop(Adjustment(panY = 5f)))
    }

    @Test
    fun zoomStaysInRange() {
        val small = CropMath.clamp(Adjustment(zoom = 0.2f), 400, 200, 100f, 200f)
        val large = CropMath.clamp(Adjustment(zoom = 50f), 400, 200, 100f, 200f)
        assertEquals(1f, small.zoom, 0f)
        assertEquals(CropMath.MAX_ZOOM, large.zoom, 0f)
    }

    @Test
    fun aRotatedLandscapePhotoFitsAPhoneScreenCompletely() {
        // Turned a quarter, 400 x 200 becomes 200 x 400: the same shape as the frame.
        assertRect(0f, 0f, 200f, 400f, crop(Adjustment(rotation = 90)))
    }

    @Test
    fun placementSurvivesSaving() {
        val adjustment = Adjustment(rotation = 270, zoom = 2.5f, panX = -0.25f, panY = 0.125f)
        assertEquals(adjustment, Adjustment.decode(adjustment.encode()))
    }

    @Test
    fun unreadablePlacementStartsAgain() {
        for (text in listOf("", "x", "45 1 0 0", "0 NaN 0 0", "0 1 0")) {
            assertEquals(text, Adjustment(), Adjustment.decode(text))
        }
    }

    @Test
    fun fourQuarterTurnsComeBack() {
        var adjustment = Adjustment(zoom = 3f, panX = 0.5f)
        repeat(4) { adjustment = CropMath.rotate(adjustment) }
        assertEquals(Adjustment(), adjustment)
    }

    @Test
    fun thePointBetweenTheFingersStaysPut() {
        // Pinch to double size around a point 25 right of the left edge, at mid height.
        val after = CropMath.gesture(
            Adjustment(), 2f, 0f, 0f, 25f, 100f, 400, 200, 100f, 200f,
        )
        val before = crop(Adjustment())
        val now = crop(after)
        assertEquals(before.left + 0.25f * before.width, now.left + 0.25f * now.width, 0.01f)
        assertEquals(before.top + 0.5f * before.height, now.top + 0.5f * now.height, 0.01f)
    }
}
