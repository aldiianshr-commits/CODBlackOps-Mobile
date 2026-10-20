package com.codblackops.mobile.core.disc;

public enum DiscPlatform {
    WII("Wii"),
    GAMECUBE("GameCube"),
    UNKNOWN("Tidak dikenal");

    public final String label;

    DiscPlatform(String label) {
        this.label = label;
    }
}
