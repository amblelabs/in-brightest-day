package dev.amble.client.screens;

import dev.amble.client.team.TeamScreen;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.SetColorTweakC2SPayload;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.CorpsColors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import dev.amble.core.menus.LanternMenu;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class LanternScreen extends AbstractContainerScreen<LanternMenu> {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 206;
    private static final int SLIDER_X = 8;
    private static final int SLIDER_WIDTH = 160;
    private static final int SLIDER_HEIGHT = 16;
    private static final int BRIGHTNESS_Y = 50;
    private static final int SATURATION_Y = 70;
    private static final int MASK_Y = 90;
    private static final int AURA_Y = 4;
    private static final int AURA_MARGIN = 7;
    private static final int INFO_X = 50;
    private static final int BAR_Y = 36;
    private static final int INFO_WIDTH = 118;
    private static final int PERCENT_GAP = 4;
    private static final int BAR_WIDTH = 90;
    private static final int BAR_HEIGHT = 5;

    private static final int RING_SLOT_INSET = 5;
    private static final int RING_SLOT_SIZE = 26;
    private static final int DIVIDER_MARGIN = 7;
    private static final int DIVIDER_GAP = 5;

    private @Nullable ColorTweakSlider brightness;
    private @Nullable ColorTweakSlider saturation;
    private @Nullable AuraToggle aura;
    private @Nullable MaskHeightSlider maskHeight;

    public LanternScreen(LanternMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        this.inventoryLabelY = IMAGE_HEIGHT - 94;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new IconButton(
                this.leftPos - IconButton.SIZE - 2, this.topPos + 4,
                new ItemStack(Items.CRAFTING_TABLE),
                Component.translatable("gui.brightestday.inventory"),
                this::returnToInventory,
                true
        ));
        this.addRenderableWidget(new IconButton(
                this.leftPos - IconButton.SIZE - 2, this.topPos + 6 + IconButton.SIZE,
                new ItemStack(Items.ENDER_EYE),
                Component.translatable("gui.brightestday.eyes"),
                () -> this.minecraft.gui.setScreen(new EyesScreen(this)),
                true
        ));
        this.addRenderableWidget(new IconButton(
                this.leftPos - IconButton.SIZE - 2, this.topPos + 8 + IconButton.SIZE * 2,
                new ItemStack(Items.LEAD),
                Component.translatable("gui.brightestday.team.title"),
                () -> this.minecraft.gui.setScreen(new TeamScreen(this)),
                true
        ));
        this.addRenderableWidget(new IconButton(
                this.leftPos - IconButton.SIZE - 2, this.topPos + 10 + IconButton.SIZE * 3,
                new ItemStack(Items.AMETHYST_CLUSTER),
                Component.translatable("gui.brightestday.spectrum.title"),
                () -> this.minecraft.gui.setScreen(new SpectrumScreen(this)),
                true
        ));
        this.addRenderableWidget(new IconButton(
                this.leftPos - IconButton.SIZE - 2, this.topPos + 12 + IconButton.SIZE * 4,
                new ItemStack(Items.GLOW_ITEM_FRAME),
                Component.translatable("gui.brightestday.insignia"),
                () -> this.minecraft.gui.setScreen(new InsigniaScreen(this)),
                true
        ));

        ColorTweak tweak = BrightestDayAttachments.getColorTweak(this.minecraft.player);
        this.brightness = this.addRenderableWidget(new ColorTweakSlider(
                this.leftPos + SLIDER_X, this.topPos + BRIGHTNESS_Y, SLIDER_WIDTH, SLIDER_HEIGHT,
                "gui.brightestday.brightness", tweak.brightness(), -1.0F, 1.0F, value -> this.updateTweak()));
        this.saturation = this.addRenderableWidget(new ColorTweakSlider(
                this.leftPos + SLIDER_X, this.topPos + SATURATION_Y, SLIDER_WIDTH, SLIDER_HEIGHT,
                "gui.brightestday.saturation", tweak.saturation(), ColorTweak.MIN_SATURATION, 1.0F, value -> this.updateTweak()));

        this.aura = this.addRenderableWidget(new AuraToggle(
                this.leftPos + IMAGE_WIDTH - AURA_MARGIN - AuraToggle.width(this.font), this.topPos + AURA_Y,
                this.font, AuraToggle.Mode.of(tweak.aura(), tweak.auraFlightOnly()), value -> this.updateTweak()));

        this.maskHeight = this.addRenderableWidget(new MaskHeightSlider(
                this.leftPos + SLIDER_X, this.topPos + MASK_Y, SLIDER_WIDTH, SLIDER_HEIGHT,
                tweak.maskOffset(), ColorTweak.MAX_MASK_OFFSET, value -> this.updateTweak()));
    }

    private void updateTweak() {
        if (this.brightness == null || this.saturation == null || this.aura == null || this.maskHeight == null) return;

        ColorTweak current = BrightestDayAttachments.getColorTweak(this.minecraft.player);
        ColorTweak tweak = new ColorTweak(this.brightness.tweak(), this.saturation.tweak(), this.aura.mode() != AuraToggle.Mode.OFF, this.aura.mode() != AuraToggle.Mode.ALWAYS, current.suit(),
                current.mask(), this.maskHeight.offset());
        if (tweak.equals(current)) return;

        BrightestDayAttachments.setColorTweak(this.minecraft.player, tweak);
        ClientPlayNetworking.send(new SetColorTweakC2SPayload(tweak));
    }

    private int tint(LanternCorps corps) {
        return ARGB.opaque(CorpsColors.apply(corps.color(), BrightestDayAttachments.getColorTweak(this.minecraft.player)));
    }

    private void returnToInventory() {
        this.minecraft.player.closeContainer();
        this.minecraft.gui.setScreen(new InventoryScreen(this.minecraft.player));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int accent = LanternWidgets.accent();
        LanternWidgets.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, accent);
        LanternWidgets.divider(graphics, this.leftPos + DIVIDER_MARGIN, this.leftPos + this.imageWidth - DIVIDER_MARGIN,
                this.topPos + this.inventoryLabelY - DIVIDER_GAP, accent);

        for (Slot slot : this.menu.slots) {
            if (slot == this.menu.getSlot(0)) {
                LanternWidgets.ringSlot(graphics, this.leftPos + slot.x - RING_SLOT_INSET, this.topPos + slot.y - RING_SLOT_INSET, RING_SLOT_SIZE, accent);
            } else {
                LanternWidgets.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 18, accent);
            }
        }

        ItemStack ring = this.menu.getRing();
        Optional<LanternCorps> corps = PowerRingItem.getCorps(ring);
        if (corps.isPresent() && !PowerRingItem.usesPower(ring)) return;

        int barX = this.leftPos + INFO_X;
        int barY = this.topPos + BAR_Y;
        graphics.fill(barX - 1, barY - 1, barX + BAR_WIDTH + 1, barY + BAR_HEIGHT + 1, LanternWidgets.BAR_FRAME);
        graphics.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, LanternWidgets.BAR_EMPTY);
        if (corps.isPresent()) {
            int filled = Math.round(BAR_WIDTH * PowerRingItem.getChargeFraction(ring));
            graphics.fill(barX, barY, barX + filled, barY + BAR_HEIGHT, this.tint(corps.get()));
            graphics.fill(barX, barY, barX + filled, barY + 1, ARGB.color(96, 255, 255, 255));
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LanternWidgets.TEXT, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LanternWidgets.TEXT_DIM, false);

        ItemStack ring = this.menu.getRing();
        Optional<LanternCorps> corps = PowerRingItem.getCorps(ring);
        if (corps.isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.brightestday.no_ring"), INFO_X, BAR_Y - 12, LanternWidgets.TEXT_DIM, false);
            return;
        }

        String name = Component.translatable(corps.get().getTranslationKey()).getString();
        if (this.font.width(name) > INFO_WIDTH) name = this.font.plainSubstrByWidth(name, INFO_WIDTH - this.font.width("…")) + "…";
        graphics.text(this.font, name, INFO_X, BAR_Y - 12, this.tint(corps.get()), true);
        if (!PowerRingItem.usesPower(ring)) return;

        int percent = Math.round(PowerRingItem.getChargeFraction(ring) * 100);
        graphics.text(this.font, percent + "%", INFO_X + BAR_WIDTH + PERCENT_GAP, BAR_Y - 2, LanternWidgets.TEXT, false);
    }
}
