package com.codblackops.mobile.core.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny, dependency-free serialisation for settings objects stored in SharedPreferences.
 * Format: one "key=value" per line, with '%', '=', '\n' and '\r' percent-escaped.
 */
public final class KeyValueCodec {
    private KeyValueCodec() {
    }

    public static String encode(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(escape(e.getKey())).append('=').append(escape(e.getValue()));
        }
        return sb.toString();
    }

    public static Map<String, String> decode(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return map;
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            map.put(unescape(line.substring(0, eq)), unescape(line.substring(eq + 1)));
        }
        return map;
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '%': sb.append("%25"); break;
                case '=': sb.append("%3D"); break;
                case '\n': sb.append("%0A"); break;
                case '\r': sb.append("%0D"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                String hex = s.substring(i + 1, i + 3);
                try {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) {
                    // fall through and keep the literal '%'
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    // ---- typed helpers ---------------------------------------------------------------------
    public static int getInt(Map<String, String> m, String key, int def) {
        String v = m.get(key);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static float getFloat(Map<String, String> m, String key, float def) {
        String v = m.get(key);
        if (v == null) return def;
        try {
            float f = Float.parseFloat(v.trim());
            return Float.isNaN(f) || Float.isInfinite(f) ? def : f;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean getBool(Map<String, String> m, String key, boolean def) {
        String v = m.get(key);
        if (v == null) return def;
        if ("true".equalsIgnoreCase(v.trim())) return true;
        if ("false".equalsIgnoreCase(v.trim())) return false;
        return def;
    }

    public static <E extends Enum<E>> E getEnum(Map<String, String> m, String key, Class<E> cls, E def) {
        String v = m.get(key);
        if (v == null) return def;
        try {
            return Enum.valueOf(cls, v.trim());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
