package com.sohonet.codeforge.scripts;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Хранилище скриптов CodeForge.
 *
 * Два источника:
 *   - {@link #TUTORIAL_EXAMPLES} - встроенные примеры и туториал
 *     (только чтение, лежат прямо в jar-нике мода);
 *   - config/codeforge/scripts.json - сохранённые пользователем скрипты
 *     с историей версий.
 *
 * Каждый экземпляр класса читает один и тот же файл на диске (см.
 * CodeForgeClient.LIBRARY и CodeForgeScreen.LIBRARY): запись через
 * любой экземпляр видна остальным после следующего обращения к файлу.
 */
public final class ScriptLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");
    private static final int MAX_VERSIONS = 20;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** folder -> script name -> версии (последняя = самая свежая). */
    private static final Type STORE_TYPE = new TypeToken<TreeMap<String, TreeMap<String, List<Version>>>>() {
    }.getType();

    /** Одна сохранённая версия скрипта. */
    public record Version(long timestampMillis, String description, String code) {
    }

    /** Скрипт в списке папки - то, что показывает вкладка MY SCRIPTS. */
    public static final class ScriptEntry {
        public final String folder;
        public final String name;
        public final String description;
        private final List<Version> versions;

        ScriptEntry(String folder, String name, String description, List<Version> versions) {
            this.folder = folder;
            this.name = name;
            this.description = description == null ? "" : description;
            this.versions = versions;
        }

        public int versionCount() {
            return versions.size();
        }

        public String latestCode() {
            return versions.isEmpty() ? "" : versions.get(versions.size() - 1).code();
        }

        public List<Version> versions() {
            return List.copyOf(versions);
        }
    }

    // ------------------------------------------------------------------
    //  Встроенные примеры / туториал
    // ------------------------------------------------------------------

    private static final Map<String, Map<String, String>> TUTORIAL = new LinkedHashMap<>();

    private static void example(String folder, String name, String code) {
        TUTORIAL.computeIfAbsent(folder, k -> new LinkedHashMap<>()).put(name, code);
    }

    static {
        // Примеры написаны через стабильный API (Client/Player/World), поэтому
        // работают и в dev-окружении, и в релизной игре.
        example("01 basics", "Hello chat", """
            say("Hello from CodeForge!");
            """);
        example("01 basics", "Who am I", """
            say("I am " + player().name());
            say("at " + player().blockPos() + " in " + player().dimension());
            """);
        example("01 basics", "Count nearby entities", """
            say(world().entitiesNear(16) + " entities within 16 blocks");
            """);
        example("01 basics", "Status line", """
            say("HP " + player().health() + "/" + player().maxHealth()
                + "  XP " + player().xpLevel()
                + "  hand " + player().mainHand());
            """);

        example("02 mod dev tutorial", "Ch1 - how a script runs", """
            // Скрипт - это тело метода run(). CodeForge компилирует его
            // ECJ прямо в игре, грузит отдельным ClassLoader и выполняет
            // в игровом потоке.
            say("Chapter 1: this method body is compiled at runtime.");
            """);
        example("02 mod dev tutorial", "Ch2 - talking to the player", """
            say("Health: " + player().health() + "/" + player().maxHealth());
            actionBar("XP level: " + player().xpLevel());
            """);
        example("02 mod dev tutorial", "Ch3 - items and inventory", """
            say("In hand: " + player().mainHand() + " x" + player().mainHandCount());
            say("Empty slots: " + player().emptySlots());
            """);
        example("02 mod dev tutorial", "Ch4 - blocks around you", """
            say("Standing on: " + world().blockBelow());
            say("Air above head: " + world().isAir(0, 2, 0));
            say("Day time: " + world().isDay() + " (tick " + world().timeOfDay() + ")");
            """);
        example("02 mod dev tutorial", "Ch5 - what can I use?", """
            // Всё, что доступно скрипту, - методы классов Client, Player, World
            // (пакет com.sohonet.codeforge.api). Они импортированы автоматически.
            say("MC " + minecraftVersion() + ", soho501 = " + soho());
            say("Loaded entities: " + world().loadedEntities());
            """);
        example("02 mod dev tutorial", "Ch6 - texture editor", """
            // Вкладка Textures рисует PNG до 32x32 и кладёт его в
            // config/codeforge/textures/. Дальше - ресурспак.
            say("Open the Textures tab to draw a sprite.");
            """);
        example("03 snippets", "Teleport up", """
            teleport(0, 20, 0);
            say("Up we go");
            """);
        example("03 snippets", "Look around report", """
            for (String line : List.of(
                    "pos " + player().blockPos(),
                    "dim " + player().dimension(),
                    "creative " + player().isCreative(),
                    "flying " + player().isFlying())) {
                log(line);
            }
            say("Wrote 4 lines to the game log");
            """);
    }

    public static final Map<String, Map<String, String>> TUTORIAL_EXAMPLES = TUTORIAL;

    // ------------------------------------------------------------------
    //  Сохранённые скрипты
    // ------------------------------------------------------------------

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("scripts.json");
    }

    private static TreeMap<String, TreeMap<String, List<Version>>> read() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return new TreeMap<>();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            TreeMap<String, TreeMap<String, List<Version>>> parsed = GSON.fromJson(json, STORE_TYPE);
            return parsed == null ? new TreeMap<>() : parsed;
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not read scripts from {}", path, e);
            return new TreeMap<>();
        }
    }

    private static void write(TreeMap<String, TreeMap<String, List<Version>>> store) {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(store, STORE_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not write scripts to {}", path, e);
        }
    }

    /** Все папки, в которых что-то лежит, включая корень (""). */
    public Set<String> getFolders() {
        Set<String> folders = new TreeSet<>();
        folders.add("");
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        for (Map.Entry<String, TreeMap<String, List<Version>>> entry : store.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                folders.add(entry.getKey());
            }
        }
        return folders;
    }

    /** Скрипты, лежащие непосредственно в {@code folder}, по алфавиту. */
    public List<ScriptEntry> getInFolder(String folder) {
        String key = folder == null ? "" : folder;
        List<ScriptEntry> result = new ArrayList<>();
        TreeMap<String, List<Version>> scripts = read().get(key);
        if (scripts == null) {
            return result;
        }
        for (Map.Entry<String, List<Version>> entry : scripts.entrySet()) {
            List<Version> versions = entry.getValue() == null ? new ArrayList<>() : entry.getValue();
            String description = versions.isEmpty() ? "" : versions.get(versions.size() - 1).description();
            result.add(new ScriptEntry(key, entry.getKey(), description, versions));
        }
        result.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return result;
    }

    /** Сохраняет новую версию скрипта; старые версии остаются в истории. */
    public void save(String folder, String name, String description, String code) {
        String folderKey = folder == null ? "" : folder.trim();
        String scriptName = name == null ? "" : name.trim();
        if (scriptName.isEmpty()) {
            return;
        }
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        List<Version> versions = store
            .computeIfAbsent(folderKey, k -> new TreeMap<>())
            .computeIfAbsent(scriptName, k -> new ArrayList<>());
        versions.add(new Version(System.currentTimeMillis(), description == null ? "" : description,
            code == null ? "" : code));
        while (versions.size() > MAX_VERSIONS) {
            versions.remove(0);
        }
        write(store);
    }

    /** Код последней версии скрипта с таким именем в любой папке, либо null. */
    public String get(String name) {
        String scriptName = name == null ? "" : name.trim();
        if (scriptName.isEmpty()) {
            return null;
        }
        for (TreeMap<String, List<Version>> scripts : read().values()) {
            List<Version> versions = scripts.get(scriptName);
            if (versions != null && !versions.isEmpty()) {
                return versions.get(versions.size() - 1).code();
            }
        }
        return null;
    }

    public boolean exists(String folder, String name) {
        List<Version> versions = read().getOrDefault(folder == null ? "" : folder, new TreeMap<>())
            .get(name);
        return versions != null && !versions.isEmpty();
    }

    public void delete(String folder, String name) {
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        TreeMap<String, List<Version>> scripts = store.get(folder == null ? "" : folder);
        if (scripts != null) {
            scripts.remove(name);
            if (scripts.isEmpty()) {
                store.remove(folder == null ? "" : folder);
            }
            write(store);
        }
    }

    /** Переименовывает скрипт (внутри той же папки). */
    public void rename(String folder, String oldName, String newName) {
        if (newName == null || newName.isBlank() || oldName.equals(newName)) {
            return;
        }
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        TreeMap<String, List<Version>> scripts = store.get(folder == null ? "" : folder);
        if (scripts == null) {
            return;
        }
        List<Version> versions = scripts.remove(oldName);
        if (versions != null) {
            scripts.put(newName.trim(), versions);
            write(store);
        }
    }

    /** Копия скрипта под именем {@code copyName} в той же папке. */
    public void duplicate(String folder, String name, String copyName) {
        if (copyName == null || copyName.isBlank()) {
            return;
        }
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        TreeMap<String, List<Version>> scripts = store.get(folder == null ? "" : folder);
        if (scripts == null) {
            return;
        }
        List<Version> versions = scripts.get(name);
        if (versions != null) {
            scripts.put(copyName.trim(), new ArrayList<>(versions));
            write(store);
        }
    }

    /** Переносит скрипт в другую папку, сохраняя историю версий. */
    public void move(String fromFolder, String name, String toFolder) {
        String target = toFolder == null ? "" : toFolder.trim();
        if (target.equals(fromFolder)) {
            return;
        }
        TreeMap<String, TreeMap<String, List<Version>>> store = read();
        TreeMap<String, List<Version>> source = store.get(fromFolder == null ? "" : fromFolder);
        if (source == null) {
            return;
        }
        List<Version> versions = source.remove(name);
        if (versions == null) {
            return;
        }
        store.computeIfAbsent(target, k -> new TreeMap<>()).put(name, versions);
        write(store);
    }
}
