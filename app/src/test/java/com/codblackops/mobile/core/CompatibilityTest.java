package com.codblackops.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.codblackops.mobile.core.disc.BlackOpsCatalog;
import com.codblackops.mobile.core.disc.ByteSource;
import com.codblackops.mobile.core.disc.Compatibility;
import com.codblackops.mobile.core.disc.DiscHeaderParser;
import com.codblackops.mobile.core.disc.DiscInfo;

import org.junit.Test;

public class CompatibilityTest {
    static DiscInfo wii(String id) {
        return DiscHeaderParser.parse(new ByteSource.ArraySource(DiscHeaderParserTest.wiiHeader(id, "t")), "a.iso");
    }

    @Test
    public void blackOpsReadyOnlyAfterBackendCheck() {
        DiscInfo bo = wii("SC7E52");
        assertEquals(Compatibility.Status.PENDING_BACKEND_CHECK, Compatibility.evaluate(bo, null, null).status);
        assertFalse(Compatibility.evaluate(bo, null, null).canLaunch());
        Compatibility ok = Compatibility.evaluate(bo, true, "SC7E52");
        assertEquals(Compatibility.Status.READY, ok.status);
        assertTrue(ok.canLaunch());
        assertEquals(Compatibility.Status.BACKEND_REJECTED, Compatibility.evaluate(bo, false, null).status);
    }

    @Test
    public void otherGamesAreNotLaunchable() {
        Compatibility c = Compatibility.evaluate(wii("RMCE01"), true, "RMCE01");
        assertEquals(Compatibility.Status.WRONG_GAME, c.status);
        assertFalse(c.canLaunch());
        DiscInfo gc = DiscHeaderParser.parse(new ByteSource.ArraySource(DiscHeaderParserTest.gcHeader("GALE01", "m")), "m.iso");
        assertEquals(Compatibility.Status.WRONG_GAME, Compatibility.evaluate(gc, true, "GALE01").status);
    }

    @Test
    public void unknownBlackOpsVariantFlagged() {
        Compatibility c = Compatibility.evaluate(wii("SC7J52"), true, "SC7J52");
        assertEquals(Compatibility.Status.WRONG_GAME, c.status);
        assertTrue(c.message.contains("mirip Black Ops"));
    }

    @Test
    public void invalidHeader() {
        DiscInfo bad = DiscInfo.error(com.codblackops.mobile.core.disc.DiscFormat.ISO, "rusak");
        assertEquals(Compatibility.Status.INVALID, Compatibility.evaluate(bad, null, null).status);
    }

    @Test
    public void catalogAndSavePath() {
        assertEquals(7, BlackOpsCatalog.knownReleases().size());
        assertTrue(BlackOpsCatalog.isBlackOpsWii("SC7P52"));
        assertEquals("53433745", BlackOpsCatalog.titleIdLowHex("SC7E52"));
        assertEquals("Wii/title/00010000/53433750/data", BlackOpsCatalog.nandSaveDataPath("SC7P52"));
        assertEquals("NTSC-U (Amerika)", BlackOpsCatalog.regionName("SC7E52"));
        assertEquals("PAL", BlackOpsCatalog.regionName("RMCP01"));
    }
}
