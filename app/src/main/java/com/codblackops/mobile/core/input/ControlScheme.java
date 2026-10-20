package com.codblackops.mobile.core.input;

/** Which emulated Wii controller the game sees. Black Ops Wii supports both. */
public enum ControlScheme {
    CLASSIC("Classic Controller (Pro)", WiiControl.EXTENSION_CLASSIC,
            WiiControl.CLASSIC_LEFT_STICK_X, WiiControl.CLASSIC_LEFT_STICK_Y),
    WIIMOTE_NUNCHUK("Wii Remote + Nunchuk", WiiControl.EXTENSION_NUNCHUK,
            WiiControl.NUNCHUK_STICK_X, WiiControl.NUNCHUK_STICK_Y);

    public final String label;
    public final int dolphinExtension;
    public final int moveStickX;
    public final int moveStickY;

    ControlScheme(String label, int dolphinExtension, int moveStickX, int moveStickY) {
        this.label = label;
        this.dolphinExtension = dolphinExtension;
        this.moveStickX = moveStickX;
        this.moveStickY = moveStickY;
    }

    /** Classic aims with the right stick; Wii Remote aims with the IR pointer. */
    public boolean aimsWithPointer() {
        return this == WIIMOTE_NUNCHUK;
    }
}
