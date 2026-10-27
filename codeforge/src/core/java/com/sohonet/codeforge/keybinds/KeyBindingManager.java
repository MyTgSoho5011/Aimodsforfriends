package com.sohonet.codeforge.keybinds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Клавиши быстрого запуска сохранённых скриптов (5 слотов).
 *
 * Привязка "слот -&gt; имя скрипта" хранится в
 * config/codeforge/keybinds.json, а сами клавиши - обычные KeyBinding,
 * поэтому их можно переназначить в настройках управления Minecraft
 * (раздел "Разное").
 */
public final class KeyBindingManager {

    public static final int SLOT_COUNT = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();

    // F7..F10 и F12 по умолчанию ни за что не отвечают (F11 - полный экран).
    private static final int[] DEFAULT_KEYS = {
        GLFW.GLFW_KEY_F7, GLFW.GLFW_KEY_F8, GLFW.GLFW_KEY_F9, GLFW.GLFW_KEY_F10, GLFW.GLFW_KEY_F12
    };

    private static final KeyBinding[] SLOT_KEYS = new KeyBinding[SLOT_COUNT];
    private static final Map<Integer, String> BOUND_SCRIPTS = new HashMap<>();

    private KeyBindingManager() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("keybinds.json");
    }

    /** Регистрирует клавиши и подтягивает сохранённые привязки (один раз при старте). */
    public static void registerKeyBindings() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            SLOT_KEYS[slot] = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.codeforge.quick_slot_" + (slot + 1),
                InputUtil.Type.KEYSYM,
                DEFAULT_KEYS[slot],
                KeyBinding.Category.MISC
            ));
        }
        load();
    }

    public static KeyBinding getSlotKey(int slot) {
        return slot >= 0 && slot < SLOT_COUNT ? SLOT_KEYS[slot] : null;
    }

    /** Имя скрипта, привязанного к слоту, либо null. */
    public static String getBoundScript(int slot) {
        return BOUND_SCRIPTS.get(slot);
    }

    public static void setBoundScript(int slot, String scriptName) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            return;
        }
        if (scriptName == null || scriptName.isBlank()) {
            BOUND_SCRIPTS.remove(slot);
        } else {
            BOUND_SCRIPTS.put(slot, scriptName.trim());
        }
        save();
    }

    private static void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Map<String, String> raw = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            if (raw != null) {
                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    try {
                        BOUND_SCRIPTS.put(Integer.parseInt(entry.getKey()), entry.getValue());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not read keybinds from {}", path, e);
        }
    }

    private static void save() {
        Path path = file();
        Map<String, String> raw = new HashMap<>();
        BOUND_SCRIPTS.forEach((slot, name) -> raw.put(String.valueOf(slot), name));
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(raw, MAP_TYPE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not write keybinds to {}", path, e);
        }
    }
}
