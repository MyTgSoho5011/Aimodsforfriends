package com.sohonet.codeforge.scripts;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Небольшой набор сокращений, который автоматически импортируется в
 * каждый скомпилированный скрипт (см. ScriptRunner.buildSource).
 *
 * Идея: чаще всего в скрипте нужно "просто написать игроку строчку" или
 * "достать игрока", и делать это через MinecraftClient.getInstance()
 * каждый раз громоздко.
 */
public final class ScriptContext {

    private ScriptContext() {
    }

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    public static boolean inGame() {
        MinecraftClient client = client();
        return client.player != null && client.world != null;
    }

    /** Печатает строку в чат игроку (в action bar, если overlay = true). */
    public static void say(String message) {
        say(message, false);
    }

    public static void say(String message, boolean overlay) {
        MinecraftClient client = client();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), overlay);
        }
    }
}
