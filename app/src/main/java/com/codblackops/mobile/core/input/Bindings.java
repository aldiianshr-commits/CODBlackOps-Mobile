package com.codblackops.mobile.core.input;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which emulated Wii input each gameplay action presses, per control scheme.
 *
 * Black Ops (Wii) lets the player customise every button in Options > Controls, so these defaults
 * are a starting point: the launcher shows the full table and every entry can be changed so the
 * on-screen buttons match the in-game layout.
 */
public final class Bindings {
    private final EnumMap<GameAction, WiiInput> classic = new EnumMap<>(GameAction.class);
    private final EnumMap<GameAction, WiiInput> wiimote = new EnumMap<>(GameAction.class);

    public static Bindings defaults() {
        Bindings b = new Bindings();
        // Classic Controller (Pro): console-style layout.
        b.classic.put(GameAction.FIRE, WiiInput.CLASSIC_ZR);
        b.classic.put(GameAction.ADS, WiiInput.CLASSIC_ZL);
        b.classic.put(GameAction.JUMP, WiiInput.CLASSIC_B);
        b.classic.put(GameAction.CROUCH, WiiInput.CLASSIC_A);
        b.classic.put(GameAction.RELOAD, WiiInput.CLASSIC_Y);
        b.classic.put(GameAction.INTERACT, WiiInput.CLASSIC_Y);
        b.classic.put(GameAction.SWITCH_WEAPON, WiiInput.CLASSIC_X);
        b.classic.put(GameAction.LETHAL, WiiInput.CLASSIC_R);
        b.classic.put(GameAction.TACTICAL, WiiInput.CLASSIC_L);
        b.classic.put(GameAction.MELEE, WiiInput.CLASSIC_DPAD_DOWN);
        b.classic.put(GameAction.SPRINT, WiiInput.CLASSIC_DPAD_UP);
        b.classic.put(GameAction.QUICK_SWITCH, WiiInput.CLASSIC_DPAD_LEFT);
        b.classic.put(GameAction.KILLSTREAK, WiiInput.CLASSIC_DPAD_RIGHT);
        b.classic.put(GameAction.PAUSE, WiiInput.CLASSIC_PLUS);
        b.classic.put(GameAction.SCOREBOARD, WiiInput.CLASSIC_MINUS);

        // Wii Remote + Nunchuk: layout reported as the game's default by players (GameFAQs,
        // "I barely played COD on Wii" thread): pointer aims, B fires, Z aims down sights, C
        // crouches/prones, A sprints, shake Nunchuk reloads, + / - throw grenades.
        b.wiimote.put(GameAction.FIRE, WiiInput.WIIMOTE_B);
        b.wiimote.put(GameAction.ADS, WiiInput.NUNCHUK_Z);
        b.wiimote.put(GameAction.CROUCH, WiiInput.NUNCHUK_C);
        b.wiimote.put(GameAction.SPRINT, WiiInput.WIIMOTE_A);
        b.wiimote.put(GameAction.JUMP, WiiInput.WIIMOTE_DPAD_UP);
        b.wiimote.put(GameAction.MELEE, WiiInput.WIIMOTE_DPAD_DOWN);
        b.wiimote.put(GameAction.SWITCH_WEAPON, WiiInput.WIIMOTE_DPAD_RIGHT);
        b.wiimote.put(GameAction.QUICK_SWITCH, WiiInput.WIIMOTE_DPAD_RIGHT);
        b.wiimote.put(GameAction.KILLSTREAK, WiiInput.WIIMOTE_DPAD_LEFT);
        b.wiimote.put(GameAction.RELOAD, WiiInput.NUNCHUK_SHAKE);
        b.wiimote.put(GameAction.INTERACT, WiiInput.NUNCHUK_SHAKE);
        b.wiimote.put(GameAction.LETHAL, WiiInput.WIIMOTE_PLUS);
        b.wiimote.put(GameAction.TACTICAL, WiiInput.WIIMOTE_MINUS);
        b.wiimote.put(GameAction.PAUSE, WiiInput.WIIMOTE_ONE);
        b.wiimote.put(GameAction.SCOREBOARD, WiiInput.WIIMOTE_TWO);
        return b;
    }

    public WiiInput get(ControlScheme scheme, GameAction action) {
        if (action.isLauncherAction()) return WiiInput.NONE;
        WiiInput i = map(scheme).get(action);
        return i == null ? WiiInput.NONE : i;
    }

    public void set(ControlScheme scheme, GameAction action, WiiInput input) {
        if (action.isLauncherAction()) {
            throw new IllegalArgumentException(action + " is handled by the launcher");
        }
        if (!input.availableIn(scheme)) {
            throw new IllegalArgumentException(input + " does not exist on " + scheme);
        }
        map(scheme).put(action, input);
    }

    public void resetScheme(ControlScheme scheme) {
        Bindings d = defaults();
        map(scheme).clear();
        map(scheme).putAll(d.map(scheme));
    }

    private EnumMap<GameAction, WiiInput> map(ControlScheme s) {
        return s == ControlScheme.CLASSIC ? classic : wiimote;
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<GameAction, WiiInput> e : classic.entrySet()) m.put("c." + e.getKey().name(), e.getValue().name());
        for (Map.Entry<GameAction, WiiInput> e : wiimote.entrySet()) m.put("w." + e.getKey().name(), e.getValue().name());
        return KeyValueCodec.encode(m);
    }

    public static Bindings decode(String text) {
        Bindings b = defaults();
        Map<String, String> m = KeyValueCodec.decode(text);
        for (Map.Entry<String, String> e : m.entrySet()) {
            String k = e.getKey();
            if (k.length() < 3 || k.charAt(1) != '.') continue;
            ControlScheme scheme = k.charAt(0) == 'c' ? ControlScheme.CLASSIC
                    : k.charAt(0) == 'w' ? ControlScheme.WIIMOTE_NUNCHUK : null;
            if (scheme == null) continue;
            try {
                GameAction a = GameAction.valueOf(k.substring(2));
                WiiInput in = WiiInput.valueOf(e.getValue());
                if (!a.isLauncherAction() && in.availableIn(scheme)) b.map(scheme).put(a, in);
            } catch (IllegalArgumentException ignored) {
                // unknown entry from an older/newer version: keep default
            }
        }
        return b;
    }
}
