package com.codblackops.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.codblackops.mobile.core.disc.ByteSource;
import com.codblackops.mobile.core.disc.DiscFormat;
import com.codblackops.mobile.core.disc.DiscHeaderParser;
import com.codblackops.mobile.core.disc.DiscInfo;
import com.codblackops.mobile.core.disc.DiscPlatform;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;

public class DiscHeaderParserTest {

    static byte[] wiiHeader(String id, String title) {
        byte[] h = new byte[0x400];
        byte[] idb = id.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(idb, 0, h, 0, 6);
        h[7] = 1; // revision
        putBE(h, 0x18, 0x5D1C9EA3);
        byte[] t = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, h, 0x20, t.length);
        return h;
    }

    static byte[] gcHeader(String id, String title) {
        byte[] h = new byte[0x400];
        System.arraycopy(id.getBytes(StandardCharsets.US_ASCII), 0, h, 0, 6);
        putBE(h, 0x1C, 0xC2339F3D);
        byte[] t = title.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, h, 0x20, t.length);
        return h;
    }

    static void putBE(byte[] b, int o, int v) {
        b[o] = (byte) (v >>> 24);
        b[o + 1] = (byte) (v >>> 16);
        b[o + 2] = (byte) (v >>> 8);
        b[o + 3] = (byte) v;
    }

    static void putLE(byte[] b, int o, long v, int bytes) {
        for (int i = 0; i < bytes; i++) b[o + i] = (byte) (v >>> (8 * i));
    }

    static DiscInfo parse(byte[] data, String name) {
        return DiscHeaderParser.parse(new ByteSource.ArraySource(data), name);
    }

    @Test
    public void rawWiiIso() {
        DiscInfo i = parse(wiiHeader("SC7E52", "Call of Duty: Black Ops"), "bo.iso");
        assertTrue(i.toString(), i.isValid());
        assertEquals(DiscFormat.ISO, i.format);
        assertEquals(DiscPlatform.WII, i.platform);
        assertEquals("SC7E52", i.gameId);
        assertEquals("Call of Duty: Black Ops", i.internalTitle);
        assertEquals(1, i.revision);
        assertFalse(i.nkit);
    }

    @Test
    public void rawGameCubeIso() {
        DiscInfo i = parse(gcHeader("GALE01", "Melee"), "x.iso");
        assertTrue(i.isValid());
        assertEquals(DiscPlatform.GAMECUBE, i.platform);
    }

    @Test
    public void nkitMarkerDetected() {
        byte[] h = wiiHeader("SC7P52", "BO");
        byte[] n = "NKIT".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(n, 0, h, 0x200, 4);
        DiscInfo i = parse(h, "bo.nkit.iso");
        assertTrue(i.isValid());
        assertTrue(i.nkit);
    }

    @Test
    public void wbfs() {
        byte[] f = new byte[0x200 + 0x400];
        f[0] = 'W'; f[1] = 'B'; f[2] = 'F'; f[3] = 'S';
        f[8] = 9; // 512-byte hd sectors
        f[9] = 21;
        System.arraycopy(wiiHeader("SC7D52", "Black Ops"), 0, f, 0x200, 0x100);
        DiscInfo i = parse(f, "game.wbfs");
        assertTrue(i.toString(), i.isValid());
        assertEquals(DiscFormat.WBFS, i.format);
        assertEquals("SC7D52", i.gameId);
    }

    @Test
    public void wbfsWithBadShiftIsRejected() {
        byte[] f = new byte[0x800];
        f[0] = 'W'; f[1] = 'B'; f[2] = 'F'; f[3] = 'S';
        f[8] = 3;
        assertFalse(parse(f, "bad.wbfs").isValid());
    }

    static byte[] wiaFamily(String magic, int discType, byte[] discHeader) {
        byte[] f = new byte[0x48 + 0x10 + 0x80 + 16];
        byte[] m = magic.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(m, 0, f, 0, 4);
        putBE(f, 0x48, discType);
        System.arraycopy(discHeader, 0, f, 0x58, 0x80);
        return f;
    }

    @Test
    public void rvzAndWia() {
        DiscInfo rvz = parse(wiaFamily("RVZ\u0001", 2, wiiHeader("SC7E52", "BO")), "bo.rvz");
        assertTrue(rvz.toString(), rvz.isValid());
        assertEquals(DiscFormat.RVZ, rvz.format);
        assertEquals("SC7E52", rvz.gameId);
        DiscInfo wia = parse(wiaFamily("WIA\u0001", 2, wiiHeader("SC7F52", "BO")), "bo.wia");
        assertTrue(wia.isValid());
        assertEquals(DiscFormat.WIA, wia.format);
    }

    @Test
    public void rvzInconsistentDiscTypeRejected() {
        DiscInfo rvz = parse(wiaFamily("RVZ\u0001", 1, wiiHeader("SC7E52", "BO")), "bo.rvz");
        assertFalse(rvz.isValid());
    }

    @Test
    public void ciso() {
        byte[] f = new byte[0x8000 + 0x400];
        f[0] = 'C'; f[1] = 'I'; f[2] = 'S'; f[3] = 'O';
        putLE(f, 4, 0x200000, 4);
        f[8] = 1;
        System.arraycopy(wiiHeader("SC7I52", "BO"), 0, f, 0x8000, 0x400);
        DiscInfo i = parse(f, "bo.ciso");
        assertTrue(i.toString(), i.isValid());
        assertEquals(DiscFormat.CISO, i.format);
        assertEquals("SC7I52", i.gameId);
    }

    static byte[] gcz(byte[] firstBlockRaw, boolean compress) throws Exception {
        int numBlocks = 2;
        byte[] block0;
        if (compress) {
            Deflater d = new Deflater();
            d.setInput(firstBlockRaw);
            d.finish();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!d.finished()) {
                int n = d.deflate(buf);
                bos.write(buf, 0, n);
            }
            d.end();
            block0 = bos.toByteArray();
        } else {
            block0 = firstBlockRaw;
        }
        byte[] block1 = new byte[16];
        int headerSize = 32 + 8 * numBlocks + 4 * numBlocks;
        byte[] f = new byte[headerSize + block0.length + block1.length];
        f[0] = 0x01; f[1] = (byte) 0xC0; f[2] = 0x0B; f[3] = (byte) 0xB1;
        putLE(f, 4, 1, 4);
        putLE(f, 8, block0.length + block1.length, 8);
        putLE(f, 16, 0x8000L * numBlocks, 8);
        putLE(f, 24, 0x8000, 4);
        putLE(f, 28, numBlocks, 4);
        long p0 = compress ? 0L : (0L | Long.MIN_VALUE);
        putLE(f, 32, p0, 8);
        putLE(f, 40, block0.length, 8);
        System.arraycopy(block0, 0, f, headerSize, block0.length);
        return f;
    }

    @Test
    public void gczCompressedAndUncompressed() throws Exception {
        byte[] raw = new byte[0x8000];
        System.arraycopy(wiiHeader("SC7S52", "Black Ops"), 0, raw, 0, 0x400);
        DiscInfo c = parse(gcz(raw, true), "bo.gcz");
        assertTrue(c.toString(), c.isValid());
        assertEquals(DiscFormat.GCZ, c.format);
        assertEquals("SC7S52", c.gameId);
        DiscInfo u = parse(gcz(raw, false), "bo.gcz");
        assertTrue(u.toString(), u.isValid());
        assertEquals("SC7S52", u.gameId);
    }

    @Test
    public void garbageIsRejected() {
        byte[] f = new byte[0x1000];
        for (int i = 0; i < f.length; i++) f[i] = (byte) (i * 31);
        DiscInfo i = parse(f, "random.iso");
        assertFalse(i.isValid());
        assertNotNull(i.error);
    }

    @Test
    public void tooSmallIsRejected() {
        assertFalse(parse(new byte[10], "tiny.iso").isValid());
    }

    @Test
    public void invalidGameIdRejected() {
        assertFalse(parse(wiiHeader("sc7e52", "lowercase"), "x.iso").isValid());
        assertFalse(DiscHeaderParser.isValidGameId("SC7E5"));
        assertTrue(DiscHeaderParser.isValidGameId("SC7E52"));
    }

    @Test
    public void candidateFileNames() {
        assertTrue(DiscFormat.isCandidateFileName("Call of Duty.WBFS"));
        assertTrue(DiscFormat.isCandidateFileName("bo.rvz"));
        assertTrue(DiscFormat.isCandidateFileName("bo.nkit.iso"));
        assertFalse(DiscFormat.isCandidateFileName("._bo.iso"));
        assertFalse(DiscFormat.isCandidateFileName("save.zip"));
        assertFalse(DiscFormat.isCandidateFileName(null));
    }
}
