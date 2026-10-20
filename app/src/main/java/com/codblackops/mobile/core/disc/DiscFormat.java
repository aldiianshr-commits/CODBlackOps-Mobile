package com.codblackops.mobile.core.disc;

import java.util.Locale;

/** Container formats the scanner recognises. All of them are readable by Dolphin's DiscIO. */
public enum DiscFormat {
    ISO("ISO", "iso", "gcm"),
    WBFS("WBFS", "wbfs"),
    RVZ("RVZ", "rvz"),
    WIA("WIA", "wia"),
    GCZ("GCZ", "gcz"),
    CISO("CISO", "ciso"),
    UNKNOWN("Tidak dikenal");

    public final String label;
    private final String[] extensions;

    DiscFormat(String label, String... extensions) {
        this.label = label;
        this.extensions = extensions;
    }

    /** True if the file name has an extension worth opening during a scan. */
    public static boolean isCandidateFileName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(".")) return false; // hidden / macOS resource forks
        for (DiscFormat f : values()) {
            for (String ext : f.extensions) {
                if (lower.endsWith("." + ext)) return true;
            }
        }
        return false;
    }

    public static DiscFormat fromFileName(String name) {
        if (name == null) return UNKNOWN;
        String lower = name.toLowerCase(Locale.ROOT);
        for (DiscFormat f : values()) {
            for (String ext : f.extensions) {
                if (lower.endsWith("." + ext)) return f;
            }
        }
        return UNKNOWN;
    }
}
