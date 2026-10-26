package com.sohonet.codeforge.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Простая эвристика: перед компиляцией скрипта ищем в тексте вызовы,
 * которые легко превращаются в "выстрел в ногу" - выход из игры,
 * удаление файлов, запуск процессов, рефлексия над приватными полями
 * и т.п.
 *
 * Это НЕ песочница (внутри JVM её не сделать), а страховка от
 * случайного копипаста: пользователь видит, что именно нашлось, и
 * должен подтвердить запуск через диалог в консоли.
 */
public final class DangerousCallScanner {

    /** Найденное совпадение: что нашли и почему это подозрительно. */
    public record Warning(String matchedText, String reason) {
        @Override
        public String toString() {
            return matchedText + " (" + reason + ")";
        }
    }

    private record Rule(String needle, String reason) {
    }

    private static final List<Rule> RULES = List.of(
        new Rule("system.exit(", "terminates the game process"),
        new Rule("runtime.getruntime()", "can spawn processes / exit the JVM"),
        new Rule(".exec(", "runs an external process"),
        new Rule("processbuilder", "runs an external process"),
        new Rule("files.delete", "deletes files on disk"),
        new Rule("file.delete", "deletes files on disk"),
        new Rule("deleteifexists", "deletes files on disk"),
        new Rule("new fileoutputstream", "writes files on disk"),
        new Rule("files.writestring", "writes files on disk"),
        new Rule("setaccessible(", "bypasses Java access control (reflection)"),
        new Rule("getdeclaredfield(", "walks private fields (reflection)"),
        new Rule("getdeclaredmethod(", "walks private methods (reflection)"),
        new Rule("unsafe", "sun.misc.Unsafe bypasses the JVM safety net"),
        new Rule("class.forname(", "loads arbitrary classes by name"),
        new Rule("jdk.internal", "uses JDK internals"),
        new Rule("socket", "opens a network connection"),
        new Rule("serveraddress", "opens a network connection")
    );

    private DangerousCallScanner() {
    }

    public static List<Warning> scan(String code) {
        List<Warning> warnings = new ArrayList<>();
        if (code == null || code.isBlank()) {
            return warnings;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            int idx = lower.indexOf(rule.needle());
            if (idx >= 0) {
                warnings.add(new Warning(readableMatch(code, idx, rule.needle()), rule.reason()));
            }
        }
        return warnings;
    }

    /** Возвращает совпадение в исходном регистре, чтобы в диалоге было видно как в коде. */
    private static String readableMatch(String code, int idx, String needle) {
        int end = Math.min(code.length(), idx + needle.length());
        return code.substring(idx, end);
    }
}
