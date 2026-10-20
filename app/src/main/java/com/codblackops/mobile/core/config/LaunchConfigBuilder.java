package com.codblackops.mobile.core.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the complete, de-duplicated list of Dolphin config values written right before a game
 * is launched. Later sources win: graphics → performance profile → diagnostics → input plumbing.
 */
public final class LaunchConfigBuilder {
    private LaunchConfigBuilder() {
    }

    public static List<ConfigWrite> build(GraphicsConfig graphics, PerformanceConfig performance,
                                          DiagnosticsConfig diagnostics, String customDriverLibraryName) {
        Map<String, ConfigWrite> ordered = new LinkedHashMap<>();
        GraphicsConfig effective = performance.capGraphics(graphics);
        put(ordered, effective.toDolphinWrites(customDriverLibraryName));
        put(ordered, performance.toDolphinWrites());
        put(ordered, diagnostics.toDolphinWrites());

        // The launcher drives emulated Wii Remote 1 through Dolphin's InputOverrider; make sure it
        // is an emulated (not real/passthrough) Wii Remote and that the others are disconnected.
        put(ordered, Arrays.asList(
                ConfigWrite.ofInt(ConfigWrite.FILE_WIIMOTE, "Wiimote1", "Source", 1),
                ConfigWrite.ofInt(ConfigWrite.FILE_WIIMOTE, "Wiimote2", "Source", 0),
                ConfigWrite.ofInt(ConfigWrite.FILE_WIIMOTE, "Wiimote3", "Source", 0),
                ConfigWrite.ofInt(ConfigWrite.FILE_WIIMOTE, "Wiimote4", "Source", 0),
                // Dolphin's own touch overlay must stay out of the way of the Black Ops HUD.
                ConfigWrite.ofBool(ConfigWrite.FILE_MAIN, "Android", "ShowInputOverlay", false),
                ConfigWrite.ofBool(ConfigWrite.FILE_MAIN, "Core", "WiimoteContinuousScanning", false),
                ConfigWrite.ofBool(ConfigWrite.FILE_MAIN, "Analytics", "Enabled", false),
                ConfigWrite.ofBool(ConfigWrite.FILE_MAIN, "Analytics", "PermissionAsked", true)
        ));
        return new ArrayList<>(ordered.values());
    }

    private static void put(Map<String, ConfigWrite> map, List<ConfigWrite> writes) {
        for (ConfigWrite w : writes) {
            map.remove(w.location()); // keep "last write wins" and move it to the end
            map.put(w.location(), w);
        }
    }
}
