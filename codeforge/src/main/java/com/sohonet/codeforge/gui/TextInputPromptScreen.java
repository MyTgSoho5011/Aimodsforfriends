package com.sohonet.codeforge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Маленький диалог "введите строку" (имя скрипта, папка, новое имя).
 * Введённое значение уходит в callback.
 */
public class TextInputPromptScreen extends Screen {

    private final Screen parent;
    private final String initialValue;
    private final Consumer<String> callback;
    private EditBoxWidget field;

    public TextInputPromptScreen(Screen parent, String title, String initialValue, Consumer<String> callback) {
        super(Text.literal(title));
        this.parent = parent;
        this.initialValue = initialValue == null ? "" : initialValue;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldWidth = Math.min(320, this.width - 40);

        this.field = EditBoxWidget.builder()
            .x(centerX - fieldWidth / 2)
            .y(this.height / 2 - 20)
            .build(this.textRenderer, fieldWidth, 20, Text.literal(this.initialValue));
        this.field.setMaxLength(512);
        this.field.setMaxLines(1);
        this.addDrawableChild(this.field);
        this.setInitialFocus(this.field);

        int buttonY = this.height / 2 + 12;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> submit())
            .dimensions(centerX - 105, buttonY, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> cancel())
            .dimensions(centerX + 5, buttonY, 100, 20).build());
    }

    private void submit() {
        String value = this.field == null ? "" : this.field.getText();
        if (callback != null) {
            callback.accept(value);
        }
    }

    private void cancel() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEnter()) {
            submit();
            return true;
        }
        if (input.isEscape()) {
            cancel();
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
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2,
            this.height / 2 - 44, 0xFFFFFF);
    }

    @Override
    public void close() {
        cancel();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
