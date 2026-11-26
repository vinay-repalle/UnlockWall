package io.github.vinayrepalle.unlockwall.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityTextTest {

    private fun kind(message: String) = ActivityText.parse("11-26 14:05:17.980 $message")?.kind

    @Test
    fun readsRealLogLines() {
        assertEquals(ActivityKind.QUICK, kind("SHOW QUICK reason=QUICK_UNLOCK tookMs=131"))
        assertEquals(ActivityKind.FACE, kind("SHOW QUICK reason=FACE_UNLOCK tookMs=132"))
        assertEquals(ActivityKind.SLOW, kind("SHOW SLOW reason=SLOW_UNLOCK tookMs=189"))
        assertEquals(ActivityKind.UNSURE, kind("KEEP reason=UNCLEAR showing=NORMAL enabled=true"))
        assertEquals(ActivityKind.SHUTDOWN, kind("SHOW NORMAL reason=SHUTTING_DOWN tookMs=280"))
        assertEquals(ActivityKind.STARTED, kind("SERVICE_STARTED"))
        assertEquals(ActivityKind.FAILED, kind("FAILED QUICK reason=QUICK_UNLOCK no picture"))
    }

    @Test
    fun skipsNoise() {
        assertNull(kind("SCREEN_ON keyguardLocked=true deviceLocked=true"))
        assertNull(kind("SHOW NORMAL reason=SCREEN_OFF tookMs=176"))
        assertNull(ActivityText.parse(""))
    }

    @Test
    fun keepsTimeToTheMinuteAndPutsNewestFirst() {
        val entries = ActivityText.entries(
            listOf("11-26 14:04:13.731 SERVICE_STARTED", "11-26 14:40:56.850 SWITCHED_OFF"),
        )
        assertEquals(
            listOf(
                ActivityEntry("11-26 14:40", ActivityKind.SWITCHED_OFF),
                ActivityEntry("11-26 14:04", ActivityKind.STARTED),
            ),
            entries,
        )
    }
}
