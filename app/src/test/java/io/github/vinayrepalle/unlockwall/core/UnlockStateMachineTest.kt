package io.github.vinayrepalle.unlockwall.core

import io.github.vinayrepalle.unlockwall.core.PhoneEvent.DeviceUnlocked
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.LockScreenCheck
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ScreenOff
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ScreenOn
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ServiceStarted
import io.github.vinayrepalle.unlockwall.core.PhoneEvent.ServiceStopping
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
        assertEquals(Request(Slot.NORMAL, Reason.SCREEN_OFF), started().onEvent(ScreenOff()))
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
        machine.onEvent(ScreenOff())
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
        machine.onEvent(ScreenOff())
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
        machine.onEvent(ScreenOff())
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
        assertNull(machine.onEvent(ScreenOff()))
    }

    @Test
    fun switchingOffForcesNormal() {
        assertEquals(
            Request(Slot.NORMAL, Reason.SWITCHED_OFF, force = true),
            started().onEvent(SwitchedOff),
        )
    }

    @Test
    fun faceIsJudgedWhenTheDeviceUnlocksNotWhenSwiped() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER))
        assertEquals(Slot.QUICK, machine.onEvent(DeviceUnlocked(LATER + 264))?.slot)
        // The swipe seconds later must not turn it into "slow".
        assertNull(machine.onEvent(UserPresent(LATER + 4_143, stillLocked = false)))
    }

    @Test
    fun slowDeviceUnlockIsSlow() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER))
        assertEquals(Slot.SLOW, machine.onEvent(DeviceUnlocked(LATER + 1_874))?.slot)
    }

    @Test
    fun deviceUnlockAfterUserPresentIsIgnored() {
        val machine = started()
        machine.unlock(LATER, 300)
        assertNull(machine.onEvent(DeviceUnlocked(LATER + 350)))
    }

    @Test
    fun deviceUnlockWithoutScreenOnWaitsForUserPresent() {
        val machine = started()
        assertNull(machine.onEvent(DeviceUnlocked(LATER)))
        assertEquals(
            Reason.UNCLEAR,
            machine.onEvent(UserPresent(LATER + 10, stillLocked = false))?.reason,
        )
    }

    @Test
    fun lockedScreenOnAnUnlockedDeviceStaysNormal() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER, keyguardLocked = true, deviceLocked = false))
        val request = machine.onEvent(UserPresent(LATER + 300, stillLocked = false))
        assertEquals(Request(Slot.NORMAL, Reason.UNCLEAR), request)
    }

    @Test
    fun screenAlreadyUnlockedByFingerprintStillCountsAsQuick() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER, keyguardLocked = false, deviceLocked = false))
        val request = machine.onEvent(UserPresent(LATER + 13, stillLocked = false))
        assertEquals(Slot.QUICK, request?.slot)
    }

    @Test
    fun doubtIsForgottenAtScreenOff() {
        val machine = started()
        machine.onEvent(ScreenOn(LATER, keyguardLocked = true, deviceLocked = false))
        machine.onEvent(ScreenOff())
        assertEquals(Slot.QUICK, machine.unlock(LATER + 5_000, 200)?.slot)
    }

    @Test
    fun stoppingServiceForcesNormal() {
        assertEquals(
            Request(Slot.NORMAL, Reason.SERVICE_STOPPING, force = true),
            started().onEvent(ServiceStopping),
        )
    }

    /** Face recognised after [tookMs], lock screen still up at that moment. */
    private fun UnlockStateMachine.faceAfter(tookMs: Long): Request? {
        onEvent(ScreenOn(LATER))
        return onEvent(DeviceUnlocked(LATER + tookMs, lockScreenShowing = true))
    }

    @Test
    fun quickFaceIsSetAtOnce() {
        val machine = started()
        assertEquals(Slot.QUICK, machine.faceAfter(264)?.slot)
        assertNull(machine.onEvent(LockScreenCheck(stillShowing = true)))
    }

    @Test
    fun slowFaceIsQuickWhenTheLockScreenStays() {
        val machine = started()
        assertNull(machine.faceAfter(1_846))
        assertEquals(
            Request(Slot.QUICK, Reason.FACE_UNLOCK),
            machine.onEvent(LockScreenCheck(stillShowing = true)),
        )
    }

    @Test
    fun lockScreenLeavingByItselfIsNotFace() {
        val machine = started()
        assertNull(machine.faceAfter(1_900))
        assertEquals(Slot.SLOW, machine.onEvent(LockScreenCheck(stillShowing = false))?.slot)
    }

    @Test
    fun userPresentBeforeTheCheckIsJudgedByTime() {
        val machine = started()
        machine.faceAfter(1_900)
        val request = machine.onEvent(UserPresent(LATER + 2_000, stillLocked = false))
        assertEquals(Slot.SLOW, request?.slot)
        assertNull(machine.onEvent(LockScreenCheck(stillShowing = false)))
    }

    @Test
    fun screenOffCancelsAPendingFaceCheck() {
        val machine = started()
        machine.faceAfter(1_846)
        machine.onEvent(ScreenOff())
        assertNull(machine.onEvent(LockScreenCheck(stillShowing = true)))
    }

    @Test
    fun slowFaceDuringTheStartHoldStaysNormal() {
        val machine = UnlockStateMachine()
        machine.onEvent(ServiceStarted(LATER - 5_000))
        machine.faceAfter(1_846)
        val request = machine.onEvent(LockScreenCheck(stillShowing = true))
        assertEquals(Request(Slot.NORMAL, Reason.JUST_STARTED), request)
    }

    private fun UnlockStateMachine.wakeAlreadyUnlocked(atMs: Long) =
        onEvent(ScreenOn(atMs, keyguardLocked = true, deviceLocked = false))

    @Test
    fun alreadyUnlockedAfterALockedScreenOffIsQuickAtOnce() {
        val machine = started()
        machine.onEvent(ScreenOff(deviceLocked = true))
        assertEquals(Slot.QUICK, machine.wakeAlreadyUnlocked(LATER)?.slot)
        // The swipe that follows, however late, changes nothing.
        assertNull(machine.onEvent(UserPresent(LATER + 3_000, stillLocked = false)))
    }

    @Test
    fun alreadyUnlockedWhenNeverLockedStaysNormal() {
        val machine = started()
        machine.onEvent(ScreenOff(deviceLocked = false))
        assertNull(machine.wakeAlreadyUnlocked(LATER))
        val request = machine.onEvent(UserPresent(LATER + 300, stillLocked = false))
        assertEquals(Request(Slot.NORMAL, Reason.UNCLEAR), request)
    }

    @Test
    fun alreadyUnlockedDuringTheStartHoldStaysNormal() {
        val machine = UnlockStateMachine()
        machine.onEvent(ServiceStarted(LATER - 5_000))
        machine.onEvent(ScreenOff(deviceLocked = true))
        assertEquals(Reason.JUST_STARTED, machine.wakeAlreadyUnlocked(LATER)?.reason)
    }

    private companion object {
        const val LATER = 500_000L
    }
}
