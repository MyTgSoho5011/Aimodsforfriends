package com.sohonet.codeforge.sandbox;

import com.sohonet.codeforge.compile.InMemoryJavaCompiler;
import com.sohonet.codeforge.safety.DangerousCallScanner;
import com.sohonet.codeforge.scripts.RunHistory;
import com.sohonet.sohoapi.api.SohoRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns a user-written method body into a compiled, loaded,
 * executed InjectedScript. Each run gets a fresh class name so
 * repeated edits don't collide with previously loaded classes.
 */
public final class ScriptRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger("codeforge");
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final String PACKAGE = "com.sohonet.codeforge.generated";

    // Сколько максимум ждём выполнения скрипта, прежде чем считать его
    // зависшим и принудительно прервать поток. Компиляция в этот лимит
    // не входит - только сам вызов InjectedScript.run(...).
    private static final long EXECUTION_TIMEOUT_MS = 5000;

    private static final ExecutorService SCRIPT_EXECUTOR =
        Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "codeforge-script");
            t.setDaemon(true);
            return t;
        });

    private ScriptRunner() {
    }

    public static final class RunResult {
        public final boolean compiled;
        public final boolean ranWithoutError;
        public final String message;
        /** true значит: компиляция НЕ запускалась, нужно спросить пользователя и повторить с forceRun=true. */
        public final boolean needsConfirmation;
        public final List<DangerousCallScanner.Warning> warnings;

        RunResult(boolean compiled, boolean ranWithoutError, String message,
                   boolean needsConfirmation, List<DangerousCallScanner.Warning> warnings) {
            this.compiled = compiled;
            this.ranWithoutError = ranWithoutError;
            this.message = message;
            this.needsConfirmation = needsConfirmation;
            this.warnings = warnings;
        }
    }

    public static RunResult compileAndRun(String userMethodBody) {
        return compileAndRun(userMethodBody, false);
    }

    public static RunResult compileAndRun(String userMethodBody, boolean forceRun) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return new RunResult(false, false, "CodeForge only runs on the client.", false, List.of());
        }

        // --- Шаг 1: проверка на явно опасные конструкции (см. DangerousCallScanner) ---
        if (!forceRun) {
            List<DangerousCallScanner.Warning> warnings = DangerousCallScanner.scan(userMethodBody);
            if (!warnings.isEmpty()) {
                StringBuilder msg = new StringBuilder("This code contains potentially dangerous calls:\n");
                for (DangerousCallScanner.Warning w : warnings) {
                    msg.append(" - ").append(w.matchedText()).append(" (").append(w.reason()).append(")\n");
                }
                msg.append("Run anyway only if you trust this code.");
                return new RunResult(false, false, msg.toString(), true, warnings);
            }
        }

        // --- Шаг 2: компиляция (см. InMemoryJavaCompiler, бьёт ECJ) ---
        int id = COUNTER.incrementAndGet();
        String simpleName = "Script_" + id + "_" + Long.toHexString(System.nanoTime());
        String fullyQualified = PACKAGE + "." + simpleName;

        String source = buildSource(fullyQualified, simpleName, userMethodBody);

        ClassLoader parentLoader = ScriptRunner.class.getClassLoader();
        InMemoryJavaCompiler.CompileResult compileResult =
            InMemoryJavaCompiler.compile(fullyQualified, source, parentLoader);

        if (!compileResult.success) {
            LOGGER.warn("CodeForge compile failed:\n{}", compileResult.diagnostics);
            RunHistory.record(userMethodBody, false, "compile failed");
            return new RunResult(false, false, "Compile error:\n" + compileResult.diagnostics, false, List.of());
        }

        // --- Шаг 3: выполнение с таймаутом в отдельном потоке ---
        try {
            Object instance = compileResult.compiledClass.getDeclaredConstructor().newInstance();
            if (!(instance instanceof InjectedScript script)) {
                RunHistory.record(userMethodBody, false, "internal error");
                return new RunResult(true, false, "Compiled class did not implement InjectedScript (internal error).", false, List.of());
            }

            MinecraftClient client = MinecraftClient.getInstance();
            Future<Void> future = SCRIPT_EXECUTOR.submit(() -> {
                script.run(client);
                return null;
            });

            try {
                future.get(EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                String okMessage = "OK. (Soho501 = " + SohoRegistry.getString("Soho501") + ")";
                RunHistory.record(userMethodBody, true, "ok");
                return new RunResult(true, true, okMessage, false, List.of());
            } catch (TimeoutException e) {
                // Скрипт завис дольше таймаута - прерываем поток (best-effort:
                // код без точек проверки Thread.interrupted() может это
                // игнорировать, но большинство обычных циклов/IO прервётся).
                future.cancel(true);
                RunHistory.record(userMethodBody, false, "timed out");
                return new RunResult(true, false,
                    "Script timed out after " + EXECUTION_TIMEOUT_MS + "ms and was interrupted " +
                    "(likely an infinite loop or a blocking call).", false, List.of());
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                LOGGER.warn("CodeForge runtime error while executing injected script", cause);
                RunHistory.record(userMethodBody, false, "runtime error");
                return new RunResult(true, false, "Runtime error: " + cause, false, List.of());
            }
        } catch (Throwable t) {
            LOGGER.warn("CodeForge error while preparing injected script", t);
            RunHistory.record(userMethodBody, false, "runtime error");
            return new RunResult(true, false, "Runtime error: " + t, false, List.of());
        }
    }

    private static String buildSource(String fullyQualified, String simpleName, String userMethodBody) {
        String pkg = fullyQualified.substring(0, fullyQualified.lastIndexOf('.'));
        return "package " + pkg + ";\n\n" +
            "import net.minecraft.client.MinecraftClient;\n" +
            "import net.minecraft.entity.*;\n" +
            "import net.minecraft.entity.player.*;\n" +
            "import net.minecraft.item.*;\n" +
            "import net.minecraft.block.*;\n" +
            "import net.minecraft.util.*;\n" +
            "import net.minecraft.util.math.*;\n" +
            "import net.minecraft.text.*;\n" +
            "import net.minecraft.world.*;\n" +
            "import com.sohonet.sohoapi.api.SohoRegistry;\n" +
            "import com.sohonet.codeforge.sandbox.InjectedScript;\n" +
            "import com.sohonet.codeforge.scripts.ScriptContext;\n\n" +
            "public class " + simpleName + " implements InjectedScript {\n" +
            "    @Override\n" +
            "    public void run(MinecraftClient client) throws Exception {\n" +
            indent(userMethodBody) +
            "\n    }\n" +
            "}\n";
    }

    private static String indent(String body) {
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            sb.append("        ").append(line).append('\n');
        }
        return sb.toString();
    }
}