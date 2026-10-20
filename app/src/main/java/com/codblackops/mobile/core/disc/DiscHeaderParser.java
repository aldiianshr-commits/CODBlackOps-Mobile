package com.codblackops.mobile.core.disc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Lightweight, dependency-free reader for the disc header of GameCube/Wii images.
 *
 * It is used for a fast first pass while scanning folders (no emulator core needed). The final,
 * authoritative validation is done by Dolphin's own DiscIO through GameFile.parse() on the device.
 *
 * Supported containers: raw ISO/GCM (incl. NKit ISO), WBFS, WIA, RVZ, CISO, GCZ.
 */
public final class DiscHeaderParser {
    public static final int WII_MAGIC = 0x5D1C9EA3;
    public static final int GAMECUBE_MAGIC = 0xC2339F3D;

    private static final int DISC_HEADER_BYTES = 0x60; // id, magic words and title
    private static final int TITLE_OFFSET = 0x20;
    private static final int TITLE_LENGTH = 0x40;

    private DiscHeaderParser() {
    }

    public static DiscInfo parse(ByteSource source, String fileName) {
        try {
            long size = source.size();
            if (size >= 0 && size < 0x20) {
                return DiscInfo.error(DiscFormat.UNKNOWN, "File terlalu kecil untuk image disc");
            }
            byte[] magic = new byte[8];
            int n = source.read(0, magic, 0, magic.length);
            if (n < 4) {
                return DiscInfo.error(DiscFormat.UNKNOWN, "Tidak bisa membaca header file");
            }

            if (startsWith(magic, "WBFS")) return parseWbfs(source);
            if (startsWith(magic, "WIA\u0001")) return parseWiaFamily(source, DiscFormat.WIA);
            if (startsWith(magic, "RVZ\u0001")) return parseWiaFamily(source, DiscFormat.RVZ);
            if (startsWith(magic, "CISO")) return parseCiso(source);
            if ((magic[0] & 0xFF) == 0x01 && (magic[1] & 0xFF) == 0xC0
                    && (magic[2] & 0xFF) == 0x0B && (magic[3] & 0xFF) == 0xB1) {
                return parseGcz(source);
            }
            return parseRawIso(source, fileName);
        } catch (IOException e) {
            return DiscInfo.error(DiscFormat.UNKNOWN, "Gagal membaca file: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- raw ISO / NKit
    private static DiscInfo parseRawIso(ByteSource source, String fileName) throws IOException {
        byte[] header = new byte[DISC_HEADER_BYTES];
        int n = source.read(0, header, 0, header.length);
        if (n < DISC_HEADER_BYTES) {
            return DiscInfo.error(DiscFormat.UNKNOWN, "Header disc tidak lengkap");
        }
        boolean nkit = false;
        byte[] nkitMarker = new byte[4];
        if (source.read(0x200, nkitMarker, 0, 4) == 4 && startsWith(nkitMarker, "NKIT")) {
            nkit = true;
        }
        DiscInfo info = fromDiscHeader(header, 0, DiscFormat.ISO, nkit);
        if (info.platform == DiscPlatform.UNKNOWN) {
            String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".iso") || lower.endsWith(".gcm")) {
                return DiscInfo.error(DiscFormat.ISO,
                        "Bukan image disc Wii/GameCube (magic word tidak ditemukan)");
            }
            return DiscInfo.error(DiscFormat.UNKNOWN, "Format file tidak dikenali");
        }
        return info;
    }

    // ---------------------------------------------------------------- WBFS
    private static DiscInfo parseWbfs(ByteSource source) throws IOException {
        byte[] head = new byte[12];
        source.readFully(0, head, 0, head.length);
        int hdSectorShift = head[8] & 0xFF;
        if (hdSectorShift < 9 || hdSectorShift > 16) {
            return DiscInfo.error(DiscFormat.WBFS, "Header WBFS tidak valid (sector shift " + hdSectorShift + ")");
        }
        long discInfoOffset = 1L << hdSectorShift;
        byte[] header = new byte[DISC_HEADER_BYTES];
        int n = source.read(discInfoOffset, header, 0, header.length);
        if (n < DISC_HEADER_BYTES) {
            return DiscInfo.error(DiscFormat.WBFS, "Data disc WBFS terpotong");
        }
        DiscInfo info = fromDiscHeader(header, 0, DiscFormat.WBFS, false);
        if (info.platform != DiscPlatform.WII) {
            return DiscInfo.error(DiscFormat.WBFS, "WBFS tidak berisi disc Wii yang valid");
        }
        return info;
    }

    // ---------------------------------------------------------------- WIA / RVZ
    static final int WIA_HEADER1_SIZE = 0x48;

    private static DiscInfo parseWiaFamily(ByteSource source, DiscFormat format) throws IOException {
        byte[] h2 = new byte[0x10 + 0x80];
        int n = source.read(WIA_HEADER1_SIZE, h2, 0, h2.length);
        if (n < h2.length) {
            return DiscInfo.error(format, "Header " + format.label + " terpotong");
        }
        int discType = readU32BE(h2, 0);
        DiscInfo info = fromDiscHeader(h2, 0x10, format, false);
        if (info.platform == DiscPlatform.UNKNOWN) {
            return DiscInfo.error(format, "Header disc di dalam " + format.label + " tidak valid");
        }
        if ((discType == 2 && info.platform != DiscPlatform.WII)
                || (discType == 1 && info.platform != DiscPlatform.GAMECUBE)) {
            return DiscInfo.error(format, "Tipe disc " + format.label + " tidak konsisten");
        }
        return info;
    }

    // ---------------------------------------------------------------- CISO
    private static final int CISO_HEADER_SIZE = 0x8000;

    private static DiscInfo parseCiso(ByteSource source) throws IOException {
        byte[] head = new byte[9];
        source.readFully(0, head, 0, head.length);
        int blockSize = readU32LE(head, 4);
        if (blockSize <= 0) {
            return DiscInfo.error(DiscFormat.CISO, "Ukuran blok CISO tidak valid");
        }
        boolean firstBlockPresent = head[8] != 0;
        if (!firstBlockPresent) {
            return DiscInfo.error(DiscFormat.CISO, "Blok pertama CISO kosong");
        }
        byte[] header = new byte[DISC_HEADER_BYTES];
        source.readFully(CISO_HEADER_SIZE, header, 0, header.length);
        DiscInfo info = fromDiscHeader(header, 0, DiscFormat.CISO, false);
        if (info.platform == DiscPlatform.UNKNOWN) {
            return DiscInfo.error(DiscFormat.CISO, "Header disc di dalam CISO tidak valid");
        }
        return info;
    }

    // ---------------------------------------------------------------- GCZ
    private static final int GCZ_HEADER_SIZE = 32;

    private static DiscInfo parseGcz(ByteSource source) throws IOException {
        byte[] head = new byte[GCZ_HEADER_SIZE];
        source.readFully(0, head, 0, head.length);
        long compressedSize = readU64LE(head, 8);
        int blockSize = readU32LE(head, 24);
        int numBlocks = readU32LE(head, 28);
        if (numBlocks <= 0 || blockSize <= 0 || blockSize > 64 * 1024 * 1024) {
            return DiscInfo.error(DiscFormat.GCZ, "Header GCZ tidak valid");
        }
        byte[] pointers = new byte[16];
        int pointerCount = numBlocks > 1 ? 2 : 1;
        source.readFully(GCZ_HEADER_SIZE, pointers, 0, 8 * pointerCount);
        long ptr0 = readU64LE(pointers, 0);
        long ptr1 = pointerCount > 1 ? readU64LE(pointers, 8) & Long.MAX_VALUE : compressedSize;
        boolean uncompressed = (ptr0 & Long.MIN_VALUE) != 0;
        long offset0 = ptr0 & Long.MAX_VALUE;
        long dataStart = GCZ_HEADER_SIZE + 8L * numBlocks + 4L * numBlocks;
        long compLen = ptr1 - offset0;
        if (compLen <= 0 || compLen > blockSize + 1024L) {
            return DiscInfo.error(DiscFormat.GCZ, "Tabel blok GCZ tidak valid");
        }
        byte[] block = new byte[(int) compLen];
        source.readFully(dataStart + offset0, block, 0, block.length);
        byte[] header;
        if (uncompressed) {
            header = block;
        } else {
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(block);
                header = new byte[DISC_HEADER_BYTES];
                int got = 0;
                while (got < header.length && !inflater.finished()) {
                    int r = inflater.inflate(header, got, header.length - got);
                    if (r == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                    got += r;
                }
                if (got < DISC_HEADER_BYTES) {
                    return DiscInfo.error(DiscFormat.GCZ, "Blok pertama GCZ tidak bisa didekompresi");
                }
            } catch (DataFormatException e) {
                return DiscInfo.error(DiscFormat.GCZ, "Data GCZ rusak: " + e.getMessage());
            } finally {
                inflater.end();
            }
        }
        if (header.length < DISC_HEADER_BYTES) {
            return DiscInfo.error(DiscFormat.GCZ, "Blok pertama GCZ terlalu kecil");
        }
        DiscInfo info = fromDiscHeader(header, 0, DiscFormat.GCZ, false);
        if (info.platform == DiscPlatform.UNKNOWN) {
            return DiscInfo.error(DiscFormat.GCZ, "Header disc di dalam GCZ tidak valid");
        }
        return info;
    }

    // ---------------------------------------------------------------- helpers
    static DiscInfo fromDiscHeader(byte[] h, int base, DiscFormat format, boolean nkit) {
        int wiiMagic = readU32BE(h, base + 0x18);
        int gcMagic = readU32BE(h, base + 0x1C);
        DiscPlatform platform;
        if (wiiMagic == WII_MAGIC) {
            platform = DiscPlatform.WII;
        } else if (gcMagic == GAMECUBE_MAGIC) {
            platform = DiscPlatform.GAMECUBE;
        } else {
            platform = DiscPlatform.UNKNOWN;
        }
        String id = new String(h, base, 6, StandardCharsets.US_ASCII);
        if (!isValidGameId(id)) {
            return DiscInfo.error(format, "ID game tidak valid");
        }
        int discNumber = h[base + 6] & 0xFF;
        int revision = h[base + 7] & 0xFF;
        String title = readCString(h, base + TITLE_OFFSET, TITLE_LENGTH);
        return new DiscInfo(format, platform, id, title, discNumber, revision, nkit, null);
    }

    public static boolean isValidGameId(String id) {
        if (id == null || id.length() != 6) return false;
        for (int i = 0; i < 6; i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!ok) return false;
        }
        return true;
    }

    static String readCString(byte[] b, int off, int max) {
        int end = off;
        int limit = Math.min(b.length, off + max);
        while (end < limit && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.ISO_8859_1).trim();
    }

    static boolean startsWith(byte[] b, String ascii) {
        if (b.length < ascii.length()) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if ((b[i] & 0xFF) != ascii.charAt(i)) return false;
        }
        return true;
    }

    static int readU32BE(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    static int readU32LE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static long readU64LE(byte[] b, int o) {
        long lo = readU32LE(b, o) & 0xFFFFFFFFL;
        long hi = readU32LE(b, o + 4) & 0xFFFFFFFFL;
        return lo | (hi << 32);
    }
}
