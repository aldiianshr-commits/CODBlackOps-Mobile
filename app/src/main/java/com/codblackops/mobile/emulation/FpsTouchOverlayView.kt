package com.codblackops.mobile.emulation

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.codblackops.mobile.core.input.ControlLayout
import com.codblackops.mobile.core.input.ControlScheme
import com.codblackops.mobile.core.input.ControlSettings
import com.codblackops.mobile.core.input.GameAction
import com.codblackops.mobile.core.input.HudElementId
import com.codblackops.mobile.core.input.StickMath
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Touch HUD for Call of Duty: Black Ops. In play mode every touch is routed to [InputBridge];
 * in edit mode elements can be dragged, pinch-resized and selected for opacity/visibility edits.
 */
@SuppressLint("ViewConstructor")
class FpsTouchOverlayView(
    context: Context,
    private val bridge: InputBridge?,
    private var settings: ControlSettings,
    layout: ControlLayout,
    val editMode: Boolean,
    private val scheme: ControlScheme = settings.activeScheme(),
) : View(context) {

    var layout: ControlLayout = layout
        set(value) {
            field = value
            invalidate()
        }

    /** Edit mode callbacks. */
    var onSelectionChanged: ((HudElementId?) -> Unit)? = null
    var onLayoutEdited: (() -> Unit)? = null

    var selected: HudElementId? = null
        private set

    /** Hidden while a gamepad is in use (auto-hide option). */
    var hudHidden = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density

    private enum class Role { BUTTON, MOVE, LOOK_STICK, SWIPE, POINTER_ABS, EDIT_DRAG }

    private class Track(
        val role: Role,
        val element: HudElementId?,
        var baseX: Float,
        var baseY: Float,
        var lastX: Float,
        var lastY: Float,
        var dragAim: Boolean = false,
    )

    private val tracks = HashMap<Int, Track>()
    private var moveKnob = floatArrayOf(0f, 0f)
    private var moveBase: FloatArray? = null
    private var lookKnob = floatArrayOf(0f, 0f)

    // pinch (edit mode)
    private var pinchStartDist = 0f
    private var pinchStartSize = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        color = Color.argb(200, 255, 255, 255)
    }

    private val accent = Color.rgb(255, 122, 26) // Black Ops orange

    init {
        isFocusable = false
        isHapticFeedbackEnabled = true
    }

    fun updateSettings(s: ControlSettings) {
        settings = s
        invalidate()
    }

    // ------------------------------------------------------------------ geometry helpers
    private fun sizePx(e: ControlLayout.Element) = e.sizeDp * density * settings.hudScale

    private fun cx(e: ControlLayout.Element) = e.cx * width

    private fun cy(e: ControlLayout.Element) = e.cy * height

    /** Whether an element makes sense for the current control mode. */
    fun isRelevant(id: HudElementId): Boolean = when (id) {
        HudElementId.LOOK_STICK -> settings.lookStyle == ControlSettings.LookStyle.STICK
        HudElementId.RECENTER -> scheme.aimsWithPointer() || settings.gyroActive()
        HudElementId.GYRO_TOGGLE -> settings.gyroActive()
        else -> true
    }

    private fun shown(id: HudElementId): Boolean {
        val e = layout.get(id)
        return if (editMode) isRelevant(id) else e.visible && isRelevant(id) && !hudHidden
    }

    private fun hit(x: Float, y: Float, buttonsOnly: Boolean): HudElementId? {
        val ids = HudElementId.values()
        for (i in ids.indices.reversed()) {
            val id = ids[i]
            if (!shown(id)) continue
            if (buttonsOnly && id.isStick) continue
            if (!editMode && id == HudElementId.MOVE_STICK) continue // handled by zone logic
            val e = layout.get(id)
            val r = sizePx(e) / 2f * if (editMode) 1.0f else 1.15f
            if (hypot(x - cx(e), y - cy(e)) <= r) return id
        }
        return null
    }

    // ------------------------------------------------------------------ touch
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return onEditTouch(event)
        val b = bridge ?: return false
        if (hudHidden) {
            // Touch brings the HUD back after gamepad use.
            if (event.actionMasked == MotionEvent.ACTION_DOWN) hudHidden = false
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                onDown(b, event.getPointerId(idx), event.getX(idx), event.getY(idx))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) onMove(b, event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onUp(b, event.getPointerId(event.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (id in tracks.keys.toList()) onUp(b, id)
                b.releaseAll()
            }
        }
        invalidate()
        return true
    }

    private fun onDown(b: InputBridge, pid: Int, x: Float, y: Float) {
        val button = hit(x, y, buttonsOnly = true)
        if (button != null) {
            val action = button.action ?: return
            val dragAim = settings.fireDragAim && action == GameAction.FIRE
            tracks[pid] = Track(Role.BUTTON, button, x, y, x, y, dragAim)
            b.press(action, InputBridge.TOUCH_SOURCE_BASE + pid, true)
            if (settings.haptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            return
        }
        if (shown(HudElementId.LOOK_STICK)) {
            val e = layout.get(HudElementId.LOOK_STICK)
            if (hypot(x - cx(e), y - cy(e)) <= sizePx(e) / 2f * 1.2f) {
                tracks[pid] = Track(Role.LOOK_STICK, HudElementId.LOOK_STICK, cx(e), cy(e), x, y)
                updateStick(b, tracks[pid]!!, x, y)
                return
            }
        }
        val moveTaken = tracks.values.any { it.role == Role.MOVE }
        if (!moveTaken && x < width * 0.45f) {
            val e = layout.get(HudElementId.MOVE_STICK)
            val inside = hypot(x - cx(e), y - cy(e)) <= sizePx(e) / 2f * 1.6f
            if (settings.floatingMoveStick || inside) {
                val bx = if (settings.floatingMoveStick && !inside) x else cx(e)
                val by = if (settings.floatingMoveStick && !inside) y else cy(e)
                tracks[pid] = Track(Role.MOVE, HudElementId.MOVE_STICK, bx, by, x, y)
                moveBase = floatArrayOf(bx, by)
                updateStick(b, tracks[pid]!!, x, y)
                return
            }
        }
        if (x >= width * 0.3f) {
            if (scheme.aimsWithPointer() && settings.pointerMode == ControlSettings.PointerMode.ABSOLUTE) {
                tracks[pid] = Track(Role.POINTER_ABS, null, x, y, x, y)
                b.setPointerAbsolute(x, y, width.toFloat(), height.toFloat())
            } else {
                tracks[pid] = Track(Role.SWIPE, null, x, y, x, y)
            }
        }
    }

    private fun onMove(b: InputBridge, pid: Int, x: Float, y: Float) {
        val t = tracks[pid] ?: return
        when (t.role) {
            Role.MOVE, Role.LOOK_STICK -> updateStick(b, t, x, y)
            Role.SWIPE -> {
                b.addSwipe((x - t.lastX) / density, (y - t.lastY) / density)
            }
            Role.BUTTON -> if (t.dragAim) b.addSwipe((x - t.lastX) / density, (y - t.lastY) / density)
            Role.POINTER_ABS -> b.setPointerAbsolute(x, y, width.toFloat(), height.toFloat())
            Role.EDIT_DRAG -> Unit
        }
        t.lastX = x
        t.lastY = y
    }

    private fun onUp(b: InputBridge, pid: Int) {
        val t = tracks.remove(pid) ?: return
        when (t.role) {
            Role.BUTTON -> t.element?.action?.let { b.press(it, InputBridge.TOUCH_SOURCE_BASE + pid, false) }
            Role.MOVE -> {
                b.setMove(InputBridge.Source.TOUCH, 0f, 0f)
                moveKnob = floatArrayOf(0f, 0f)
                moveBase = null
            }
            Role.LOOK_STICK -> {
                b.setLookStick(InputBridge.Source.TOUCH, 0f, 0f)
                lookKnob = floatArrayOf(0f, 0f)
            }
            Role.SWIPE, Role.POINTER_ABS -> b.endAim()
            Role.EDIT_DRAG -> Unit
        }
        if (t.role == Role.BUTTON && t.dragAim) b.endAim()
    }

    private fun updateStick(b: InputBridge, t: Track, x: Float, y: Float) {
        val e = layout.get(t.element ?: return)
        val radius = sizePx(e) / 2f
        val v = StickMath.touchToStick(x - t.baseX, y - t.baseY, radius)
        if (t.role == Role.MOVE) {
            val d = StickMath.radialDeadzone(v[0], v[1], settings.moveDeadzone)
            moveKnob = v
            b.setMove(InputBridge.Source.TOUCH, d[0], d[1])
        } else {
            val d = StickMath.radialDeadzone(v[0], v[1], settings.lookDeadzone)
            val sx = d[0] * settings.lookSensX
            var sy = d[1] * settings.lookSensY
            if (settings.invertY) sy = -sy
            val c = StickMath.clampUnit(sx, sy)
            lookKnob = v
            b.setLookStick(InputBridge.Source.TOUCH, c[0], c[1])
        }
    }

    // ------------------------------------------------------------------ edit mode
    private fun onEditTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val id = hit(event.x, event.y, buttonsOnly = false)
                selected = id
                onSelectionChanged?.invoke(id)
                if (id != null) tracks[event.getPointerId(0)] = Track(Role.EDIT_DRAG, id, event.x, event.y, event.x, event.y)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val sel = selected
                if (sel != null && event.pointerCount >= 2) {
                    pinchStartDist = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                    pinchStartSize = layout.get(sel).sizeDp
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val sel = selected
                if (sel != null) {
                    val e = layout.get(sel)
                    if (event.pointerCount >= 2 && pinchStartDist > 0f) {
                        val d = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
                        val size = pinchStartSize * d / pinchStartDist
                        layout.set(sel, e.cx, e.cy, size, e.opacity, e.visible)
                    } else {
                        val t = tracks[event.getPointerId(0)]
                        if (t != null && t.role == Role.EDIT_DRAG && width > 0 && height > 0) {
                            val nx = e.cx + (event.x - t.lastX) / width
                            val ny = e.cy + (event.y - t.lastY) / height
                            layout.set(sel, nx, ny, e.sizeDp, e.opacity, e.visible)
                            t.lastX = event.x
                            t.lastY = event.y
                        }
                    }
                    onLayoutEdited?.invoke()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                pinchStartDist = 0f
                // keep dragging with the remaining finger without a jump
                val remaining = if (event.actionIndex == 0) 1 else 0
                tracks.clear()
                val sel = selected
                if (sel != null) {
                    val x = event.getX(remaining)
                    val y = event.getY(remaining)
                    tracks[event.getPointerId(remaining)] = Track(Role.EDIT_DRAG, sel, x, y, x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracks.clear()
                pinchStartDist = 0f
            }
        }
        invalidate()
        return true
    }

    fun select(id: HudElementId?) {
        selected = id
        invalidate()
    }

    // ------------------------------------------------------------------ drawing
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        if (!editMode && hudHidden) {
            drawStatus(canvas, "HUD disembunyikan (gamepad) – sentuh layar untuk menampilkan")
            return
        }
        if (editMode) canvas.drawColor(Color.argb(140, 10, 12, 14))
        for (id in HudElementId.values()) {
            if (!shown(id)) continue
            val e = layout.get(id)
            if (id.isStick) drawStick(canvas, id, e) else drawButton(canvas, id, e)
        }
        if (!editMode) {
            val b = bridge
            if (b != null && scheme.aimsWithPointer()) drawPointer(canvas, b)
            if (settings.showStatusChip && b != null) drawStatus(canvas, statusText(b))
        }
    }

    private fun alphaFor(e: ControlLayout.Element): Int {
        val o = if (e.opacity < 0f) settings.hudOpacity else e.opacity
        val base = (o * 255).toInt().coerceIn(20, 255)
        return if (editMode && !e.visible) base / 3 else base
    }

    private fun drawButton(canvas: Canvas, id: HudElementId, e: ControlLayout.Element) {
        val a = alphaFor(e)
        val r = sizePx(e) / 2f
        val x = cx(e)
        val y = cy(e)
        val action = id.action
        val b = bridge
        val active = !editMode && b != null && action != null && (b.isPressed(action) || b.isLatched(action) ||
            (action == GameAction.GYRO_TOGGLE && b.gyroEnabled))
        fill.color = if (active) Color.argb(min(255, a + 40), 255, 122, 26) else Color.argb(a, 18, 20, 22)
        canvas.drawCircle(x, y, r, fill)
        stroke.color = if (active) Color.argb(255, 255, 200, 150) else Color.argb(a, 230, 230, 230)
        canvas.drawCircle(x, y, r - stroke.strokeWidth / 2f, stroke)
        if (editMode && id == selected) {
            stroke.color = accent
            canvas.drawCircle(x, y, r + 6 * density, stroke)
        }
        if (editMode && !e.visible) canvas.drawCircle(x, y, r + 3 * density, dashed)
        val label = id.hudLabel()
        text.textSize = max(9f * density, min(r * 0.42f, 15f * density))
        text.alpha = min(255, a + 60)
        val tw = text.measureText(label)
        if (tw > r * 1.8f) text.textSize = text.textSize * (r * 1.8f / tw)
        canvas.drawText(label, x, y - (text.descent() + text.ascent()) / 2f, text)
        text.alpha = 255
    }

    private fun drawStick(canvas: Canvas, id: HudElementId, e: ControlLayout.Element) {
        val a = alphaFor(e)
        val r = sizePx(e) / 2f
        var x = cx(e)
        var y = cy(e)
        val knob = if (id == HudElementId.MOVE_STICK) moveKnob else lookKnob
        if (!editMode && id == HudElementId.MOVE_STICK) moveBase?.let { x = it[0]; y = it[1] }
        fill.color = Color.argb(a / 2, 18, 20, 22)
        canvas.drawCircle(x, y, r, fill)
        stroke.color = Color.argb(a, 230, 230, 230)
        canvas.drawCircle(x, y, r - stroke.strokeWidth / 2f, stroke)
        if (editMode && id == selected) {
            stroke.color = accent
            canvas.drawCircle(x, y, r + 6 * density, stroke)
        }
        if (editMode && !e.visible) canvas.drawCircle(x, y, r + 3 * density, dashed)
        val kx = x + knob[0] * r * 0.6f
        val ky = y - knob[1] * r * 0.6f
        fill.color = Color.argb(min(255, a + 30), 255, 122, 26)
        canvas.drawCircle(kx, ky, r * 0.38f, fill)
        text.textSize = max(9f * density, r * 0.2f)
        text.alpha = a
        canvas.drawText(id.hudLabel(), x, y + r + text.textSize * 1.1f, text)
        text.alpha = 255
    }

    private fun drawPointer(canvas: Canvas, b: InputBridge) {
        val px = (b.pointer.x() + 1f) / 2f * width
        val py = (1f - b.pointer.y()) / 2f * height
        stroke.color = Color.argb(200, 255, 122, 26)
        val r = 10f * density
        canvas.drawCircle(px, py, r, stroke)
        canvas.drawLine(px - r * 1.6f, py, px - r * 0.6f, py, stroke)
        canvas.drawLine(px + r * 0.6f, py, px + r * 1.6f, py, stroke)
        canvas.drawLine(px, py - r * 1.6f, px, py - r * 0.6f, stroke)
        canvas.drawLine(px, py + r * 0.6f, px, py + r * 1.6f, stroke)
    }

    private fun statusText(b: InputBridge): String {
        val parts = ArrayList<String>()
        parts.add(settings.mode.label)
        parts.add(if (b.layoutSet == ControlSettings.LayoutSet.CAMPAIGN) "Campaign" else "Multiplayer")
        if (settings.gyroActive()) parts.add(if (b.gyroEnabled) "Gyro ON" else "Gyro OFF")
        if (b.isLatched(GameAction.SPRINT)) parts.add("LARI")
        if (b.isLatched(GameAction.ADS)) parts.add("ADS")
        return parts.joinToString("  •  ")
    }

    private fun drawStatus(canvas: Canvas, s: String) {
        text.textSize = 11f * density
        val w = text.measureText(s) + 20f * density
        val h = 20f * density
        val x = width / 2f
        val y = height - h - 4f * density
        fill.color = Color.argb(120, 0, 0, 0)
        canvas.drawRoundRect(x - w / 2, y, x + w / 2, y + h, h / 2, h / 2, fill)
        text.alpha = 220
        canvas.drawText(s, x, y + h / 2 - (text.descent() + text.ascent()) / 2f, text)
        text.alpha = 255
    }
}
