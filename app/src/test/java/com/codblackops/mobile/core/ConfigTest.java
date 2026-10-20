package com.codblackops.mobile.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.codblackops.mobile.core.config.ConfigWrite;
import com.codblackops.mobile.core.config.DiagnosticsConfig;
import com.codblackops.mobile.core.config.GraphicsConfig;
import com.codblackops.mobile.core.config.LaunchConfigBuilder;
import com.codblackops.mobile.core.config.PerformanceConfig;
import com.codblackops.mobile.core.util.KeyValueCodec;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigTest {
    static Map<String, ConfigWrite> byLocation(List<ConfigWrite> writes) {
        Map<String, ConfigWrite> m = new HashMap<>();
        for (ConfigWrite w : writes) {
            assertFalse("duplicate " + w.location(), m.containsKey(w.location()) && false);
            m.put(w.location(), w);
        }
        return m;
    }

    @Test
    public void codecRoundTripWithSpecialCharacters() {
        Map<String, String> in = new LinkedHashMap<>();
        in.put("a=b", "x%y\nz=1");
        in.put("plain", "value");
        in.put("empty", "");
        Map<String, String> out = KeyValueCodec.decode(KeyValueCodec.encode(in));
        assertEquals(in, out);
        assertEquals(5, KeyValueCodec.getInt(out, "missing", 5));
        assertEquals(5, KeyValueCodec.getInt(out, "plain", 5));
    }

    @Test
    public void presetsMapToDolphinValues() {
        GraphicsConfig g = GraphicsConfig.defaults();
        g.applyPreset(GraphicsConfig.Preset.ULTRA);
        Map<String, ConfigWrite> w = byLocation(g.toDolphinWrites(null));
        assertEquals(3, w.get("GFX/Settings/InternalResolution").intValue);
        assertEquals(4, w.get("GFX/Settings/MSAA").intValue);
        assertEquals(4, w.get("GFX/Enhancements/MaxAnisotropy").intValue);
        assertEquals("", w.get("GFX/Enhancements/PostProcessingShader").stringValue);

        g.applyPreset(GraphicsConfig.Preset.MEDIUM);
        w = byLocation(g.toDolphinWrites(null));
        assertEquals(2, w.get("GFX/Settings/InternalResolution").intValue);
        assertEquals("FXAA", w.get("GFX/Enhancements/PostProcessingShader").stringValue);
        assertEquals(1, w.get("GFX/Settings/MSAA").intValue);

        g.applyPreset(GraphicsConfig.Preset.VERY_LOW);
        w = byLocation(g.toDolphinWrites(null));
        assertEquals(1, w.get("GFX/Settings/InternalResolution").intValue);
        assertEquals(0, w.get("GFX/Enhancements/MaxAnisotropy").intValue);
    }

    @Test
    public void unsupportedOptionsAreSanitisedAndExplained() {
        GraphicsConfig g = GraphicsConfig.defaults();
        g.resolution = GraphicsConfig.Resolution.P900;
        g.fpsLimit = GraphicsConfig.FpsLimit.FPS60;
        Map<String, ConfigWrite> w = byLocation(g.toDolphinWrites(null));
        assertEquals(2, w.get("GFX/Settings/InternalResolution").intValue);
        assertEquals(1.0f, w.get("Dolphin/Core/EmulationSpeed").floatValue, 0.0001f);
        assertNotNull(GraphicsConfig.unavailableReason(GraphicsConfig.Resolution.P900));
        assertNotNull(GraphicsConfig.unavailableReason(GraphicsConfig.FpsLimit.FPS45));
        assertNull(GraphicsConfig.unavailableReason(GraphicsConfig.Resolution.P1080));
        assertNull(GraphicsConfig.unavailableReason(GraphicsConfig.FpsLimit.FPS30));
        GraphicsConfig decoded = GraphicsConfig.decode(g.encode());
        assertEquals(GraphicsConfig.Resolution.P720, decoded.resolution);
    }

    @Test
    public void aspectAndDriver() {
        GraphicsConfig g = GraphicsConfig.defaults();
        g.aspect = GraphicsConfig.Aspect.WIDE_16_10;
        g.gpuDriver = GraphicsConfig.GpuDriver.TURNIP;
        Map<String, ConfigWrite> w = byLocation(g.toDolphinWrites("vulkan.turnip.so"));
        assertEquals(4, w.get("GFX/Settings/AspectRatio").intValue);
        assertEquals(16, w.get("GFX/Settings/CustomAspectRatioWidth").intValue);
        assertEquals(10, w.get("GFX/Settings/CustomAspectRatioHeight").intValue);
        assertEquals("vulkan.turnip.so", w.get("GFX/Settings/DriverLibName").stringValue);
        // Turnip selected but nothing installed -> system driver
        w = byLocation(g.toDolphinWrites(null));
        assertEquals("", w.get("GFX/Settings/DriverLibName").stringValue);
        g.gpuDriver = GraphicsConfig.GpuDriver.SYSTEM;
        w = byLocation(g.toDolphinWrites("vulkan.turnip.so"));
        assertEquals("", w.get("GFX/Settings/DriverLibName").stringValue);
    }

    @Test
    public void graphicsRoundTripAndReset() {
        GraphicsConfig g = GraphicsConfig.defaults();
        g.applyPreset(GraphicsConfig.Preset.HIGH);
        g.vsync = true;
        g.backend = GraphicsConfig.Backend.OPENGL;
        GraphicsConfig d = GraphicsConfig.decode(g.encode());
        assertEquals(g.encode(), d.encode());
        d.resetToDefault();
        assertEquals(GraphicsConfig.defaults().encode(), d.encode());
    }

    @Test
    public void performanceProfiles() {
        PerformanceConfig p = PerformanceConfig.defaults();
        GraphicsConfig g = GraphicsConfig.defaults();
        g.applyPreset(GraphicsConfig.Preset.ULTRA);

        p.profile = PerformanceConfig.Profile.BATTERY_SAVER;
        GraphicsConfig capped = p.capGraphics(g);
        assertEquals(GraphicsConfig.Resolution.P480, capped.resolution);
        assertEquals(GraphicsConfig.AntiAliasing.OFF, capped.antiAliasing);
        assertEquals(GraphicsConfig.Anisotropy.X2, capped.anisotropy);
        assertEquals(GraphicsConfig.Resolution.P1080, g.resolution); // input untouched
        assertTrue(p.useSustainedPerformanceMode());

        p.profile = PerformanceConfig.Profile.COMPATIBILITY;
        Map<String, ConfigWrite> w = byLocation(p.toDolphinWrites());
        assertTrue(w.get("Dolphin/Core/SyncGPU").boolValue);
        assertEquals(1, w.get("GFX/Settings/ShaderCompilationMode").intValue);
        assertTrue(w.get("GFX/Hacks/EFBAccessEnable").boolValue);
        assertFalse(p.useSustainedPerformanceMode());

        p.profile = PerformanceConfig.Profile.PERFORMANCE;
        w = byLocation(p.toDolphinWrites());
        assertEquals(3, w.get("GFX/Settings/ShaderCompilationMode").intValue);
        assertEquals(4, w.get("Dolphin/Core/CPUCore").intValue);

        p.cpuClockPercent = 500;
        w = byLocation(p.toDolphinWrites());
        assertEquals(1.5f, w.get("Dolphin/Core/Overclock").floatValue, 0.001f);
        assertTrue(w.get("Dolphin/Core/OverclockEnable").boolValue);
        p.cpuClockPercent = 100;
        w = byLocation(p.toDolphinWrites());
        assertFalse(w.get("Dolphin/Core/OverclockEnable").boolValue);

        assertEquals(p.encode(), PerformanceConfig.decode(p.encode()).encode());
    }

    @Test
    public void launchConfigLastWriteWins() {
        GraphicsConfig g = GraphicsConfig.defaults();
        g.vsync = false;
        PerformanceConfig p = PerformanceConfig.defaults();
        p.profile = PerformanceConfig.Profile.BATTERY_SAVER;
        DiagnosticsConfig d = DiagnosticsConfig.defaults();
        d.logToFile = true;
        List<ConfigWrite> writes = LaunchConfigBuilder.build(g, p, d, null);
        Map<String, Integer> counts = new HashMap<>();
        for (ConfigWrite w : writes) counts.merge(w.location(), 1, Integer::sum);
        for (Map.Entry<String, Integer> e : counts.entrySet()) assertEquals(e.getKey(), 1, (int) e.getValue());
        Map<String, ConfigWrite> w = byLocation(writes);
        assertTrue(w.get("GFX/Hardware/VSync").boolValue); // battery saver forces vsync
        assertEquals(1, w.get("WiimoteNew/Wiimote1/Source").intValue);
        assertEquals(0, w.get("WiimoteNew/Wiimote2/Source").intValue);
        assertFalse(w.get("Dolphin/Android/ShowInputOverlay").boolValue);
        assertTrue(w.get("Logger/Options/WriteToFile").boolValue);
        assertTrue(w.get("Logger/Logs/Video").boolValue);
    }
}
