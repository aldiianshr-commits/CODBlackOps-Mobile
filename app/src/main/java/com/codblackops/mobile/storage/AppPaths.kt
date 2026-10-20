package com.codblackops.mobile.storage

import android.os.Environment
import java.io.File

/** Fixed public folders requested for the app. */
object AppPaths {
    const val ROOT_NAME = "CODBlackOps"
    const val SAVE_NAME = "Save"
    const val BACKUP_NAME = "Backups"
    const val LOGS_NAME = "Logs"

    /** /storage/emulated/0/CODBlackOps */
    @Suppress("DEPRECATION")
    val root: File
        get() = File(Environment.getExternalStorageDirectory(), ROOT_NAME)

    /** /storage/emulated/0/CODBlackOps/Save */
    val saveDir: File
        get() = File(root, SAVE_NAME)

    val backupDir: File
        get() = File(saveDir, BACKUP_NAME)

    val logsDir: File
        get() = File(root, LOGS_NAME)

    /** Creates the folder structure; returns false when storage is not writable. */
    fun ensureDirs(): Boolean {
        return try {
            listOf(root, saveDir, backupDir, logsDir).all { it.isDirectory || it.mkdirs() }
        } catch (_: SecurityException) {
            false
        }
    }

    /** Folder names inside the game folder that the scanner skips. */
    val scanSkipNames = setOf(SAVE_NAME, LOGS_NAME, BACKUP_NAME, "Android", ".thumbnails")
}
