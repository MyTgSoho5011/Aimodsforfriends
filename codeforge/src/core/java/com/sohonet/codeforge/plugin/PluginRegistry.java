package com.sohonet.codeforge.plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Реестр загруженных плагинов CodeForge.
 *
 * Плагин регистрирует здесь две вещи:
 *   - обучающие примеры (попадают во вкладку Tutorial в папку
 *     "plugins/&lt;id&gt;/...", см. {@link #getAllPluginTutorialExamples()});
 *   - команды чата "/cf plugin &lt;id&gt; ..." (см. {@link #dispatchCommand}).
 *
 * Сам реестр ничего не читает с диска - этим занимается PluginLoader.
 */
public final class PluginRegistry {

    /** Что должен реализовать плагин, чтобы CodeForge его принял. */
    public interface CodeForgePlugin {
        String getPluginId();

        String getDisplayName();

        String getVersion();

        /** Вызывается один раз при загрузке - здесь плагин регистрирует примеры и команды. */
        void onLoad(PluginContext context);
    }

    /** То, что видит плагин во время onLoad (с отсечкой по его id). */
    public interface PluginContext {
        void registerTutorialExample(String folder, String name, String code);

        void registerCommand(String commandName, Consumer<String> handler);
    }

    private static final class Registration {
        final CodeForgePlugin plugin;
        final Map<String, Consumer<String>> commands = new LinkedHashMap<>();
        final Map<String, Map<String, String>> tutorialExamples = new LinkedHashMap<>();

        Registration(CodeForgePlugin plugin) {
            this.plugin = plugin;
        }
    }

    private final Map<String, Registration> plugins = new LinkedHashMap<>();

    /** Регистрирует плагин и сразу вызывает его onLoad с ограниченным контекстом. */
    public void register(CodeForgePlugin plugin) {
        Registration registration = new Registration(plugin);
        PluginContext context = new PluginContext() {
            @Override
            public void registerTutorialExample(String folder, String name, String code) {
                String safeFolder = folder == null ? "" : folder.trim();
                String path = safeFolder.isEmpty()
                    ? "plugins/" + plugin.getPluginId()
                    : "plugins/" + plugin.getPluginId() + "/" + safeFolder;
                registration.tutorialExamples.computeIfAbsent(path, k -> new LinkedHashMap<>()).put(name, code);
            }

            @Override
            public void registerCommand(String commandName, Consumer<String> handler) {
                String key = commandName == null || commandName.isBlank() ? "default" : commandName.trim();
                registration.commands.put(key, handler);
            }
        };
        plugin.onLoad(context);
        plugins.put(plugin.getPluginId(), registration);
    }

    public boolean isLoaded(String pluginId) {
        return plugins.containsKey(pluginId);
    }

    public List<CodeForgePlugin> getPlugins() {
        return plugins.values().stream().map(r -> r.plugin).toList();
    }

    /**
     * Все примеры, зарегистрированные плагинами: folder -&gt; name -&gt; code.
     * Папки уже с префиксом "plugins/&lt;id&gt;", поэтому в дереве Tutorial
     * они выглядят как обычные подпапки.
     */
    public Map<String, Map<String, String>> getAllPluginTutorialExamples() {
        Map<String, Map<String, String>> merged = new TreeMap<>();
        for (Registration registration : plugins.values()) {
            for (Map.Entry<String, Map<String, String>> entry : registration.tutorialExamples.entrySet()) {
                merged.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>()).putAll(entry.getValue());
            }
        }
        return merged;
    }

    /**
     * Вызывает команду плагина: "/cf plugin &lt;pluginId&gt; [имя-команды] &lt;args&gt;".
     *
     * Если у плагина одна команда, её имя можно не писать. Если команд
     * несколько - первым словом выбирается нужная.
     *
     * @return true, если плагин найден и у него есть подходящая команда
     */
    public boolean dispatchCommand(String pluginId, String args) {
        Registration registration = plugins.get(pluginId);
        if (registration == null || registration.commands.isEmpty()) {
            return false;
        }
        String raw = args == null ? "" : args.trim();
        int space = raw.indexOf(' ');
        String firstWord = space < 0 ? raw : raw.substring(0, space);
        String rest = space < 0 ? "" : raw.substring(space + 1).trim();

        Consumer<String> byName = registration.commands.get(firstWord);
        if (byName != null && registration.commands.size() > 1) {
            byName.accept(rest);
            return true;
        }
        new ArrayList<>(registration.commands.values()).get(0).accept(raw);
        return true;
    }
}
