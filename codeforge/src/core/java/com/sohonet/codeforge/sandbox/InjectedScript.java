package com.sohonet.codeforge.sandbox;

/**
 * Интерфейс, который реализует собранный из скрипта класс.
 *
 * Сигнатура намеренно не содержит типов Minecraft: скрипт должен
 * компилироваться и в dev-окружении, и в релизной игре (где классы игры
 * называются по intermediary). Всё, что нужно скрипту, доступно через
 * стабильный API com.sohonet.codeforge.api.Client.
 */
public interface InjectedScript {
    void run() throws Exception;
}
