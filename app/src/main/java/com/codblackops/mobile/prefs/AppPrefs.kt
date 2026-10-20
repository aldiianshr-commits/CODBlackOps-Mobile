package com.codblackops.mobile.prefs

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import com.codblackops.mobile.core.config.DiagnosticsConfig
import com.codblackops.mobile.core.config.GraphicsConfig
import com.codblackops.mobile.core.config.PerformanceConfig
import com.codblackops.mobile.core.input.Bindings
import com.codblackops.mobile.core.input.ControlLayout
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GamepadMapping

/**
 * Launcher settings storage. Graphics and performance profiles can be stored per game ID
 * ("konfigurasi per game"); when a game has no own profile the global one is used.
 */
object AppPrefs {
    private const val NAME = "codblackops_launcher"
    const val GLOBAL = "global"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    private fun profileKey(kind: String, gameId: String?) = "$kind.${gameId ?: GLOBAL}"

    // ---------------------------------------------------------------- graphics / performance
    fun hasGameProfile(ctx: Context, gameId: String): Boolean =
        prefs(ctx).contains(profileKey("graphics", gameId)) || prefs(ctx).contains(profileKey("performance", gameId))

    fun graphics(ctx: Context, gameId: String?): GraphicsConfig {
        val p = prefs(ctx)
        val own = gameId?.let { p.getString(profileKey("graphics", it), null) }
        val text = own ?: p.getString(profileKey("graphics", null), null)
        return if (text == null) GraphicsConfig.defaults() else GraphicsConfig.decode(text)
    }

    fun setGraphics(ctx: Context, gameId: String?, cfg: GraphicsConfig) {
        prefs(ctx).edit().putString(profileKey("graphics", gameId), cfg.encode()).apply()
    }

    fun performance(ctx: Context, gameId: String?): PerformanceConfig {
        val p = prefs(ctx)
        val own = gameId?.let { p.getString(profileKey("performance", it), null) }
        val text = own ?: p.getString(profileKey("performance", null), null)
        return if (text == null) PerformanceConfig.defaults() else PerformanceConfig.decode(text)
    }

    fun setPerformance(ctx: Context, gameId: String?, cfg: PerformanceConfig) {
        prefs(ctx).edit().putString(profileKey("performance", gameId), cfg.encode()).apply()
    }

    fun clearGameProfile(ctx: Context, gameId: String) {
        prefs(ctx).edit()
            .remove(profileKey("graphics", gameId))
            .remove(profileKey("performance", gameId))
            .apply()
    }

    // ---------------------------------------------------------------- diagnostics
    fun diagnostics(ctx: Context): DiagnosticsConfig =
        DiagnosticsConfig.decode(prefs(ctx).getString("diagnostics", null))

    fun setDiagnostics(ctx: Context, cfg: DiagnosticsConfig) {
        prefs(ctx).edit().putString("diagnostics", cfg.encode()).apply()
    }

    // ---------------------------------------------------------------- controls
    fun controls(ctx: Context): ControlSettings = ControlSettings.decode(prefs(ctx).getString("controls", null))

    fun setControls(ctx: Context, s: ControlSettings) {
        prefs(ctx).edit().putString("controls", s.encode()).apply()
    }

    fun bindings(ctx: Context): Bindings = Bindings.decode(prefs(ctx).getString("bindings", null))

    fun setBindings(ctx: Context, b: Bindings) {
        prefs(ctx).edit().putString("bindings", b.encode()).apply()
    }

    fun gamepad(ctx: Context): GamepadMapping = GamepadMapping.decode(prefs(ctx).getString("gamepad", null))

    fun setGamepad(ctx: Context, g: GamepadMapping) {
        prefs(ctx).edit().putString("gamepad", g.encode()).apply()
    }

    fun layoutPreset(ctx: Context): ControlLayout.Preset {
        val stored = prefs(ctx).getString("layout.preset", null)
        if (stored != null) {
            try {
                return ControlLayout.Preset.valueOf(stored)
            } catch (_: IllegalArgumentException) {
            }
        }
        return detectPreset(ctx)
    }

    fun setLayoutPreset(ctx: Context, preset: ControlLayout.Preset) {
        prefs(ctx).edit().putString("layout.preset", preset.name).apply()
    }

    fun detectPreset(ctx: Context): ControlLayout.Preset {
        val isPad6 = Build.DEVICE.equals("pipa", ignoreCase = true) || Build.MODEL.contains("Pad 6", ignoreCase = true)
        val sw = ctx.resources.configuration.smallestScreenWidthDp
        val large = (ctx.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >=
            Configuration.SCREENLAYOUT_SIZE_LARGE
        return if (isPad6 || sw >= 600 || large) ControlLayout.Preset.XIAOMI_PAD_6 else ControlLayout.Preset.PHONE
    }

    fun layout(ctx: Context, set: ControlSettings.LayoutSet): ControlLayout {
        val fallback = ControlLayout.preset(layoutPreset(ctx), set)
        val text = prefs(ctx).getString("layout.${set.name}", null) ?: return fallback
        return ControlLayout.decode(text, fallback)
    }

    fun setLayout(ctx: Context, set: ControlSettings.LayoutSet, layout: ControlLayout) {
        prefs(ctx).edit().putString("layout.${set.name}", layout.encode()).apply()
    }

    fun resetLayout(ctx: Context, set: ControlSettings.LayoutSet) {
        prefs(ctx).edit().remove("layout.${set.name}").apply()
    }

    // ---------------------------------------------------------------- storage / session
    fun safTreeUri(ctx: Context): String? = prefs(ctx).getString("saf.tree", null)

    fun setSafTreeUri(ctx: Context, uri: String?) {
        prefs(ctx).edit().apply { if (uri == null) remove("saf.tree") else putString("saf.tree", uri) }.apply()
    }

    fun autoBackupOnExit(ctx: Context): Boolean = prefs(ctx).getBoolean("save.autoBackup", true)

    fun setAutoBackupOnExit(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("save.autoBackup", v).apply()
    }

    fun includeSaveStates(ctx: Context): Boolean = prefs(ctx).getBoolean("save.includeStates", true)

    fun setIncludeSaveStates(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("save.includeStates", v).apply()
    }

    /** Game ID whose saves still need to be mirrored after the last emulation session. */
    fun pendingSaveSync(ctx: Context): String? = prefs(ctx).getString("save.pending", null)

    fun setPendingSaveSync(ctx: Context, gameId: String?) {
        prefs(ctx).edit().apply { if (gameId == null) remove("save.pending") else putString("save.pending", gameId) }
            .apply()
    }

    fun lastPlayedGameId(ctx: Context): String? = prefs(ctx).getString("last.gameId", null)

    fun setLastPlayedGameId(ctx: Context, id: String) {
        prefs(ctx).edit().putString("last.gameId", id).apply()
    }
}
