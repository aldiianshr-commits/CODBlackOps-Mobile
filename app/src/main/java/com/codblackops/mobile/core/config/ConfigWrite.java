package com.codblackops.mobile.core.config;

import java.util.Locale;

/**
 * One value to store in Dolphin's layered config system (NativeConfig on Android).
 * The file/section/key triplets match Dolphin's Config::Info definitions
 * (Source/Core/Core/Config/*.cpp of the pinned Dolphin release).
 */
public final class ConfigWrite {
    public enum Type { INT, BOOL, FLOAT, STRING }

    public static final String FILE_MAIN = "Dolphin";
    public static final String FILE_GFX = "GFX";
    public static final String FILE_WIIMOTE = "WiimoteNew";
    public static final String FILE_SYSCONF = "SYSCONF";
    public static final String FILE_LOGGER = "Logger";

    public final String file;
    public final String section;
    public final String key;
    public final Type type;
    public final int intValue;
    public final boolean boolValue;
    public final float floatValue;
    public final String stringValue;

    private ConfigWrite(String file, String section, String key, Type type, int i, boolean b, float f, String s) {
        this.file = file;
        this.section = section;
        this.key = key;
        this.type = type;
        this.intValue = i;
        this.boolValue = b;
        this.floatValue = f;
        this.stringValue = s;
    }

    public static ConfigWrite ofInt(String file, String section, String key, int value) {
        return new ConfigWrite(file, section, key, Type.INT, value, false, 0f, null);
    }

    public static ConfigWrite ofBool(String file, String section, String key, boolean value) {
        return new ConfigWrite(file, section, key, Type.BOOL, 0, value, 0f, null);
    }

    public static ConfigWrite ofFloat(String file, String section, String key, float value) {
        return new ConfigWrite(file, section, key, Type.FLOAT, 0, false, value, null);
    }

    public static ConfigWrite ofString(String file, String section, String key, String value) {
        return new ConfigWrite(file, section, key, Type.STRING, 0, false, 0f, value == null ? "" : value);
    }

    /** "GFX/Settings/InternalResolution" — used for de-duplication and logs. */
    public String location() {
        return file + "/" + section + "/" + key;
    }

    public String valueAsString() {
        switch (type) {
            case INT: return Integer.toString(intValue);
            case BOOL: return Boolean.toString(boolValue);
            case FLOAT: return String.format(Locale.ROOT, "%.3f", floatValue);
            default: return stringValue;
        }
    }

    @Override
    public String toString() {
        return location() + " = " + valueAsString();
    }
}
