package com.codblackops.mobile.storage

import android.content.Context
import android.net.Uri
import com.codblackops.mobile.core.disc.BlackOpsCatalog
import com.codblackops.mobile.core.disc.ByteSource
import com.codblackops.mobile.core.disc.Compatibility
import com.codblackops.mobile.core.disc.DiscFormat
import com.codblackops.mobile.core.disc.DiscHeaderParser
import com.codblackops.mobile.core.disc.DiscInfo
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.model.GameFile
import java.io.File

/** One file found during a scan, with header and backend validation results. */
data class ScannedGame(
    /** Absolute path or content:// URI; passed unchanged to Dolphin. */
    val location: String,
    val fileName: String,
    val sizeBytes: Long,
    val source: Source,
    val header: DiscInfo,
    val backendOk: Boolean?,
    val backendTitle: String?,
    val backendGameId: String?,
    val backendFormat: String?,
    val compatibility: Compatibility,
) {
    enum class Source(val label: String) { DEFAULT_FOLDER("Folder default"), SAF_FOLDER("Folder pilihan (SAF)") }

    /** Effective game ID (backend first, header second). */
    val gameId: String? get() = backendGameId?.takeIf { it.isNotEmpty() } ?: header.gameId

    val title: String
        get() = backendTitle?.takeIf { it.isNotBlank() }
            ?: header.internalTitle?.takeIf { it.isNotBlank() }
            ?: fileName

    val formatLabel: String get() = backendFormat?.takeIf { it.isNotBlank() } ?: header.format.label

    val regionLabel: String get() = BlackOpsCatalog.regionName(gameId)
}

data class ScanResult(val games: List<ScannedGame>, val notes: List<String>, val scannedFiles: Int)

object GameScanner {
    private const val MAX_DEPTH = 3

    suspend fun scan(ctx: Context): ScanResult = withContext(Dispatchers.IO) {
        val notes = ArrayList<String>()
        val games = ArrayList<ScannedGame>()
        var scanned = 0
        val backendReady = DolphinBridge.isReady()
        if (!backendReady) notes.add("Backend Dolphin belum siap; validasi backend akan dilakukan setelah inisialisasi.")

        // 1) Default folder /storage/emulated/0/CODBlackOps
        if (StorageAccess.hasFolderAccess(ctx)) {
            if (!AppPaths.ensureDirs()) notes.add("Folder ${AppPaths.root.path} tidak bisa dibuat.")
            val files = walk(AppPaths.root, 0)
            for (f in files) {
                scanned++
                val header = try {
                    ByteSource.FileSource(f).use { DiscHeaderParser.parse(it, f.name) }
                } catch (e: Exception) {
                    DiscInfo.error(DiscFormat.fromFileName(f.name), "Tidak bisa dibuka: ${e.message}")
                }
                games.add(validate(f.absolutePath, f.name, f.length(), ScannedGame.Source.DEFAULT_FOLDER, header, backendReady))
            }
        } else {
            notes.add("Izin akses folder ${AppPaths.root.path} belum diberikan.")
        }

        // 2) Folder picked with the Storage Access Framework
        val treeString = AppPrefs.safTreeUri(ctx)
        if (treeString != null) {
            val tree = Uri.parse(treeString)
            if (StorageAccess.hasPersistedTree(ctx, tree)) {
                val saf = SafTree(ctx, tree)
                for (doc in saf.walkFiles(maxDepth = MAX_DEPTH, skipDirs = AppPaths.scanSkipNames)) {
                    if (!DiscFormat.isCandidateFileName(doc.name)) continue
                    scanned++
                    val header = saf.openByteSource(doc)?.use { DiscHeaderParser.parse(it, doc.name) }
                        ?: DiscInfo.error(DiscFormat.fromFileName(doc.name), "Dokumen SAF tidak bisa dibuka")
                    games.add(
                        validate(doc.uri.toString(), doc.name, doc.size, ScannedGame.Source.SAF_FOLDER, header, backendReady),
                    )
                }
            } else {
                notes.add("Izin folder SAF sudah tidak berlaku; pilih ulang folder.")
            }
        }

        // Same file reachable through both routes -> keep the direct path.
        val unique = games.distinctBy { "${it.fileName}|${it.sizeBytes}|${it.header.gameId}" }
        val sorted = unique.sortedWith(
            compareBy<ScannedGame> { if (it.compatibility.canLaunch()) 0 else 1 }.thenBy { it.fileName.lowercase() },
        )
        AppLog.i("Pindai selesai: $scanned file kandidat, ${sorted.count { it.compatibility.canLaunch() }} siap dijalankan")
        ScanResult(sorted, notes, scanned)
    }

    private fun walk(dir: File, depth: Int): List<File> {
        val out = ArrayList<File>()
        val children = dir.listFiles() ?: return out
        for (c in children) {
            if (c.isDirectory) {
                if (depth < MAX_DEPTH && c.name !in AppPaths.scanSkipNames) out.addAll(walk(c, depth + 1))
            } else if (DiscFormat.isCandidateFileName(c.name)) {
                out.add(c)
            }
        }
        return out
    }

    /** Asks Dolphin's DiscIO (GameFile.parse) to open the file; this is the authoritative check. */
    private fun validate(
        location: String,
        name: String,
        size: Long,
        source: ScannedGame.Source,
        header: DiscInfo,
        backendReady: Boolean,
    ): ScannedGame {
        var ok: Boolean? = null
        var title: String? = null
        var id: String? = null
        var format: String? = null
        if (backendReady && header.isValid()) {
            try {
                val gf = GameFile.parse(location)
                if (gf != null) {
                    ok = true
                    title = gf.getTitle()
                    id = gf.getGameId()
                    format = gf.getFileFormatName()
                } else {
                    ok = false
                }
            } catch (t: Throwable) {
                AppLog.w("GameFile.parse gagal untuk $name", t)
                ok = false
            }
        }
        val compat = Compatibility.evaluate(header, ok, id)
        return ScannedGame(location, name, size, source, header, ok, title, id, format, compat)
    }
}
