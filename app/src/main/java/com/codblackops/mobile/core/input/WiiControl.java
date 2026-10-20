package com.codblackops.mobile.core.input;

/**
 * Control IDs of Dolphin's InputOverrider (ciface::Touch::ControlID). Values 0-55 are upstream
 * Dolphin; 56-61 are added by patches/0001-codblackops-dolphin-integration.patch so touch and
 * gyro input can trigger the Wii Remote / Nunchuk shake gestures used for melee and grenades.
 */
public final class WiiControl {
    private WiiControl() {
    }

    public static final int WIIMOTE_A_BUTTON = 18;
    public static final int WIIMOTE_B_BUTTON = 19;
    public static final int WIIMOTE_ONE_BUTTON = 20;
    public static final int WIIMOTE_TWO_BUTTON = 21;
    public static final int WIIMOTE_PLUS_BUTTON = 22;
    public static final int WIIMOTE_MINUS_BUTTON = 23;
    public static final int WIIMOTE_HOME_BUTTON = 24;
    public static final int WIIMOTE_DPAD_UP = 25;
    public static final int WIIMOTE_DPAD_DOWN = 26;
    public static final int WIIMOTE_DPAD_LEFT = 27;
    public static final int WIIMOTE_DPAD_RIGHT = 28;
    public static final int WIIMOTE_IR_X = 29;
    public static final int WIIMOTE_IR_Y = 30;

    public static final int NUNCHUK_C_BUTTON = 31;
    public static final int NUNCHUK_Z_BUTTON = 32;
    public static final int NUNCHUK_STICK_X = 33;
    public static final int NUNCHUK_STICK_Y = 34;

    public static final int CLASSIC_A_BUTTON = 35;
    public static final int CLASSIC_B_BUTTON = 36;
    public static final int CLASSIC_X_BUTTON = 37;
    public static final int CLASSIC_Y_BUTTON = 38;
    public static final int CLASSIC_ZL_BUTTON = 39;
    public static final int CLASSIC_ZR_BUTTON = 40;
    public static final int CLASSIC_PLUS_BUTTON = 41;
    public static final int CLASSIC_MINUS_BUTTON = 42;
    public static final int CLASSIC_HOME_BUTTON = 43;
    public static final int CLASSIC_DPAD_UP = 44;
    public static final int CLASSIC_DPAD_DOWN = 45;
    public static final int CLASSIC_DPAD_LEFT = 46;
    public static final int CLASSIC_DPAD_RIGHT = 47;
    public static final int CLASSIC_L_DIGITAL = 48;
    public static final int CLASSIC_R_DIGITAL = 49;
    public static final int CLASSIC_L_ANALOG = 50;
    public static final int CLASSIC_R_ANALOG = 51;
    public static final int CLASSIC_LEFT_STICK_X = 52;
    public static final int CLASSIC_LEFT_STICK_Y = 53;
    public static final int CLASSIC_RIGHT_STICK_X = 54;
    public static final int CLASSIC_RIGHT_STICK_Y = 55;

    // CODBlackOps-Mobile native patch additions
    public static final int WIIMOTE_SHAKE_X = 56;
    public static final int WIIMOTE_SHAKE_Y = 57;
    public static final int WIIMOTE_SHAKE_Z = 58;
    public static final int NUNCHUK_SHAKE_X = 59;
    public static final int NUNCHUK_SHAKE_Y = 60;
    public static final int NUNCHUK_SHAKE_Z = 61;

    public static final int FIRST_WII_CONTROL = WIIMOTE_A_BUTTON;
    public static final int LAST_WII_CONTROL = NUNCHUK_SHAKE_Z;
    public static final int CONTROL_COUNT = LAST_WII_CONTROL + 1;

    /** Dolphin WiimoteEmu::ExtensionNumber values. */
    public static final int EXTENSION_NUNCHUK = 1;
    public static final int EXTENSION_CLASSIC = 2;
}
