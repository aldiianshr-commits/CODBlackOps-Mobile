package com.codblackops.mobile.core.input;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Position, size, opacity and visibility of every HUD element. Positions are the element centre
 * as a fraction of the overlay width/height so a layout survives resolution changes.
 */
public final class ControlLayout {

    public static final class Element {
        public float cx;
        public float cy;
        public float sizeDp;
        /** Per-element opacity 0.1..1, or -1 to use the global HUD opacity. */
        public float opacity;
        public boolean visible;

        public Element(float cx, float cy, float sizeDp, float opacity, boolean visible) {
            this.cx = cx;
            this.cy = cy;
            this.sizeDp = sizeDp;
            this.opacity = opacity;
            this.visible = visible;
        }

        Element copy() {
            return new Element(cx, cy, sizeDp, opacity, visible);
        }
    }

    public enum Preset {
        PHONE("Ponsel"),
        XIAOMI_PAD_6("Xiaomi Pad 6 (tablet 11\")");

        public final String label;

        Preset(String label) {
            this.label = label;
        }
    }

    public static final float MIN_SIZE_DP = 28f;
    public static final float MAX_SIZE_DP = 260f;

    private final EnumMap<HudElementId, Element> elements = new EnumMap<>(HudElementId.class);

    public Element get(HudElementId id) {
        Element e = elements.get(id);
        if (e == null) {
            e = new Element(0.5f, 0.5f, 56f, -1f, false);
            elements.put(id, e);
        }
        return e;
    }

    public void set(HudElementId id, float cx, float cy, float sizeDp, float opacity, boolean visible) {
        elements.put(id, new Element(clamp01(cx), clamp01(cy), clampSize(sizeDp),
                opacity < 0 ? -1f : Math.max(0.1f, Math.min(1f, opacity)), visible));
    }

    public ControlLayout copy() {
        ControlLayout c = new ControlLayout();
        for (Map.Entry<HudElementId, Element> e : elements.entrySet()) c.elements.put(e.getKey(), e.getValue().copy());
        return c;
    }

    public static ControlLayout preset(Preset preset, ControlSettings.LayoutSet set) {
        ControlLayout l = new ControlLayout();
        boolean mp = set == ControlSettings.LayoutSet.MULTIPLAYER;
        if (preset == Preset.XIAOMI_PAD_6) {
            l.set(HudElementId.MOVE_STICK, 0.12f, 0.70f, 190, -1, true);
            l.set(HudElementId.LOOK_STICK, 0.66f, 0.64f, 160, -1, false);
            l.set(HudElementId.SPRINT, 0.12f, 0.38f, 70, -1, true);
            l.set(HudElementId.FIRE_LEFT, 0.25f, 0.45f, 76, -1, true);
            l.set(HudElementId.FIRE, 0.88f, 0.62f, 110, -1, true);
            l.set(HudElementId.ADS, 0.95f, 0.42f, 80, -1, true);
            l.set(HudElementId.JUMP, 0.95f, 0.82f, 76, -1, true);
            l.set(HudElementId.CROUCH, 0.86f, 0.88f, 70, -1, true);
            l.set(HudElementId.RELOAD, 0.78f, 0.50f, 68, -1, true);
            l.set(HudElementId.INTERACT, 0.76f, 0.32f, 62, -1, true);
            l.set(HudElementId.MELEE, 0.78f, 0.76f, 68, -1, true);
            l.set(HudElementId.LETHAL, 0.69f, 0.88f, 64, -1, true);
            l.set(HudElementId.TACTICAL, 0.62f, 0.90f, 58, -1, true);
            l.set(HudElementId.SWITCH_WEAPON, 0.95f, 0.20f, 70, -1, true);
            l.set(HudElementId.QUICK_SWITCH, 0.88f, 0.20f, 58, -1, true);
            l.set(HudElementId.KILLSTREAK, 0.81f, 0.20f, 58, -1, mp);
            l.set(HudElementId.PAUSE, 0.55f, 0.06f, 48, -1, true);
            l.set(HudElementId.SCOREBOARD, 0.48f, 0.06f, 48, -1, mp);
            l.set(HudElementId.EMU_MENU, 0.03f, 0.07f, 48, -1, true);
            l.set(HudElementId.LAYOUT_SWITCH, 0.62f, 0.06f, 48, -1, true);
            l.set(HudElementId.GYRO_TOGGLE, 0.41f, 0.06f, 48, -1, true);
            l.set(HudElementId.RECENTER, 0.68f, 0.32f, 54, -1, true);
        } else {
            l.set(HudElementId.MOVE_STICK, 0.14f, 0.68f, 150, -1, true);
            l.set(HudElementId.LOOK_STICK, 0.62f, 0.62f, 120, -1, false);
            l.set(HudElementId.SPRINT, 0.14f, 0.30f, 54, -1, true);
            l.set(HudElementId.FIRE_LEFT, 0.30f, 0.38f, 60, -1, true);
            l.set(HudElementId.FIRE, 0.86f, 0.60f, 88, -1, true);
            l.set(HudElementId.ADS, 0.95f, 0.38f, 64, -1, true);
            l.set(HudElementId.JUMP, 0.95f, 0.80f, 60, -1, true);
            l.set(HudElementId.CROUCH, 0.84f, 0.88f, 56, -1, true);
            l.set(HudElementId.RELOAD, 0.74f, 0.50f, 54, -1, true);
            l.set(HudElementId.INTERACT, 0.70f, 0.30f, 50, -1, true);
            l.set(HudElementId.MELEE, 0.74f, 0.74f, 54, -1, true);
            l.set(HudElementId.LETHAL, 0.62f, 0.88f, 52, -1, true);
            l.set(HudElementId.TACTICAL, 0.52f, 0.88f, 48, -1, true);
            l.set(HudElementId.SWITCH_WEAPON, 0.95f, 0.16f, 56, -1, true);
            l.set(HudElementId.QUICK_SWITCH, 0.85f, 0.16f, 46, -1, true);
            l.set(HudElementId.KILLSTREAK, 0.75f, 0.16f, 46, -1, mp);
            l.set(HudElementId.PAUSE, 0.56f, 0.07f, 40, -1, true);
            l.set(HudElementId.SCOREBOARD, 0.47f, 0.07f, 40, -1, mp);
            l.set(HudElementId.EMU_MENU, 0.04f, 0.09f, 40, -1, true);
            l.set(HudElementId.LAYOUT_SWITCH, 0.65f, 0.07f, 40, -1, true);
            l.set(HudElementId.GYRO_TOGGLE, 0.38f, 0.07f, 40, -1, true);
            l.set(HudElementId.RECENTER, 0.62f, 0.30f, 44, -1, true);
        }
        return l;
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<HudElementId, Element> e : elements.entrySet()) {
            Element v = e.getValue();
            m.put(e.getKey().name(), String.format(Locale.ROOT, "%.4f,%.4f,%.1f,%.3f,%b",
                    v.cx, v.cy, v.sizeDp, v.opacity, v.visible));
        }
        return KeyValueCodec.encode(m);
    }

    /** Decodes a layout; missing elements are filled from {@code fallback}. */
    public static ControlLayout decode(String text, ControlLayout fallback) {
        ControlLayout l = fallback.copy();
        for (Map.Entry<String, String> e : KeyValueCodec.decode(text).entrySet()) {
            HudElementId id;
            try {
                id = HudElementId.valueOf(e.getKey());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            String[] p = e.getValue().split(",");
            if (p.length != 5) continue;
            try {
                l.set(id, Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]),
                        Float.parseFloat(p[3]), Boolean.parseBoolean(p[4]));
            } catch (NumberFormatException ignored) {
                // keep fallback for this element
            }
        }
        return l;
    }

    static float clamp01(float v) {
        if (Float.isNaN(v)) return 0.5f;
        return Math.max(0f, Math.min(1f, v));
    }

    static float clampSize(float v) {
        if (Float.isNaN(v)) return 56f;
        return Math.max(MIN_SIZE_DP, Math.min(MAX_SIZE_DP, v));
    }
}
