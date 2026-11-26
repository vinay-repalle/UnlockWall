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
    private var shuttingDown = false

    /** Returns null when nothing should change. */
    fun onEvent(event: PhoneEvent): Request? {
        if (shuttingDown && event !is PhoneEvent.ServiceStarted) return null
        return when (event) {
            is PhoneEvent.ServiceStarted -> {
                startedAtMs = event.atMs
                screenOnAtMs = null
                unlocked = false
                shuttingDown = false
                Request(Slot.NORMAL, Reason.SERVICE_STARTED, force = true)
            }

            is PhoneEvent.ScreenOn -> onScreenOn(event.atMs)

            PhoneEvent.ScreenOff -> {
                screenOnAtMs = null
                unlocked = false
                Request(Slot.NORMAL, Reason.SCREEN_OFF)
            }

            is PhoneEvent.UserPresent -> onUserPresent(event)

            PhoneEvent.ShuttingDown -> {
                shuttingDown = true
                Request(Slot.NORMAL, Reason.SHUTTING_DOWN, force = true)
            }

            PhoneEvent.SwitchedOff -> {
                screenOnAtMs = null
                unlocked = false
                Request(Slot.NORMAL, Reason.SWITCHED_OFF, force = true)
            }
        }
    }

    private fun onScreenOn(atMs: Long): Request? {
        // The screen came on twice around one unlock, so a SCREEN_OFF was lost.
        if (unlocked && screenOnAtMs != null) {
            screenOnAtMs = atMs
            unlocked = false
            return Request(Slot.NORMAL, Reason.MISSED_SCREEN_OFF)
        }
        // If already unlocked, this SCREEN_ON arrived after its USER_PRESENT: ignore it.
        if (!unlocked && screenOnAtMs == null) screenOnAtMs = atMs
        return null
    }

    private fun onUserPresent(event: PhoneEvent.UserPresent): Request? {
        // One decision per unlock; repeats are ignored.
        if (unlocked) return null
        // A late signal from an earlier unlock: the phone is locked again already.
        if (event.stillLocked) return Request(Slot.NORMAL, Reason.UNCLEAR)
        unlocked = true

        val started = startedAtMs
        if (started == null || event.atMs - started < holdAfterStartMs) {
            return Request(Slot.NORMAL, Reason.JUST_STARTED)
        }
        val screenOn = screenOnAtMs ?: return Request(Slot.NORMAL, Reason.UNCLEAR)
        val tookMs = event.atMs - screenOn
        return when {
            tookMs < 0 -> Request(Slot.NORMAL, Reason.UNCLEAR)
            tookMs < quickLimitMs -> Request(Slot.QUICK, Reason.QUICK_UNLOCK)
            else -> Request(Slot.SLOW, Reason.SLOW_UNLOCK)
        }
    }

    companion object {
        const val QUICK_LIMIT_MS = 1_000L

        /** Signals can be late or out of order shortly after a reboot (finding F15). */
        const val HOLD_AFTER_START_MS = 60_000L
    }
}
