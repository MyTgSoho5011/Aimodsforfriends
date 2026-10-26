package com.sohonet.codeforge.compile;

import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureClassLoader;
import java.util.*;

/**
 * Компилирует один Java-класс в рантайме с помощью ECJ (Eclipse
 * Compiler for Java) - это ЧИСТО JAVA-БИБЛИОТЕКА, а не внешний
 * процесс javac. Она зашита прямо внутрь мода (include() в
 * build.gradle.kts), поэтому игроку не нужно ставить себе JDK -
 * обычной JRE, с которой и так идёт Minecraft, достаточно: сам
 * компилятор "приезжает" вместе с jar-ником мода.
 *
 * В отличие от стандартного javax.tools.JavaCompiler (который
 * доступен только если игра запущена на полном JDK), ECJ работает
 * на любой JVM, потому что сам является обычной Java-библиотекой,
 * а не обёрткой над системным компилятором.
 *
 * Технически ECJ в "batch" режиме читает .java с диска и пишет
 * .class на диск (у него нет официального in-memory API, которым
 * было бы просто и надёжно пользоваться), поэтому мы используем
 * временную папку во временной директории ОС как промежуточный
 * буфер, а затем сразу удаляем её. Для сниппетов из консоли это
 * занимает миллисекунды и ничего не оставляет на диске после себя.
 */
public final class InMemoryJavaCompiler {

    private InMemoryJavaCompiler() {
    }

    public static final class CompileResult {
        public final boolean success;
        public final String diagnostics;
        public final Class<?> compiledClass;

        CompileResult(boolean success, String diagnostics, Class<?> compiledClass) {
            this.success = success;
            this.diagnostics = diagnostics;
            this.compiledClass = compiledClass;
        }
    }

    /**
     * Компилирует {@code sourceCode} (должен объявлять публичный
     * класс с именем {@code className}) с помощью ECJ и грузит
     * результат, используя {@code parentClassLoader} как родителя,
     * чтобы скомпилированный класс видел классы игры/мода.
     */
    public static CompileResult compile(String className, String sourceCode, ClassLoader parentClassLoader) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("codeforge-ecj-");
        } catch (Exception e) {
            return new CompileResult(false, "Could not create temp dir for compilation: " + e, null);
        }

        try {
            Path srcDir = tempDir.resolve("src");
            Path outDir = tempDir.resolve("out");
            Files.createDirectories(srcDir);
            Files.createDirectories(outDir);

            // Пишем исходник на диск в правильную пакетную структуру папок,
            // например com/sohonet/codeforge/generated/Script_1_abc.java
            String relativePath = className.replace('.', '/') + ".java";
            Path sourceFile = srcDir.resolve(relativePath);
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, sourceCode, StandardCharsets.UTF_8);

            String classpath = buildClasspath(parentClassLoader);

            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            PrintWriter outWriter = new PrintWriter(outBuf, true, StandardCharsets.UTF_8);
            PrintWriter errWriter = new PrintWriter(errBuf, true, StandardCharsets.UTF_8);

            // Аргументы командной строки ECJ batch-компилятора:
            // -nowarn подавляет шум предупреждений, -proc:none отключает
            // annotation processing (он нам не нужен и может тормозить),
            // -source/-target 21 - целевая версия байткода под нашу JVM.
            String[] args = new String[]{
                "-nowarn",
                "-proc:none",
                "-source", "21",
                "-target", "21",
                "-encoding", "UTF-8",
                "-cp", classpath,
                "-d", outDir.toAbsolutePath().toString(),
                sourceFile.toAbsolutePath().toString()
            };

            Main ecjMain = new Main(outWriter, errWriter, false, null, null);
            boolean ok = ecjMain.compile(args);

            String diagnostics = outBuf.toString(StandardCharsets.UTF_8) + errBuf.toString(StandardCharsets.UTF_8);

            if (!ok) {
                return new CompileResult(false, diagnostics, null);
            }

            // Грузим .class-файлы из outDir кастомным загрузчиком классов,
            // родитель - classloader мода/игры, чтобы скомпилированный
            // код видел классы Minecraft и Soho API.
            URL outUrl = outDir.toUri().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[]{outUrl}, parentClassLoader);
            Class<?> loaded = loader.loadClass(className);

            return new CompileResult(true, diagnostics, loaded);
        } catch (Exception e) {
            return new CompileResult(false, "Compiler error: " + e, null);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /** Best-effort classpath string so ECJ can resolve game/mod classes by name. */
    private static String buildClasspath(ClassLoader cl) {
        String javaClassPath = System.getProperty("java.class.path", "");
        StringBuilder sb = new StringBuilder(javaClassPath);
        if (cl instanceof URLClassLoader urlClassLoader) {
            for (URL url : urlClassLoader.getURLs()) {
                sb.append(File.pathSeparatorChar).append(url.getPath());
            }
        }
        return sb.toString();
    }

    private static void deleteRecursively(Path dir) {
        try {
            if (dir == null || !Files.exists(dir)) {
                return;
            }
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }
}