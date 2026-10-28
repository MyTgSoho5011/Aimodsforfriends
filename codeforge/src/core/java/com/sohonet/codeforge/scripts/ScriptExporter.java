package com.sohonet.codeforge.scripts;

import com.sohonet.codeforge.sandbox.ScriptRunner;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Экспорт содержимого редактора в настоящий .java-файл.
 *
 * Кнопка "Export .java" на вкладке Editor складывает файл в
 * config/codeforge/exports/, оборачивая тело метода в такой же класс,
 * какой собирает ScriptRunner, - чтобы файл можно было открыть в IDE
 * и отладить скрипт как обычный Java-код.
 */
public final class ScriptExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");
    private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ScriptExporter() {
    }

    /** @return путь к созданному файлу или null, если записать не удалось. */
    public static Path export(String userMethodBody) {
        String methodBody = userMethodBody == null ? "" : userMethodBody;
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("exports");
        String className = "ExportedScript_" + NAME_FORMAT.format(LocalDateTime.now());
        Path target = dir.resolve(className + ".java");
        try {
            Files.createDirectories(dir);
            Files.writeString(target, wrap(className, methodBody), StandardCharsets.UTF_8);
            return target;
        } catch (Exception e) {
            LOGGER.warn("[CodeForge] could not export script to {}", target, e);
            return null;
        }
    }

    private static String wrap(String className, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("// Exported by CodeForge. Скрипт использует стабильный API мода\n");
        sb.append("// (com.sohonet.codeforge.api), поэтому компилируется и в dev, и в релизной игре.\n\n");
        sb.append("package com.sohonet.codeforge.generated;\n\n");
        sb.append("import com.sohonet.codeforge.api.*;\n");
        sb.append("import com.sohonet.codeforge.sandbox.InjectedScript;\n");
        sb.append("import java.util.*;\n");
        sb.append("import static com.sohonet.codeforge.api.Client.*;\n\n");
        sb.append("public class ").append(className).append(" implements InjectedScript {\n");
        sb.append("    @Override\n");
        sb.append("    public void run() throws Exception {\n");
        for (String line : body.split("\n", -1)) {
            sb.append("        ").append(line).append('\n');
        }
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
