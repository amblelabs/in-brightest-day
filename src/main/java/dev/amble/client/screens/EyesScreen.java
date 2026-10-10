package dev.amble.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.networking.payloads.c2s.SetEyesC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.EyePaint;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class EyesScreen extends Screen {
    private static final int CELL = 16;
    private static final int GRID = CELL * EyePaint.SIZE;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int GRID_LINE = 0x40000000;
    private static final int HOVER = 0xA0FFFFFF;
    private static final int BRIGHTNESS_STEP = 10;

    private final @Nullable Screen parent;
    private final Supplier<Identifier> skin;
    private final IntSupplier color;
    private final Consumer<EyePaint> apply;
    private EyePaint eyes;
    private int brush = EyePaint.WHITE;
    private int stroke = EyePaint.NONE;
    private boolean painting;
    private int gridLeft;
    private int gridTop;
    private @Nullable Button whiteButton;
    private @Nullable Button corpsButton;

    public EyesScreen(@Nullable Screen parent) {
        this(parent, BrightestDayAttachments.getEyes(Minecraft.getInstance().player), () -> Minecraft.getInstance().player.getSkin().body().texturePath(),
                () -> CorpsColors.of(Minecraft.getInstance().player), eyes -> {
                    BrightestDayAttachments.setEyes(Minecraft.getInstance().player, eyes);
                    ClientPlayNetworking.send(new SetEyesC2SPayload(eyes));
                });
    }

    public EyesScreen(@Nullable Screen parent, EyePaint eyes, Supplier<Identifier> skin, IntSupplier color, Consumer<EyePaint> apply) {
        super(Component.translatable("gui.brightestday.eyes"));
        this.parent = parent;
        this.eyes = eyes;
        this.skin = skin;
        this.color = color;
        this.apply = apply;
    }

    @Override
    protected void init() {
        this.gridLeft = (this.width - GRID) / 2 - (BUTTON_WIDTH + GAP * 2) / 2;
        this.gridTop = (this.height - GRID) / 2;

        int buttonX = this.gridLeft + GRID + GAP * 3;
        this.whiteButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.selectBrush(EyePaint.WHITE))
                .bounds(buttonX, this.gridTop, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        this.corpsButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> this.selectBrush(EyePaint.CORPS))
                .bounds(buttonX, this.gridTop + BUTTON_HEIGHT + GAP, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.eyes.clear"), button -> this.update(this.eyes.cleared()))
                .bounds(buttonX, this.gridTop + (BUTTON_HEIGHT + GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        int brightnessStep = Math.round((this.eyes.brightness() - EyePaint.MIN_BRIGHTNESS) / (float) BRIGHTNESS_STEP);
        this.addRenderableWidget(new StepSlider(buttonX, this.gridTop + (BUTTON_HEIGHT + GAP) * 3, BUTTON_WIDTH, BUTTON_HEIGHT, brightnessStep,
                (EyePaint.MAX_BRIGHTNESS - EyePaint.MIN_BRIGHTNESS) / BRIGHTNESS_STEP,
                step -> Component.translatable("gui.brightestday.eyes.brightness", EyePaint.MIN_BRIGHTNESS + step * BRIGHTNESS_STEP),
                step -> this.update(this.eyes.withBrightness(EyePaint.MIN_BRIGHTNESS + step * BRIGHTNESS_STEP))));
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(buttonX, this.gridTop + GRID - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        this.refreshBrushes();
    }

    private void selectBrush(int brush) {
        this.brush = brush;
        this.refreshBrushes();
    }

    private void refreshBrushes() {
        if (this.whiteButton != null) this.whiteButton.setMessage(this.brushLabel(EyePaint.WHITE, "gui.brightestday.eyes.white"));
        if (this.corpsButton != null) this.corpsButton.setMessage(this.brushLabel(EyePaint.CORPS, "gui.brightestday.eyes.corps"));
    }

    private Component brushLabel(int brush, String key) {
        Component label = Component.translatable(key);
        return this.brush == brush ? Component.literal("▶ ").append(label) : label;
    }

    private int corpsColor() {
        return ARGB.opaque(this.color.getAsInt());
    }

    private int cellAt(double x, double y) {
        int column = (int) Math.floor((x - this.gridLeft) / CELL);
        int row = (int) Math.floor((y - this.gridTop) / CELL);
        if (x < this.gridLeft || y < this.gridTop || column >= EyePaint.SIZE || row >= EyePaint.SIZE) return -1;
        return row * EyePaint.SIZE + column;
    }

    private void paint(int cell) {
        this.update(this.eyes.with(cell % EyePaint.SIZE, cell / EyePaint.SIZE, this.stroke));
    }

    private void update(EyePaint next) {
        if (next.equals(this.eyes)) return;
        this.eyes = next;
        this.apply.accept(next);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        int cell = this.cellAt(event.x(), event.y());
        if (cell < 0) return false;

        this.stroke = event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? EyePaint.NONE : this.brush;
        this.painting = true;
        this.paint(cell);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!this.painting) return super.mouseDragged(event, dx, dy);
        int cell = this.cellAt(event.x(), event.y());
        if (cell >= 0) this.paint(cell);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.painting = false;
        return super.mouseReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        graphics.centeredText(this.font, this.title, this.gridLeft + GRID / 2, this.gridTop - 22, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("gui.brightestday.eyes.hint"), this.gridLeft + GRID / 2, this.gridTop + GRID + 8, 0xFFA0A0A0);

        Identifier skin = this.skin.get();
        graphics.fill(this.gridLeft - 1, this.gridTop - 1, this.gridLeft + GRID + 1, this.gridTop + GRID + 1, 0xFF000000);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, this.gridLeft, this.gridTop, 8.0F, 8.0F, GRID, GRID, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, skin, this.gridLeft, this.gridTop, 40.0F, 8.0F, GRID, GRID, 8, 8, 64, 64);

        int corps = this.corpsColor();
        for (int row = 0; row < EyePaint.SIZE; row++) {
            for (int column = 0; column < EyePaint.SIZE; column++) {
                int kind = this.eyes.get(column, row);
                if (kind == EyePaint.NONE) continue;
                int x = this.gridLeft + column * CELL;
                int y = this.gridTop + row * CELL;
                graphics.fill(x, y, x + CELL, y + CELL, kind == EyePaint.WHITE ? 0xFFFFFFFF : corps);
            }
        }
        for (int i = 1; i < EyePaint.SIZE; i++) {
            graphics.fill(this.gridLeft + i * CELL, this.gridTop, this.gridLeft + i * CELL + 1, this.gridTop + GRID, GRID_LINE);
            graphics.fill(this.gridLeft, this.gridTop + i * CELL, this.gridLeft + GRID, this.gridTop + i * CELL + 1, GRID_LINE);
        }

        int hovered = this.cellAt(mouseX, mouseY);
        if (hovered >= 0) {
            graphics.outline(this.gridLeft + (hovered % EyePaint.SIZE) * CELL, this.gridTop + (hovered / EyePaint.SIZE) * CELL, CELL, CELL, HOVER);
        }

        int swatchX = this.gridLeft + GRID + GAP;
        graphics.fill(swatchX, this.gridTop + 6, swatchX + 6, this.gridTop + 14, 0xFFFFFFFF);
        graphics.fill(swatchX, this.gridTop + BUTTON_HEIGHT + GAP + 6, swatchX + 6, this.gridTop + BUTTON_HEIGHT + GAP + 14, corps);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
