package com.codblackops.mobile.backend

import androidx.fragment.app.FragmentActivity
import com.codblackops.mobile.core.config.GraphicsConfig
import com.codblackops.mobile.core.config.LaunchConfigBuilder
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.dolphin.GpuDrivers
import com.codblackops.mobile.emulation.EmulationHooks
import com.codblackops.mobile.emulation.GameSession
import com.codblackops.mobile.prefs.AppPrefs
import com.codblackops.mobile.storage.SaveManager
import com.codblackops.mobile.storage.ScannedGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.activities.EmulationActivity

/**
 * Runs the game with the Dolphin core that is compiled into this APK (Dolphin 2609a, GPL-2.0+).
 * Dolphin emulates the Wii; its PowerPC CPU emulation uses a JIT that recompiles Broadway code to
 * ARM64 at run time ("rekompilasi dinamis"), which is different from static recompilation.
 */
class DolphinBackend : GameBackend {
    override val id = "dolphin"
    override val displayName = "Dolphin (emulasi Wii terintegrasi)"
    override val technique =
        "Emulasi Wii penuh di dalam APK ini. CPU PowerPC Broadway dijalankan oleh JIT ARM64 Dolphin " +
            "(rekompilasi dinamis saat runtime), GPU lewat Vulkan atau OpenGL ES."

    override fun unavailableReason(): String? = try {
        NativeLibrary.GetVersionString()
        null
    } catch (e: UnsatisfiedLinkError) {
        "Library native Dolphin (libmain.so) tidak dimuat: ${e.message}"
    }

    override suspend fun launch(activity: FragmentActivity, game: ScannedGame) {
        if (!game.compatibility.canLaunch()) throw LaunchException(game.compatibility.message)
        val gameId = game.gameId ?: throw LaunchException("ID game tidak diketahui")
        if (!DolphinBridge.awaitReady(activity)) throw LaunchException("Backend Dolphin gagal diinisialisasi")
        if (!DolphinBridge.isEmulationIdle()) throw LaunchException("Masih ada game yang berjalan")

        val session = GameSession.fromPrefs(activity, gameId)
        val graphics = AppPrefs.graphics(activity, gameId)
        val performance = session.performance
        val diagnostics = AppPrefs.diagnostics(activity)

        var driverLib: String? = null
        if (graphics.gpuDriver == GraphicsConfig.GpuDriver.TURNIP) {
            driverLib = GpuDrivers.installedDriver()?.libraryName
            if (driverLib.isNullOrEmpty()) {
                AppLog.w("Turnip dipilih tetapi belum ada driver kustom terpasang; memakai driver sistem")
                driverLib = null
            }
        }
        val writes = LaunchConfigBuilder.build(graphics, performance, diagnostics, driverLib)

        withContext(Dispatchers.Main) {
            try {
                DolphinBridge.applyConfig(writes)
                DolphinBridge.configureWiimote(session.scheme)
            } catch (t: Throwable) {
                throw LaunchException("Gagal menerapkan konfigurasi ke Dolphin: ${t.message}", t)
            }
        }
        for (w in writes) AppLog.i("  $w")

        withContext(Dispatchers.IO) { SaveManager.prepareForLaunch(activity, gameId) }

        EmulationHooks.prepareSession(session)
        AppPrefs.setLastPlayedGameId(activity, gameId)
        AppLog.i(
            "Meluncurkan $gameId (${game.formatLabel}) dari ${game.location}; profil=${performance.profile}, " +
                "skema=${session.scheme}, backend GPU=${graphics.backend}",
        )
        withContext(Dispatchers.Main) {
            EmulationActivity.launch(activity, game.location, false)
        }
    }
}
