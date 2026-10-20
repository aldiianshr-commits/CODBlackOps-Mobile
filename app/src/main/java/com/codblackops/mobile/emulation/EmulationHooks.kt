package com.codblackops.mobile.emulation

import android.os.Build
import android.os.PowerManager
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.codblackops.mobile.core.input.GameAction
import com.codblackops.mobile.diag.AppLog
import com.codblackops.mobile.dolphin.DolphinBridge
import com.codblackops.mobile.prefs.AppPrefs
import org.dolphinemu.dolphinemu.activities.EmulationActivity

/**
 * Entry points called from Dolphin's EmulationActivity (see the integration patch). They attach
 * the Black Ops HUD, gyro, gamepad and thermal handling to the running game.
 */
object EmulationHooks {
    @Volatile
    private var externalInputActive = false
    private var session: GameSession? = null

    private var bridge: InputBridge? = null
    private var overlay: FpsTouchOverlayView? = null
    private var gyro: GyroController? = null
    private var gamepad: GamepadController? = null
    private var thermal: ThermalMonitor? = null
    private var thermalReduced = false

    /** Read by the patched Dolphin InputOverlay: true while the launcher owns Wii Remote 1. */
    @JvmStatic
    fun isExternalOverlayActive(): Boolean = externalInputActive

    /** Called by the Dolphin backend right before EmulationActivity is started. */
    fun prepareSession(s: GameSession) {
        session = s
        externalInputActive = true
    }

    fun currentSession(): GameSession? = session

    fun onCreate(activity: EmulationActivity, root: ViewGroup) {
        val s = session ?: run {
            // Process was recreated by Android while the game was in the background.
            val id = AppPrefs.lastPlayedGameId(activity) ?: return
            GameSession.fromPrefs(activity, id).also { session = it }
        }
        externalInputActive = true
        try {
            attach(activity, root, s)
        } catch (t: Throwable) {
            AppLog.e("Gagal memasang HUD Black Ops", t)
            Toast.makeText(activity, "Kontrol sentuh gagal dimuat: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun attach(activity: EmulationActivity, root: ViewGroup, s: GameSession) {
        detach()
        val b = InputBridge(s) { action -> onLauncherAction(activity, action) }
        bridge = b

        val layout = AppPrefs.layout(activity, b.layoutSet)
        val view = FpsTouchOverlayView(activity, b, s.controls, layout, editMode = false, scheme = s.scheme)
        b.onStateChanged = { view.invalidate() }
        // Above the game surface and Dolphin's z-order workaround view, below Dolphin's pause
        // menu container (the last child of activity_emulation.xml).
        val index = minOf(2, root.childCount)
        root.addView(view, index, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay = view
        if (s.controls.mode == com.codblackops.mobile.core.input.ControlSettings.Mode.GAMEPAD &&
            s.controls.autoHideHudOnGamepad
        ) {
            view.hudHidden = true
        }

        gyro = GyroController(activity, b, s.controls).also {
            if (s.controls.gyroActive() && !it.available) {
                Toast.makeText(activity, "Sensor gyroscope tidak tersedia di perangkat ini", Toast.LENGTH_LONG).show()
            }
        }
        gamepad = GamepadController(b, s.gamepad, s.controls) {
            if (s.controls.autoHideHudOnGamepad) overlay?.hudHidden = true
        }

        if (s.performance.useSustainedPerformanceMode() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val pm = activity.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
            if (pm.isSustainedPerformanceModeSupported) {
                activity.window.setSustainedPerformanceMode(true)
                AppLog.i("Sustained performance mode aktif")
            }
        }

        thermalReduced = false
        thermal = ThermalMonitor(activity) { status -> onThermalSevere(activity, status) }.also { it.start() }
        AppLog.i("HUD terpasang: mode=${s.controls.mode} skema=${s.scheme} game=${s.gameId}")
    }

    private fun detach() {
        thermal?.stop()
        thermal = null
        gyro?.stop()
        gyro = null
        gamepad = null
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlay = null
        bridge?.dispose()
        bridge = null
    }

    fun onResume(activity: EmulationActivity) {
        bridge?.start()
        val s = session ?: return
        if (s.controls.gyroActive()) gyro?.start()
    }

    fun onPause(activity: EmulationActivity) {
        gyro?.stop()
        gamepad?.releaseAll()
        bridge?.releaseAll()
        bridge?.stop()
    }

    /** Dolphin reports the game booted (title known). Re-register in case the core reloaded input. */
    fun onBootSucceeded(activity: EmulationActivity) {
        bridge?.register()
        val s = session
        AppLog.i("Game berjalan: ${s?.gameId}")
        Toast.makeText(
            activity,
            "Black Ops berjalan. Tombol MENU = menu emulator, PAUSE = menu game.",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun onDestroy(activity: EmulationActivity, finishing: Boolean) {
        detach()
        if (finishing) {
            val id = session?.gameId
            externalInputActive = false
            session = null
            DolphinBridge.clearLiveOverrides()
            if (id != null) AppPrefs.setPendingSaveSync(activity, id)
            AppLog.i("Sesi emulasi selesai ($id)")
        }
    }

    fun dispatchKeyEvent(activity: EmulationActivity, event: KeyEvent): Boolean =
        gamepad?.onKeyEvent(event) ?: false

    fun dispatchGenericMotionEvent(activity: EmulationActivity, event: MotionEvent): Boolean =
        gamepad?.onMotionEvent(event) ?: false

    fun useImmersiveFullscreen(activity: EmulationActivity): Boolean = session?.graphics?.fullscreen ?: true

    private fun onLauncherAction(activity: EmulationActivity, action: GameAction) {
        when (action) {
            GameAction.EMULATOR_MENU -> {
                bridge?.releaseAll()
                activity.toggleEmulatorMenu()
            }
            GameAction.LAYOUT_SWITCH -> {
                val b = bridge ?: return
                overlay?.layout = AppPrefs.layout(activity, b.layoutSet)
                val s = AppPrefs.controls(activity)
                s.layoutSet = b.layoutSet
                AppPrefs.setControls(activity, s)
                Toast.makeText(activity, "Tata letak: ${b.layoutSet.name.lowercase()}", Toast.LENGTH_SHORT).show()
            }
            GameAction.GYRO_TOGGLE -> {
                val b = bridge ?: return
                if (b.gyroEnabled) gyro?.start() else gyro?.stop()
                Toast.makeText(activity, if (b.gyroEnabled) "Gyro aktif" else "Gyro nonaktif", Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    private fun onThermalSevere(activity: EmulationActivity, status: Int) {
        val s = session ?: return
        if (!s.performance.thermalGuard || thermalReduced) return
        if (s.graphics.resolution.dolphinScale <= 1) return
        thermalReduced = true
        DolphinBridge.setLiveInternalResolution(1)
        AppLog.w("Termal berat (status $status): resolusi internal diturunkan ke 1x untuk sesi ini")
        Toast.makeText(activity, "Perangkat panas: resolusi diturunkan ke 480p sementara", Toast.LENGTH_LONG).show()
    }
}
