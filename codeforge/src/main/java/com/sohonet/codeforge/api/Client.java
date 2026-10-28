package com.sohonet.codeforge.api;

import com.sohonet.sohoapi.api.SohoRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Стабильный API для скриптов CodeForge.
 *
 * Зачем он нужен: в релизной игре классы Minecraft называются по
 * intermediary (class_310 вместо MinecraftClient), поэтому скрипт,
 * который импортирует классы игры напрямую, компилируется только в
 * dev-окружении. Классы этого пакета компилируются Loom'ом вместе с
 * модом (то есть правильно ремапятся под прод), а имена у них
 * постоянные - поэтому скрипт, написанный через {@code Client}/
 * {@code Player}/{@code World}, компилируется и работает везде.
 *
 * Все методы, которые дёргают игру, вызываются в игровом потоке:
 * {@link com.sohonet.codeforge.sandbox.ScriptRunner} запускает скрипт
 * через {@code MinecraftClient.execute(...)}.
 */
public final class Client {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");

    private Client() {
    }

    /** Есть ли сейчас мир и игрок (в меню/на загрузке вернёт false). */
    public static boolean inGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && client.player != null && client.world != null;
    }

    /** Текущий игрок. Бросает IllegalStateException вне игры. */
    public static Player player() {
        return new Player(requireClient());
    }

    /** Текущий мир. Бросает IllegalStateException вне игры. */
    public static World world() {
        return new World(requireClient());
    }

    /** Печатает строку в чат игроку. */
    public static void say(String message) {
        MinecraftClient client = requireClient();
        client.player.sendMessage(Text.literal(String.valueOf(message)), false);
    }

    /** Печатает строку над хотбаром (action bar). */
    public static void actionBar(String message) {
        MinecraftClient client = requireClient();
        client.player.sendMessage(Text.literal(String.valueOf(message)), true);
    }

    /** Пишет строку в лог игры (полезно, когда чат закрыт). */
    public static void log(String message) {
        LOGGER.info("[script] {}", message);
    }

    /** Телепорт на смещение от текущей позиции (в одиночной игре/на сервере с правами). */
    public static void teleport(double dx, double dy, double dz) {
        MinecraftClient client = requireClient();
        client.player.requestTeleport(
            client.player.getX() + dx,
            client.player.getY() + dy,
            client.player.getZ() + dz);
    }

    /** Версия Minecraft, например "1.21.11". */
    public static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
    }

    /** Значение из Soho API (по умолчанию "Absolutely Boss"). */
    public static String soho() {
        return SohoRegistry.getString("Soho501");
    }

    /** Выполнить код в игровом потоке (безопасно из любого потока). */
    public static void execute(Runnable task) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            task.run();
        } else {
            client.execute(task);
        }
    }

    static MinecraftClient requireClient() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) {
            throw new IllegalStateException("Not in a world right now (join a single-player world or a server first).");
        }
        return client;
    }
}
