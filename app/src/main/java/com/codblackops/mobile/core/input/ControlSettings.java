package com.codblackops.mobile.core.input;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/** All tunables of the touch / pointer / gyro / gamepad control system. */
public final class ControlSettings {

    public enum Mode {
        TOUCH_FPS("Touch FPS", "Analog kiri bergerak, geser layar kanan atau analog kanan untuk kamera. "
                + "Game melihat Classic Controller."),
        WII_POINTER("Wii Pointer", "Sentuhan layar menggerakkan pointer Wii Remote (IR) seperti membidik "
                + "dengan Wii Remote asli. Game melihat Wii Remote + Nunchuk."),
        GYROSCOPE("Gyroscope", "Membidik dengan memiringkan/memutar perangkat menggunakan sensor gyro."),
        GAMEPAD("Gamepad", "Gamepad Bluetooth/USB. HUD sentuh disembunyikan saat gamepad dipakai.");

        public final String label;
        public final String description;

        Mode(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public enum LookStyle { SWIPE, STICK }

    public enum FireMode { HOLD, TAP }

    public enum PressMode { HOLD, TOGGLE }

    public enum PointerMode { RELATIVE, ABSOLUTE }

    public enum GyroAxis { YAW, ROLL }

    public enum LayoutSet { CAMPAIGN, MULTIPLAYER }

    public enum RightStickAxes { Z_RZ, RX_RY }

    public Mode mode = Mode.TOUCH_FPS;
    /** Controller the game sees while in Gyroscope mode. */
    public ControlScheme gyroScheme = ControlScheme.WIIMOTE_NUNCHUK;

    // Touch camera
    public LookStyle lookStyle = LookStyle.SWIPE;
    public float lookSensX = 1.0f;
    public float lookSensY = 0.8f;
    public float lookAcceleration = 0.3f;
    public float lookDeadzone = 0.02f;
    public float lookAntiDeadzone = 0.12f;
    public float lookSmoothing = 0.35f;
    public boolean invertY = false;

    // Movement
    public float moveDeadzone = 0.12f;
    public boolean floatingMoveStick = true;

    // Wii pointer
    public PointerMode pointerMode = PointerMode.RELATIVE;
    public float pointerSens = 1.0f;
    public boolean pointerRecenterOnRelease = true;

    // Gyro
    public float gyroSensX = 1.0f;
    public float gyroSensY = 1.0f;
    public boolean gyroInvertX = false;
    public boolean gyroInvertY = false;
    public GyroAxis gyroAxis = GyroAxis.YAW;
    public float gyroDeadzone = 0.015f;
    public float gyroBiasX = 0f;
    public float gyroBiasY = 0f;
    public float gyroBiasZ = 0f;
    /** Also use gyro for fine aim on top of touch in Touch FPS / Wii Pointer modes. */
    public boolean gyroAssist = false;

    // Buttons
    public FireMode fireMode = FireMode.HOLD;
    public PressMode sprintMode = PressMode.TOGGLE;
    public PressMode adsMode = PressMode.HOLD;
    public boolean fireDragAim = true;
    public int interactHoldMs = 650;

    // HUD
    public float hudOpacity = 0.6f;
    public float hudScale = 1.0f;
    public boolean haptics = true;
    public boolean autoHideHudOnGamepad = true;
    public boolean showStatusChip = true;
    public LayoutSet layoutSet = LayoutSet.CAMPAIGN;

    // Gamepad
    public RightStickAxes rightStickAxes = RightStickAxes.Z_RZ;
    public float gamepadDeadzone = 0.15f;
    public float gamepadLookSens = 1.0f;
    public boolean gamepadInvertY = false;

    public static ControlSettings defaults() {
        return new ControlSettings();
    }

    public ControlSettings copy() {
        return decode(encode());
    }

    /** Controller that the emulated Wii should have attached for the current mode. */
    public ControlScheme activeScheme() {
        switch (mode) {
            case WII_POINTER:
                return ControlScheme.WIIMOTE_NUNCHUK;
            case GYROSCOPE:
                return gyroScheme;
            case GAMEPAD:
            case TOUCH_FPS:
            default:
                return ControlScheme.CLASSIC;
        }
    }

    public boolean gyroActive() {
        return mode == Mode.GYROSCOPE || gyroAssist;
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("mode", mode.name());
        m.put("gyroScheme", gyroScheme.name());
        m.put("lookStyle", lookStyle.name());
        m.put("lookSensX", Float.toString(lookSensX));
        m.put("lookSensY", Float.toString(lookSensY));
        m.put("lookAccel", Float.toString(lookAcceleration));
        m.put("lookDeadzone", Float.toString(lookDeadzone));
        m.put("lookAntiDeadzone", Float.toString(lookAntiDeadzone));
        m.put("lookSmoothing", Float.toString(lookSmoothing));
        m.put("invertY", Boolean.toString(invertY));
        m.put("moveDeadzone", Float.toString(moveDeadzone));
        m.put("floatingMove", Boolean.toString(floatingMoveStick));
        m.put("pointerMode", pointerMode.name());
        m.put("pointerSens", Float.toString(pointerSens));
        m.put("pointerRecenter", Boolean.toString(pointerRecenterOnRelease));
        m.put("gyroSensX", Float.toString(gyroSensX));
        m.put("gyroSensY", Float.toString(gyroSensY));
        m.put("gyroInvertX", Boolean.toString(gyroInvertX));
        m.put("gyroInvertY", Boolean.toString(gyroInvertY));
        m.put("gyroAxis", gyroAxis.name());
        m.put("gyroDeadzone", Float.toString(gyroDeadzone));
        m.put("gyroBiasX", Float.toString(gyroBiasX));
        m.put("gyroBiasY", Float.toString(gyroBiasY));
        m.put("gyroBiasZ", Float.toString(gyroBiasZ));
        m.put("gyroAssist", Boolean.toString(gyroAssist));
        m.put("fireMode", fireMode.name());
        m.put("sprintMode", sprintMode.name());
        m.put("adsMode", adsMode.name());
        m.put("fireDragAim", Boolean.toString(fireDragAim));
        m.put("interactHoldMs", Integer.toString(interactHoldMs));
        m.put("hudOpacity", Float.toString(hudOpacity));
        m.put("hudScale", Float.toString(hudScale));
        m.put("haptics", Boolean.toString(haptics));
        m.put("autoHideHud", Boolean.toString(autoHideHudOnGamepad));
        m.put("statusChip", Boolean.toString(showStatusChip));
        m.put("layoutSet", layoutSet.name());
        m.put("rightStickAxes", rightStickAxes.name());
        m.put("gamepadDeadzone", Float.toString(gamepadDeadzone));
        m.put("gamepadLookSens", Float.toString(gamepadLookSens));
        m.put("gamepadInvertY", Boolean.toString(gamepadInvertY));
        return KeyValueCodec.encode(m);
    }

    public static ControlSettings decode(String text) {
        ControlSettings s = new ControlSettings();
        Map<String, String> m = KeyValueCodec.decode(text);
        s.mode = KeyValueCodec.getEnum(m, "mode", Mode.class, s.mode);
        s.gyroScheme = KeyValueCodec.getEnum(m, "gyroScheme", ControlScheme.class, s.gyroScheme);
        s.lookStyle = KeyValueCodec.getEnum(m, "lookStyle", LookStyle.class, s.lookStyle);
        s.lookSensX = clamp(KeyValueCodec.getFloat(m, "lookSensX", s.lookSensX), 0.1f, 4f);
        s.lookSensY = clamp(KeyValueCodec.getFloat(m, "lookSensY", s.lookSensY), 0.1f, 4f);
        s.lookAcceleration = clamp(KeyValueCodec.getFloat(m, "lookAccel", s.lookAcceleration), 0f, 1.5f);
        s.lookDeadzone = clamp(KeyValueCodec.getFloat(m, "lookDeadzone", s.lookDeadzone), 0f, 0.4f);
        s.lookAntiDeadzone = clamp(KeyValueCodec.getFloat(m, "lookAntiDeadzone", s.lookAntiDeadzone), 0f, 0.5f);
        s.lookSmoothing = clamp(KeyValueCodec.getFloat(m, "lookSmoothing", s.lookSmoothing), 0f, 0.9f);
        s.invertY = KeyValueCodec.getBool(m, "invertY", s.invertY);
        s.moveDeadzone = clamp(KeyValueCodec.getFloat(m, "moveDeadzone", s.moveDeadzone), 0f, 0.5f);
        s.floatingMoveStick = KeyValueCodec.getBool(m, "floatingMove", s.floatingMoveStick);
        s.pointerMode = KeyValueCodec.getEnum(m, "pointerMode", PointerMode.class, s.pointerMode);
        s.pointerSens = clamp(KeyValueCodec.getFloat(m, "pointerSens", s.pointerSens), 0.1f, 4f);
        s.pointerRecenterOnRelease = KeyValueCodec.getBool(m, "pointerRecenter", s.pointerRecenterOnRelease);
        s.gyroSensX = clamp(KeyValueCodec.getFloat(m, "gyroSensX", s.gyroSensX), 0.1f, 5f);
        s.gyroSensY = clamp(KeyValueCodec.getFloat(m, "gyroSensY", s.gyroSensY), 0.1f, 5f);
        s.gyroInvertX = KeyValueCodec.getBool(m, "gyroInvertX", s.gyroInvertX);
        s.gyroInvertY = KeyValueCodec.getBool(m, "gyroInvertY", s.gyroInvertY);
        s.gyroAxis = KeyValueCodec.getEnum(m, "gyroAxis", GyroAxis.class, s.gyroAxis);
        s.gyroDeadzone = clamp(KeyValueCodec.getFloat(m, "gyroDeadzone", s.gyroDeadzone), 0f, 0.2f);
        s.gyroBiasX = KeyValueCodec.getFloat(m, "gyroBiasX", 0f);
        s.gyroBiasY = KeyValueCodec.getFloat(m, "gyroBiasY", 0f);
        s.gyroBiasZ = KeyValueCodec.getFloat(m, "gyroBiasZ", 0f);
        s.gyroAssist = KeyValueCodec.getBool(m, "gyroAssist", s.gyroAssist);
        s.fireMode = KeyValueCodec.getEnum(m, "fireMode", FireMode.class, s.fireMode);
        s.sprintMode = KeyValueCodec.getEnum(m, "sprintMode", PressMode.class, s.sprintMode);
        s.adsMode = KeyValueCodec.getEnum(m, "adsMode", PressMode.class, s.adsMode);
        s.fireDragAim = KeyValueCodec.getBool(m, "fireDragAim", s.fireDragAim);
        s.interactHoldMs = Math.max(200, Math.min(1500, KeyValueCodec.getInt(m, "interactHoldMs", s.interactHoldMs)));
        s.hudOpacity = clamp(KeyValueCodec.getFloat(m, "hudOpacity", s.hudOpacity), 0.1f, 1f);
        s.hudScale = clamp(KeyValueCodec.getFloat(m, "hudScale", s.hudScale), 0.5f, 1.8f);
        s.haptics = KeyValueCodec.getBool(m, "haptics", s.haptics);
        s.autoHideHudOnGamepad = KeyValueCodec.getBool(m, "autoHideHud", s.autoHideHudOnGamepad);
        s.showStatusChip = KeyValueCodec.getBool(m, "statusChip", s.showStatusChip);
        s.layoutSet = KeyValueCodec.getEnum(m, "layoutSet", LayoutSet.class, s.layoutSet);
        s.rightStickAxes = KeyValueCodec.getEnum(m, "rightStickAxes", RightStickAxes.class, s.rightStickAxes);
        s.gamepadDeadzone = clamp(KeyValueCodec.getFloat(m, "gamepadDeadzone", s.gamepadDeadzone), 0f, 0.5f);
        s.gamepadLookSens = clamp(KeyValueCodec.getFloat(m, "gamepadLookSens", s.gamepadLookSens), 0.1f, 3f);
        s.gamepadInvertY = KeyValueCodec.getBool(m, "gamepadInvertY", s.gamepadInvertY);
        return s;
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
