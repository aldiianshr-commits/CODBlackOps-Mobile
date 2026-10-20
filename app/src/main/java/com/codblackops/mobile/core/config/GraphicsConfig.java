package com.codblackops.mobile.core.config;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Graphics settings as presented in the launcher, plus the exact mapping onto Dolphin's
 * configuration keys. Options the backend cannot honour are modelled explicitly with a reason
 * (see {@link #unavailableReason}) instead of being silently accepted.
 */
public final class GraphicsConfig {

    /** Internal rendering resolution. Dolphin only supports integer multiples of 640x528. */
    public enum Resolution {
        P480(1, "480p", "1x native (640x528)"),
        P720(2, "720p", "2x native (1280x1056)"),
        P900(-1, "900p", "tidak tersedia"),
        P1080(3, "1080p", "3x native (1920x1584)");

        public final int dolphinScale;
        public final String label;
        public final String detail;

        Resolution(int dolphinScale, String label, String detail) {
            this.dolphinScale = dolphinScale;
            this.label = label;
            this.detail = detail;
        }

        public boolean isSupported() {
            return dolphinScale > 0;
        }
    }

    public enum FpsLimit {
        FPS30(30), FPS45(45), FPS60(60);
        public final int fps;

        FpsLimit(int fps) {
            this.fps = fps;
        }

        public boolean isSupported() {
            return this == FPS30;
        }
    }

    /** Requested texture quality levels. None maps to a real Dolphin option (see reason). */
    public enum TextureQuality { LOW, MEDIUM, HIGH }

    /** Requested shadow quality levels. None maps to a real Dolphin option (see reason). */
    public enum ShadowQuality { LOW, MEDIUM, HIGH }

    public enum AntiAliasing {
        OFF("Mati", 1, ""),
        FXAA("FXAA", 1, "FXAA"),
        MSAA2("MSAA 2x", 2, ""),
        MSAA4("MSAA 4x", 4, "");

        public final String label;
        public final int msaaSamples;
        public final String postProcessingShader;

        AntiAliasing(String label, int msaaSamples, String postProcessingShader) {
            this.label = label;
            this.msaaSamples = msaaSamples;
            this.postProcessingShader = postProcessingShader;
        }
    }

    public enum Anisotropy {
        OFF("Mati", 0), X2("2x", 1), X4("4x", 2), X8("8x", 3), X16("16x", 4);
        public final String label;
        /** Dolphin AnisotropicFilteringMode value (Force1x = 0 ... Force16x = 4). */
        public final int dolphinValue;

        Anisotropy(String label, int dolphinValue) {
            this.label = label;
            this.dolphinValue = dolphinValue;
        }
    }

    public enum Aspect {
        AUTO("Otomatis", 0), WIDE_16_9("16:9", 1), WIDE_16_10("16:10", 4);
        public final String label;
        /** Dolphin AspectMode: Auto = 0, ForceWide = 1, Custom = 4. */
        public final int dolphinMode;

        Aspect(String label, int dolphinMode) {
            this.label = label;
            this.dolphinMode = dolphinMode;
        }
    }

    public enum GpuDriver { SYSTEM, TURNIP }

    public enum Backend {
        VULKAN("Vulkan", "Vulkan"), OPENGL("OpenGL ES", "OGL");
        public final String label;
        public final String dolphinName;

        Backend(String label, String dolphinName) {
            this.label = label;
            this.dolphinName = dolphinName;
        }
    }

    public enum Preset { VERY_LOW, LOW, MEDIUM, HIGH, ULTRA, CUSTOM }

    public Resolution resolution = Resolution.P720;
    public FpsLimit fpsLimit = FpsLimit.FPS30;
    public AntiAliasing antiAliasing = AntiAliasing.FXAA;
    public Anisotropy anisotropy = Anisotropy.X4;
    public boolean vsync = false;
    public boolean shaderCache = true;
    public GpuDriver gpuDriver = GpuDriver.SYSTEM;
    public Aspect aspect = Aspect.AUTO;
    public boolean fullscreen = true;
    public boolean customTextures = false;
    public Backend backend = Backend.VULKAN;
    public boolean showFps = false;
    public Preset preset = Preset.MEDIUM;

    public static GraphicsConfig defaults() {
        GraphicsConfig c = new GraphicsConfig();
        c.applyPreset(Preset.MEDIUM);
        return c;
    }

    public GraphicsConfig copy() {
        return decode(encode());
    }

    /** Applies a preset. Only touches options that a preset controls. */
    public void applyPreset(Preset p) {
        switch (p) {
            case VERY_LOW:
                resolution = Resolution.P480;
                antiAliasing = AntiAliasing.OFF;
                anisotropy = Anisotropy.OFF;
                customTextures = false;
                break;
            case LOW:
                resolution = Resolution.P480;
                antiAliasing = AntiAliasing.OFF;
                anisotropy = Anisotropy.X2;
                customTextures = false;
                break;
            case MEDIUM:
                resolution = Resolution.P720;
                antiAliasing = AntiAliasing.FXAA;
                anisotropy = Anisotropy.X4;
                break;
            case HIGH:
                resolution = Resolution.P720;
                antiAliasing = AntiAliasing.MSAA2;
                anisotropy = Anisotropy.X8;
                break;
            case ULTRA:
                resolution = Resolution.P1080;
                antiAliasing = AntiAliasing.MSAA4;
                anisotropy = Anisotropy.X16;
                break;
            case CUSTOM:
            default:
                break;
        }
        preset = p;
    }

    /** Resets everything, including non-preset options, to the launcher defaults. */
    public void resetToDefault() {
        GraphicsConfig d = defaults();
        resolution = d.resolution;
        fpsLimit = d.fpsLimit;
        antiAliasing = d.antiAliasing;
        anisotropy = d.anisotropy;
        vsync = d.vsync;
        shaderCache = d.shaderCache;
        gpuDriver = d.gpuDriver;
        aspect = d.aspect;
        fullscreen = d.fullscreen;
        customTextures = d.customTextures;
        backend = d.backend;
        showFps = d.showFps;
        preset = d.preset;
    }

    /** Replaces any unsupported value with the nearest supported one. */
    public void sanitize() {
        if (!resolution.isSupported()) resolution = Resolution.P720;
        if (!fpsLimit.isSupported()) fpsLimit = FpsLimit.FPS30;
    }

    // ------------------------------------------------------------------------- availability
    public static final String REASON_900P =
            "Dolphin hanya mendukung kelipatan bulat resolusi asli Wii (1x/2x/3x). 900p tidak bisa "
                    + "dicapai tanpa skala pecahan, jadi opsi ini dinonaktifkan.";
    public static final String REASON_FPS =
            "Call of Duty: Black Ops Wii berjalan maksimum 30 FPS secara native. Backend tidak bisa "
                    + "menaikkan FPS game tanpa patch kode game (tidak tersedia); mempercepat emulasi "
                    + "akan mempercepat seluruh logika game, bukan menambah frame.";
    public static final String REASON_TEXTURE =
            "Game Wii hanya punya satu set tekstur. Dolphin tidak memiliki pengaturan kualitas "
                    + "tekstur; yang tersedia adalah filter anisotropik dan paket tekstur kustom HD.";
    public static final String REASON_SHADOW =
            "Bayangan dirender oleh game itu sendiri. Dolphin tidak punya pengaturan resolusi "
                    + "bayangan; ketajaman bayangan mengikuti resolusi internal.";

    public static String unavailableReason(Resolution r) {
        return r.isSupported() ? null : REASON_900P;
    }

    public static String unavailableReason(FpsLimit f) {
        return f.isSupported() ? null : REASON_FPS;
    }

    // ------------------------------------------------------------------------- dolphin mapping

    /**
     * Dolphin config values for this graphics profile.
     *
     * @param customDriverLibraryName library name of an installed custom (Turnip) driver, or null
     *                                when no custom driver is installed.
     */
    public List<ConfigWrite> toDolphinWrites(String customDriverLibraryName) {
        GraphicsConfig c = copy();
        c.sanitize();
        List<ConfigWrite> w = new ArrayList<>();
        w.add(ConfigWrite.ofString(ConfigWrite.FILE_MAIN, "Core", "GFXBackend", c.backend.dolphinName));
        w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Settings", "InternalResolution", c.resolution.dolphinScale));
        // 30 FPS == native game speed; EmulationSpeed 1.0 keeps Dolphin's limiter at 100 %.
        w.add(ConfigWrite.ofFloat(ConfigWrite.FILE_MAIN, "Core", "EmulationSpeed", 1.0f));
        w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Settings", "MSAA", c.antiAliasing.msaaSamples));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Settings", "SSAA", false));
        w.add(ConfigWrite.ofString(ConfigWrite.FILE_GFX, "Enhancements", "PostProcessingShader",
                c.antiAliasing.postProcessingShader));
        w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Enhancements", "MaxAnisotropy", c.anisotropy.dolphinValue));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Hardware", "VSync", c.vsync));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Settings", "ShaderCache", c.shaderCache));
        w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Settings", "AspectRatio", c.aspect.dolphinMode));
        if (c.aspect == Aspect.WIDE_16_10) {
            w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Settings", "CustomAspectRatioWidth", 16));
            w.add(ConfigWrite.ofInt(ConfigWrite.FILE_GFX, "Settings", "CustomAspectRatioHeight", 10));
        }
        // Black Ops renders in 16:9 when the virtual Wii is set to widescreen.
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_SYSCONF, "IPL", "AR", true));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Settings", "HiresTextures", c.customTextures));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Settings", "ShowFPS", c.showFps));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_GFX, "Settings", "ShowFTimes", c.showFps));
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_MAIN, "Interface", "ExpandToCutoutArea", c.fullscreen));
        String lib = c.gpuDriver == GpuDriver.TURNIP && customDriverLibraryName != null
                ? customDriverLibraryName : "";
        w.add(ConfigWrite.ofString(ConfigWrite.FILE_GFX, "Settings", "DriverLibName", lib));
        return w;
    }

    // ------------------------------------------------------------------------- persistence
    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("resolution", resolution.name());
        m.put("fps", fpsLimit.name());
        m.put("aa", antiAliasing.name());
        m.put("af", anisotropy.name());
        m.put("vsync", Boolean.toString(vsync));
        m.put("shaderCache", Boolean.toString(shaderCache));
        m.put("driver", gpuDriver.name());
        m.put("aspect", aspect.name());
        m.put("fullscreen", Boolean.toString(fullscreen));
        m.put("customTextures", Boolean.toString(customTextures));
        m.put("backend", backend.name());
        m.put("showFps", Boolean.toString(showFps));
        m.put("preset", preset.name());
        return KeyValueCodec.encode(m);
    }

    public static GraphicsConfig decode(String text) {
        GraphicsConfig d = new GraphicsConfig();
        Map<String, String> m = KeyValueCodec.decode(text);
        if (m.isEmpty()) {
            d.applyPreset(Preset.MEDIUM);
            return d;
        }
        d.resolution = KeyValueCodec.getEnum(m, "resolution", Resolution.class, d.resolution);
        d.fpsLimit = KeyValueCodec.getEnum(m, "fps", FpsLimit.class, d.fpsLimit);
        d.antiAliasing = KeyValueCodec.getEnum(m, "aa", AntiAliasing.class, d.antiAliasing);
        d.anisotropy = KeyValueCodec.getEnum(m, "af", Anisotropy.class, d.anisotropy);
        d.vsync = KeyValueCodec.getBool(m, "vsync", d.vsync);
        d.shaderCache = KeyValueCodec.getBool(m, "shaderCache", d.shaderCache);
        d.gpuDriver = KeyValueCodec.getEnum(m, "driver", GpuDriver.class, d.gpuDriver);
        d.aspect = KeyValueCodec.getEnum(m, "aspect", Aspect.class, d.aspect);
        d.fullscreen = KeyValueCodec.getBool(m, "fullscreen", d.fullscreen);
        d.customTextures = KeyValueCodec.getBool(m, "customTextures", d.customTextures);
        d.backend = KeyValueCodec.getEnum(m, "backend", Backend.class, d.backend);
        d.showFps = KeyValueCodec.getBool(m, "showFps", d.showFps);
        d.preset = KeyValueCodec.getEnum(m, "preset", Preset.class, d.preset);
        d.sanitize();
        return d;
    }
}
