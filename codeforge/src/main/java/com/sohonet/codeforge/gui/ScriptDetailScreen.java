package com.sohonet.codeforge.gui;

import com.sohonet.codeforge.scripts.ScriptLibrary;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Детали одного сохранённого скрипта: история версий и rename /
 * duplicate / move to folder / delete.
 *
 * Открывается кнопкой "..." рядом со скриптом на вкладке MY SCRIPTS
 * (см. CodeForgeScreen.renderLibraryFolder).
 */
public class ScriptDetailScreen extends Screen {

    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Screen parent;
    private final ScriptLibrary library;
    private final String folder;
    private final String scriptName;
    private final Consumer<String> onLoadIntoEditor;

    public ScriptDetailScreen(Screen parent, ScriptLibrary library, String folder, String scriptName,
                              Consumer<String> onLoadIntoEditor) {
        super(Text.literal(scriptName + "  -  " + (folder == null || folder.isEmpty() ? "/" : folder)));
        this.parent = parent;
        this.library = library;
        this.folder = folder == null ? "" : folder;
        this.scriptName = scriptName;
        this.onLoadIntoEditor = onLoadIntoEditor;
    }

    private ScriptLibrary.ScriptEntry findEntry() {
        for (ScriptLibrary.ScriptEntry entry : library.getInFolder(folder)) {
            if (entry.name.equals(scriptName)) {
                return entry;
            }
        }
        return null;
    }

    @Override
    protected void init() {
        int margin = 20;
        ScriptLibrary.ScriptEntry entry = findEntry();
        int actionsY = this.height - 52;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Load latest into editor"), b -> {
            if (entry != null && onLoadIntoEditor != null) {
                onLoadIntoEditor.accept(entry.latestCode());
            }
            back();
        }).dimensions(margin, actionsY, 170, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Rename..."), b ->
            this.client.setScreen(new TextInputPromptScreen(this, "New name", scriptName, newName -> {
                if (newName != null && !newName.isBlank()) {
                    library.rename(folder, scriptName, newName.trim());
                }
                back();
            }))).dimensions(margin + 176, actionsY, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Duplicate..."), b ->
            this.client.setScreen(new TextInputPromptScreen(this, "Copy name", scriptName + " copy", copyName -> {
                if (copyName != null && !copyName.isBlank()) {
                    library.duplicate(folder, scriptName, copyName.trim());
                }
                back();
            }))).dimensions(margin + 272, actionsY, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Move to folder..."), b ->
            this.client.setScreen(new TextInputPromptScreen(this, "Move to folder (empty = root)", folder, target -> {
                library.move(folder, scriptName, target == null ? "" : target);
                back();
            }))).dimensions(margin + 378, actionsY, 130, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), b -> {
            library.delete(folder, scriptName);
            back();
        }).dimensions(margin + 514, actionsY, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> back())
            .dimensions(this.width - margin - 80, actionsY, 80, 20).build());

        if (entry == null) {
            this.addDrawableChild(new MultilineTextWidget(margin, 60,
                Text.literal("This script disappeared from disk."), this.textRenderer));
            return;
        }

        List<ScriptLibrary.Version> versions = entry.versions();
        int rowY = 52;
        this.addDrawableChild(new MultilineTextWidget(margin, rowY,
            Text.literal("Versions (newest first) - click to load that version:"), this.textRenderer));
        rowY += 16;

        for (int i = versions.size() - 1; i >= 0; i--) {
            if (rowY > this.height - 70) {
                break;
            }
            final ScriptLibrary.Version version = versions.get(i);
            String preview = version.code().split("\n", 2)[0].trim();
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            String label = TIME_FORMAT.format(Instant.ofEpochMilli(version.timestampMillis()))
                + (version.description().isEmpty() ? "" : "  -  " + version.description())
                + "   |   " + preview;
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                if (onLoadIntoEditor != null) {
                    onLoadIntoEditor.accept(version.code());
                }
                back();
            }).dimensions(margin, rowY, this.width - margin * 2, 20).build());
            rowY += 22;
        }
    }

    private void back() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // ВАЖНО (1.21.9+): НЕ вызываем this.renderBackground(...) - движок уже
        // применяет блюр фона один раз за кадр, а второй вызов бросает
        // IllegalStateException: Can only blur once per frame (был краш при
        // открытии консоли). Вместо блюра - обычная полупрозрачная заливка.
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 24, 0xFFFFFF);
    }

    @Override
    public void close() {
        back();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
