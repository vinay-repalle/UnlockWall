package io.github.vinayrepalle.unlockwall.diagnostics

import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** A short event log kept in app-private storage. It holds times and states only. */
class Diagnostics(private val file: File) {
    private val writer = Executors.newSingleThreadExecutor()
    private val format = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(message: String) {
        val line = "${LocalDateTime.now().format(format)} $message"
        Log.i("UnlockWall", line)
        writer.execute {
            runCatching {
                file.appendText(line + "\n")
                if (file.length() > MAX_BYTES) {
                    file.writeText(file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    /** Newest last. */
    fun recent(count: Int): List<String> =
        runCatching { file.readLines().takeLast(count) }.getOrDefault(emptyList())

    /** Waits for queued lines to reach disk (used at shutdown). */
    fun flush() {
        writer.submit {}.get()
    }

    private companion object {
        const val MAX_BYTES = 200_000L
        const val KEEP_LINES = 1_000
    }
}
