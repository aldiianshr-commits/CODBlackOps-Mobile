package com.codblackops.mobile.core.input;

/** A physical input on the emulated controller that a game action can be bound to. */
public enum WiiInput {
    NONE("Tidak dipetakan", null, false),

    CLASSIC_A("Classic: a", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_A_BUTTON),
    CLASSIC_B("Classic: b", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_B_BUTTON),
    CLASSIC_X("Classic: x", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_X_BUTTON),
    CLASSIC_Y("Classic: y", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_Y_BUTTON),
    CLASSIC_L("Classic: L", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_L_DIGITAL, WiiControl.CLASSIC_L_ANALOG),
    CLASSIC_R("Classic: R", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_R_DIGITAL, WiiControl.CLASSIC_R_ANALOG),
    CLASSIC_ZL("Classic: ZL", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_ZL_BUTTON),
    CLASSIC_ZR("Classic: ZR", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_ZR_BUTTON),
    CLASSIC_PLUS("Classic: +", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_PLUS_BUTTON),
    CLASSIC_MINUS("Classic: -", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_MINUS_BUTTON),
    CLASSIC_HOME("Classic: Home", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_HOME_BUTTON),
    CLASSIC_DPAD_UP("Classic: D-Pad atas", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_DPAD_UP),
    CLASSIC_DPAD_DOWN("Classic: D-Pad bawah", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_DPAD_DOWN),
    CLASSIC_DPAD_LEFT("Classic: D-Pad kiri", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_DPAD_LEFT),
    CLASSIC_DPAD_RIGHT("Classic: D-Pad kanan", ControlScheme.CLASSIC, false, WiiControl.CLASSIC_DPAD_RIGHT),

    WIIMOTE_A("Wii Remote: A", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_A_BUTTON),
    WIIMOTE_B("Wii Remote: B (pelatuk)", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_B_BUTTON),
    WIIMOTE_ONE("Wii Remote: 1", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_ONE_BUTTON),
    WIIMOTE_TWO("Wii Remote: 2", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_TWO_BUTTON),
    WIIMOTE_PLUS("Wii Remote: +", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_PLUS_BUTTON),
    WIIMOTE_MINUS("Wii Remote: -", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_MINUS_BUTTON),
    WIIMOTE_HOME("Wii Remote: Home", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_HOME_BUTTON),
    WIIMOTE_DPAD_UP("Wii Remote: D-Pad atas", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_DPAD_UP),
    WIIMOTE_DPAD_DOWN("Wii Remote: D-Pad bawah", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_DPAD_DOWN),
    WIIMOTE_DPAD_LEFT("Wii Remote: D-Pad kiri", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_DPAD_LEFT),
    WIIMOTE_DPAD_RIGHT("Wii Remote: D-Pad kanan", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.WIIMOTE_DPAD_RIGHT),
    NUNCHUK_C("Nunchuk: C", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.NUNCHUK_C_BUTTON),
    NUNCHUK_Z("Nunchuk: Z", ControlScheme.WIIMOTE_NUNCHUK, false, WiiControl.NUNCHUK_Z_BUTTON),
    WIIMOTE_SHAKE("Goyang Wii Remote", ControlScheme.WIIMOTE_NUNCHUK, true,
            WiiControl.WIIMOTE_SHAKE_X, WiiControl.WIIMOTE_SHAKE_Y, WiiControl.WIIMOTE_SHAKE_Z),
    NUNCHUK_SHAKE("Goyang Nunchuk", ControlScheme.WIIMOTE_NUNCHUK, true,
            WiiControl.NUNCHUK_SHAKE_X, WiiControl.NUNCHUK_SHAKE_Y, WiiControl.NUNCHUK_SHAKE_Z);

    public final String label;
    /** Scheme this input belongs to; null for NONE. */
    public final ControlScheme scheme;
    /** Shake gestures are sent as a short motion pulse instead of a held button. */
    public final boolean isShake;
    private final int[] controls;

    WiiInput(String label, ControlScheme scheme, boolean isShake, int... controls) {
        this.label = label;
        this.scheme = scheme;
        this.isShake = isShake;
        this.controls = controls;
    }

    public int[] controls() {
        return controls.clone();
    }

    public int controlCount() {
        return controls.length;
    }

    public int control(int i) {
        return controls[i];
    }

    public boolean availableIn(ControlScheme s) {
        return this == NONE || scheme == s;
    }
}
