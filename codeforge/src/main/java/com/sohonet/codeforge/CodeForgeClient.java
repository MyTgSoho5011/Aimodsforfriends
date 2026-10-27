package com.sohonet.codeforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sohonet.codeforge.gui.CodeForgeScreen;
import com.sohonet.codeforge.keybinds.KeyBindingManager;
import com.sohonet.codeforge.mods.ModListHelper;
import com.sohonet.codeforge.plugin.PluginLoader;
import com.sohonet.codeforge.plugin.PluginRegistry;
import com.sohonet.codeforge.sandbox.ScriptRunner;
import com.sohonet.codeforge.scripts.ScriptLibrary;
import com.sohonet.sohoapi.api.SohoRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CodeForgeClient implements ClientModInitializer {

    public static final String MOD_ID = "codeforge";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Библиотека сохранённых скриптов - используется и консолью, и
    // командой чата, и клавишами быстрого запуска. Это тот же класс,
    // что и в CodeForgeScreen; каждый экземпляр читает тот же файл
    // на диске, так что они всегда видят одни и те же сохранённые
    // скрипты (запись в любом месте сразу видна остальным после
    // следующего обращения к диску).
    private static final ScriptLibrary LIBRARY = new ScriptLibrary();

    // Реестр плагинов - сюда плагины регистрируют свои туториальные
    // примеры и команды при загрузке (см. PluginLoader/PluginRegistry).
    // static final, как и LIBRARY - один общий реестр на всю игровую
    // сессию, виден и консоли (вкладка Plugins / Tutorial), и команде
    // "/cf plugin ...".
    public static final PluginRegistry PLUGIN_REGISTRY = new PluginRegistry();
    private static List<PluginLoader.LoadedPlugin> loadedPlugins = List.of();

    private static KeyBinding openConsoleKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[CodeForge] loaded, built on Soho API (Soho501 = {})", SohoRegistry.getString("Soho501"));

        openConsoleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.codeforge.open_console",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            KeyBinding.Category.MISC
        ));

        KeyBindingManager.registerKeyBindings();

        // Плагины грузятся после регистрации клавиш/обработчиков самого
        // CodeForge, но до того, как игрок сможет что-либо нажать -
        // к моменту, когда откроется первый экран консоли, все плагины
        // из config/codeforge/plugins/ уже должны быть загружены и
        // зарегистрированы в PLUGIN_REGISTRY.
        loadedPlugins = PluginLoader.loadAll(PLUGIN_REGISTRY);
        LOGGER.info("[CodeForge] loaded {} plugin(s) from {}", loadedPlugins.size(), PluginLoader.getPluginDir());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConsoleKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CodeForgeScreen());
                }
            }

            // Проверяем все слоты быстрого запуска на каждый тик - если
            // слот нажат И на него назначен скрипт, запускаем его.
            for (int slot = 0; slot < KeyBindingManager.SLOT_COUNT; slot++) {
                KeyBinding keyBinding = KeyBindingManager.getSlotKey(slot);
                while (keyBinding.wasPressed()) {
                    String scriptName = KeyBindingManager.getBoundScript(slot);
                    if (scriptName != null) {
                        runSavedScriptByName(client, scriptName);
                    }
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(literal("cf")
                // "/cf mods" - отдельная подкоманда, печатает в чат список
                // установленных модов (см. ModListHelper и Tutorial Ch5 про
                // то, зачем это нужно - узнать, какие классы доступны для
                // импорта в скриптах).
                .then(literal("mods").executes(ctx -> {
                    printModListToChat(MinecraftClient.getInstance());
                    return 1;
                }))
                // "/cf plugin <pluginId> <args...>" - передаёт остаток строки
                // обработчику команды, зарегистрированному плагином через
                // PluginContext.registerCommand(...). "<args...>" необязателен -
                // если плагин вызван без аргументов, argsHandler получит "".
                .then(literal("plugin")
                    .then(argument("pluginArgs", greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "pluginArgs");
                            dispatchPluginCommand(MinecraftClient.getInstance(), raw);
                            return 1;
                        })))
                // "/cf <имя скрипта>" - запускает сохранённый скрипт по имени.
                .then(argument("scriptName", greedyString())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "scriptName");
                        runSavedScriptByName(MinecraftClient.getInstance(), name);
                        return 1;
                    }))));
    }

    public static List<PluginLoader.LoadedPlugin> getLoadedPlugins() {
        return loadedPlugins;
    }

    /** Разбирает "<pluginId> <остаток строки>" и передаёт обработчику плагина, см. PluginContext.registerCommand. */
    private static void dispatchPluginCommand(MinecraftClient client, String raw) {
        String pluginId;
        String args;
        int space = raw.indexOf(' ');
        if (space < 0) {
            pluginId = raw;
            args = "";
        } else {
            pluginId = raw.substring(0, space);
            args = raw.substring(space + 1);
        }

        boolean handled = PLUGIN_REGISTRY.dispatchCommand(pluginId, args);
        if (!handled && client.player != null) {
            client.player.sendMessage(Text.literal(
                "[CodeForge] No command registered for plugin \"" + pluginId + "\" " +
                "(either it's not installed, or it didn't register a command)."), false);
        }
    }

    private static void printModListToChat(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        List<ModListHelper.ModInfo> mods = ModListHelper.getInstalledMods();
        client.player.sendMessage(Text.literal("[CodeForge] " + mods.size() + " mods installed:"), false);
        for (ModListHelper.ModInfo mod : mods) {
            client.player.sendMessage(Text.literal(" - " + mod.id() + " (v" + mod.version() + ")"), false);
        }
    }

    /**
     * Общая логика запуска сохранённого скрипта по имени - используется
     * и из /cf, и из клавиш быстрого запуска.
     *
     * ВАЖНО: в отличие от консоли (CodeForgeScreen), которая компилирует
     * в фоновом потоке через CompletableFuture, здесь компиляция
     * выполняется СИНХРОННО прямо на клиентском тике. Для коротких
     * сохранённых скриптов это обычно доли секунды и не заметно, но
     * если когда-нибудь появится привязка очень длинных/сложных
     * скриптов к клавише, стоит перенести этот вызов в фоновый поток
     * по аналогии с CodeForgeScreen.compileAndRun(...).
     */
    private static void runSavedScriptByName(MinecraftClient client, String scriptName) {
        String code = LIBRARY.get(scriptName);
        if (code == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[CodeForge] No saved script named \"" + scriptName + "\""), false);
            }
            return;
        }

        ScriptRunner.RunResult result = ScriptRunner.compileAndRun(code, false);

        if (result.needsConfirmation) {
            // Команда /cf и клавиши быстрого запуска не открывают диалог
            // подтверждения (нет GUI-контекста) - просто предупреждаем и
            // просим подтвердить вручную через консоль.
            if (client.player != null) {
                client.player.sendMessage(Text.literal(
                    "[CodeForge] \"" + scriptName + "\" contains potentially dangerous calls - " +
                    "open it in the console (]) to confirm and run it manually."), false);
            }
            return;
        }

        if (client.player != null && !result.ranWithoutError) {
            client.player.sendMessage(Text.literal("[CodeForge] \"" + scriptName + "\" failed: " + result.message), false);
        }
    }
}