package com.codblackops.mobile.core.input;

/**
 * Gameplay actions of Call of Duty: Black Ops (Wii). Button actions are bound to a {@link WiiInput}
 * per {@link ControlScheme} (see {@link Bindings}); launcher actions ({@link #isLauncherAction()})
 * are handled by the app itself and never reach the emulated controller.
 */
public enum GameAction {
    FIRE("Tembak", "TEMBAK"),
    ADS("Bidik (ADS / scope)", "BIDIK"),
    RELOAD("Isi ulang", "ISI"),
    INTERACT("Interaksi / ambil senjata / pintu", "AKSI"),
    LETHAL("Granat (lethal)", "GRANAT"),
    TACTICAL("Granat taktis", "TAKTIS"),
    MELEE("Serangan jarak dekat", "PISAU"),
    JUMP("Lompat / aksi kontekstual", "LOMPAT"),
    CROUCH("Jongkok (tahan: tiarap)", "JONGKOK"),
    SPRINT("Lari", "LARI"),
    SWITCH_WEAPON("Ganti senjata", "SENJATA"),
    QUICK_SWITCH("Ganti cepat / senjata sebelumnya", "CEPAT"),
    KILLSTREAK("Killstreak / perlengkapan", "STREAK"),
    PAUSE("Pause (menu game)", "PAUSE"),
    SCOREBOARD("Skor / objektif", "SKOR"),

    // Launcher-side actions
    EMULATOR_MENU("Menu emulator (save state, keluar)", "MENU"),
    LAYOUT_SWITCH("Ganti tata letak Campaign/Multiplayer", "MODE"),
    GYRO_TOGGLE("Gyro hidup/mati", "GYRO"),
    RECENTER_AIM("Pusatkan bidikan", "PUSAT");

    public final String label;
    public final String hudLabel;

    GameAction(String label, String hudLabel) {
        this.label = label;
        this.hudLabel = hudLabel;
    }

    public boolean isLauncherAction() {
        return this == EMULATOR_MENU || this == LAYOUT_SWITCH || this == GYRO_TOGGLE || this == RECENTER_AIM;
    }

    /** How a press on the touch screen / gamepad is turned into Wii button state. */
    public enum Behavior {
        /** Wii button follows the finger. */
        HOLD,
        /** Each press produces one short press of the Wii button. */
        PULSE,
        /** Each press flips a latched state. */
        TOGGLE,
        /** A press is held for at least the "interact" duration (CoD uses hold-to-use). */
        LONG_PRESS
    }

    public Behavior behavior(ControlSettings s) {
        switch (this) {
            case FIRE:
                return s.fireMode == ControlSettings.FireMode.TAP ? Behavior.PULSE : Behavior.HOLD;
            case SPRINT:
                return s.sprintMode == ControlSettings.PressMode.TOGGLE ? Behavior.TOGGLE : Behavior.HOLD;
            case ADS:
                return s.adsMode == ControlSettings.PressMode.TOGGLE ? Behavior.TOGGLE : Behavior.HOLD;
            case INTERACT:
                return Behavior.LONG_PRESS;
            case MELEE:
                return Behavior.PULSE;
            default:
                return Behavior.HOLD;
        }
    }
}
