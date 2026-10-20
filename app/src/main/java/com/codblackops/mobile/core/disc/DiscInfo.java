package com.codblackops.mobile.core.disc;

/** Result of {@link DiscHeaderParser#parse}. Immutable. */
public final class DiscInfo {
    public final DiscFormat format;
    public final DiscPlatform platform;
    /** Six character game ID, e.g. "SC7E52"; null when parsing failed. */
    public final String gameId;
    public final String internalTitle;
    public final int discNumber;
    public final int revision;
    public final boolean nkit;
    /** Human readable (Indonesian) error, null when the header was read successfully. */
    public final String error;

    public DiscInfo(DiscFormat format, DiscPlatform platform, String gameId, String internalTitle,
                    int discNumber, int revision, boolean nkit, String error) {
        this.format = format;
        this.platform = platform;
        this.gameId = gameId;
        this.internalTitle = internalTitle;
        this.discNumber = discNumber;
        this.revision = revision;
        this.nkit = nkit;
        this.error = error;
    }

    public static DiscInfo error(DiscFormat format, String message) {
        return new DiscInfo(format, DiscPlatform.UNKNOWN, null, null, 0, 0, false, message);
    }

    public boolean isValid() {
        return error == null && gameId != null;
    }

    @Override
    public String toString() {
        return isValid()
                ? format.label + " " + platform.label + " " + gameId + " rev" + revision + " \"" + internalTitle + "\""
                : format.label + " error: " + error;
    }
}
