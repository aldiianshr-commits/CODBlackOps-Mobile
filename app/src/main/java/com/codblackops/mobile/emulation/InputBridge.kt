package com.codblackops.mobile.emulation

import android.os.SystemClock
import android.view.Choreographer
import com.codblackops.mobile.core.input.ActionStateMachine
import com.codblackops.mobile.core.input.AimProcessor
import com.codblackops.mobile.core.input.ControlScheme
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GameAction
import com.codblackops.mobile.core.input.GyroProcessor
import com.codblackops.mobile.core.input.PointerState
import com.codblackops.mobile.core.input.StickMath
import com.codblackops.mobile.core.input.WiiControl
import com.codblackops.mobile.diag.AppLog
import org.dolphinemu.dolphinemu.features.input.model.InputOverrider
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Single owner of emulated Wii Remote 1 while a game runs. Touch HUD, gyro and gamepad all feed
 * this bridge; once per display frame it computes stick/pointer values and writes them, together
 * with button changes from [ActionStateMachine], to Dolphin's InputOverrider.
 *
 * All methods must be called on the main thread.
 */
class InputBridge(
    val session: GameSession,
    private val launcherActions: (GameAction) -> Unit,
) : Choreographer.FrameCallback {

    enum class Source { TOUCH, GAMEPAD }

    companion object {
        const val WIIMOTE_INDEX = 0
        const val GAMEPAD_KEY_SOURCE = 1
        const val GAMEPAD_AXIS_SOURCE = 2
        const val TOUCH_SOURCE_BASE = 1000

        /** Pointer speed (units/s) at full gamepad/look-stick deflection in Wii Remote mode. */
        const val POINTER_STICK_SPEED = 1.6f
        private const val SPRINT_RELEASE_MS = 450L
    }

    val scheme: ControlScheme = session.scheme
    val settings: ControlSettings = session.controls

    private val machine = ActionStateMachine { control, value ->
        InputOverrider.setControlState(WIIMOTE_INDEX, control, value)
    }

    val pointer = PointerState()
    private val aim = AimProcessor().apply { configure(settings) }

    private val move = HashMap<Source, FloatArray>()
    private val lookStick = HashMap<Source, FloatArray>()
    private var swipeDx = 0f
    private var swipeDy = 0f
    private var gyroYaw = 0f
    private var gyroPitch = 0f

    var gyroEnabled: Boolean = settings.gyroActive()
        private set
    var layoutSet: ControlSettings.LayoutSet = settings.layoutSet
        private set

    private var lastFrameNanos = 0L
    private var running = false
    private var stillSince = 0L
    private val lastSent = HashMap<Int, Double>()

    /** Called after any visible state change (latched buttons, pointer, layout). */
    var onStateChanged: (() -> Unit)? = null

    init {
        machine.setLauncherActionListener { a -> handleLauncherAction(a) }
        register()
    }

    /** (Re)registers the override hook on Wii Remote 1 and writes a neutral state. */
    fun register() {
        InputOverrider.registerWii(WIIMOTE_INDEX)
        lastSent.clear()
        machine.configure(scheme, session.bindings, settings, now())
        writeAxes(force = true)
        AppLog.i("InputOverrider terdaftar untuk Wii Remote 1 (${scheme.label})")
    }

    fun dispose() {
        stop()
        machine.releaseAll(now())
        move.clear()
        lookStick.clear()
        writeAxes(force = true)
        InputOverrider.unregisterWii(WIIMOTE_INDEX)
    }

    private fun now() = SystemClock.uptimeMillis()

    // ------------------------------------------------------------------ inputs
    fun press(action: GameAction, sourceId: Int, down: Boolean) {
        machine.press(action, sourceId, down, now())
        onStateChanged?.invoke()
    }

    fun releaseSource(sourceId: Int) {
        machine.releaseSource(sourceId, now())
    }

    fun isLatched(action: GameAction) = machine.isLatched(action)

    fun isPressed(action: GameAction) = machine.isPressed(action)

    fun setMove(source: Source, x: Float, y: Float) {
        move[source] = floatArrayOf(x, y)
    }

    fun setLookStick(source: Source, x: Float, y: Float) {
        lookStick[source] = floatArrayOf(x, y)
    }

    /** Swipe-to-look movement in dp (screen axes: x right, y down). */
    fun addSwipe(dxDp: Float, dyDp: Float) {
        if (scheme.aimsWithPointer()) {
            val dy = if (settings.invertY) -dyDp else dyDp
            pointer.addRelative(dxDp, dy, settings.pointerSens)
            onStateChanged?.invoke()
        } else {
            swipeDx += dxDp
            swipeDy += dyDp
        }
    }

    fun setPointerAbsolute(x: Float, y: Float, width: Float, height: Float) {
        pointer.setAbsolute(x, y, width, height)
        onStateChanged?.invoke()
    }

    /** Finger left the aiming area. */
    fun endAim() {
        if (scheme.aimsWithPointer() && settings.pointerRecenterOnRelease &&
            settings.pointerMode == ControlSettings.PointerMode.RELATIVE
        ) {
            pointer.recenter()
            onStateChanged?.invoke()
        }
    }

    fun onGyro(yawRate: Float, pitchRate: Float) {
        if (!gyroEnabled) {
            gyroYaw = 0f
            gyroPitch = 0f
            return
        }
        gyroYaw = yawRate
        gyroPitch = pitchRate
    }

    fun releaseAll() {
        machine.releaseAll(now())
        move.clear()
        lookStick.clear()
        swipeDx = 0f
        swipeDy = 0f
        gyroYaw = 0f
        gyroPitch = 0f
        aim.reset()
        writeAxes(force = true)
        onStateChanged?.invoke()
    }

    // ------------------------------------------------------------------ launcher actions
    private fun handleLauncherAction(a: GameAction) {
        when (a) {
            GameAction.GYRO_TOGGLE -> {
                gyroEnabled = !gyroEnabled
                if (!gyroEnabled) onGyro(0f, 0f)
            }
            GameAction.LAYOUT_SWITCH -> {
                layoutSet = if (layoutSet == ControlSettings.LayoutSet.CAMPAIGN) {
                    ControlSettings.LayoutSet.MULTIPLAYER
                } else {
                    ControlSettings.LayoutSet.CAMPAIGN
                }
            }
            GameAction.RECENTER_AIM -> {
                pointer.recenter()
                aim.reset()
            }
            else -> Unit
        }
        launcherActions(a)
        onStateChanged?.invoke()
    }

    // ------------------------------------------------------------------ frame loop
    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val dt = if (lastFrameNanos == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.1f)
        lastFrameNanos = frameTimeNanos
        val t = now()
        machine.tick(t)
        autoReleaseSprint(t)
        aim.update(swipeDx, swipeDy, dt)
        swipeDx = 0f
        swipeDy = 0f
        if (scheme.aimsWithPointer()) {
            val ls = strongest(lookStick)
            if (ls[0] != 0f || ls[1] != 0f) {
                pointer.addVelocity(ls[0] * POINTER_STICK_SPEED * settings.pointerSens,
                    ls[1] * POINTER_STICK_SPEED * settings.pointerSens, dt)
                onStateChanged?.invoke()
            }
            if (gyroYaw != 0f || gyroPitch != 0f) {
                pointer.addVelocity(gyroYaw * GyroProcessor.POINTER_PER_RAD, gyroPitch * GyroProcessor.POINTER_PER_RAD, dt)
                onStateChanged?.invoke()
            }
        }
        writeAxes(force = false)
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun autoReleaseSprint(t: Long) {
        if (!machine.isLatched(GameAction.SPRINT)) {
            stillSince = 0L
            return
        }
        val m = strongest(move)
        if (hypot(m[0], m[1]) < 0.2f) {
            if (stillSince == 0L) stillSince = t
            if (t - stillSince > SPRINT_RELEASE_MS) {
                machine.unlatch(GameAction.SPRINT, t)
                stillSince = 0L
                onStateChanged?.invoke()
            }
        } else {
            stillSince = 0L
        }
    }

    private fun strongest(map: Map<Source, FloatArray>): FloatArray {
        var best = floatArrayOf(0f, 0f)
        var bestMag = 0f
        for (v in map.values) {
            val m = hypot(v[0], v[1])
            if (m > bestMag) {
                best = v
                bestMag = m
            }
        }
        return best
    }

    private fun writeAxes(force: Boolean) {
        val mv = strongest(move)
        writeStick(scheme.moveStickX, scheme.moveStickY, mv[0], mv[1], force)
        if (scheme.aimsWithPointer()) {
            send(WiiControl.WIIMOTE_IR_X, pointer.x().toDouble(), force)
            send(WiiControl.WIIMOTE_IR_Y, pointer.y().toDouble(), force)
        } else {
            val ls = strongest(lookStick)
            var lx = aim.x() + ls[0] + gyroYaw * GyroProcessor.STICK_PER_RAD_S
            var ly = aim.y() + ls[1] + gyroPitch * GyroProcessor.STICK_PER_RAD_S
            val c = StickMath.clampUnit(lx, ly)
            lx = c[0]
            ly = c[1]
            writeStick(WiiControl.CLASSIC_RIGHT_STICK_X, WiiControl.CLASSIC_RIGHT_STICK_Y, lx, ly, force)
        }
    }

    private fun writeStick(xControl: Int, yControl: Int, x: Float, y: Float, force: Boolean) {
        var sx = x
        var sy = y
        val r = hypot(sx, sy)
        if (r > 0f) {
            val gate = try {
                InputOverrider.getGateRadiusAtAngle(WIIMOTE_INDEX, xControl, StickMath.gateAngle(sx, sy)).toFloat()
            } catch (_: Throwable) {
                1f
            }
            if (gate > 0f && r > gate) {
                sx = sx / r * gate
                sy = sy / r * gate
            }
        }
        send(xControl, sx.toDouble(), force)
        send(yControl, sy.toDouble(), force)
    }

    private fun send(control: Int, value: Double, force: Boolean) {
        val last = lastSent[control]
        if (!force && last != null && abs(last - value) < 1e-4) return
        lastSent[control] = value
        InputOverrider.setControlState(WIIMOTE_INDEX, control, value)
    }
}
