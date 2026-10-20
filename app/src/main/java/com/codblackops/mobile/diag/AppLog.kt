package com.codblackops.mobile.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Launcher log (separate from Dolphin's own dolphin.log). Kept small and rotated, written to the
 * app's private files dir and exportable from the Diagnostics screen.
 */
object AppLog {
    private const val TAG = "CODBlackOps"
    private const val MAX_BYTES = 512 * 1024L
    private var file: File? = null
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            val dir = File(context.filesDir, "logs").apply { mkdirs() }
            val f = File(dir, "launcher.log")
            if (f.length() > MAX_BYTES) {
                val old = File(dir, "launcher.old.log")
                old.delete()
                f.renameTo(old)
            }
            file = File(dir, "launcher.log")
        }
    }

    fun logFile(): File? = file

    fun i(message: String) = write("I", message, null)

    fun w(message: String, t: Throwable? = null) = write("W", message, t)

    fun e(message: String, t: Throwable? = null) = write("E", message, t)

    private fun write(level: String, message: String, t: Throwable?) {
        when (level) {
            "E" -> Log.e(TAG, message, t)
            "W" -> Log.w(TAG, message, t)
            else -> Log.i(TAG, message)
        }
        val f = file ?: return
        synchronized(lock) {
            try {
                val line = buildString {
                    append(timeFormat.format(Date())).append(' ').append(level).append(' ').append(message)
                    if (t != null) append(" | ").append(t.javaClass.simpleName).append(": ").append(t.message)
                    append('\n')
                }
                f.appendText(line)
            } catch (_: Exception) {
                // logging must never crash the app
            }
        }
    }

    fun tail(maxLines: Int = 200): String {
        val f = file ?: return ""
        return tailOf(f, maxLines)
    }

    fun tailOf(f: File, maxLines: Int): String {
        if (!f.isFile) return ""
        return try {
            val lines = f.readLines()
            lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "Gagal membaca log: ${e.message}"
        }
    }
}
