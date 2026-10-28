package com.sohonet.codeforge.scripts;

import com.sohonet.codeforge.api.Client;
import com.sohonet.codeforge.api.Player;
import com.sohonet.codeforge.api.World;

/**
 * Старое имя для API скриптов, оставлено для совместимости с уже
 * сохранёнными скриптами. Реальная реализация - в
 * {@link com.sohonet.codeforge.api.Client} (там же объяснение, почему
 * скрипты больше не импортируют классы Minecraft напрямую).
 */
public final class ScriptContext {

    private ScriptContext() {
    }

    public static boolean inGame() {
        return Client.inGame();
    }

    public static Player player() {
        return Client.player();
    }

    public static World world() {
        return Client.world();
    }

    public static void say(String message) {
        Client.say(message);
    }

    public static void say(String message, boolean overlay) {
        if (overlay) {
            Client.actionBar(message);
        } else {
            Client.say(message);
        }
    }

    public static void log(String message) {
        Client.log(message);
    }
}
