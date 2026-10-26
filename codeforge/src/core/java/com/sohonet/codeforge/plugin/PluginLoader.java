package com.sohonet.codeforge.plugin;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Загружает плагины CodeForge из config/codeforge/plugins/*.jar.
 *
 * Плагин - обычный jar с записью
 * META-INF/services/com.sohonet.codeforge.plugin.PluginRegistry$CodeForgePlugin
 * (штатный Java ServiceLoader). Такой плагин не является Fabric-модом:
 * он видит классы игры (родитель - загрузчик CodeForge) и больше ничего.
 */
public final class PluginLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");

    /** Загруженный плагин вместе с путём до его jar-ника. */
    public record LoadedPlugin(PluginRegistry.CodeForgePlugin plugin, Path jarPath) {
    }

    private PluginLoader() {
    }

    public static Path getPluginDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("plugins");
    }

    /** Грузит все .jar из папки плагинов. Битые плагины логируются и пропускаются. */
    public static List<LoadedPlugin> loadAll(PluginRegistry registry) {
        Path dir = getPluginDir();
        List<LoadedPlugin> loaded = new ArrayList<>();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("[CodeForge] could not create plugin dir {}", dir, e);
            return loaded;
        }

        List<Path> jars;
        try (Stream<Path> stream = Files.list(dir)) {
            jars = stream.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
        } catch (IOException e) {
            LOGGER.warn("[CodeForge] could not list plugin dir {}", dir, e);
            return loaded;
        }

        ClassLoader parent = PluginLoader.class.getClassLoader();
        for (Path jar : jars) {
            try {
                URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, parent);
                int before = registry.getPlugins().size();
                for (PluginRegistry.CodeForgePlugin plugin
                    : ServiceLoader.load(PluginRegistry.CodeForgePlugin.class, loader)) {
                    registry.register(plugin);
                    loaded.add(new LoadedPlugin(plugin, jar));
                    LOGGER.info("[CodeForge] plugin {} v{} loaded from {}",
                        plugin.getDisplayName(), plugin.getVersion(), jar.getFileName());
                }
                if (registry.getPlugins().size() == before) {
                    LOGGER.warn("[CodeForge] {} contains no CodeForgePlugin service entry, skipped",
                        jar.getFileName());
                }
            } catch (Throwable t) {
                LOGGER.warn("[CodeForge] failed to load plugin {}", jar.getFileName(), t);
            }
        }
        return loaded;
    }
}
