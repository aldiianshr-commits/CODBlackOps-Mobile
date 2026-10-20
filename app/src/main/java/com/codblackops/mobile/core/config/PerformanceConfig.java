package com.codblackops.mobile.core.config;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance profile (Battery Saver / Balanced / Performance / Compatibility) and advanced CPU
 * options. Every value maps to an actual Dolphin option or an Android API used by the launcher.
 */
public final class PerformanceConfig {

    public enum Profile {
        BATTERY_SAVER("Hemat Baterai",
                "Resolusi dibatasi 1x, tanpa MSAA, shader asinkron, render thread tunggal, mode "
                        + "kinerja berkelanjutan Android (mengurangi throttling panas)."),
        BALANCED("Seimbang",
                "Shader asinkron (ubershader), backend multithreading, dual core, hack EFB aman. "
                        + "Disarankan untuk Xiaomi Pad 6."),
        PERFORMANCE("Performa",
                "Shader asinkron tanpa menunggu (bisa ada objek yang muncul terlambat), V-Sync mati, "
                        + "semua hack kecepatan aktif."),
        COMPATIBILITY("Kompatibilitas",
                "Sync GPU, shader sinkron dengan ubershader, akses EFB dari CPU aktif. Paling akurat, "
                        + "paling lambat. Gunakan jika ada glitch grafis atau crash.");

        public final String label;
        public final String description;

        Profile(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** Dolphin PowerPC::CPUCore values. */
    public enum CpuCore {
        JIT_ARM64("JIT ARM64 (rekompilasi dinamis)", 4),
        CACHED_INTERPRETER("Cached Interpreter (sangat lambat, debug)", 5);

        public final String label;
        public final int dolphinValue;

        CpuCore(String label, int dolphinValue) {
            this.label = label;
            this.dolphinValue = dolphinValue;
        }
    }

    public Profile profile = Profile.BALANCED;
    public CpuCore cpuCore = CpuCore.JIT_ARM64;
    /** Emulated CPU clock in percent, 100 = unchanged. */
    public int cpuClockPercent = 100;
    /** Lower internal resolution automatically when Android reports severe thermal status. */
    public boolean thermalGuard = true;
    /** Warn before launch when available RAM is low. */
    public boolean memoryGuard = true;

    public static PerformanceConfig defaults() {
        return new PerformanceConfig();
    }

    public PerformanceConfig copy() {
        return decode(encode());
    }

    public boolean useSustainedPerformanceMode() {
        return profile == Profile.BATTERY_SAVER || profile == Profile.BALANCED;
    }

    /** Returns a graphics config with this profile's caps applied (does not mutate input). */
    public GraphicsConfig capGraphics(GraphicsConfig in) {
        GraphicsConfig g = in.copy();
        if (profile == Profile.BATTERY_SAVER) {
            g.resolution = GraphicsConfig.Resolution.P480;
            if (g.antiAliasing == GraphicsConfig.AntiAliasing.MSAA2
                    || g.antiAliasing == GraphicsConfig.AntiAliasing.MSAA4) {
                g.antiAliasing = GraphicsConfig.AntiAliasing.OFF;
            }
            if (g.anisotropy.ordinal() > GraphicsConfig.Anisotropy.X2.ordinal()) {
                g.anisotropy = GraphicsConfig.Anisotropy.X2;
            }
            g.vsync = true;
        } else if (profile == Profile.PERFORMANCE) {
            g.vsync = false;
        }
        return g;
    }

    public List<ConfigWrite> toDolphinWrites() {
        List<ConfigWrite> w = new ArrayList<>();
        final String M = ConfigWrite.FILE_MAIN;
        final String G = ConfigWrite.FILE_GFX;
        w.add(ConfigWrite.ofInt(M, "Core", "CPUCore", cpuCore.dolphinValue));
        w.add(ConfigWrite.ofBool(M, "Core", "Fastmem", true));
        w.add(ConfigWrite.ofBool(M, "Core", "MMU", false));
        w.add(ConfigWrite.ofBool(M, "Core", "DSPHLE", true));
        int clamped = Math.max(50, Math.min(150, cpuClockPercent));
        w.add(ConfigWrite.ofBool(M, "Core", "OverclockEnable", clamped != 100));
        w.add(ConfigWrite.ofFloat(M, "Core", "Overclock", clamped / 100f));

        boolean cpuThread = true;
        boolean syncGpu = false;
        int shaderMode; // ShaderCompilationMode enum
        boolean waitForShaders = false;
        boolean backendMultithreading = true;
        boolean efbAccess = false;
        boolean efbToTexture = true;
        boolean deferEfbCopies = true;
        boolean fastDepth = true;
        boolean fastTextureSampling = true;

        switch (profile) {
            case BATTERY_SAVER:
                shaderMode = 2; // AsynchronousUberShaders
                backendMultithreading = false;
                break;
            case PERFORMANCE:
                shaderMode = 3; // AsynchronousSkipRendering
                break;
            case COMPATIBILITY:
                shaderMode = 1; // SynchronousUberShaders
                waitForShaders = true;
                syncGpu = true;
                efbAccess = true;
                efbToTexture = false;
                deferEfbCopies = false;
                fastDepth = false;
                fastTextureSampling = false;
                break;
            case BALANCED:
            default:
                shaderMode = 2;
                break;
        }
        w.add(ConfigWrite.ofBool(M, "Core", "CPUThread", cpuThread));
        w.add(ConfigWrite.ofBool(M, "Core", "SyncGPU", syncGpu));
        w.add(ConfigWrite.ofInt(G, "Settings", "ShaderCompilationMode", shaderMode));
        w.add(ConfigWrite.ofBool(G, "Settings", "WaitForShadersBeforeStarting", waitForShaders));
        w.add(ConfigWrite.ofBool(G, "Settings", "BackendMultithreading", backendMultithreading));
        w.add(ConfigWrite.ofBool(G, "Hacks", "EFBAccessEnable", efbAccess));
        w.add(ConfigWrite.ofBool(G, "Hacks", "EFBToTextureEnable", efbToTexture));
        w.add(ConfigWrite.ofBool(G, "Hacks", "DeferEFBCopies", deferEfbCopies));
        w.add(ConfigWrite.ofBool(G, "Settings", "FastDepthCalc", fastDepth));
        w.add(ConfigWrite.ofBool(G, "Hacks", "FastTextureSampling", fastTextureSampling));
        w.add(ConfigWrite.ofBool(G, "Hacks", "SkipDuplicateXFBs", true));
        return w;
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("profile", profile.name());
        m.put("cpuCore", cpuCore.name());
        m.put("cpuClock", Integer.toString(cpuClockPercent));
        m.put("thermalGuard", Boolean.toString(thermalGuard));
        m.put("memoryGuard", Boolean.toString(memoryGuard));
        return KeyValueCodec.encode(m);
    }

    public static PerformanceConfig decode(String text) {
        PerformanceConfig p = new PerformanceConfig();
        Map<String, String> m = KeyValueCodec.decode(text);
        p.profile = KeyValueCodec.getEnum(m, "profile", Profile.class, p.profile);
        p.cpuCore = KeyValueCodec.getEnum(m, "cpuCore", CpuCore.class, p.cpuCore);
        p.cpuClockPercent = Math.max(50, Math.min(150, KeyValueCodec.getInt(m, "cpuClock", p.cpuClockPercent)));
        p.thermalGuard = KeyValueCodec.getBool(m, "thermalGuard", p.thermalGuard);
        p.memoryGuard = KeyValueCodec.getBool(m, "memoryGuard", p.memoryGuard);
        return p;
    }
}
