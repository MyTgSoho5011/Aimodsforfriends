package com.sohonet.codeforge.sandbox;

import com.sohonet.codeforge.api.Client;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Собирает classpath для ECJ.
 *
 * В релизной игре System.getProperty("java.class.path") почти пустой
 * (игру грузит Knot), поэтому jar самого мода надо искать через
 * ProtectionDomain его же классов. Иначе скрипт не видит даже
 * com.sohonet.codeforge.api.* - именно это давало "cannot be resolved".
 */
final class RuntimeClasspath {

    private RuntimeClasspath() {
    }

    static String build() {
        Set<String> entries = new LinkedHashSet<>();

        // 1) Откуда загружены наши классы - это jar мода (или build/classes в dev).
        for (Class<?> anchor : new Class<?>[]{ScriptRunner.class, InjectedScript.class, Client.class}) {
            addCodeSource(anchor, entries);
        }

        // 2) Классический classpath (dev-запуск, IDE, runClient).
        String property = System.getProperty("java.class.path", "");
        for (String part : property.split(File.pathSeparator)) {
            if (!part.isBlank()) {
                entries.add(part);
            }
        }

        // 3) URL-ы загрузчика нашего мода, если он URLClassLoader.
        ClassLoader loader = ScriptRunner.class.getClassLoader();
        if (loader instanceof URLClassLoader urlLoader) {
            for (URL url : urlLoader.getURLs()) {
                addUrl(url, entries);
            }
        }

        return String.join(File.pathSeparator, entries);
    }

    private static void addCodeSource(Class<?> type, Set<String> entries) {
        try {
            var source = type.getProtectionDomain().getCodeSource();
            if (source != null) {
                addUrl(source.getLocation(), entries);
            }
        } catch (Exception ignored) {
        }
    }

    private static void addUrl(URL url, Set<String> entries) {
        if (url == null) {
            return;
        }
        try {
            Path path = Path.of(url.toURI());
            entries.add(path.toAbsolutePath().toString());
        } catch (Exception ignored) {
        }
    }
}
