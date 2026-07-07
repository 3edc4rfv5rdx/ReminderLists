package com.reminderlists.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Local-only file logger (TZ 8). Toggled by "Write logs to file" setting, off by default.
// Never logs PIN codes or record texts. Size-based rotation keeps one previous file. Nothing
// is ever sent anywhere.
object Logger {
    private const val TAG = "ReminderLists"
    private const val MAX_BYTES = 512 * 1024L // rotate app.log -> app.log.1 past this size
    private val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    @Volatile
    var writeToFile: Boolean = false

    // Set once at app startup; the internal logs/ directory (TZ 8, included in backups 3.8).
    @Volatile
    private var logDir: File? = null

    fun init(context: Context) {
        logDir = File(context.filesDir, "logs").apply { mkdirs() }
    }

    fun i(msg: String) {
        Log.i(TAG, msg)
        if (writeToFile) appendToFile("INFO", msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        Log.w(TAG, msg, t)
        if (writeToFile) appendToFile("WARN", msg + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        if (writeToFile) appendToFile("ERROR", msg + (t?.let { " | ${it.javaClass.simpleName}: ${it.message}" } ?: ""))
    }

    private fun appendToFile(level: String, msg: String) {
        val dir = logDir ?: return
        synchronized(lock) {
            try {
                val file = File(dir, "app.log")
                if (file.length() > MAX_BYTES) {
                    val backup = File(dir, "app.log.1")
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText("${timestamp.format(Date())} $level $msg\n")
            } catch (_: Exception) {
                // Logging must never crash the app; drop the line on any I/O error.
            }
        }
    }
}
