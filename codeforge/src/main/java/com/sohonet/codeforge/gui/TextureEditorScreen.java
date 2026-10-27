package com.sohonet.codeforge.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Пиксель-арт редактор до 32x32 (вкладка TEXTURES).
 *
 * ЛКМ - рисовать выбранным цветом, ПКМ - стереть пиксель.
 * "Save PNG" кладёт файл в config/codeforge/textures/&lt;имя&gt;.png,
 * откуда его можно забрать в ресурспак (см. Tutorial Ch6).
 *
 * Ввод мыши в 1.21.11 приходит объектом Click (x(), y(), button()).
 */
public class TextureEditorScreen extends Screen {

    private static final int SIZE = 32;

    private static final int[] PALETTE = {
        0x000000, 0xFFFFFF, 0x7F7F7F, 0xFF5555, 0xFFAA00, 0xFFFF55,
        0x55FF55, 0x00AA00, 0x55FFFF, 0x5555FF, 0xFF55FF, 0x8B4513
    };

    private final Screen parent;
    private final int[] pixels = new int[SIZE * SIZE]; // ARGB, 0 = прозрачный
    private int selectedColor = 0xFF000000;
    private int cellSize;
    private int gridLeft;
    private int gridTop;
    private EditBoxWidget nameField;
    private String status = "";

    public TextureEditorScreen(Screen parent) {
        super(Text.literal("CodeForge texture editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 20;
        this.cellSize = Math.max(6, Math.min(16, (this.height - 130) / SIZE));
        this.gridLeft = margin;
        this.gridTop = 44;

        int paletteY = gridTop + SIZE * cellSize + 8;
        for (int i = 0; i < PALETTE.length; i++) {
            final int color = PALETTE[i];
            this.addDrawableChild(ButtonWidget.builder(Text.literal("  "), b -> {
                selectedColor = 0xFF000000 | color;
                status = "Colour selected.";
            }).dimensions(margin + i * 24, paletteY, 22, 18).build());
        }

        int controlsY = paletteY + 26;
        this.nameField = EditBoxWidget.builder()
            .x(margin)
            .y(controlsY)
            .build(this.textRenderer, 160, 20, Text.literal("my_texture"));
        this.nameField.setMaxLines(1);
        this.addDrawableChild(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save PNG"), b -> savePng())
            .dimensions(margin + 166, controlsY, 90, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> {
            Arrays.fill(pixels, 0);
            status = "Cleared.";
        }).dimensions(margin + 262, controlsY, 70, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> {
            if (this.client != null) {
                this.client.setScreen(parent);
            }
        }).dimensions(margin + 338, controlsY, 70, 20).build());
    }

    private void savePng() {
        String name = nameField == null ? "texture" : nameField.getText().trim();
        if (name.isEmpty()) {
            name = "texture";
        }
        if (!name.endsWith(".png")) {
            name = name + ".png";
        }
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("codeforge").resolve("textures");
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, SIZE, SIZE, pixels, 0, SIZE);
            ImageIO.write(image, "PNG", target.toFile());
            status = "Saved to " + target;
        } catch (Exception e) {
            status = "Save failed: " + e.getMessage();
        }
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= gridLeft && mouseY >= gridTop
            && mouseX < gridLeft + SIZE * cellSize && mouseY < gridTop + SIZE * cellSize;
    }

    private void paint(double mouseX, double mouseY, int button) {
        int col = (int) ((mouseX - gridLeft) / cellSize);
        int row = (int) ((mouseY - gridTop) / cellSize);
        if (col < 0 || row < 0 || col >= SIZE || row >= SIZE) {
            return;
        }
        pixels[row * SIZE + col] = button == 1 ? 0x00000000 : selectedColor;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int button = click.button();
        if ((button == 0 || button == 1) && insideGrid(click.x(), click.y())) {
            paint(click.x(), click.y(), button);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        int button = click.button();
        if ((button == 0 || button == 1) && insideGrid(click.x(), click.y())) {
            paint(click.x(), click.y(), button);
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 16, 0xFFFFFF);

        // Шахматный фон, чтобы прозрачные пиксели было видно.
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                int x = gridLeft + col * cellSize;
                int y = gridTop + row * cellSize;
                int checker = ((row + col) & 1) == 0 ? 0xFF404040 : 0xFF303030;
                int argb = pixels[row * SIZE + col];
                context.fill(x, y, x + cellSize, y + cellSize, argb == 0 ? checker : argb);
            }
        }

        int paletteY = gridTop + SIZE * cellSize + 8;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = 20 + i * 24;
            context.fill(x + 3, paletteY + 3, x + 21, paletteY + 17, 0xFF000000 | PALETTE[i]);
        }

        if (!status.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(status), 20, this.height - 24, 0xAAAAAA);
        }
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
