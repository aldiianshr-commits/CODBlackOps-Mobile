package com.codblackops.mobile.core.input;

/** Every element of the touch HUD. Sticks have no action; buttons trigger a {@link GameAction}. */
public enum HudElementId {
    MOVE_STICK(null, true, "GERAK"),
    LOOK_STICK(null, true, "KAMERA"),
    FIRE(GameAction.FIRE, false, null),
    FIRE_LEFT(GameAction.FIRE, false, null),
    ADS(GameAction.ADS, false, null),
    RELOAD(GameAction.RELOAD, false, null),
    INTERACT(GameAction.INTERACT, false, null),
    LETHAL(GameAction.LETHAL, false, null),
    TACTICAL(GameAction.TACTICAL, false, null),
    MELEE(GameAction.MELEE, false, null),
    JUMP(GameAction.JUMP, false, null),
    CROUCH(GameAction.CROUCH, false, null),
    SPRINT(GameAction.SPRINT, false, null),
    SWITCH_WEAPON(GameAction.SWITCH_WEAPON, false, null),
    QUICK_SWITCH(GameAction.QUICK_SWITCH, false, null),
    KILLSTREAK(GameAction.KILLSTREAK, false, null),
    PAUSE(GameAction.PAUSE, false, null),
    SCOREBOARD(GameAction.SCOREBOARD, false, null),
    EMU_MENU(GameAction.EMULATOR_MENU, false, null),
    LAYOUT_SWITCH(GameAction.LAYOUT_SWITCH, false, null),
    GYRO_TOGGLE(GameAction.GYRO_TOGGLE, false, null),
    RECENTER(GameAction.RECENTER_AIM, false, null);

    /** Null for sticks. */
    public final GameAction action;
    public final boolean isStick;
    private final String stickLabel;

    HudElementId(GameAction action, boolean isStick, String stickLabel) {
        this.action = action;
        this.isStick = isStick;
        this.stickLabel = stickLabel;
    }

    public String hudLabel() {
        return isStick ? stickLabel : action.hudLabel;
    }

    public String description() {
        if (this == MOVE_STICK) return "Analog kiri (bergerak)";
        if (this == LOOK_STICK) return "Analog kanan (kamera/bidik)";
        if (this == FIRE_LEFT) return "Tembak (tombol kiri)";
        return action.label;
    }
}
