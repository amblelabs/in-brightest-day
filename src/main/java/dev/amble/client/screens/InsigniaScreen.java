package dev.amble.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.client.render.InsigniaLayer;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.SetInsigniaAnchorC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.visuals.Insignia;
import dev.amble.core.visuals.InsigniaAnchor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class InsigniaScreen extends Screen {
    private static final int MAX_CELL = 8;
    private static final int MIN_CELL = 4;
    private static final int MARGIN_Y = 70;
    private static final int[][] FACES = {
            {8, 8, 40, 8},
            {20, 20, 20, 36},
            {4, 20, 4, 36},
            {20, 52, 4, 52},
            {44, 20, 44, 36},
            {36, 52, 52, 52}
    };
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;
    private static final int GRID_LINE = 0x40000000;
    private static final int CENTER_MARK = 0xFFFFFFFF;
    private static final int PREVIEW_ALPHA = 0xB0;

    private final @Nullable Screen parent;
    private final Supplier<Identifier> skin;
    private final Supplier<LanternCorps> corps;
    private final IntSupplier color;
    private final Consumer<InsigniaAnchor> apply;
    private InsigniaAnchor anchor;
    private boolean dragging;
    private int gridLeft;
    private int gridTop;
    private int cell = MAX_CELL;

    public InsigniaScreen(@Nullable Screen parent) {
        this(parent, BrightestDayAttachments.getInsigniaAnchor(Minecraft.getInstance().player), () -> Minecraft.getInstance().player.getSkin().body().texturePath(),
                () -> PowerRingItem.getWornCorps(Minecraft.getInstance().player).orElse(LanternCorps.GREEN), () -> CorpsColors.of(Minecraft.getInstance().player), anchor -> {
                    BrightestDayAttachments.setInsigniaAnchor(Minecraft.getInstance().player, anchor);
                    ClientPlayNetworking.send(new SetInsigniaAnchorC2SPayload(anchor));
                });
    }

    public InsigniaScreen(@Nullable Screen parent, InsigniaAnchor anchor, Supplier<Identifier> skin, Supplier<LanternCorps> corps, IntSupplier color, Consumer<InsigniaAnchor> apply) {
        super(Component.translatable("gui.brightestday.insignia"));
        this.parent = parent;
        this.anchor = anchor;
        this.skin = skin;
        this.corps = corps;
        this.color = color;
        this.apply = apply;
    }

    @Override
    protected void init() {
        this.cell = Mth.clamp((this.height - MARGIN_Y) / (int) InsigniaAnchor.HEIGHT, MIN_CELL, MAX_CELL);
        int gridWidth = this.gridWidth();
        int gridHeight = this.gridHeight();
        this.gridLeft = (this.width - gridWidth) / 2 - (BUTTON_WIDTH + GAP * 2) / 2;
        this.gridTop = (this.height - gridHeight) / 2;

        int buttonX = this.gridLeft + gridWidth + GAP * 3;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.insignia.reset"), button -> {
            this.update(InsigniaAnchor.DEFAULT);
            this.rebuildWidgets();
        }).bounds(buttonX, this.gridTop, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        int distanceSteps = Math.round(InsigniaAnchor.MAX_DISTANCE / InsigniaAnchor.DISTANCE_STEP);
        this.addRenderableWidget(new StepSlider(buttonX, this.gridTop + (BUTTON_HEIGHT + GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT,
                Math.round(this.anchor.distance() / InsigniaAnchor.DISTANCE_STEP), distanceSteps,
                step -> Component.translatable("gui.brightestday.insignia.distance", String.format("%.2f", step * InsigniaAnchor.DISTANCE_STEP)),
                step -> this.update(this.anchor.withDistance(step * InsigniaAnchor.DISTANCE_STEP).sanitized())));
        this.addRenderableWidget(new StepSlider(buttonX, this.gridTop + (BUTTON_HEIGHT + GAP) * 3, BUTTON_WIDTH, BUTTON_HEIGHT,
                this.anchor.echoes(), InsigniaAnchor.MAX_ECHOES,
                step -> Component.translatable("gui.brightestday.insignia.echoes", step),
                step -> this.update(this.anchor.withEchoes(step).sanitized())));
        int scaleMin = Math.round(InsigniaAnchor.MIN_SCALE / InsigniaAnchor.SCALE_STEP);
        int scaleSteps = Math.round(InsigniaAnchor.MAX_SCALE / InsigniaAnchor.SCALE_STEP) - scaleMin;
        this.addRenderableWidget(new StepSlider(buttonX, this.gridTop + (BUTTON_HEIGHT + GAP) * 4, BUTTON_WIDTH, BUTTON_HEIGHT,
                Math.round(this.anchor.scale() / InsigniaAnchor.SCALE_STEP) - scaleMin, scaleSteps,
                step -> Component.translatable("gui.brightestday.insignia.scale", String.format("%.2f", (step + scaleMin) * InsigniaAnchor.SCALE_STEP)),
                step -> this.update(this.anchor.withScale((step + scaleMin) * InsigniaAnchor.SCALE_STEP).sanitized())));
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(buttonX, this.gridTop + gridHeight - BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    private int gridWidth() {
        return this.cell * (int) InsigniaAnchor.WIDTH;
    }

    private int gridHeight() {
        return this.cell * (int) InsigniaAnchor.HEIGHT;
    }

    private boolean inGrid(double x, double y) {
        return x >= this.gridLeft && y >= this.gridTop && x <= this.gridLeft + this.gridWidth() && y <= this.gridTop + this.gridHeight();
    }

    private void moveTo(double x, double y) {
        float px = (float) Mth.clamp((x - this.gridLeft) / this.cell, 0.0, InsigniaAnchor.WIDTH);
        float py = (float) Mth.clamp((y - this.gridTop) / this.cell, 0.0, InsigniaAnchor.HEIGHT);
        this.update(this.anchor.withPosition(px, py).sanitized());
    }

    private void update(InsigniaAnchor next) {
        if (next.equals(this.anchor)) return;
        this.anchor = next;
        this.apply.accept(next);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !this.inGrid(event.x(), event.y())) return false;
        this.dragging = true;
        this.moveTo(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!this.dragging) return super.mouseDragged(event, dx, dy);
        this.moveTo(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);

        int gridWidth = this.gridWidth();
        int gridHeight = this.gridHeight();
        graphics.centeredText(this.font, this.title, this.gridLeft + gridWidth / 2, this.gridTop - 22, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("gui.brightestday.insignia.hint"), this.gridLeft + gridWidth / 2, this.gridTop + gridHeight + 8, 0xFFA0A0A0);

        Identifier skin = this.skin.get();
        InsigniaAnchor.Region selected = this.anchor.region();
        for (InsigniaAnchor.Region region : InsigniaAnchor.Region.values()) {
            int[] face = FACES[region.ordinal()];
            int left = this.gridLeft + region.left * this.cell;
            int top = this.gridTop + region.top * this.cell;
            int width = region.right - region.left;
            int height = region.bottom - region.top;
            int right = left + width * this.cell;
            int bottom = top + height * this.cell;
            graphics.fill(left - 1, top - 1, right + 1, bottom + 1, region == selected ? ARGB.opaque(this.color.getAsInt()) : 0xFF000000);
            graphics.fill(left, top, right, bottom, 0xFF000000);
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, left, top, face[0], face[1], width * this.cell, height * this.cell, width, height, 64, 64);
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, left, top, face[2], face[3], width * this.cell, height * this.cell, width, height, 64, 64);
            for (int column = 1; column < width; column++) graphics.fill(left + column * this.cell, top, left + column * this.cell + 1, bottom, GRID_LINE);
            for (int row = 1; row < height; row++) graphics.fill(left, top + row * this.cell, right, top + row * this.cell + 1, GRID_LINE);
        }

        LanternCorps corps = this.corps.get();
        Identifier texture = Insignia.texture(corps);
        if (texture == null) texture = Insignia.texture(LanternCorps.GREEN);
        int size = Math.round(InsigniaLayer.SIZE * this.anchor.scale() * 16.0F * this.cell);
        int centerX = this.gridLeft + Math.round(this.anchor.x() * this.cell);
        int centerY = this.gridTop + Math.round(this.anchor.y() * this.cell);
        int color = ARGB.color(PREVIEW_ALPHA, ARGB.opaque(this.color.getAsInt()));
        graphics.enableScissor(this.gridLeft - this.cell * 2, this.gridTop - this.cell * 2, this.gridLeft + gridWidth + this.cell * 2, this.gridTop + gridHeight + this.cell * 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, centerX - size / 2, centerY - size / 2, 0.0F, 0.0F, size, size, 16, 16, 16, 16, color);
        graphics.disableScissor();
        graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, CENTER_MARK);
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, CENTER_MARK);
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
