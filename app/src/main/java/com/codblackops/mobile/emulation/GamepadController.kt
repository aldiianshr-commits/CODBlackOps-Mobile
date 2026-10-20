package com.codblackops.mobile.emulation

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GamepadMapping
import com.codblackops.mobile.core.input.StickMath
import kotlin.math.max

/**
 * Bluetooth/USB gamepads. Buttons go through [GamepadMapping] (user-remappable), left stick moves,
 * right stick looks/aims, analog triggers act as their mapped buttons (R2 fire, L2 ADS by default).
 */
class GamepadController(
    private val bridge: InputBridge,
    private val mapping: GamepadMapping,
    private val settings: ControlSettings,
    private val onActivity: () -> Unit,
) {
    private var r2Down = false
    private var l2Down = false
    private var hatX = 0
    private var hatY = 0

    companion object {
        fun isGamepad(event: InputEvent): Boolean {
            val s = event.source
            return (s and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (s and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (!isGamepad(event)) return false
        val action = mapping.actionFor(event.keyCode) ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return true
        when (event.action) {
            KeyEvent.ACTION_DOWN -> bridge.press(action, InputBridge.GAMEPAD_KEY_SOURCE * 100 + event.keyCode, true)
            KeyEvent.ACTION_UP -> bridge.press(action, InputBridge.GAMEPAD_KEY_SOURCE * 100 + event.keyCode, false)
            else -> return false
        }
        onActivity()
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if ((event.source and InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        val dz = settings.gamepadDeadzone
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val (rxAxis, ryAxis) = if (settings.rightStickAxes == ControlSettings.RightStickAxes.RX_RY) {
            MotionEvent.AXIS_RX to MotionEvent.AXIS_RY
        } else {
            MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ
        }
        val rx = event.getAxisValue(rxAxis)
        val ry = event.getAxisValue(ryAxis)

        val move = StickMath.radialDeadzone(lx, -ly, dz)
        bridge.setMove(InputBridge.Source.GAMEPAD, move[0], move[1])

        val look = StickMath.radialDeadzone(rx, -ry, dz)
        val sens = settings.gamepadLookSens
        val invert = if (settings.gamepadInvertY) -1f else 1f
        val c = StickMath.clampUnit(look[0] * sens, look[1] * sens * invert)
        bridge.setLookStick(InputBridge.Source.GAMEPAD, c[0], c[1])

        val rt = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val lt = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        r2Down = trigger(rt, r2Down, GamepadMapping.KEYCODE_BUTTON_R2)
        l2Down = trigger(lt, l2Down, GamepadMapping.KEYCODE_BUTTON_L2)

        // D-pad reported as a hat on many controllers
        val hx = hatValue(event.getAxisValue(MotionEvent.AXIS_HAT_X))
        val hy = hatValue(event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        if (hx != hatX) {
            hatKey(hatX, GamepadMapping.KEYCODE_DPAD_LEFT, GamepadMapping.KEYCODE_DPAD_RIGHT, false)
            hatKey(hx, GamepadMapping.KEYCODE_DPAD_LEFT, GamepadMapping.KEYCODE_DPAD_RIGHT, true)
            hatX = hx
        }
        if (hy != hatY) {
            hatKey(hatY, GamepadMapping.KEYCODE_DPAD_UP, GamepadMapping.KEYCODE_DPAD_DOWN, false)
            hatKey(hy, GamepadMapping.KEYCODE_DPAD_UP, GamepadMapping.KEYCODE_DPAD_DOWN, true)
            hatY = hy
        }
        onActivity()
        return true
    }

    private fun trigger(value: Float, wasDown: Boolean, keyCode: Int): Boolean {
        val down = if (wasDown) value > 0.35f else value > 0.55f // hysteresis
        if (down != wasDown) {
            mapping.actionFor(keyCode)?.let { bridge.press(it, InputBridge.GAMEPAD_AXIS_SOURCE * 100 + keyCode, down) }
        }
        return down
    }

    private fun hatValue(v: Float): Int = when {
        v < -0.5f -> -1
        v > 0.5f -> 1
        else -> 0
    }

    private fun hatKey(value: Int, negKey: Int, posKey: Int, down: Boolean) {
        val key = when (value) {
            -1 -> negKey
            1 -> posKey
            else -> return
        }
        mapping.actionFor(key)?.let { bridge.press(it, InputBridge.GAMEPAD_AXIS_SOURCE * 100 + key, down) }
    }

    fun releaseAll() {
        bridge.setMove(InputBridge.Source.GAMEPAD, 0f, 0f)
        bridge.setLookStick(InputBridge.Source.GAMEPAD, 0f, 0f)
        r2Down = false
        l2Down = false
        hatX = 0
        hatY = 0
    }
}
