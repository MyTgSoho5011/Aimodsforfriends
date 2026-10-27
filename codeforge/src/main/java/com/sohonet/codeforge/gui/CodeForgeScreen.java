package com.sohonet.codeforge.gui;

import com.sohonet.codeforge.CodeForgeClient;
import com.sohonet.codeforge.mods.ModListHelper;
import com.sohonet.codeforge.plugin.PluginLoader;
import com.sohonet.codeforge.sandbox.ScriptRunner;
import com.sohonet.codeforge.scripts.RunHistory;
import com.sohonet.codeforge.scripts.ScriptExporter;
import com.sohonet.codeforge.scripts.ScriptLibrary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Главный экран консоли CodeForge.
 *
 * --- ПОЯСНЕНИЕ ДЛЯ ЧИТАЮЩЕГО КОД ---
 * Шесть вкладок сверху:
 *   EDITOR     - редактор кода + Compile & Run, Save as..., Clear,
 *                Copy, Paste, Export.
 *   TUTORIAL   - встроенные обучающие примеры, сгруппированные по
 *                папкам (ScriptLibrary.TUTORIAL_EXAMPLES), включая
 *                полный туториал по написанию модов в папке
 *                "02 mod dev tutorial".
 *   MY SCRIPTS - сохранённые пользователем скрипты, тоже по папкам,
 *                с версиями (клик на "..." у скрипта открывает
 *                ScriptDetailScreen с историей версий, rename,
 *                duplicate, move to folder).
 *   HISTORY    - автоматический журнал последних запусков.
 *   MODS       - список установленных Fabric-модов (ModListHelper),
 *                подсказывает, какие классы доступны для импорта
 *                из скрипта (см. туториал, глава 5).
 *   TEXTURES   - кнопка, открывающая TextureEditorScreen (отдельный
 *                полноэкранный pixel-art редактор, не встроен как
 *                вкладка из-за сложности своего layout).
 *
 * Навигация по папкам в TUTORIAL/MY SCRIPTS: currentFolder - это
 * просто строка текущей открытой "папки" (пустая строка = корень).
 * Список сначала показывает подпапки как кнопки (клик - "зайти"),
 * затем скрипты прямо в этой папке. Кнопка ".." поднимается на
 * уровень выше, если мы не в корне.
 */
public class CodeForgeScreen extends Screen {

    private static final ExecutorService COMPILE_POOL =
        Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "codeforge-compiler");
            t.setDaemon(true);
            return t;
        });

    private static final ScriptLibrary LIBRARY = new ScriptLibrary();

    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile("line\\s+(\\d+)");
    private static final DateTimeFormatter HISTORY_TIME_FORMAT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private enum Tab { EDITOR, TUTORIAL, MY_SCRIPTS, HISTORY, MODS, TEXTURES, PLUGINS }

    private Tab currentTab = Tab.EDITOR;
    // Текущая открытая "папка" на вкладках TUTORIAL / MY SCRIPTS (пустая строка = корень).
    private String currentFolder = "";

    // --- виджеты вкладки EDITOR ---
    private EditBoxWidget codeArea;
    /** -1 = ошибок компиляции нет; иначе номер строки, подсвечиваемой над редактором. */
    private int errorLine = -1;
    private MultilineTextWidget outputWidget;
    private ButtonWidget runButton;
    private String lastOutput = "";
    private boolean running = false;
    private String editorText = null;
    // Папка и описание, которые будут предложены по умолчанию при следующем Save as...
    // (обновляются, когда скрипт загружается из My Scripts/Tutorial/History).
    private String pendingSaveFolder = "";
    private String pendingSaveDescription = "";

    public CodeForgeScreen() {
        super(Text.translatable("codeforge.title"));
    }

    @Override
    protected void init() {
        int margin = 20;

        int tabY = 24;
        // Вкладки раскладываются циклом, а не вручную посчитанными
        // отступами - так добавление новой вкладки (как PLUGINS здесь)
        // не требует пересчитывать координаты всех остальных кнопок.
        String[] tabLabels = {"Editor", "Tutorial", "My Scripts", "History", "Mods", "Textures", "Plugins"};
        Tab[] tabValues = {Tab.EDITOR, Tab.TUTORIAL, Tab.MY_SCRIPTS, Tab.HISTORY, Tab.MODS, Tab.TEXTURES, Tab.PLUGINS};
        int tabW = (this.width - margin * 2 - (tabLabels.length - 1) * 4) / tabLabels.length;
        for (int i = 0; i < tabLabels.length; i++) {
            int x = margin + i * (tabW + 4);
            this.addDrawableChild(tabButton(tabLabels[i], tabValues[i], x, tabY, tabW));
        }

        switch (currentTab) {
            case EDITOR -> initEditorTab(margin, tabY);
            case TUTORIAL -> initFolderBrowserTab(margin, tabY, true);
            case MY_SCRIPTS -> initFolderBrowserTab(margin, tabY, false);
            case HISTORY -> initHistoryTab(margin, tabY);
            case MODS -> initModsTab(margin, tabY);
            case TEXTURES -> initTexturesTab(margin, tabY);
            case PLUGINS -> initPluginsTab(margin, tabY);
        }
    }

    private ButtonWidget tabButton(String label, Tab tab, int x, int y, int w) {
        String prefix = (currentTab == tab) ? "> " : "";
        return ButtonWidget.builder(Text.literal(prefix + label), b -> {
            this.currentTab = tab;
            this.currentFolder = "";
            this.clearChildren();
            this.init();
        }).dimensions(x, y, w, 18).build();
    }

    // ------------------------------------------------------------
    //  Вкладка EDITOR
    // ------------------------------------------------------------

    private void initEditorTab(int margin, int tabY) {
        int top = tabY + 24;
        int fieldWidth = this.width - margin * 2;
        int fieldHeight = Math.max(80, (this.height - top - 130));

        // В 1.21.9+ EditBox - это модель многострочного текста, а виджет
        // называется EditBoxWidget и создаётся билдером.
        // Сочетания клавиш (Ctrl+Enter - запуск, Ctrl+S - сохранить)
        // обрабатывает сам экран, см. keyPressed ниже.
        this.codeArea = EditBoxWidget.builder()
            .x(margin)
            .y(top)
            .placeholder(Text.translatable("codeforge.placeholder"))
            .build(this.textRenderer, fieldWidth, fieldHeight, Text.literal(""));
        this.codeArea.setMaxLength(1_000_000);
        this.codeArea.setMaxLines(64);
        this.codeArea.setText(editorText != null ? editorText : Text.translatable("codeforge.placeholder").getString());
        this.codeArea.setChangeListener(text -> this.editorText = text);
        this.addDrawableChild(this.codeArea);

        int buttonY = top + fieldHeight + 6;
        int btnW = 94;

        this.runButton = ButtonWidget.builder(Text.translatable("codeforge.run"), b -> compileAndRun())
            .dimensions(margin, buttonY, btnW, 20).build();
        this.addDrawableChild(this.runButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save as..."), b -> openSavePrompt())
            .dimensions(margin + btnW + 6, buttonY, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("codeforge.clear"), b -> {
            this.lastOutput = "";
            refreshOutput();
        }).dimensions(margin + (btnW + 6) * 2, buttonY, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Copy"), b -> copyToClipboard())
            .dimensions(margin + (btnW + 6) * 3, buttonY, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Paste"), b -> pasteFromClipboard())
            .dimensions(margin + (btnW + 6) * 4, buttonY, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Export .java"), b -> exportToFile())
            .dimensions(margin + (btnW + 6) * 5, buttonY, btnW + 20, 20).build());

        this.outputWidget = new MultilineTextWidget(margin, buttonY + 28, Text.literal(lastOutput), this.textRenderer);
        this.outputWidget.setMaxWidth(fieldWidth);
        this.addDrawableChild(this.outputWidget);
    }

    private void compileAndRun() {
        compileAndRun(false);
    }

    private void compileAndRun(boolean forceRun) {
        if (running) {
            return;
        }
        running = true;
        if (runButton != null) {
            runButton.active = false;
        }
        lastOutput = "Compiling...";
        refreshOutput();
        this.errorLine = -1;

        String source = this.codeArea.getText();
        editorText = source;
        CompletableFuture
            .supplyAsync(() -> ScriptRunner.compileAndRun(source, forceRun), COMPILE_POOL)
            .thenAccept(result -> {
                if (this.client == null) {
                    return;
                }
                this.client.execute(() -> {
                    running = false;
                    if (runButton != null) {
                        runButton.active = true;
                    }

                    if (result.needsConfirmation) {
                        showDangerousCodeConfirm(result.message, source);
                        return;
                    }

                    lastOutput = (result.compiled ? "" : "[COMPILE FAILED]\n") +
                        (result.compiled && !result.ranWithoutError ? "[RUNTIME ERROR]\n" : "") +
                        result.message;

                    if (!result.compiled) {
                        Matcher m = ERROR_LINE_PATTERN.matcher(result.message);
                        if (m.find()) {
                            try {
                                int line = Integer.parseInt(m.group(1));
                                this.errorLine = line;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    refreshOutput();
                });
            });
    }

    private void showDangerousCodeConfirm(String message, String source) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new ConfirmDialogScreen(this, "Potentially dangerous code", message, "Run anyway", confirmed -> {
            if (confirmed) {
                compileAndRun(true);
            } else {
                lastOutput = "Run cancelled.";
                refreshOutput();
            }
        }));
    }

    private void onLargePasteDetected() {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new ConfirmDialogScreen(this, "Pasted code",
            "You just pasted a block of code from the clipboard. Make sure you trust where it came from before running it.",
            "OK, got it", confirmed -> {
            }));
    }

    private void refreshOutput() {
        if (this.outputWidget != null) {
            this.outputWidget.setMessage(Text.literal(lastOutput));
        }
    }

    private void openSavePrompt() {
        if (this.client == null) {
            return;
        }
        String codeToSave = this.codeArea.getText();
        this.client.setScreen(new TextInputPromptScreen(this, "Save as... (name)", "", name -> {
            if (name == null || name.isBlank()) {
                return;
            }
            String trimmedName = name.trim();
            // После имени спрашиваем папку отдельным диалогом (проще, чем одно поле
            // с разделителем "/" - так понятнее новичку, что это два разных поля).
            if (this.client != null) {
                this.client.setScreen(new TextInputPromptScreen(this, "Folder (empty = root)", pendingSaveFolder, folder -> {
                    String folderValue = folder == null ? "" : folder.trim();
                    LIBRARY.save(folderValue, trimmedName, pendingSaveDescription, codeToSave);
                    pendingSaveFolder = folderValue;
                    lastOutput = "Saved as \"" + (folderValue.isEmpty() ? trimmedName : folderValue + "/" + trimmedName) + "\".";
                    this.clearChildren();
                    this.init();
                    refreshOutput();
                }));
            }
        }));
    }

    private void copyToClipboard() {
        if (this.client == null) {
            return;
        }
        this.client.keyboard.setClipboard(this.codeArea.getText());
        lastOutput = "Copied to clipboard.";
        refreshOutput();
    }

    private void pasteFromClipboard() {
        if (this.client == null) {
            return;
        }
        String clipboard = this.client.keyboard.getClipboard();
        if (clipboard != null && !clipboard.isEmpty()) {
            this.codeArea.setText(this.codeArea.getText() + clipboard);
            if (clipboard.length() > 400) {
                onLargePasteDetected();
            }
        }
    }

    private void exportToFile() {
        Path exported = ScriptExporter.export(this.codeArea.getText());
        lastOutput = exported != null
            ? "Exported to: " + exported
            : "Export failed - check the game log for details.";
        refreshOutput();
    }

    // ------------------------------------------------------------
    //  Вкладки TUTORIAL / MY SCRIPTS - общий просмотрщик папок.
    //
    //  isTutorial=true  -> источник ScriptLibrary.TUTORIAL_EXAMPLES
    //                      (встроенные примеры, read-only, без версий).
    //  isTutorial=false -> источник LIBRARY (сохранённые пользователем
    //                      скрипты, с версиями, можно удалять/переименовывать).
    // ------------------------------------------------------------

    private void initFolderBrowserTab(int margin, int tabY, boolean isTutorial) {
        int top = tabY + 24;

        String headerText = isTutorial
            ? "Tutorial examples" + (currentFolder.isEmpty() ? "" : " / " + currentFolder) + ":"
            : "Your saved scripts" + (currentFolder.isEmpty() ? "" : " / " + currentFolder) + ":";
        this.addDrawableChild(new MultilineTextWidget(margin, top, Text.literal(headerText), this.textRenderer));

        int rowY = top + 16;
        int rowHeight = 22;

        if (!currentFolder.isEmpty()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(".. (up)"), b -> {
                int lastSlash = currentFolder.lastIndexOf('/');
                currentFolder = lastSlash < 0 ? "" : currentFolder.substring(0, lastSlash);
                this.clearChildren();
                this.init();
            }).dimensions(margin, rowY, this.width - margin * 2, 20).build());
            rowY += rowHeight;
        }

        boolean[] renderedAnything = { false };
        if (isTutorial) {
            renderTutorialFolder(margin, rowY, rowHeight, renderedAnything);
        } else {
            renderLibraryFolder(margin, rowY, rowHeight, renderedAnything);
        }

        if (!renderedAnything[0]) {
            String emptyMsg = isTutorial ? "No examples here." : "Nothing here yet. Save a script from the Editor tab.";
            this.addDrawableChild(new MultilineTextWidget(margin, rowY, Text.literal(emptyMsg), this.textRenderer));
        }
    }

    /**
     * Рисует содержимое текущей папки на вкладке Tutorial - объединяет
     * встроенные примеры (ScriptLibrary.TUTORIAL_EXAMPLES) и примеры,
     * зарегистрированные плагинами (CodeForgeClient.PLUGIN_REGISTRY.
     * getAllPluginTutorialExamples() - каждый плагин кладёт свои примеры
     * в папку "plugins/<его id>/...", см. PluginRegistry.ScopedContext.
     * registerTutorialExample). Для пользователя оба источника выглядят
     * как одно общее дерево папок - разницы в интерфейсе нет, видно
     * только по пути "plugins/..." в дереве, что это от плагина.
     */
    private void renderTutorialFolder(int margin, int startY, int rowHeight, boolean[] renderedAnything) {
        int rowY = startY;

        java.util.TreeSet<String> subFolders = new java.util.TreeSet<>();
        for (String folder : ScriptLibrary.TUTORIAL_EXAMPLES.keySet()) {
            String sub = nextPathSegment(folder, currentFolder);
            if (sub != null) {
                subFolders.add(sub);
            }
        }
        for (String folder : CodeForgeClient.PLUGIN_REGISTRY.getAllPluginTutorialExamples().keySet()) {
            String sub = nextPathSegment(folder, currentFolder);
            if (sub != null) {
                subFolders.add(sub);
            }
        }
        for (String sub : subFolders) {
            String fullPath = currentFolder.isEmpty() ? sub : currentFolder + "/" + sub;
            if (rowY > this.height - 40) {
                return;
            }
            renderedAnything[0] = true;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("[folder] " + sub), b -> {
                currentFolder = fullPath;
                this.clearChildren();
                this.init();
            }).dimensions(margin, rowY, this.width - margin * 2, 20).build());
            rowY += rowHeight;
        }

        Map<String, String> builtIn = ScriptLibrary.TUTORIAL_EXAMPLES.get(currentFolder);
        if (builtIn != null) {
            for (Map.Entry<String, String> entry : builtIn.entrySet()) {
                if (rowY > this.height - 40) {
                    return;
                }
                renderedAnything[0] = true;
                String code = entry.getValue();
                this.addDrawableChild(ButtonWidget.builder(Text.literal(entry.getKey()), b -> loadIntoEditor(code, currentFolder, ""))
                    .dimensions(margin, rowY, this.width - margin * 2, 20).build());
                rowY += rowHeight;
            }
        }

        Map<String, String> fromPlugins = CodeForgeClient.PLUGIN_REGISTRY.getAllPluginTutorialExamples().get(currentFolder);
        if (fromPlugins != null) {
            for (Map.Entry<String, String> entry : fromPlugins.entrySet()) {
                if (rowY > this.height - 40) {
                    return;
                }
                renderedAnything[0] = true;
                String code = entry.getValue();
                this.addDrawableChild(ButtonWidget.builder(Text.literal(entry.getKey() + " [plugin]"), b -> loadIntoEditor(code, currentFolder, ""))
                    .dimensions(margin, rowY, this.width - margin * 2, 20).build());
                rowY += rowHeight;
            }
        }
    }

    private void renderLibraryFolder(int margin, int startY, int rowHeight, boolean[] renderedAnything) {
        int rowY = startY;

        java.util.TreeSet<String> subFolders = new java.util.TreeSet<>();
        for (String folder : LIBRARY.getFolders()) {
            String sub = nextPathSegment(folder, currentFolder);
            if (sub != null) {
                subFolders.add(sub);
            }
        }
        for (String sub : subFolders) {
            String fullPath = currentFolder.isEmpty() ? sub : currentFolder + "/" + sub;
            if (rowY > this.height - 40) {
                return;
            }
            renderedAnything[0] = true;
            this.addDrawableChild(ButtonWidget.builder(Text.literal("[folder] " + sub), b -> {
                currentFolder = fullPath;
                this.clearChildren();
                this.init();
            }).dimensions(margin, rowY, this.width - margin * 2, 20).build());
            rowY += rowHeight;
        }

        for (ScriptLibrary.ScriptEntry entry : LIBRARY.getInFolder(currentFolder)) {
            if (rowY > this.height - 40) {
                return;
            }
            renderedAnything[0] = true;
            String label = entry.name + " (v" + entry.versionCount() + ")" +
                (entry.description.isEmpty() ? "" : " - " + shorten(entry.description, 40));

            this.addDrawableChild(ButtonWidget.builder(Text.literal(label),
                b -> loadIntoEditor(entry.latestCode(), entry.folder, entry.description))
                .dimensions(margin, rowY, this.width - margin * 2 - 60, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal("..."), b -> {
                if (this.client != null) {
                    this.client.setScreen(new ScriptDetailScreen(this, LIBRARY, entry.folder, entry.name,
                        code -> loadIntoEditor(code, entry.folder, entry.description)));
                }
            }).dimensions(this.width - margin - 55, rowY, 55, 20).build());

            rowY += rowHeight;
        }
    }

    /** Если folderPath начинается с currentFolder + "/", возвращает следующий сегмент пути; иначе null. */
    private static String nextPathSegment(String folderPath, String currentFolder) {
        if (currentFolder.isEmpty()) {
            if (folderPath.isEmpty()) {
                return null;
            }
            int slash = folderPath.indexOf('/');
            return slash < 0 ? folderPath : folderPath.substring(0, slash);
        }
        String prefix = currentFolder + "/";
        if (!folderPath.startsWith(prefix)) {
            return null;
        }
        String rest = folderPath.substring(prefix.length());
        if (rest.isEmpty()) {
            return null;
        }
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ------------------------------------------------------------
    //  Вкладка HISTORY
    // ------------------------------------------------------------

    private void initHistoryTab(int margin, int tabY) {
        int top = tabY + 24;
        this.addDrawableChild(new MultilineTextWidget(margin, top,
            Text.literal("Recent runs (most recent first) - click to reload into the editor:"), this.textRenderer));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear history"), b -> {
            RunHistory.clear();
            this.clearChildren();
            this.init();
        }).dimensions(this.width - margin - 110, top, 110, 18).build());

        List<RunHistory.Entry> entries = RunHistory.getEntries();
        int rowY = top + 22;
        int rowHeight = 22;

        if (entries.isEmpty()) {
            this.addDrawableChild(new MultilineTextWidget(margin, rowY, Text.literal("No runs yet."), this.textRenderer));
            return;
        }

        for (RunHistory.Entry entry : entries) {
            if (rowY > this.height - 40) {
                break;
            }
            String time = HISTORY_TIME_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis()));
            String statusIcon = entry.success() ? "OK" : "FAIL";
            String preview = firstLinePreview(entry.code());
            String label = "[" + time + "] [" + statusIcon + "] " + preview;

            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> loadIntoEditor(entry.code(), "", ""))
                .dimensions(margin, rowY, this.width - margin * 2, 20).build());
            rowY += rowHeight;
        }
    }

    private static String firstLinePreview(String code) {
        String firstLine = code.split("\n", 2)[0].trim();
        if (firstLine.isEmpty()) {
            firstLine = "(empty first line)";
        }
        return firstLine.length() > 60 ? firstLine.substring(0, 60) + "..." : firstLine;
    }

    // ------------------------------------------------------------
    //  Вкладка MODS
    // ------------------------------------------------------------

    private void initModsTab(int margin, int tabY) {
        int top = tabY + 24;
        this.addDrawableChild(new MultilineTextWidget(margin, top,
            Text.literal("Installed mods - their classes are importable from any script (see Tutorial Ch5):"),
            this.textRenderer));

        List<ModListHelper.ModInfo> mods = ModListHelper.getInstalledMods();
        int rowY = top + 18;
        int rowHeight = 12;

        for (ModListHelper.ModInfo mod : mods) {
            if (rowY > this.height - 20) {
                this.addDrawableChild(new MultilineTextWidget(margin, rowY,
                    Text.literal("... and " + (mods.size() - (rowY - top - 18) / rowHeight) + " more (use /cf mods in chat to see the full list)"),
                    this.textRenderer));
                break;
            }
            String line = mod.id() + "  (" + mod.name() + ", v" + mod.version() + ")";
            this.addDrawableChild(new MultilineTextWidget(margin, rowY, Text.literal(line), this.textRenderer));
            rowY += rowHeight;
        }
    }

    // ------------------------------------------------------------
    //  Вкладка TEXTURES
    // ------------------------------------------------------------

    private void initTexturesTab(int margin, int tabY) {
        int top = tabY + 24;
        MultilineTextWidget info = new MultilineTextWidget(margin, top,
            Text.literal("Draw pixel art (up to 32x32) and save it as a PNG texture. " +
                "Saved files land in config/codeforge/textures/ - see Tutorial Ch6 for how to turn " +
                "one into a real item texture via a resource pack."),
            this.textRenderer);
        info.setMaxWidth(this.width - margin * 2);
        this.addDrawableChild(info);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Texture Editor"), b -> {
            if (this.client != null) {
                this.client.setScreen(new TextureEditorScreen(this));
            }
        }).dimensions(margin, top + 40, 180, 20).build());
    }

    // ------------------------------------------------------------
    //  Вкладка PLUGINS
    //
    //  --- ПОЯСНЕНИЕ ДЛЯ ЧИТАЮЩЕГО КОД ---
    //  Показывает все плагины, успешно загруженные PluginLoader при
    //  старте игры из config/codeforge/plugins/ (список берётся из
    //  CodeForgeClient.getLoadedPlugins() - сам экран не занимается
    //  загрузкой, только отображением уже загруженного). Туториальные
    //  примеры, которые плагины зарегистрировали через PluginContext,
    //  видны не здесь, а во вкладке Tutorial, в папке "plugins/<id>/..."
    //  (см. renderTutorialFolder - он объединяет встроенные примеры
    //  ScriptLibrary.TUTORIAL_EXAMPLES и плагинные PLUGIN_REGISTRY.
    //  getAllPluginTutorialExamples() в одно общее дерево папок).
    // ------------------------------------------------------------

    private void initPluginsTab(int margin, int tabY) {
        int top = tabY + 24;

        List<PluginLoader.LoadedPlugin> plugins = CodeForgeClient.getLoadedPlugins();

        MultilineTextWidget info = new MultilineTextWidget(margin, top,
            Text.literal("Drop a plugin .jar into " + PluginLoader.getPluginDir() +
                " and restart the game to load it. " + plugins.size() + " plugin(s) currently loaded:"),
            this.textRenderer);
        info.setMaxWidth(this.width - margin * 2);
        this.addDrawableChild(info);

        int rowY = top + 30;
        int rowHeight = 14;

        if (plugins.isEmpty()) {
            this.addDrawableChild(new MultilineTextWidget(margin, rowY,
                Text.literal("No plugins loaded. Drop a jar implementing PluginRegistry.CodeForgePlugin into that folder."), this.textRenderer));
            return;
        }

        for (PluginLoader.LoadedPlugin loaded : plugins) {
            if (rowY > this.height - 20) {
                break;
            }
            String line = loaded.plugin().getDisplayName() + " v" + loaded.plugin().getVersion() +
                "  (id: " + loaded.plugin().getPluginId() + ", " + loaded.jarPath().getFileName() + ")";
            this.addDrawableChild(new MultilineTextWidget(margin, rowY, Text.literal(line), this.textRenderer));
            rowY += rowHeight;
        }
    }

    // ------------------------------------------------------------

    private void loadIntoEditor(String code, String folder, String description) {
        this.editorText = code;
        this.pendingSaveFolder = folder == null ? "" : folder;
        this.pendingSaveDescription = description == null ? "" : description;
        this.currentTab = Tab.EDITOR;
        this.clearChildren();
        this.init();
    }

    /**
     * Горячие клавиши редактора. В 1.21.11 ввод приходит объектом KeyInput
     * (key(), scancode(), modifiers()), а не тремя int'ами.
     */
    @Override
    public boolean keyPressed(KeyInput input) {
        boolean ctrlOrCmd = input.hasCtrlOrCmd();
        if (ctrlOrCmd && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            compileAndRun();
            return true;
        }
        if (ctrlOrCmd && input.key() == GLFW.GLFW_KEY_S) {
            openSavePrompt();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // ВАЖНО (1.21.9+): НЕ вызываем this.renderBackground(...) - движок уже
        // применяет блюр фона один раз за кадр, а второй вызов бросает
        // IllegalStateException: Can only blur once per frame (был краш при
        // открытии консоли). Вместо блюра - обычная полупрозрачная заливка.
        context.fill(0, 0, this.width, this.height, 0x88000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, this.getTitle(), 20, 8, 0xFFFFFF);
        if (this.errorLine > 0) {
            context.drawTextWithShadow(this.textRenderer,
                Text.literal("compile error near line " + this.errorLine), 20, this.height - 12, 0xFF5555);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}