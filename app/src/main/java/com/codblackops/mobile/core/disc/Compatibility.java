package com.codblackops.mobile.core.disc;

/**
 * Compatibility verdict shown in the game list. The wording deliberately avoids claiming that the
 * game runs well: that can only be known from a real run on the device.
 */
public final class Compatibility {
    public enum Status {
        /** Black Ops Wii, header valid, accepted by the backend. */
        READY,
        /** Black Ops Wii but stored as NKit: Dolphin can run it, with known risks. */
        READY_WITH_WARNING,
        /** Header looked fine but the backend has not validated it yet (core not initialised). */
        PENDING_BACKEND_CHECK,
        /** Valid Wii/GameCube disc, but not Call of Duty: Black Ops. */
        WRONG_GAME,
        /** Readable header but the backend refused to open the file. */
        BACKEND_REJECTED,
        /** Not a disc image, truncated or corrupt. */
        INVALID
    }

    public final Status status;
    public final String message;

    private Compatibility(Status status, String message) {
        this.status = status;
        this.message = message;
    }

    public boolean canLaunch() {
        return status == Status.READY || status == Status.READY_WITH_WARNING;
    }

    /**
     * @param header      result of the fast header parse
     * @param backendOk   null = backend not checked yet, true = Dolphin GameFile.parse succeeded
     * @param backendId   game ID reported by the backend (may differ from header for odd dumps)
     */
    public static Compatibility evaluate(DiscInfo header, Boolean backendOk, String backendId) {
        if (header == null || !header.isValid()) {
            String why = header == null ? "File tidak terbaca" : header.error;
            return new Compatibility(Status.INVALID, why);
        }
        String id = backendId != null && !backendId.isEmpty() ? backendId : header.gameId;
        if (header.platform == DiscPlatform.GAMECUBE) {
            return new Compatibility(Status.WRONG_GAME,
                    "Disc GameCube (" + id + "). Aplikasi ini hanya untuk Call of Duty: Black Ops versi Wii.");
        }
        if (!BlackOpsCatalog.isBlackOpsWii(id)) {
            if (BlackOpsCatalog.looksLikeBlackOps(id)) {
                return new Compatibility(Status.WRONG_GAME,
                        "ID " + id + " mirip Black Ops tetapi tidak ada di daftar rilis yang dikenal.");
            }
            return new Compatibility(Status.WRONG_GAME,
                    "Game lain (" + id + "), bukan Call of Duty: Black Ops Wii.");
        }
        if (backendOk == null) {
            return new Compatibility(Status.PENDING_BACKEND_CHECK,
                    "Header Black Ops Wii valid; menunggu validasi backend Dolphin.");
        }
        if (!backendOk) {
            return new Compatibility(Status.BACKEND_REJECTED,
                    "Backend Dolphin tidak dapat membuka file ini (mungkin rusak atau terpotong).");
        }
        String region = BlackOpsCatalog.regionName(id);
        if (header.nkit) {
            return new Compatibility(Status.READY_WITH_WARNING,
                    "Black Ops Wii " + region + " dalam format NKit. Dapat dijalankan, tetapi NKit diketahui "
                            + "bisa menimbulkan masalah; disarankan dump ISO/RVZ asli.");
        }
        return new Compatibility(Status.READY,
                "Black Ops Wii " + region + " dikenali backend Dolphin. Performa di perangkat ini belum diverifikasi.");
    }
}
