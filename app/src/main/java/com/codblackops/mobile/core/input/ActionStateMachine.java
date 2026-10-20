package com.codblackops.mobile.core.input;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts action presses from any number of sources (touch pointers, gamepad, ...) into the
 * state of emulated Wii buttons, honouring per-action behaviour (hold, tap pulse, toggle,
 * hold-to-interact) and shake gestures. Writes only changed values to the {@link ControlSink}
 * (on device: Dolphin's InputOverrider.setControlState for Wii Remote 1).
 *
 * Not thread-safe: call from the UI thread only (same thread Dolphin's own overlay uses).
 */
public final class ActionStateMachine {
    public interface ControlSink {
        void setControl(int control, double value);
    }

    public interface LauncherActionListener {
        void onLauncherAction(GameAction action);
    }

    /** Minimum time a Wii button stays pressed so the game (polling at 30-60 Hz) can see it. */
    public static final long MIN_PRESS_MS = 60;
    public static final long PULSE_MS = 90;
    public static final long SHAKE_MS = 320;

    private final ControlSink sink;
    private LauncherActionListener launcherListener;

    private ControlScheme scheme = ControlScheme.CLASSIC;
    private Bindings bindings = Bindings.defaults();
    private ControlSettings settings = ControlSettings.defaults();

    private final EnumMap<GameAction, Set<Integer>> pressedBy = new EnumMap<>(GameAction.class);
    private final EnumSet<GameAction> latched = EnumSet.noneOf(GameAction.class);
    private final EnumMap<GameAction, Long> activeUntil = new EnumMap<>(GameAction.class);

    private final double[] lastWritten = new double[WiiControl.CONTROL_COUNT];

    public ActionStateMachine(ControlSink sink) {
        this.sink = sink;
        Arrays.fill(lastWritten, Double.NaN);
    }

    public void setLauncherActionListener(LauncherActionListener l) {
        this.launcherListener = l;
    }

    /** (Re)configures the machine and writes a neutral state for every button of the scheme. */
    public void configure(ControlScheme scheme, Bindings bindings, ControlSettings settings, long nowMs) {
        this.scheme = scheme;
        this.bindings = bindings;
        this.settings = settings;
        pressedBy.clear();
        latched.clear();
        activeUntil.clear();
        Arrays.fill(lastWritten, Double.NaN);
        for (WiiInput in : WiiInput.values()) {
            if (in == WiiInput.NONE || in.scheme != scheme) continue;
            for (int i = 0; i < in.controlCount(); i++) write(in.control(i), 0.0);
        }
        tick(nowMs);
    }

    public ControlScheme scheme() {
        return scheme;
    }

    public void press(GameAction action, int sourceId, boolean down, long nowMs) {
        if (action.isLauncherAction()) {
            if (down && launcherListener != null) launcherListener.onLauncherAction(action);
            return;
        }
        Set<Integer> sources = pressedBy.get(action);
        if (sources == null) {
            sources = new HashSet<>();
            pressedBy.put(action, sources);
        }
        boolean wasPressed = !sources.isEmpty();
        if (down) sources.add(sourceId);
        else sources.remove(sourceId);
        boolean isPressed = !sources.isEmpty();

        if (!wasPressed && isPressed) onPressEdge(action, nowMs);
        tick(nowMs);
    }

    private void onPressEdge(GameAction action, long now) {
        WiiInput input = bindings.get(scheme, action);
        if (input.isShake) {
            activeUntil.put(action, now + SHAKE_MS);
            return;
        }
        switch (action.behavior(settings)) {
            case TOGGLE:
                if (latched.contains(action)) latched.remove(action);
                else latched.add(action);
                break;
            case PULSE:
                activeUntil.put(action, now + PULSE_MS);
                break;
            case LONG_PRESS:
                activeUntil.put(action, now + settings.interactHoldMs);
                break;
            case HOLD:
            default:
                activeUntil.put(action, now + MIN_PRESS_MS);
                break;
        }
    }

    /** Releases a toggled action (e.g. sprint stops when the player stops moving). */
    public void unlatch(GameAction action, long nowMs) {
        if (latched.remove(action)) tick(nowMs);
    }

    public boolean isLatched(GameAction action) {
        return latched.contains(action);
    }

    public boolean isPressed(GameAction action) {
        Set<Integer> s = pressedBy.get(action);
        return s != null && !s.isEmpty();
    }

    /** Releases everything held by one source (finger lifted, gamepad disconnected). */
    public void releaseSource(int sourceId, long nowMs) {
        for (Set<Integer> s : pressedBy.values()) s.remove(sourceId);
        tick(nowMs);
    }

    public void releaseAll(long nowMs) {
        pressedBy.clear();
        latched.clear();
        activeUntil.clear();
        tick(nowMs);
    }

    boolean isActive(GameAction action, long now) {
        WiiInput input = bindings.get(scheme, action);
        Long until = activeUntil.get(action);
        boolean timed = until != null && now < until;
        if (input.isShake) return timed;
        switch (action.behavior(settings)) {
            case TOGGLE:
                return latched.contains(action);
            case PULSE:
                return timed;
            case LONG_PRESS:
            case HOLD:
            default:
                return isPressed(action) || timed;
        }
    }

    /** Recomputes the Wii button state and writes changes. Call every frame. */
    public void tick(long nowMs) {
        double[] desired = new double[WiiControl.CONTROL_COUNT];
        for (GameAction a : GameAction.values()) {
            if (a.isLauncherAction()) continue;
            WiiInput input = bindings.get(scheme, a);
            if (input == WiiInput.NONE || input.scheme != scheme) continue;
            if (!isActive(a, nowMs)) continue;
            for (int i = 0; i < input.controlCount(); i++) desired[input.control(i)] = 1.0;
        }
        for (WiiInput in : WiiInput.values()) {
            if (in == WiiInput.NONE || in.scheme != scheme) continue;
            for (int i = 0; i < in.controlCount(); i++) {
                int c = in.control(i);
                write(c, desired[c]);
            }
        }
        // drop expired timers
        for (Map.Entry<GameAction, Long> e : new EnumMap<>(activeUntil).entrySet()) {
            if (nowMs >= e.getValue()) activeUntil.remove(e.getKey());
        }
    }

    private void write(int control, double value) {
        if (lastWritten[control] == value) return;
        lastWritten[control] = value;
        sink.setControl(control, value);
    }

    /** Current value last sent for a control (NaN if never written). Used by tests and the HUD. */
    public double lastValue(int control) {
        return lastWritten[control];
    }
}
