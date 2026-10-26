package com.sohonet.codeforge.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.EditBox;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Поле ввода кода для вкладки Editor.
 *
 * Это тонкая надстройка над ванильным EditBox:
 *   - снимает лимит длины (у EditBox по умолчанию 32 символа);
 *   - Ctrl+Enter = Compile & Run, Ctrl+S = Save as...;
 *   - предупреждает о большой вставке из буфера обмена;
 *   - подсвечивает строку с ошибкой компиляции (номер берётся из
 *     диагностики ECJ, см. CodeForgeScreen).
 *
 * Полноценную подсветку синтаксиса сознательно не делаем: переопределять
 * внутреннюю отрисовку EditBox в каждой версии Minecraft - источник
 * постоянных поломок, а пользы для 10-строчных скриптов немного.
 */
public class HighlightedCodeArea extends EditBox {

    /** Сколько символов считаем "большой вставкой", чтобы показать предупреждение. */
    private static final int LARGE_PASTE_THRESHOLD = 400;
    private static final int MAX_CODE_LENGTH = 1_000_000;

    private final TextRenderer textRenderer;
    private Runnable onRunRequested;
    private Runnable onSaveRequested;
    private Consumer<String> onLargePaste;
    private int errorLine = -1;

    public HighlightedCodeArea(TextRenderer textRenderer, int x, int y, int width, int height, Text placeholder) {
        super(textRenderer, x, y, width, height, placeholder);
        this.textRenderer = textRenderer;
        this.setMaxLength(MAX_CODE_LENGTH);
    }

    public void setOnRunRequested(Runnable onRunRequested) {
        this.onRunRequested = onRunRequested;
    }

    public void setOnSaveRequested(Runnable onSaveRequested) {
        this.onSaveRequested = onSaveRequested;
    }

    public void setOnLargePaste(Consumer<String> onLargePaste) {
        this.onLargePaste = onLargePaste;
    }

    /** -1 = ошибок нет. */
    public void setErrorLine(int errorLine) {
        this.errorLine = errorLine;
    }

    /** Вставка текста из буфера обмена (с предупреждением, если блок большой). */
    public void pasteText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String current = this.getText();
        this.setText(current + text);
        if (text.length() >= LARGE_PASTE_THRESHOLD && onLargePaste != null) {
            onLargePaste.accept(text);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Ctrl+Enter / Cmd+Enter - запуск, Ctrl+S / Cmd+S - сохранение.
        boolean ctrl = (modifiers & 0x2) != 0; // GLFW_MOD_CONTROL
        boolean cmd = (modifiers & 0x8) != 0;  // GLFW_MOD_SUPER
        if ((ctrl || cmd) && (keyCode == 257 || keyCode == 335)) { // Enter / Numpad Enter
            if (onRunRequested != null) {
                onRunRequested.run();
            }
            return true;
        }
        if ((ctrl || cmd) && keyCode == 83) { // S
            if (onSaveRequested != null) {
                onSaveRequested.run();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        if (errorLine > 0) {
            Text marker = Text.literal("compile error near line " + errorLine);
            context.drawTextWithShadow(this.textRenderer, marker,
                this.getX() + this.getWidth() - this.textRenderer.getWidth(marker) - 4,
                this.getY() - 10, 0xFF5555);
        }
    }
}
