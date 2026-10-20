package com.codblackops.mobile.dolphin

import android.content.Context
import com.codblackops.mobile.core.config.ConfigWrite
import com.codblackops.mobile.core.input.ControlScheme
import com.codblackops.mobile.diag.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.dolphinemu.dolphinemu.NativeLibrary
import org.dolphinemu.dolphinemu.features.input.model.MappingCommon
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.ControlGroup
import org.dolphinemu.dolphinemu.features.input.model.controlleremu.EmulatedController
import org.dolphinemu.dolphinemu.features.settings.model.NativeConfig
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization

/**
 * Thin, typed access to the Dolphin core compiled into this APK (libmain.so + Dolphin's Kotlin
 * layer). Everything here talks to the real backend; nothing is simulated.
 */
object DolphinBridge {

    fun isReady(): Boolean = DirectoryInitialization.areDolphinDirectoriesReady()

    /** Starts Dolphin's user-directory initialisation (no-op if already running/done). Main thread. */
    fun startInit(ctx: Context) {
        DirectoryInitialization.start(ctx.applicationContext)
    }

    /** Waits until Dolphin's directories and core are initialised. */
    suspend fun awaitReady(ctx: Context, timeoutMs: Long = 60_000): Boolean {
        withContext(Dispatchers.Main) { startInit(ctx) }
        val start = System.currentTimeMillis()
        while (!isReady()) {
            if (System.currentTimeMillis() - start > timeoutMs) return false
            delay(100)
        }
        return true
    }

    fun isEmulationIdle(): Boolean = try {
        NativeLibrary.IsUninitialized()
    } catch (_: Throwable) {
        true
    }

    fun version(): String = try {
        NativeLibrary.GetVersionString()
    } catch (t: Throwable) {
        "tidak diketahui"
    }

    fun userDirectory(): String? = if (isReady()) DirectoryInitialization.getUserDirectory() else null

    /**
     * Writes config values to Dolphin's base layer and saves the INI files.
     * Must only be called while no game is running.
     */
    fun applyConfig(writes: List<ConfigWrite>) {
        check(isReady()) { "Dolphin belum siap" }
        check(isEmulationIdle()) { "Tidak bisa mengubah konfigurasi saat game berjalan" }
        val layer = NativeConfig.LAYER_BASE
        for (w in writes) {
            when (w.type) {
                ConfigWrite.Type.INT -> NativeConfig.setInt(layer, w.file, w.section, w.key, w.intValue)
                ConfigWrite.Type.BOOL -> NativeConfig.setBoolean(layer, w.file, w.section, w.key, w.boolValue)
                ConfigWrite.Type.FLOAT -> NativeConfig.setFloat(layer, w.file, w.section, w.key, w.floatValue)
                ConfigWrite.Type.STRING ->
                    NativeConfig.setString(layer, w.file, w.section, w.key, w.stringValue ?: "")
                null -> Unit
            }
        }
        // A live tweak from a previous session (thermal guard) must not override the new value.
        NativeConfig.deleteKey(NativeConfig.LAYER_CURRENT, ConfigWrite.FILE_GFX, "Settings", "InternalResolution")
        NativeConfig.save(NativeConfig.LAYER_BASE)
        AppLog.i("Konfigurasi Dolphin diterapkan (${writes.size} nilai)")
    }

    /** Reads back a value from the active layer (for verification / diagnostics). */
    fun readActive(w: ConfigWrite): String = when (w.type) {
        ConfigWrite.Type.INT -> NativeConfig.getInt(NativeConfig.LAYER_ACTIVE, w.file, w.section, w.key, -9999).toString()
        ConfigWrite.Type.BOOL -> NativeConfig.getBoolean(NativeConfig.LAYER_ACTIVE, w.file, w.section, w.key, false).toString()
        ConfigWrite.Type.FLOAT -> NativeConfig.getFloat(NativeConfig.LAYER_ACTIVE, w.file, w.section, w.key, -1f).toString()
        ConfigWrite.Type.STRING -> NativeConfig.getString(NativeConfig.LAYER_ACTIVE, w.file, w.section, w.key, "")
        null -> ""
    }

    /**
     * Prepares emulated Wii Remote 1 for the launcher: clears every physical-device mapping
     * (the launcher feeds all input through InputOverrider, so Dolphin's own mappings would only
     * add conflicting input such as phone tilt) and attaches the Nunchuk or Classic Controller.
     */
    fun configureWiimote(scheme: ControlScheme) {
        check(isReady()) { "Dolphin belum siap" }
        val wiimote = EmulatedController.getWiimote(0)
        wiimote.clearSettings()
        var attached = false
        for (i in 0 until wiimote.getGroupCount()) {
            val group = wiimote.getGroup(i)
            if (group.getGroupType() == ControlGroup.TYPE_ATTACHMENTS) {
                group.getAttachmentSetting().setIntValue(scheme.dolphinExtension)
                attached = true
            }
        }
        MappingCommon.save()
        AppLog.i("Wii Remote 1 dikonfigurasi: ${scheme.label} (extension=${scheme.dolphinExtension}, ok=$attached)")
        if (!attached) AppLog.w("Grup attachment Wii Remote tidak ditemukan")
    }

    /** Lowers the internal resolution of the running game (thermal guard). */
    fun setLiveInternalResolution(scale: Int) {
        NativeConfig.setInt(NativeConfig.LAYER_CURRENT, ConfigWrite.FILE_GFX, "Settings", "InternalResolution", scale)
    }

    fun clearLiveOverrides() {
        try {
            NativeConfig.deleteKey(NativeConfig.LAYER_CURRENT, ConfigWrite.FILE_GFX, "Settings", "InternalResolution")
        } catch (_: Throwable) {
        }
    }
}
