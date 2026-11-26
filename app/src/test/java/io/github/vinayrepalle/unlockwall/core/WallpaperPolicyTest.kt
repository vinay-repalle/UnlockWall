package io.github.vinayrepalle.unlockwall.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperPolicyTest {

    private val all = Slot.entries.toSet()
    private val quick = Request(Slot.QUICK, Reason.QUICK_UNLOCK)
    private val slow = Request(Slot.SLOW, Reason.SLOW_UNLOCK)

    private fun decide(
        request: Request,
        enabled: Boolean = true,
        available: Set<Slot> = all,
        showing: Slot? = Slot.NORMAL,
    ) = WallpaperPolicy.decide(request, enabled, available, showing)

    @Test
    fun showsTheRequestedSlot() {
        assertEquals(Slot.QUICK, decide(quick))
        assertEquals(Slot.SLOW, decide(slow))
    }

    @Test
    fun switchOffChangesNothing() {
        assertNull(decide(quick, enabled = false))
    }

    @Test
    fun switchingOffStillRestoresNormal() {
        val off = Request(Slot.NORMAL, Reason.SWITCHED_OFF, force = true)
        assertEquals(Slot.NORMAL, decide(off, enabled = false, showing = Slot.QUICK))
    }

    @Test
    fun missingPictureFallsBackToNormal() {
        val available = setOf(Slot.NORMAL, Slot.QUICK)
        assertEquals(Slot.NORMAL, decide(slow, available = available, showing = Slot.QUICK))
    }

    @Test
    fun withoutNormalPictureNothingChanges() {
        assertNull(decide(quick, available = setOf(Slot.QUICK, Slot.SLOW)))
    }

    @Test
    fun skipsWhenAlreadyShowing() {
        assertNull(decide(quick, showing = Slot.QUICK))
    }

    @Test
    fun unknownCurrentPictureIsAlwaysReplaced() {
        assertEquals(Slot.NORMAL, decide(Request(Slot.NORMAL, Reason.SCREEN_OFF), showing = null))
    }

    @Test
    fun forceSetsEvenWhenRecordedAsShowing() {
        val start = Request(Slot.NORMAL, Reason.SERVICE_STARTED, force = true)
        assertEquals(Slot.NORMAL, decide(start, showing = Slot.NORMAL))
    }
}
