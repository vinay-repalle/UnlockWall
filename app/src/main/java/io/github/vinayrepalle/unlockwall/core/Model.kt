package io.github.vinayrepalle.unlockwall.core

// Plain Kotlin: nothing in this package may import Android classes.

/**
 * The three home screen wallpapers. Android does not report how the phone was unlocked,
 * so QUICK and SLOW describe speed only (see docs/EXPERIMENT_RESULTS.md).
 */
enum class Slot {
    /** The safe default, shown while locked and whenever in doubt. */
    NORMAL,
    QUICK,
    SLOW,
}

/** What the phone told us. All times are milliseconds since boot. */
sealed interface PhoneEvent {
    data class ServiceStarted(val atMs: Long) : PhoneEvent

    /** [keyguardLocked] and [deviceLocked] are read when the screen-on signal arrives. */
    data class ScreenOn(
        val atMs: Long,
        val keyguardLocked: Boolean = true,
        val deviceLocked: Boolean = true,
    ) : PhoneEvent

    /**
     * The phone stopped requiring authentication. With face unlock the lock screen can
     * stay until a swipe, so this comes seconds before [UserPresent] (finding F17).
     */
    data class DeviceUnlocked(val atMs: Long, val lockScreenShowing: Boolean = false) : PhoneEvent

    /** Sent a short while after a [DeviceUnlocked] that left the lock screen showing. */
    data class LockScreenCheck(val stillShowing: Boolean) : PhoneEvent
    /** [deviceLocked] says whether authentication was already required at screen off. */
    data class ScreenOff(val deviceLocked: Boolean = false) : PhoneEvent

    /** [stillLocked] is the keyguard state read when the signal arrived. */
    data class UserPresent(val atMs: Long, val stillLocked: Boolean) : PhoneEvent
    data object ShuttingDown : PhoneEvent

    /** The service is being destroyed. */
    data object ServiceStopping : PhoneEvent

    /** The user turned the app's switch off. */
    data object SwitchedOff : PhoneEvent
}

enum class Reason {
    SERVICE_STARTED,
    SCREEN_OFF,
    QUICK_UNLOCK,
    SLOW_UNLOCK,

    /** Unlocked while the lock screen waited for a swipe: face unlock (finding F18). */
    FACE_UNLOCK,
    JUST_STARTED,
    UNCLEAR,
    MISSED_SCREEN_OFF,
    SHUTTING_DOWN,
    SWITCHED_OFF,
    SERVICE_STOPPING,
    PICTURES_CHANGED,
}

/**
 * A wish to show [slot]. [force] means: set it even if our record says it is already
 * showing, because that record cannot be trusted at this moment.
 */
data class Request(val slot: Slot, val reason: Reason, val force: Boolean = false)
