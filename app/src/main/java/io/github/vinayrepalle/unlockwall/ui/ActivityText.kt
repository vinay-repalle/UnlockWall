package io.github.vinayrepalle.unlockwall.ui

/** What an event in the log means to the user. */
enum class ActivityKind {
    QUICK,
    FACE,
    SLOW,
    UNSURE,
    JUST_STARTED,
    SHUTDOWN,
    STARTED,
    SWITCHED_OFF,
    FAILED,
}

data class ActivityEntry(val time: String, val kind: ActivityKind)

/** Turns technical log lines ("MM-dd HH:mm:ss.SSS MESSAGE") into entries worth showing. */
object ActivityText {

    fun parse(line: String): ActivityEntry? {
        if (line.length < PREFIX) return null
        val message = line.substring(PREFIX)
        val reason = message.substringAfter("reason=", "").substringBefore(' ')
        val word = message.substringBefore(' ')
        val kind = when {
            word == "FAILED" || word == "ERROR" -> ActivityKind.FAILED
            word == "SERVICE_STARTED" -> ActivityKind.STARTED
            word == "SWITCHED_OFF" -> ActivityKind.SWITCHED_OFF
            word != "SHOW" && word != "KEEP" -> null
            reason == "QUICK_UNLOCK" -> ActivityKind.QUICK
            reason == "FACE_UNLOCK" -> ActivityKind.FACE
            reason == "SLOW_UNLOCK" -> ActivityKind.SLOW
            reason == "UNCLEAR" || reason == "MISSED_SCREEN_OFF" -> ActivityKind.UNSURE
            reason == "JUST_STARTED" -> ActivityKind.JUST_STARTED
            reason == "SHUTTING_DOWN" -> ActivityKind.SHUTDOWN
            // Putting Normal back at screen off happens constantly: not worth a line.
            else -> null
        } ?: return null
        // "11-26 14:05:17.980" is shown as "11-26 14:05".
        return ActivityEntry(line.substring(0, 11), kind)
    }

    /** Newest first. */
    fun entries(lines: List<String>): List<ActivityEntry> = lines.asReversed().mapNotNull(::parse)

    private const val PREFIX = 19
}
