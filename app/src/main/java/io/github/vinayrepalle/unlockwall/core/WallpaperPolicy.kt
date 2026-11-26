package io.github.vinayrepalle.unlockwall.core

/** Decides which picture, if any, to actually set for a [Request]. */
object WallpaperPolicy {

    /**
     * @param enabled the user's switch.
     * @param available slots that have a picture.
     * @param showing the slot we last set successfully, or null if unknown.
     * @return the slot to set now, or null to leave the wallpaper alone.
     */
    fun decide(request: Request, enabled: Boolean, available: Set<Slot>, showing: Slot?): Slot? {
        // Turning off must still put NORMAL back, although the switch is already off.
        if (!enabled && request.reason != Reason.SWITCHED_OFF) return null
        // Without a NORMAL picture we could never undo a change, so change nothing.
        if (Slot.NORMAL !in available) return null
        val target = if (request.slot in available) request.slot else Slot.NORMAL
        return if (request.force || target != showing) target else null
    }
}
