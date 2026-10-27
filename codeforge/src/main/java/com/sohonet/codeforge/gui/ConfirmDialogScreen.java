package com.sohonet.codeforge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Универсальный диалог да/нет с текстом: подтверждение запуска
 * потенциально опасного кода и предупреждение о большой вставке.
 */
public class ConfirmDialogScreen extends Screen {

    private final Screen parent;
    private final String message;
    private final String confirmLabel;
    private final Consumer<Boolean> callback;

    public ConfirmDialogScreen(Screen parent, String title, String message, String confirmLabel,
                               Consumer<Boolean> callback) {
        super(Text.literal(title));
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int dialogWidth = Math.min(420, this.width - 40);
        int left = centerX - dialogWidth / 2;

        MultilineTextWidget text = new MultilineTextWidget(left, 60, Text.literal(this.message), this.textRenderer);
        text.setMaxWidth(dialogWidth);
        this.addDrawableChild(text);

        int buttonY = this.height - 60;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(confirmLabel), b -> finish(true))
            .dimensions(centerX - 165, buttonY, 160, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> finish(false))
            .dimensions(centerX + 5, buttonY, 160, 20).build());
    }

    private void finish(boolean confirmed) {
        if (callback != null) {
            callback.accept(confirmed);
        }
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
        context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 30, 0xFFFFFF);
    }

    @Override
    public void close() {
        finish(false);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
