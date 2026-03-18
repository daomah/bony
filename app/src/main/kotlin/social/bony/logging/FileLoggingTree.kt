package social.bony.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_LOG_BYTES = 2 * 1024 * 1024L // 2 MB
private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

/**
 * Timber tree that writes log lines to [logFile] in the app's private files dir.
 *
 * Rotates: when the file exceeds [MAX_LOG_BYTES] it is moved to [logFile].1
 * and a fresh file is started, giving two generations of history.
 */
class FileLoggingTree(private val logFile: File) : Timber.Tree() {

    init {
        logFile.parentFile?.mkdirs()
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            rotate()
            val level = priorityLabel(priority)
            val timestamp = DATE_FORMAT.format(Date())
            val line = buildString {
                append("$timestamp $level/$tag: $message")
                if (t != null) append("\n${Log.getStackTraceString(t)}")
                append("\n")
            }
            FileWriter(logFile, /* append = */ true).use { it.write(line) }
        } catch (_: Exception) {
            // Never crash because of logging
        }
    }

    private fun rotate() {
        if (logFile.length() < MAX_LOG_BYTES) return
        val rotated = File(logFile.parent, "${logFile.name}.1")
        rotated.delete()
        logFile.renameTo(rotated)
    }

    private fun priorityLabel(priority: Int) = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG   -> "D"
        Log.INFO    -> "I"
        Log.WARN    -> "W"
        Log.ERROR   -> "E"
        Log.ASSERT  -> "A"
        else        -> "?"
    }
}
