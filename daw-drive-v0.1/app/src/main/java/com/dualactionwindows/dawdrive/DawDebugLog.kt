package com.dualactionwindows.dawdrive

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DawDebugLog {

    private const val PREFS = "daw_drive_debug_log"
    private const val KEY_LOG = "entries"
    private const val MAX_ENTRIES = 300
    private const val TAG = "DAWDrive"

    @Synchronized
    fun log(context: Context, event: String, detail: String = "") {
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US
        ).format(Date())

        val line = buildString {
            append(timestamp)
            append(" | ")
            append(event)
            if (detail.isNotBlank()) {
                append(" | ")
                append(detail.replace("\n", " "))
            }
        }

        Log.i(TAG, line)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_LOG, "")
            .orEmpty()
            .lineSequence()
            .filter { it.isNotBlank() }
            .toMutableList()

        existing += line

        prefs.edit()
            .putString(
                KEY_LOG,
                existing.takeLast(MAX_ENTRIES).joinToString("\n")
            )
            .apply()
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LOG, "")
            .orEmpty()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOG)
            .apply()
        Log.i(TAG, "Debug log cleared")
    }
}
