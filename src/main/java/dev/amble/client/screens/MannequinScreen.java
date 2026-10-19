package dev.amble.client.screens;

import dev.amble.client.render.SuitStyles;
import dev.amble.client.effects.PoseAnimations;
import dev.amble.client.poses.PoseLibrary;
import dev.amble.client.render.Holograms;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.mannequin.Hologram;
import dev.amble.core.mannequin.HologramSettings;
import dev.amble.core.menus.MannequinMenu;
import dev.amble.core.networking.payloads.c2s.MannequinEditC2SPayload;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.MaskLayer;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

public class MannequinScreen extends AbstractContainerScreen<MannequinMenu> {
    private static final int IMAGE_WIDTH = 284;
    private static final int IMAGE_HEIGHT = 274;
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 16;
    private static final int NAME_Y = 18;
    private static final int NAME_X = 44;
    private static final int APPLY_WIDTH = 50;
    private static final int TOGGLE_LEFT = 44;
    private static final int TOGGLE_MIDDLE = 124;
    private static final int TOGGLE_RIGHT = 206;
    private static final int TOGGLE_Y = 40;
    private static final int TOGGLE_STEP = 18;
    private static final int POSE_Y = 98;
    private static final int ARROW_WIDTH = 16;
    private static final int ROTATE_Y = 118;
    private static final int ROTATE_STEP = 15;
    private static final int EYES_Y = 138;
    private static final int INSIGNIA_Y = 156;
    private static final int RING_SLOT_INSET = 5;
    private static final int RING_SLOT_SIZE = 26;
    private static final int DIVIDER_GAP = 5;

    private HologramSettings settings = HologramSettings.DEFAULT;
    private boolean loaded;
    private @Nullable EditBox name;

    public MannequinScreen(MannequinMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        this.inventoryLabelX = MannequinMenu.INVENTORY_X;
        this.inventoryLabelY = MannequinMenu.INVENTORY_Y - 11;
    }

    private @Nullable Mannequin mannequin() {
        Entity entity = this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.menu.entityId());
        return entity instanceof Mannequin mannequin ? mannequin : null;
    }

    private Hologram hologram() {
        Mannequin mannequin = this.mannequin();
        return mannequin == null ? Hologram.EMPTY : Holograms.of(mannequin);
    }

    private boolean owner() {
        return this.hologram().owner().map(this.minecraft.player.getUUID()::equals).orElse(true);
    }

    private @Nullable LanternCorps corps() {
        Mannequin mannequin = this.mannequin();
        return mannequin == null ? null : Holograms.corps(mannequin).orElse(null);
    }

    private int color() {
        Mannequin mannequin = this.mannequin();
        return ARGB.opaque(mannequin == null ? Holograms.IDLE_COLOR : Holograms.color(mannequin));
    }

    private Identifier skin() {
        return this.mannequin() instanceof ClientAvatarEntity avatar ? avatar.getSkin().body().texturePath() : DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected void init() {
        super.init();
        if (!this.loaded) {
            this.settings = this.hologram().settings();
            this.loaded = this.mannequin() != null;
        }

        String typed = this.name == null ? this.settings.skin() : this.name.getValue();
        this.name = this.addRenderableWidget(new EditBox(this.font, this.leftPos + NAME_X, this.topPos + NAME_Y, IMAGE_WIDTH - NAME_X - MARGIN - APPLY_WIDTH - 4, ROW_HEIGHT,
                Component.translatable("gui.brightestday.mannequin.skin")));
        this.name.setMaxLength(HologramSettings.MAX_NAME);
        this.name.setHint(Component.translatable("gui.brightestday.mannequin.skin"));
        this.name.setValue(typed);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.mannequin.apply"), button -> this.applyName())
                .bounds(this.leftPos + IMAGE_WIDTH - MARGIN - APPLY_WIDTH, this.topPos + NAME_Y, APPLY_WIDTH, ROW_HEIGHT).build());

        if (this.owner()) {
            Component lockLabel = Component.translatable("gui.brightestday.mannequin.lock");
            this.addRenderableWidget(new LanternToggle(this.leftPos + IMAGE_WIDTH - MARGIN - LanternToggle.width(this.font, lockLabel), this.topPos + 1,
                    lockLabel, this.font, this.settings.locked(), value -> this.edit(s -> s.withLocked(value))));
        }

        this.addRenderableWidget(Button.builder(this.suitLabel(), button -> this.cycleSuit())
                .bounds(this.leftPos + TOGGLE_LEFT, this.topPos + TOGGLE_Y, TOGGLE_MIDDLE - TOGGLE_LEFT - 4, ROW_HEIGHT).build());
        this.toggle(TOGGLE_MIDDLE, 0, "gui.brightestday.mannequin.aura", this.settings.aura(), value -> s -> s.withAura(value));
        this.toggle(TOGGLE_RIGHT, 0, "gui.brightestday.mask", this.settings.mask(), value -> s -> s.withMask(value));
        this.toggle(TOGGLE_LEFT, 1, "gui.brightestday.insignia", this.settings.insignia(), value -> s -> s.withInsignia(value));
        this.toggle(TOGGLE_MIDDLE, 1, "gui.brightestday.mannequin.glow", this.settings.glow(), value -> s -> s.withGlow(value));
        this.toggle(TOGGLE_RIGHT, 1, "gui.brightestday.mannequin.pad", this.settings.pad(), value -> s -> s.withPad(value));
        this.toggle(TOGGLE_LEFT, 2, "gui.brightestday.mannequin.hide_name", this.settings.hideName(), value -> s -> s.withHideName(value));
        this.toggle(TOGGLE_MIDDLE, 2, "gui.brightestday.mannequin.hard_light", this.settings.hardLight(), value -> s -> s.withHardLight(value));
        this.toggle(TOGGLE_RIGHT, 2, "gui.brightestday.mannequin.left_handed", this.settings.leftHanded(), value -> s -> s.withLeftHanded(value));

        this.addRenderableWidget(Button.builder(Component.literal("◀"), button -> this.cyclePose(-1))
                .bounds(this.leftPos + MARGIN, this.topPos + POSE_Y, ARROW_WIDTH, ROW_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("▶"), button -> this.cyclePose(1))
                .bounds(this.leftPos + IMAGE_WIDTH - MARGIN - ARROW_WIDTH, this.topPos + POSE_Y, ARROW_WIDTH, ROW_HEIGHT).build());

        int steps = 360 / ROTATE_STEP;
        int step = Math.floorMod(Math.round(this.settings.yaw() / ROTATE_STEP), steps);
        this.addRenderableWidget(new StepSlider(this.leftPos + MARGIN, this.topPos + ROTATE_Y, (IMAGE_WIDTH - MARGIN * 2 - 4) / 2, ROW_HEIGHT, step, steps - 1,
                value -> Component.translatable("gui.brightestday.mannequin.rotation", value * ROTATE_STEP),
                value -> this.edit(s -> s.withYaw(Mth.wrapDegrees(value * ROTATE_STEP)))));
        int sliderWidth = (IMAGE_WIDTH - MARGIN * 2 - 4) / 2;
        int layerWidth = 64;
        this.addRenderableWidget(new MaskHeightSlider(this.leftPos + MARGIN + sliderWidth + 4, this.topPos + ROTATE_Y, sliderWidth - layerWidth - 4, ROW_HEIGHT,
                this.settings.maskOffset(), ColorTweak.MAX_MASK_OFFSET, value -> this.edit(s -> s.withMaskOffset(value))));
        this.addRenderableWidget(Button.builder(this.settings.maskLayer().label(), button -> {
                    MaskLayer next = this.settings.maskLayer().next();
                    button.setMessage(next.label());
                    this.edit(s -> s.withMaskLayer(next));
                })
                .bounds(this.leftPos + MARGIN + sliderWidth * 2 + 4 - layerWidth, this.topPos + ROTATE_Y, layerWidth, ROW_HEIGHT).build());

        int half = (IMAGE_WIDTH - MARGIN * 2 - 4) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.mannequin.copy_eyes"),
                        button -> this.edit(s -> s.withEyes(BrightestDayAttachments.getEyes(this.minecraft.player))))
                .bounds(this.leftPos + MARGIN, this.topPos + EYES_Y, half, ROW_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.mannequin.edit_eyes"),
                        button -> this.minecraft.gui.setScreen(new EyesScreen(this, this.settings.eyes(), this::skin, this::color, eyes -> this.edit(s -> s.withEyes(eyes)))))
                .bounds(this.leftPos + MARGIN + half + 4, this.topPos + EYES_Y, half, ROW_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.mannequin.copy_insignia"),
                        button -> this.edit(s -> s.withAnchor(BrightestDayAttachments.getInsigniaAnchor(this.minecraft.player))))
                .bounds(this.leftPos + MARGIN, this.topPos + INSIGNIA_Y, half, ROW_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.mannequin.edit_insignia"),
                        button -> this.minecraft.gui.setScreen(new InsigniaScreen(this, this.settings.anchor(), this::skin,
                                () -> this.corps() == null ? LanternCorps.GREEN : this.corps(), this::color, anchor -> this.edit(s -> s.withAnchor(anchor)))))
                .bounds(this.leftPos + MARGIN + half + 4, this.topPos + INSIGNIA_Y, half, ROW_HEIGHT).build());
    }

    private void toggle(int x, int row, String key, boolean selected, java.util.function.Function<Boolean, UnaryOperator<HologramSettings>> change) {
        this.addRenderableWidget(new LanternToggle(this.leftPos + x, this.topPos + TOGGLE_Y + row * TOGGLE_STEP, Component.translatable(key), this.font, selected,
                value -> this.edit(change.apply(value))));
    }

    private List<String> suitStyles() {
        LanternCorps corps = this.corps();
        return SuitStyles.styles(corps == null ? LanternCorps.GREEN : corps);
    }

    private Component suitLabel() {
        Component style = this.settings.suit()
                ? SuitStyles.name(SuitStyles.resolve(this.corps() == null ? LanternCorps.GREEN : this.corps(), this.settings.style()))
                : Component.translatable("gui.brightestday.mannequin.suit_off");
        return Component.translatable("gui.brightestday.mannequin.suit_style", style);
    }

    private void cycleSuit() {
        List<String> styles = this.suitStyles();
        int current = this.settings.suit() ? styles.indexOf(SuitStyles.resolve(this.corps() == null ? LanternCorps.GREEN : this.corps(), this.settings.style())) + 1 : 0;
        int next = (current + 1) % (styles.size() + 1);
        if (next == 0) this.edit(s -> s.withSuit(false));
        else this.edit(s -> s.withSuit(true).withStyle(styles.get(next - 1)));
        this.rebuildWidgets();
    }

    private List<PoseLibrary.Pose> poses() {
        return PoseAnimations.mannequinPoses(this.corps());
    }

    private void cyclePose(int direction) {
        int count = this.poses().size();
        if (count == 0) {
            this.edit(s -> s.withPose(0));
            return;
        }
        int current = Math.min(this.settings.pose(), count);
        this.edit(s -> s.withPose(Math.floorMod(current + direction, count + 1)));
    }

    private Component poseLabel() {
        List<PoseLibrary.Pose> poses = this.poses();
        int pose = this.settings.pose();
        if (pose <= 0 || poses.isEmpty()) return Component.translatable("gui.brightestday.mannequin.pose.none");
        return Component.translatable("gui.brightestday.mannequin.pose", poses.get((pose - 1) % poses.size()).name());
    }

    private void applyName() {
        if (this.name != null) this.edit(s -> s.withSkin(this.name.getValue()));
    }

    private void edit(UnaryOperator<HologramSettings> change) {
        HologramSettings next = change.apply(this.settings).sanitized();
        if (next.equals(this.settings)) return;
        this.settings = next;
        ClientPlayNetworking.send(new MannequinEditC2SPayload(this.menu.entityId(), next));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.name != null && this.name.isFocused()) {
            if (event.isEscape()) {
                this.name.setFocused(false);
                this.setFocused(null);
                return true;
            }
            if (event.isConfirmation()) {
                this.applyName();
                return true;
            }
            return this.name.keyPressed(event) || this.name.canConsumeInput() || super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int accent = this.color();
        LanternWidgets.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, accent);
        LanternWidgets.divider(graphics, this.leftPos + MARGIN, this.leftPos + this.imageWidth - MARGIN,
                this.topPos + this.inventoryLabelY - DIVIDER_GAP, accent);

        for (Slot slot : this.menu.slots) {
            if (slot == this.menu.getSlot(0)) {
                LanternWidgets.ringSlot(graphics, this.leftPos + slot.x - RING_SLOT_INSET, this.topPos + slot.y - RING_SLOT_INSET, RING_SLOT_SIZE, accent);
            } else {
                LanternWidgets.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, 18, accent);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LanternWidgets.TEXT, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LanternWidgets.TEXT_DIM, false);
        graphics.text(this.font, Component.translatable("gui.brightestday.mannequin.skin_label"), MARGIN, NAME_Y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, LanternWidgets.TEXT_DIM, false);
        graphics.centeredText(this.font, this.poseLabel(), IMAGE_WIDTH / 2, POSE_Y + (ROW_HEIGHT - this.font.lineHeight) / 2 + 1, LanternWidgets.TEXT);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (this.mannequin() == null) this.minecraft.player.closeContainer();
    }
}
