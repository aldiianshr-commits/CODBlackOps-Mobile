package com.codblackops.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.codblackops.mobile.core.save.BackupPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BackupPolicyTest {
    @Test
    public void namingAndRotation() {
        String n = BackupPolicy.fileName("SC7E52", 0L, "Auto");
        assertEquals("SC7E52_19700101-000000_auto.zip", n);
        BackupPolicy.Entry e = BackupPolicy.parse(n);
        assertNotNull(e);
        assertEquals("auto", e.tag);
        assertEquals(0L, e.timeMs);
        assertNull(BackupPolicy.parse("random.zip"));

        List<String> names = new ArrayList<>();
        for (int i = 0; i < 5; i++) names.add(BackupPolicy.fileName("SC7E52", i * 1000L, null));
        names.add(BackupPolicy.fileName("SC7P52", 99_000L, null));
        names.add("notes.txt");
        List<String> del = BackupPolicy.toDelete("SC7E52", names, 3);
        assertEquals(Arrays.asList(names.get(1), names.get(0)), del);
        assertEquals(5, BackupPolicy.listFor("SC7E52", names).size());
        assertEquals(names.get(4), BackupPolicy.listFor("SC7E52", names).get(0).fileName);
    }
}
