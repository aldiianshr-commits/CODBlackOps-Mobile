package com.codblackops.mobile.dolphin

import android.content.Context
import com.codblackops.mobile.diag.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.model.GpuDriverMetadata
import org.dolphinemu.dolphinemu.utils.GpuDriverHelper
import org.dolphinemu.dolphinemu.utils.GpuDriverInstallResult
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * GPU driver selection: the system Adreno driver or a Turnip (Mesa) driver package loaded through
 * Dolphin's libadrenotools integration. Turnip packages are not bundled; the user either picks a
 * downloaded .zip or lets the launcher download one from the AdrenoToolsDrivers releases.
 */
object GpuDrivers {
    const val RELEASES_API = "https://api.github.com/repos/K11MCH1/AdrenoToolsDrivers/releases?per_page=12"

    data class RemoteDriver(val releaseName: String, val assetName: String, val url: String, val sizeBytes: Long)

    fun supportsCustomDrivers(): Boolean = try {
        GpuDriverHelper.supportsCustomDriverLoading()
    } catch (t: Throwable) {
        AppLog.w("supportsCustomDriverLoading gagal", t)
        false
    }

    fun installedDriver(): GpuDriverMetadata? = try {
        if (DolphinBridge.isReady()) GpuDriverHelper.getInstalledDriverMetadata() else null
    } catch (t: Throwable) {
        null
    }

    fun systemDriver(ctx: Context): GpuDriverMetadata? = try {
        GpuDriverHelper.getSystemDriverMetadata(ctx)
    } catch (t: Throwable) {
        null
    }

    fun install(stream: InputStream): GpuDriverInstallResult {
        val r = GpuDriverHelper.installDriver(stream)
        AppLog.i("Instalasi driver GPU: $r")
        return r
    }

    fun uninstall() {
        GpuDriverHelper.uninstallDriver()
        AppLog.i("Driver GPU kustom dihapus, kembali ke driver sistem")
    }

    fun describe(r: GpuDriverInstallResult): String = when (r) {
        GpuDriverInstallResult.Success -> "Driver berhasil dipasang"
        GpuDriverInstallResult.InvalidArchive -> "Arsip driver tidak valid"
        GpuDriverInstallResult.MissingMetadata -> "meta.json tidak ditemukan di arsip (bukan paket adrenotools)"
        GpuDriverInstallResult.InvalidMetadata -> "meta.json tidak valid"
        GpuDriverInstallResult.UnsupportedAndroidVersion -> "Versi Android terlalu lama untuk driver ini"
        GpuDriverInstallResult.AlreadyInstalled -> "Driver sudah terpasang"
        GpuDriverInstallResult.FileNotFound -> "File driver tidak ditemukan"
        else -> r.name
    }

    /** Lists Turnip .zip assets from recent AdrenoToolsDrivers releases (network). */
    suspend fun fetchTurnipDrivers(): List<RemoteDriver> = withContext(Dispatchers.IO) {
        val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "CODBlackOps-Mobile")
        try {
            val code = conn.responseCode
            if (code != 200) throw IllegalStateException("GitHub API HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            val out = ArrayList<RemoteDriver>()
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val relName = rel.optString("name", rel.optString("tag_name"))
                val assets = rel.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    val name = a.optString("name")
                    val isTurnip = name.contains("turnip", ignoreCase = true) || relName.contains("turnip", ignoreCase = true)
                    if (isTurnip && name.endsWith(".zip", ignoreCase = true)) {
                        out.add(RemoteDriver(relName, name, a.optString("browser_download_url"), a.optLong("size")))
                    }
                }
            }
            out
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads and installs a remote driver package. */
    suspend fun downloadAndInstall(ctx: Context, driver: RemoteDriver): GpuDriverInstallResult =
        withContext(Dispatchers.IO) {
            val tmp = File(ctx.cacheDir, "driver-download.zip")
            var url = URL(driver.url)
            var conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "CODBlackOps-Mobile")
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            // GitHub serves assets through a redirect to another host; follow it manually if needed.
            var redirects = 0
            while (conn.responseCode in 300..399 && redirects < 5) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                url = URL(url, loc)
                conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "CODBlackOps-Mobile")
                redirects++
            }
            try {
                if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            AppLog.i("Driver diunduh: ${driver.assetName} (${tmp.length()} byte)")
            val result = tmp.inputStream().use { install(it) }
            tmp.delete()
            result
        }
}
