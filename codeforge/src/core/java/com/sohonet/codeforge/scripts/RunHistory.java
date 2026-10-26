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
import java.util.Collections;
import java.util.List;

/**
 * Журнал последних запусков скриптов (вкладка HISTORY в консоли).
 *
 * Хранится в config/codeforge/history.json. Держим максимум
 * {@link #MAX_ENTRIES} записей - история нужна, чтобы вернуть в
 * редактор недавний код, а не как архив.
 */
public final class RunHistory {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");
    private static final int MAX_ENTRIES = 50;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
    }.getType();

    /** Один запуск: код, результат и время. */
    public record Entry(String code, boolean success, String note, long timestampMillis) {
    }

    private static List<Entry> entries;

    private RunHistory() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("history.json");
    }

    private static synchronized List<Entry> load() {
        if (entries != null) {
            return entries;
        }
        Path path = file();
        if (!Files.isRegularFile(path)) {
            entries = new ArrayList<>();
            return entries;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<Entry> parsed = GSON.fromJson(json, LIST_TYPE);
            entries = parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not read run history from {}", path, e);
            entries = new ArrayList<>();
        }
        return entries;
    }

    private static void persist() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(load(), LIST_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not write run history to {}", path, e);
        }
    }

    /** Добавляет запись в начало (самая свежая - первая). */
    public static synchronized void record(String code, boolean success, String note) {
        List<Entry> list = load();
        list.add(0, new Entry(code == null ? "" : code, success, note == null ? "" : note,
            System.currentTimeMillis()));
        while (list.size() > MAX_ENTRIES) {
            list.remove(list.size() - 1);
        }
        persist();
    }

    /** Все записи, самые свежие первыми. */
    public static synchronized List<Entry> getEntries() {
        return Collections.unmodifiableList(new ArrayList<>(load()));
    }

    public static synchronized void clear() {
        load().clear();
        persist();
    }
}
