package io.github.vinayrepalle.unlockwall.core

import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ScreenOff
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ScreenOn
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ServiceStarted
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ShuttingDown
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.SwitchedOff
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.UserPresent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnlockStateMachineTest {

    /** A machine whose 60 s start hold is already over at time [LATER]. */
    private fun started() = UnlockStateMachine().apply { onEvent(ServiceStarted(0)) }

    private fun UnlockStateMachine.unlock(screenOnAt: Long, tookMs: Long): Request? {
        onEvent(ScreenOn(screenOnAt))
        return onEvent(UserPresent(screenOnAt + tookMs, stillLocked = false))
    }

    @Test
    fun serviceStartForcesNormal() {
        assertEquals(
            Request(Slot.NORMAL, Reason.SERVICE_STARTED, force = true),
            UnlockStateMachine().onEvent(ServiceStarted(5)),
        )
    }

    @Test
    fun screenOffAsksForNormal() {
        assertEquals(Request(Slot.NORMAL, Reason.SCREEN_OFF), started().onEvent(ScreenOff))
    }

    @Test
    fun fastUnlockIsQuick() {
        assertEquals(Slot.QUICK, started().unlock(LATER, 527)?.slot)
    }

    @Test
    fun slowUnlockIsSlow() {
        assertEquals(Slot.SLOW, started().unlock(LATER, 1874)?.slot)
    }

    @Test
    fun limitItselfCountsAsSlow() {
        assertEquals(Slot.QUICK, started().unlock(LATER, 999)?.slot)
        assertEquals(Slot.SLOW, started().unlock(LATER, 1000)?.slot)
    }

    @Test
    fun screenOnAloneChangesNothing() {
        assertNull(started().onEvent(ScreenOn(LATER)))
    }

    @Test
    fun secondScreenOnDoesNotMakeAnUnlockLookQuicker() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER))
        machine.onEvent(ScreenOn(LATER + 2_900))
        val request = machine.onEvent(UserPresent(LATER + 3_000, stillLocked = false))
        assertEquals(Slot.SLOW, request?.slot)
    }

    @Test
    fun repeatedUserPresentIsIgnored() {
        val machine = started()
        machine.unlock(LATER, 300)
        assertNull(machine.onEvent(UserPresent(LATER + 400, stillLocked = false)))
    }

    @Test
    fun userPresentWithoutScreenOnIsUnclear() {
        val machine = started()
        machine.onEvent(ScreenOff)
        assertEquals(
            Request(Slot.NORMAL, Reason.UNCLEAR),
            machine.onEvent(UserPresent(LATER, stillLocked = false)),
        )
        // The SCREEN_ON that arrives late (seen in F12) must not start a new unlock.
        assertNull(machine.onEvent(ScreenOn(LATER + 400)))
        assertNull(machine.onEvent(UserPresent(LATER + 450, stillLocked = false)))
    }

    @Test
    fun lateUserPresentOnALockedPhoneIsUnclear() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER))
        assertEquals(
            Request(Slot.NORMAL, Reason.UNCLEAR),
            machine.onEvent(UserPresent(LATER + 100, stillLocked = true)),
        )
        // The real unlock that follows is still judged.
        val real = machine.onEvent(UserPresent(LATER + 2_000, stillLocked = false))
        assertEquals(Slot.SLOW, real?.slot)
    }

    @Test
    fun nextUnlockAfterScreenOffIsJudgedAgain() {
        val machine = started()
        machine.unlock(LATER, 2_000)
        machine.onEvent(ScreenOff)
        assertEquals(Slot.QUICK, machine.unlock(LATER + 10_000, 200)?.slot)
    }

    @Test
    fun lostScreenOffPutsNormalBack() {
        val machine = started()
        machine.unlock(LATER, 200)
        assertEquals(
            Request(Slot.NORMAL, Reason.MISSED_SCREEN_OFF),
            machine.onEvent(ScreenOn(LATER + 60_000)),
        )
        val next = machine.onEvent(UserPresent(LATER + 62_000, stillLocked = false))
        assertEquals(Slot.SLOW, next?.slot)
    }

    @Test
    fun holdAfterStartKeepsNormal() {
        val machine = UnlockStateMachine()
        machine.onEvent(ServiceStarted(100_000))
        assertEquals(Reason.JUST_STARTED, machine.unlock(159_000, 200)?.reason)
        machine.onEvent(ScreenOff)
        assertEquals(Slot.QUICK, machine.unlock(160_000, 200)?.slot)
    }

    @Test
    fun withoutAServiceStartNothingPersonalIsAsked() {
        assertEquals(Slot.NORMAL, UnlockStateMachine().unlock(LATER, 200)?.slot)
    }

    @Test
    fun shutdownForcesNormalAndThenIgnoresEverything() {
        val machine = started()
        assertEquals(
            Request(Slot.NORMAL, Reason.SHUTTING_DOWN, force = true),
            machine.onEvent(ShuttingDown),
        )
        assertNull(machine.onEvent(ScreenOn(LATER)))
        assertNull(machine.onEvent(UserPresent(LATER + 100, stillLocked = false)))
        assertNull(machine.onEvent(ScreenOff))
    }

    @Test
    fun switchingOffForcesNormal() {
        assertEquals(
            Request(Slot.NORMAL, Reason.SWITCHED_OFF, force = true),
            started().onEvent(SwitchedOff),
        )
    }

    private companion object {
        const val LATER = 500_000L
    }
}
