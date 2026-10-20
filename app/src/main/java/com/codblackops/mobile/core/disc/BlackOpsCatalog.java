package com.codblackops.mobile.core.disc;

import java.util.Collections;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Known releases of Call of Duty: Black Ops for Nintendo Wii (developer Treyarch/n-Space,
 * publisher Activision, maker code 52). IDs taken from Dolphin's Data/Sys/wiitdb and
 * GameSettings/SC7.ini shipped with the backend.
 */
public final class BlackOpsCatalog {
    public static final String GAME_ID_PREFIX = "SC7";
    public static final String MAKER_CODE = "52";

    private static final Map<String, String> REGIONS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SC7E52", "NTSC-U (Amerika)");
        m.put("SC7Z52", "NTSC-U (Amerika, rilis alternatif)");
        m.put("SC7P52", "PAL (Eropa)");
        m.put("SC7D52", "PAL (Jerman)");
        m.put("SC7F52", "PAL (Prancis)");
        m.put("SC7I52", "PAL (Italia)");
        m.put("SC7S52", "PAL (Spanyol)");
        REGIONS = Collections.unmodifiableMap(m);
    }

    private BlackOpsCatalog() {
    }

    public static Map<String, String> knownReleases() {
        return REGIONS;
    }

    public static boolean isBlackOpsWii(String gameId) {
        return gameId != null && REGIONS.containsKey(gameId);
    }

    /** Looks like a Black Ops disc but is not in the known list (e.g. an unknown re-release). */
    public static boolean looksLikeBlackOps(String gameId) {
        return gameId != null && gameId.length() == 6 && gameId.startsWith(GAME_ID_PREFIX)
                && gameId.endsWith(MAKER_CODE);
    }

    public static String regionName(String gameId) {
        if (gameId == null || gameId.length() < 4) return "Tidak diketahui";
        String known = REGIONS.get(gameId);
        if (known != null) return known;
        switch (gameId.charAt(3)) {
            case 'E': return "NTSC-U";
            case 'J': return "NTSC-J";
            case 'K': return "NTSC-K";
            case 'P': case 'D': case 'F': case 'I': case 'S': case 'X': case 'Y': return "PAL";
            default: return "Tidak diketahui";
        }
    }

    /**
     * Wii title ID low word as Dolphin names the NAND folder: the first four characters of the
     * game ID as lowercase hex ("SC7E" -> "53433745").
     */
    public static String titleIdLowHex(String gameId) {
        if (gameId == null || gameId.length() < 4) {
            throw new IllegalArgumentException("Game ID terlalu pendek: " + gameId);
        }
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 4; i++) {
            sb.append(String.format(Locale.ROOT, "%02x", (int) gameId.charAt(i)));
        }
        return sb.toString();
    }

    /** Relative path of the save data inside Dolphin's user directory. */
    public static String nandSaveDataPath(String gameId) {
        return "Wii/title/00010000/" + titleIdLowHex(gameId) + "/data";
    }
}
