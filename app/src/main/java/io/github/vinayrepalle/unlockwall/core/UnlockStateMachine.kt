package io.github.vinayrepalle.unlockwall.core

/**
 * Turns phone events into wallpaper requests. Every doubtful case asks for NORMAL, so a
 * personal picture is only requested for an unlock that is clearly quick.
 *
 * Not thread safe: feed it from one thread.
 */
class UnlockStateMachine(
    private val quickLimitMs: Long = QUICK_LIMIT_MS,
    private val holdAfterStartMs: Long = HOLD_AFTER_START_MS,
) {
    private var startedAtMs: Long? = null

    /** First SCREEN_ON since the screen was last off. A later one would look quicker. */
    private var screenOnAtMs: Long? = null
    private var unlocked = false

    /** The phone required authentication when the screen last went off. */
    private var lockedAtScreenOff = false

    /** Lock screen up but nothing to authenticate, and it was not locked at screen off. */
    private var doubtfulWake = false

    /** A slow-looking unlock that left the lock screen up: waiting to see if it stays. */
    private var pendingUnlockAtMs: Long? = null
    private var shuttingDown = false

    /** Returns null when nothing should change. */
    fun onEvent(event: PhoneEvent): Request? {
        if (shuttingDown && event !is PhoneEvent.ServiceStarted) return null
        return when (event) {
            is PhoneEvent.ServiceStarted -> {
                startedAtMs = event.atMs
                screenOnAtMs = null
                unlocked = false
                doubtfulWake = false
                lockedAtScreenOff = false
                pendingUnlockAtMs = null
                shuttingDown = false
                Request(Slot.NORMAL, Reason.SERVICE_STARTED, force = true)
            }

            is PhoneEvent.ScreenOn -> onScreenOn(event)

            is PhoneEvent.ScreenOff -> {
                lockedAtScreenOff = event.deviceLocked
                screenOnAtMs = null
                unlocked = false
                doubtfulWake = false
                pendingUnlockAtMs = null
                Request(Slot.NORMAL, Reason.SCREEN_OFF)
            }

            is PhoneEvent.UserPresent -> onUserPresent(event)

            // Without a SCREEN_ON there is nothing to time yet: wait for USER_PRESENT.
            is PhoneEvent.DeviceUnlocked -> onDeviceUnlocked(event)

            is PhoneEvent.LockScreenCheck -> onLockScreenCheck(event.stillShowing)

            PhoneEvent.ServiceStopping ->
                Request(Slot.NORMAL, Reason.SERVICE_STOPPING, force = true)

            PhoneEvent.ShuttingDown -> {
                shuttingDown = true
                Request(Slot.NORMAL, Reason.SHUTTING_DOWN, force = true)
            }

            PhoneEvent.SwitchedOff -> {
                screenOnAtMs = null
                unlocked = false
                pendingUnlockAtMs = null
                lockedAtScreenOff = false
                Request(Slot.NORMAL, Reason.SWITCHED_OFF, force = true)
            }
        }
    }

    private fun onScreenOn(event: PhoneEvent.ScreenOn): Request? {
        val alreadyUnlocked = event.keyguardLocked && !event.deviceLocked
        val wasLocked = lockedAtScreenOff
        lockedAtScreenOff = false
        // The screen came on twice around one unlock, so a SCREEN_OFF was lost.
        if (unlocked && screenOnAtMs != null) {
            pendingUnlockAtMs = null
            screenOnAtMs = event.atMs
            unlocked = false
            doubtfulWake = alreadyUnlocked
            return Request(Slot.NORMAL, Reason.MISSED_SCREEN_OFF)
        }
        // If already unlocked, this SCREEN_ON arrived after its USER_PRESENT: ignore it.
        if (!unlocked && screenOnAtMs == null) {
            screenOnAtMs = event.atMs
            // Locked when the screen went off, unlocked already now: someone authenticated
            // before this signal arrived, which only a very fast face match does (F19).
            if (alreadyUnlocked && wasLocked) return judge(event.atMs)
            doubtfulWake = alreadyUnlocked
        }
        return null
    }

    private fun onUserPresent(event: PhoneEvent.UserPresent): Request? {
        // One decision per unlock; repeats are ignored.
        if (unlocked) return null
        // A late signal from an earlier unlock: the phone is locked again already.
        if (event.stillLocked) return Request(Slot.NORMAL, Reason.UNCLEAR)
        // A swipe before the check: judge by when the phone actually unlocked.
        return judge(pendingUnlockAtMs ?: event.atMs)
    }

    private fun onDeviceUnlocked(event: PhoneEvent.DeviceUnlocked): Request? {
        val screenOn = screenOnAtMs
        if (unlocked || screenOn == null || pendingUnlockAtMs != null) return null
        // Too slow to be quick by time, but it may be face: look again shortly.
        if (event.lockScreenShowing && event.atMs - screenOn >= quickLimitMs && !doubtfulWake) {
            pendingUnlockAtMs = event.atMs
            return null
        }
        return judge(event.atMs)
    }

    private fun onLockScreenCheck(stillShowing: Boolean): Request? {
        val unlockedAt = pendingUnlockAtMs ?: return null
        if (unlocked) return null
        val byTime = judge(unlockedAt)
        // Finger and PIN take the lock screen away at once; only face leaves it waiting.
        return if (stillShowing && byTime.reason == Reason.SLOW_UNLOCK) {
            Request(Slot.QUICK, Reason.FACE_UNLOCK)
        } else {
            byTime
        }
    }

    /** Decides quick or slow for the unlock that happened at [atMs]. */
    private fun judge(atMs: Long): Request {
        unlocked = true
        pendingUnlockAtMs = null
        val started = startedAtMs
        if (started == null || atMs - started < holdAfterStartMs) {
            return Request(Slot.NORMAL, Reason.JUST_STARTED)
        }
        val screenOn = screenOnAtMs
        if (screenOn == null || doubtfulWake) return Request(Slot.NORMAL, Reason.UNCLEAR)
        val tookMs = atMs - screenOn
        return when {
            tookMs < 0 -> Request(Slot.NORMAL, Reason.UNCLEAR)
            tookMs < quickLimitMs -> Request(Slot.QUICK, Reason.QUICK_UNLOCK)
            else -> Request(Slot.SLOW, Reason.SLOW_UNLOCK)
        }
    }

    companion object {
        const val QUICK_LIMIT_MS = 1_000L

        /** Finger and PIN cleared the lock screen within 0.2 s; face waited for a swipe. */
        const val LOCK_SCREEN_CHECK_MS = 500L

        /** Signals can be late or out of order shortly after a reboot (finding F15). */
        const val HOLD_AFTER_START_MS = 60_000L
    }
}
