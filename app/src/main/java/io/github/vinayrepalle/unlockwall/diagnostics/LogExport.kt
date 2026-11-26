package io.github.vinayrepalle.unlockwall.diagnostics

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Copies the event log to the phone's Downloads folder, when the user asks, so that a
 * problem can be looked into. It holds phone model, times and events; no pictures.
 */
object LogExport {

    /** Returns the file name, or null if it could not be saved. Blocking. */
    fun save(context: Context, lines: List<String>): String? {
        val stamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
        val name = "unlockwall-log-$stamp.txt"
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        val header = "UnlockWall $version on ${Build.MANUFACTURER} ${Build.MODEL}, " +
            "Android ${Build.VERSION.RELEASE}, build ${Build.DISPLAY}"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        return try {
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            val out = resolver.openOutputStream(uri) ?: return null
            out.bufferedWriter().use { writer ->
                writer.appendLine(header)
                lines.forEach(writer::appendLine)
            }
            name
        } catch (e: Exception) {
            null
        }
    }
}
