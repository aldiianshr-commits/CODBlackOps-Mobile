package com.codblackops.mobile.core.config;

import com.codblackops.mobile.core.util.KeyValueCodec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic logging. When enabled, Dolphin's logger writes graphics/CPU/boot messages to
 * <user dir>/Logs/dolphin.log, which the launcher can show and export.
 */
public final class DiagnosticsConfig {
    /** Dolphin LogType short names relevant to graphics and performance problems. */
    public static final String[] LOG_TYPES = {
            "Video", "Host GPU", "CORE", "BOOT", "JIT", "PowerPC", "DVD", "Audio", "Wiimote", "MI", "IOS_FS"
    };

    public enum Verbosity {
        ERROR("Error", 2), WARNING("Warning", 3), INFO("Info", 4);
        public final String label;
        /** Common::Log::LogLevel value. */
        public final int dolphinValue;

        Verbosity(String label, int dolphinValue) {
            this.label = label;
            this.dolphinValue = dolphinValue;
        }
    }

    public boolean logToFile = false;
    public Verbosity verbosity = Verbosity.WARNING;

    public static DiagnosticsConfig defaults() {
        return new DiagnosticsConfig();
    }

    public List<ConfigWrite> toDolphinWrites() {
        List<ConfigWrite> w = new ArrayList<>();
        w.add(ConfigWrite.ofBool(ConfigWrite.FILE_LOGGER, "Options", "WriteToFile", logToFile));
        w.add(ConfigWrite.ofInt(ConfigWrite.FILE_LOGGER, "Options", "Verbosity", verbosity.dolphinValue));
        for (String type : LOG_TYPES) {
            w.add(ConfigWrite.ofBool(ConfigWrite.FILE_LOGGER, "Logs", type, logToFile));
        }
        return w;
    }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("logToFile", Boolean.toString(logToFile));
        m.put("verbosity", verbosity.name());
        return KeyValueCodec.encode(m);
    }

    public static DiagnosticsConfig decode(String text) {
        DiagnosticsConfig d = new DiagnosticsConfig();
        Map<String, String> m = KeyValueCodec.decode(text);
        d.logToFile = KeyValueCodec.getBool(m, "logToFile", d.logToFile);
        d.verbosity = KeyValueCodec.getEnum(m, "verbosity", Verbosity.class, d.verbosity);
        return d;
    }
}
