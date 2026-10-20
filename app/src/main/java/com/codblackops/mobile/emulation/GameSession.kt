package com.codblackops.mobile.emulation

import android.content.Context
import com.codblackops.mobile.core.config.GraphicsConfig
import com.codblackops.mobile.core.config.PerformanceConfig
import com.codblackops.mobile.core.input.Bindings
import com.codblackops.mobile.core.input.ControlScheme
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GamepadMapping
import com.codblackops.mobile.prefs.AppPrefs

/** Everything the in-game layer needs, frozen at launch time. */
data class GameSession(
    val gameId: String,
    val scheme: ControlScheme,
    val controls: ControlSettings,
    val bindings: Bindings,
    val gamepad: GamepadMapping,
    val graphics: GraphicsConfig,
    val performance: PerformanceConfig,
) {
    companion object {
        fun fromPrefs(ctx: Context, gameId: String): GameSession {
            val controls = AppPrefs.controls(ctx)
            val perf = AppPrefs.performance(ctx, gameId)
            return GameSession(
                gameId = gameId,
                scheme = controls.activeScheme(),
                controls = controls,
                bindings = AppPrefs.bindings(ctx),
                gamepad = AppPrefs.gamepad(ctx),
                graphics = perf.capGraphics(AppPrefs.graphics(ctx, gameId)),
                performance = perf,
            )
        }
    }
}
