package com.reminderlists.util

import android.util.Log

// Local-only file logger (TZ 8). Toggled by "Write logs to file" setting, off by default.
// Never logs PIN codes or record texts. File rotation TODO. Scaffold: forwards to logcat;
// file sink to be implemented against logs/ in internal storage.
object Logger {
    private const val TAG = "ReminderLists"

    @Volatile
    var writeToFile: Boolean = false

    fun i(msg: String) {
        Log.i(TAG, msg)
        if (writeToFile) appendToFile("INFO", msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        Log.w(TAG, msg, t)
        if (writeToFile) appendToFile("WARN", msg)
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(TAG, msg, t)
        if (writeToFile) appendToFile("ERROR", msg)
    }

    private fun appendToFile(level: String, msg: String) {
        // TODO: write timestamped "$level $msg" to logs/ with size-based rotation (TZ 8).
    }
}
