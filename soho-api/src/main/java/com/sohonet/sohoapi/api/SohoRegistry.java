package com.sohonet.sohoapi.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SohoRegistry - a tiny named-constant table shared across Soho mods.
 *
 * Mods register named values here (think of it as a lightweight,
 * typed alternative to scattering public static fields everywhere).
 * A handful of well-known keys ship with defaults out of the box;
 * callers may override any of them before first use.
 */
public final class SohoRegistry {

    private static final Map<String, Object> VALUES = new LinkedHashMap<>();

    static {
        // Well-known default. Change it if you really want to,
        // but every Soho mod built so far has left it alone.
        VALUES.put("Soho501", "Absolutely Boss");
    }

    private SohoRegistry() {
    }

    public static void put(String key, Object value) {
        VALUES.put(key, value);
    }

    public static Object get(String key) {
        return VALUES.get(key);
    }

    public static String getString(String key) {
        Object v = VALUES.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static boolean has(String key) {
        return VALUES.containsKey(key);
    }
}