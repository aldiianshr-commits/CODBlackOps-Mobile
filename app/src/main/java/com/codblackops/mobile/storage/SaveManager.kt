package com.codblackops.mobile.storage

import android.content.Context
import android.net.Uri
import com.codblackops.mobile.core.disc.BlackOpsCatalog
import com.codblackops.mobile.core.save.BackupPolicy
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Save data of Black Ops lives in Dolphin's emulated Wii NAND
 * (<user dir>/Wii/title/00010000/<title id>/data). This class mirrors it to
 * /storage/emulated/0/CODBlackOps/Save (or the SAF folder), and creates/restores zip backups.
 */
object SaveManager {
    private const val ZIP_DATA_PREFIX = "data/"
    private const val ZIP_STATE_PREFIX = "StateSaves/"

    data class Status(
        val gameId: String,
        val nandPath: String,
        val nandFileCount: Int,
        val nandBytes: Long,
        val nandLastModified: Long,
        val stateFiles: Int,
        val targetDescription: String?,
        val externalHasData: Boolean,
        val backups: List<BackupPolicy.Entry>,
    )

    // ------------------------------------------------------------------ targets
    /** Where mirrored saves and backups are written. */
    interface Target {
        val description: String
        fun hasMirror(gameId: String): Boolean
        fun writeMirror(gameId: String, sourceDataDir: File)
        fun readMirror(gameId: String, destDataDir: File): Boolean
        fun listBackups(): List<String>
        fun writeBackup(name: String, writer: (OutputStream) -> Unit)
        fun openBackup(name: String): InputStream?
        fun deleteBackup(name: String)
    }

    private class FileTarget : Target {
        override val description = AppPaths.saveDir.path
        private fun mirrorDir(gameId: String) = File(AppPaths.saveDir, "$gameId/data")

        override fun hasMirror(gameId: String) = mirrorDir(gameId).listFiles()?.isNotEmpty() == true

        override fun writeMirror(gameId: String, sourceDataDir: File) {
            val dst = mirrorDir(gameId)
            dst.deleteRecursively()
            if (!dst.mkdirs() && !dst.isDirectory) throw IOException("Tidak bisa membuat ${dst.path}")
            sourceDataDir.copyRecursively(dst, overwrite = true)
        }

        override fun readMirror(gameId: String, destDataDir: File): Boolean {
            val src = mirrorDir(gameId)
            if (src.listFiles()?.isNotEmpty() != true) return false
            destDataDir.mkdirs()
            src.copyRecursively(destDataDir, overwrite = true)
            return true
        }

        override fun listBackups(): List<String> = AppPaths.backupDir.list()?.toList() ?: emptyList()

        override fun writeBackup(name: String, writer: (OutputStream) -> Unit) {
            AppPaths.backupDir.mkdirs()
            File(AppPaths.backupDir, name).outputStream().use(writer)
        }

        override fun openBackup(name: String): InputStream? =
            File(AppPaths.backupDir, name).takeIf { it.isFile }?.inputStream()

        override fun deleteBackup(name: String) {
            File(AppPaths.backupDir, name).delete()
        }
    }

    private class SafTarget(ctx: Context, treeUri: Uri) : Target {
        private val tree = SafTree(ctx, treeUri)
        override val description = "Folder SAF: $treeUri/${AppPaths.SAVE_NAME}"

        private fun saveDirId(): String =
            tree.ensureDir(tree.rootId, AppPaths.SAVE_NAME)?.documentId ?: throw IOException("Folder Save SAF tidak bisa dibuat")

        private fun backupDirId(): String =
            tree.ensureDir(saveDirId(), AppPaths.BACKUP_NAME)?.documentId ?: throw IOException("Folder Backups SAF tidak bisa dibuat")

        override fun hasMirror(gameId: String): Boolean {
            val g = tree.findChild(saveDirId(), gameId) ?: return false
            val d = tree.findChild(g.documentId, "data") ?: return false
            return tree.list(d.documentId).isNotEmpty()
        }

        override fun writeMirror(gameId: String, sourceDataDir: File) {
            val gameDir = tree.ensureDir(saveDirId(), gameId) ?: throw IOException("Folder $gameId SAF gagal")
            tree.findChild(gameDir.documentId, "data")?.let { tree.delete(it) }
            val data = tree.ensureDir(gameDir.documentId, "data") ?: throw IOException("Folder data SAF gagal")
            copyDirToSaf(sourceDataDir, data.documentId)
        }

        private fun copyDirToSaf(src: File, parentId: String) {
            for (f in src.listFiles() ?: emptyArray()) {
                if (f.isDirectory) {
                    val d = tree.ensureDir(parentId, f.name) ?: throw IOException("Gagal membuat ${f.name}")
                    copyDirToSaf(f, d.documentId)
                } else {
                    val out = tree.createFile(parentId, f.name, "application/octet-stream")
                        ?: throw IOException("Gagal menulis ${f.name}")
                    out.use { o -> f.inputStream().use { it.copyTo(o) } }
                }
            }
        }

        override fun readMirror(gameId: String, destDataDir: File): Boolean {
            val g = tree.findChild(saveDirId(), gameId) ?: return false
            val d = tree.findChild(g.documentId, "data") ?: return false
            if (tree.list(d.documentId).isEmpty()) return false
            destDataDir.mkdirs()
            copySafToDir(d.documentId, destDataDir)
            return true
        }

        private fun copySafToDir(parentId: String, dst: File) {
            for (doc in tree.list(parentId)) {
                val target = File(dst, doc.name)
                if (doc.isDirectory) {
                    target.mkdirs()
                    copySafToDir(doc.documentId, target)
                } else {
                    tree.openInput(doc)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                }
            }
        }

        override fun listBackups(): List<String> = tree.list(backupDirId()).filter { !it.isDirectory }.map { it.name }

        override fun writeBackup(name: String, writer: (OutputStream) -> Unit) {
            val out = tree.createFile(backupDirId(), name, "application/zip") ?: throw IOException("Gagal membuat $name")
            out.use(writer)
        }

        override fun openBackup(name: String): InputStream? =
            tree.findChild(backupDirId(), name)?.let { tree.openInput(it) }

        override fun deleteBackup(name: String) {
            tree.findChild(backupDirId(), name)?.let { tree.delete(it) }
        }
    }

    fun target(ctx: Context): Target? {
        if (StorageAccess.hasFolderAccess(ctx) && AppPaths.ensureDirs()) return FileTarget()
        val tree = AppPrefs.safTreeUri(ctx)?.let { Uri.parse(it) } ?: return null
        if (!StorageAccess.treeIsWritable(ctx, tree)) return null
        return SafTarget(ctx, tree)
    }

    // ------------------------------------------------------------------ NAND helpers
    private fun userDir(): File = File(DolphinBridge.userDirectory() ?: throw IOException("Dolphin belum siap"))

    fun nandDataDir(gameId: String): File = File(userDir(), BlackOpsCatalog.nandSaveDataPath(gameId))

    private fun stateFiles(gameId: String): List<File> =
        File(userDir(), "StateSaves").listFiles { f -> f.isFile && f.name.startsWith("$gameId.") }?.toList()
            ?: emptyList()

    private fun requireIdle() {
        if (!DolphinBridge.isEmulationIdle()) throw IOException("Game sedang berjalan; tutup game terlebih dahulu")
    }

    fun status(ctx: Context, gameId: String): Status {
        val dir = nandDataDir(gameId)
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val t = try {
            target(ctx)
        } catch (e: Exception) {
            null
        }
        val backups = try {
            t?.let { BackupPolicy.listFor(gameId, it.listBackups()) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val ext = try {
            t?.hasMirror(gameId) ?: false
        } catch (e: Exception) {
            false
        }
        return Status(
            gameId = gameId,
            nandPath = dir.path,
            nandFileCount = files.size,
            nandBytes = files.sumOf { it.length() },
            nandLastModified = files.maxOfOrNull { it.lastModified() } ?: 0L,
            stateFiles = stateFiles(gameId).size,
            targetDescription = t?.description,
            externalHasData = ext,
            backups = backups,
        )
    }

    /** Copies NAND save -> Save/<GAMEID>/data. */
    fun mirrorToExternal(ctx: Context, gameId: String): String {
        requireIdle()
        val t = target(ctx) ?: throw IOException("Tidak ada folder Save yang bisa ditulis (beri izin akses atau pilih folder SAF)")
        val src = nandDataDir(gameId)
        if (src.listFiles()?.isNotEmpty() != true) return "Belum ada save di NAND untuk $gameId"
        t.writeMirror(gameId, src)
        AppLog.i("Save $gameId disalin ke ${t.description}")
        return "Save disalin ke ${t.description}/$gameId"
    }

    /** Copies Save/<GAMEID>/data -> NAND. Overwrites NAND after making a backup. */
    fun importFromExternal(ctx: Context, gameId: String, onlyIfNandEmpty: Boolean): String {
        requireIdle()
        val t = target(ctx) ?: throw IOException("Folder Save tidak tersedia")
        val dst = nandDataDir(gameId)
        val nandHasData = dst.listFiles()?.isNotEmpty() == true
        if (onlyIfNandEmpty && nandHasData) return "NAND sudah berisi save; impor dilewati"
        if (!t.hasMirror(gameId)) return "Folder Save tidak berisi save $gameId"
        if (nandHasData) backup(ctx, gameId, "pre-import")
        dst.deleteRecursively()
        t.readMirror(gameId, dst)
        AppLog.i("Save $gameId diimpor dari ${t.description}")
        return "Save $gameId diimpor dari folder Save"
    }

    fun backup(ctx: Context, gameId: String, tag: String?): String {
        requireIdle()
        val t = target(ctx) ?: throw IOException("Folder Save tidak tersedia untuk backup")
        val dataDir = nandDataDir(gameId)
        val states = if (AppPrefs.includeSaveStates(ctx)) stateFiles(gameId) else emptyList()
        if (dataDir.listFiles()?.isNotEmpty() != true && states.isEmpty()) {
            return "Tidak ada save untuk di-backup"
        }
        val name = BackupPolicy.fileName(gameId, System.currentTimeMillis(), tag)
        t.writeBackup(name) { out ->
            ZipOutputStream(out).use { zip ->
                if (dataDir.isDirectory) {
                    dataDir.walkTopDown().filter { it.isFile }.forEach { f ->
                        val rel = f.relativeTo(dataDir).invariantSeparatorsPath
                        zip.putNextEntry(ZipEntry(ZIP_DATA_PREFIX + rel))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                for (s in states) {
                    zip.putNextEntry(ZipEntry(ZIP_STATE_PREFIX + s.name))
                    s.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        for (old in BackupPolicy.toDelete(gameId, t.listBackups(), BackupPolicy.DEFAULT_KEEP)) t.deleteBackup(old)
        AppLog.i("Backup dibuat: $name")
        return "Backup dibuat: $name"
    }

    fun restore(ctx: Context, gameId: String, backupName: String): String {
        requireIdle()
        val t = target(ctx) ?: throw IOException("Folder Save tidak tersedia")
        val input = t.openBackup(backupName) ?: throw IOException("Backup $backupName tidak ditemukan")
        val dataDir = nandDataDir(gameId)
        // Safety net before overwriting anything.
        if (dataDir.listFiles()?.isNotEmpty() == true) backup(ctx, gameId, "pre-restore")
        val tmp = File(dataDir.parentFile, "data.restore-tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        val stateDir = File(userDir(), "StateSaves").apply { mkdirs() }
        var count = 0
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val (base, rel) = when {
                        e.name.startsWith(ZIP_DATA_PREFIX) -> tmp to e.name.removePrefix(ZIP_DATA_PREFIX)
                        e.name.startsWith(ZIP_STATE_PREFIX) -> stateDir to e.name.removePrefix(ZIP_STATE_PREFIX)
                        else -> continue
                    }
                    val out = File(base, rel)
                    // Zip-slip protection
                    if (!out.canonicalPath.startsWith(base.canonicalPath + File.separator)) {
                        throw IOException("Entri zip tidak aman: ${e.name}")
                    }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                    count++
                }
            }
        }
        dataDir.deleteRecursively()
        if (!tmp.renameTo(dataDir)) {
            tmp.copyRecursively(dataDir, overwrite = true)
            tmp.deleteRecursively()
        }
        AppLog.i("Backup $backupName dipulihkan ($count file)")
        return "Backup dipulihkan ($count file)"
    }

    fun deleteNandSave(ctx: Context, gameId: String): String {
        requireIdle()
        val dir = nandDataDir(gameId)
        if (dir.listFiles()?.isNotEmpty() == true) backup(ctx, gameId, "pre-delete")
        dir.deleteRecursively()
        AppLog.i("Save NAND $gameId dihapus")
        return "Save $gameId dihapus dari NAND (backup otomatis dibuat bila ada data)"
    }

    /** Called before launch: import the user's mirrored save into an empty NAND. */
    fun prepareForLaunch(ctx: Context, gameId: String) {
        try {
            val msg = importFromExternal(ctx, gameId, onlyIfNandEmpty = true)
            AppLog.i("Persiapan save: $msg")
        } catch (e: Exception) {
            AppLog.w("Persiapan save dilewati: ${e.message}")
        }
    }

    /** Called after a session: mirror and (optionally) back up. */
    fun afterSession(ctx: Context, gameId: String): String {
        val parts = ArrayList<String>()
        parts.add(mirrorToExternal(ctx, gameId))
        if (AppPrefs.autoBackupOnExit(ctx)) parts.add(backup(ctx, gameId, "auto"))
        return parts.joinToString("; ")
    }
}
