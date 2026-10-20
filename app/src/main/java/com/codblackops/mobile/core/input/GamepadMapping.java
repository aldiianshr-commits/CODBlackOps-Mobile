package com.codblackops.mobile.core.input;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Android gamepad key code -> game action. Sticks and triggers are handled separately
 * (left stick = move, right stick = camera/aim, R2 = fire, L2 = ADS).
 * Key code values are android.view.KeyEvent constants (kept as ints so this class has no
 * Android dependency and can be unit-tested on the JVM).
 */
public final class GamepadMapping {
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_BUTTON_A = 96;
    public static final int KEYCODE_BUTTON_B = 97;
    public static final int KEYCODE_BUTTON_X = 99;
    public static final int KEYCODE_BUTTON_Y = 100;
    public static final int KEYCODE_BUTTON_L1 = 102;
    public static final int KEYCODE_BUTTON_R1 = 103;
    public static final int KEYCODE_BUTTON_L2 = 104;
    public static final int KEYCODE_BUTTON_R2 = 105;
    public static final int KEYCODE_BUTTON_THUMBL = 106;
    public static final int KEYCODE_BUTTON_THUMBR = 107;
    public static final int KEYCODE_BUTTON_START = 108;
    public static final int KEYCODE_BUTTON_SELECT = 109;
    public static final int KEYCODE_BUTTON_MODE = 110;

    private final Map<Integer, GameAction> map = new LinkedHashMap<>();

    public static GamepadMapping defaults() {
        GamepadMapping g = new GamepadMapping();
        g.map.put(KEYCODE_BUTTON_A, GameAction.JUMP);
        g.map.put(KEYCODE_BUTTON_B, GameAction.CROUCH);
        g.map.put(KEYCODE_BUTTON_X, GameAction.RELOAD);
        g.map.put(KEYCODE_BUTTON_Y, GameAction.SWITCH_WEAPON);
        g.map.put(KEYCODE_BUTTON_L1, GameAction.TACTICAL);
        g.map.put(KEYCODE_BUTTON_R1, GameAction.LETHAL);
        g.map.put(KEYCODE_BUTTON_L2, GameAction.ADS);
        g.map.put(KEYCODE_BUTTON_R2, GameAction.FIRE);
        g.map.put(KEYCODE_BUTTON_THUMBL, GameAction.SPRINT);
        g.map.put(KEYCODE_BUTTON_THUMBR, GameAction.MELEE);
        g.map.put(KEYCODE_BUTTON_START, GameAction.PAUSE);
        g.map.put(KEYCODE_BUTTON_SELECT, GameAction.SCOREBOARD);
        g.map.put(KEYCODE_BUTTON_MODE, GameAction.EMULATOR_MENU);
        g.map.put(KEYCODE_DPAD_UP, GameAction.KILLSTREAK);
        g.map.put(KEYCODE_DPAD_DOWN, GameAction.INTERACT);
        g.map.put(KEYCODE_DPAD_LEFT, GameAction.QUICK_SWITCH);
        g.map.put(KEYCODE_DPAD_RIGHT, GameAction.GYRO_TOGGLE);
        return g;
    }

    /** @return action for the key, or null if unmapped. */
    public GameAction actionFor(int keyCode) {
        return map.get(keyCode);
    }

    /** Assigns a key; any other key bound to the same action keeps its binding. */
    public void bind(int keyCode, GameAction action) {
        if (action == null) map.remove(keyCode);
        else map.put(keyCode, action);
    }

    /** First key bound to {@code action}, or -1. */
    public int keyFor(GameAction action) {
        for (Map.Entry<Integer, GameAction> e : map.entrySet()) {
            if (e.getValue() == action) return e.getKey();
        }
        return -1;
    }

    public void unbindAction(GameAction action) {
        map.values().removeIf(a -> a == action);
    }

    public Map<Integer, GameAction> asMap() {
        return new LinkedHashMap<>(map);
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<Integer, GameAction> e : map.entrySet()) m.put(Integer.toString(e.getKey()), e.getValue().name());
        return KeyValueCodec.encode(m);
    }

    public static GamepadMapping decode(String text) {
        Map<String, String> m = KeyValueCodec.decode(text);
        if (m.isEmpty()) return defaults();
        GamepadMapping g = new GamepadMapping();
        for (Map.Entry<String, String> e : m.entrySet()) {
            try {
                g.map.put(Integer.parseInt(e.getKey()), GameAction.valueOf(e.getValue()));
            } catch (IllegalArgumentException ignored) {
                // skip unknown entry
            }
        }
        return g;
    }
}
