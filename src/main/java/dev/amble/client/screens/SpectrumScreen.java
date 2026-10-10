package dev.amble.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.networking.payloads.c2s.AbandonPilgrimageC2SPayload;
import dev.amble.core.networking.payloads.c2s.RingBondC2SPayload;
import dev.amble.core.networking.payloads.c2s.SetSuccessorC2SPayload;
import dev.amble.core.progression.Emotion;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Milestones;
import dev.amble.core.progression.Pilgrimage;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.progression.SpectrumMeters;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class SpectrumScreen extends Screen {
    private static final int WIDTH = 304;
    private static final int HEIGHT = 214;
    private static final int MARGIN = 10;
    private static final int LIST_WIDTH = 92;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_Y = 28;
    private static final int DETAIL_X = MARGIN + LIST_WIDTH + 10;
    private static final int DETAIL_WIDTH = WIDTH - DETAIL_X - MARGIN;
    private static final int METER_HEIGHT = 6;
    private static final int TASK_BAR_HEIGHT = 3;
    private static final int DONE = 0xFF7DFF9A;
    private static final int LOCKED = 0xFF5E5A50;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_PADDING = 6;
    private static final int BUTTON_Y = 5;
    private static final int BUTTON_GAP = 4;
    private static final long CONFIRM_MILLIS = 4000L;
    private static final int HOPE = ARGB.opaque(LanternCorps.BLUE.color());
    private static final int SUCCESSOR_GAP = 4;
    private static final int SUCCESSOR_HEIGHT = 18;
    private static final int SUCCESSOR_BOX = 110;
    private static final int SUCCESSOR_BUTTON = 44;
    private static final Component SUCCESSOR_LABEL = Component.translatable("gui.brightestday.spectrum.successor");

    private final @Nullable Screen parent;
    private Emotion selected;
    private boolean chosen;
    private int left;
    private int top;
    private long confirmUntil;
    private @Nullable String confirmKey;
    private final List<AbstractWidget> successorRow = new ArrayList<>();
    private @Nullable EditBox successor;

    private record Action(String key, Component label, int color, boolean enabled, boolean confirm, Runnable run) {}

    public SpectrumScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.brightestday.spectrum.title"));
        this.parent = parent;
        this.selected = Emotion.WILL;
    }

    @Override
    protected void init() {
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT - SUCCESSOR_GAP - SUCCESSOR_HEIGHT) / 2;
        if (!this.chosen && this.minecraft.player != null) {
            PowerRingItem.getWornCorps(this.minecraft.player).flatMap(Emotion::of).ifPresent(emotion -> this.selected = emotion);
        }
        this.chosen = true;

        String typed = this.successor != null ? this.successor.getValue()
                : this.minecraft.player == null ? "" : PowerRingItem.getWornRing(this.minecraft.player).getOrDefault(BrightestDayComponents.SUCCESSOR, "");
        this.successorRow.clear();
        int y = this.top + HEIGHT + SUCCESSOR_GAP;
        int x = this.left + MARGIN + this.font.width(SUCCESSOR_LABEL) + 6;
        EditBox box = new EditBox(this.font, x, y, SUCCESSOR_BOX, SUCCESSOR_HEIGHT, SUCCESSOR_LABEL);
        box.setMaxLength(16);
        box.setHint(Component.translatable("gui.brightestday.spectrum.successor.hint"));
        box.setValue(typed);
        this.successor = box;
        this.successorRow.add(this.addRenderableWidget(box));
        x += SUCCESSOR_BOX + 4;
        this.successorRow.add(this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.spectrum.successor.set"),
                button -> ClientPlayNetworking.send(new SetSuccessorC2SPayload(box.getValue()))).bounds(x, y, SUCCESSOR_BUTTON, SUCCESSOR_HEIGHT).build()));
        x += SUCCESSOR_BUTTON + 4;
        this.successorRow.add(this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.spectrum.successor.clear"), button -> {
            box.setValue("");
            ClientPlayNetworking.send(new SetSuccessorC2SPayload(""));
        }).bounds(x, y, SUCCESSOR_BUTTON, SUCCESSOR_HEIGHT).build()));
    }

    private boolean bonded() {
        return this.minecraft.player != null && this.minecraft.player.getAttachedOrElse(RingBonds.STATUS, RingBonds.Status.NONE).corps().isPresent();
    }

    private int rowY(int index) {
        return this.top + LIST_Y + index * ROW_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean bonded = this.bonded();
        for (AbstractWidget widget : this.successorRow) widget.visible = bonded;
        super.extractRenderState(graphics, mouseX, mouseY, a);
        Player player = this.minecraft.player;
        if (player == null) return;

        LanternCorps corps = this.selected.corps();
        int accent = ARGB.opaque(corps.color());
        LanternWidgets.panel(graphics, this.left, this.top, WIDTH, HEIGHT, accent);
        graphics.text(this.font, this.title, this.left + MARGIN, this.top + 11, LanternWidgets.TEXT, true);
        LanternWidgets.divider(graphics, this.left + MARGIN, this.left + WIDTH - MARGIN, this.top + 23, accent);

        this.list(graphics, player, mouseX, mouseY);
        this.detail(graphics, player, corps, accent);
        this.actionButtons(graphics, player, mouseX, mouseY);
        if (bonded) graphics.text(this.font, SUCCESSOR_LABEL, this.left + MARGIN, this.top + HEIGHT + SUCCESSOR_GAP + (SUCCESSOR_HEIGHT - 8) / 2, LanternWidgets.TEXT, true);
    }

    private void list(GuiGraphicsExtractor graphics, Player player, int mouseX, int mouseY) {
        LanternCorps worn = PowerRingItem.getWornCorps(player).orElse(null);
        Emotion[] emotions = Emotion.values();
        for (int i = 0; i < emotions.length; i++) {
            Emotion emotion = emotions[i];
            int color = ARGB.opaque(emotion.corps().color());
            int x = this.left + MARGIN;
            int y = this.rowY(i);
            boolean hovered = mouseX >= x && mouseX < x + LIST_WIDTH && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            boolean chosen = emotion == this.selected;

            if (chosen || hovered) graphics.fill(x, y, x + LIST_WIDTH, y + ROW_HEIGHT - 2, ARGB.color(chosen ? 0x50 : 0x28, color));
            graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 2, color);

            Component name = emotion.corps().displayName();
            String label = this.font.plainSubstrByWidth(name.getString(), LIST_WIDTH - 22);
            graphics.text(this.font, label, x + 6, y + 3, chosen ? 0xFFFFFFFF : LanternWidgets.TEXT, true);
            graphics.text(this.font, String.valueOf(RingRanks.rank(player, emotion.corps())), x + LIST_WIDTH - 9, y + 3,
                    emotion.corps() == worn ? DONE : LanternWidgets.TEXT_DIM, true);

            int barWidth = LIST_WIDTH - 10;
            int filled = Math.round(barWidth * SpectrumMeters.get(player, emotion) / (float) Emotion.MAX);
            graphics.fill(x + 6, y + 14, x + 6 + barWidth, y + 16, LanternWidgets.BAR_EMPTY);
            graphics.fill(x + 6, y + 14, x + 6 + filled, y + 16, color);
        }
    }

    private void detail(GuiGraphicsExtractor graphics, Player player, LanternCorps corps, int accent) {
        int x = this.left + DETAIL_X;
        int y = this.top + LIST_Y;
        int rank = RingRanks.rank(player, corps);

        graphics.text(this.font, corps.displayName(), x, y, accent, true);
        Component rankText = Component.translatable("gui.brightestday.spectrum.rank", rank, RingRanks.MAX_RANK);
        graphics.text(this.font, rankText, x + DETAIL_WIDTH - this.font.width(rankText), y, LanternWidgets.TEXT, true);
        y += 12;

        for (FormattedCharSequence line : this.font.split(Component.translatable("gui.brightestday.spectrum.path." + corps.getSerializedName()), DETAIL_WIDTH)) {
            graphics.text(this.font, line, x, y, LanternWidgets.TEXT_DIM, false);
            y += 9;
        }
        y += 4;

        int meter = SpectrumMeters.get(player, this.selected);
        graphics.text(this.font, Component.translatable("gui.brightestday.spectrum.meter",
                Component.translatable("emotion.brightestday." + this.selected.getSerializedName()), meter, Emotion.MAX), x, y, LanternWidgets.TEXT, false);
        y += 10;
        graphics.fill(x - 1, y - 1, x + DETAIL_WIDTH + 1, y + METER_HEIGHT + 1, LanternWidgets.BAR_FRAME);
        graphics.fill(x, y, x + DETAIL_WIDTH, y + METER_HEIGHT, LanternWidgets.BAR_EMPTY);
        graphics.fillGradient(x, y, x + Math.round(DETAIL_WIDTH * meter / (float) Emotion.MAX), y + METER_HEIGHT,
                ARGB.srgbLerp(0.4F, accent, 0xFFFFFFFF), accent);
        int gate = x + DETAIL_WIDTH * Emotion.GATE / Emotion.MAX;
        graphics.fill(gate, y - 2, gate + 1, y + METER_HEIGHT + 2, meter >= Emotion.GATE ? DONE : 0xFFFFFFFF);
        y += METER_HEIGHT + 4;

        int capacity = Math.round(RingRanks.capacityFraction(rank) * 100);
        int arsenal = Math.round(RingRanks.arsenalFraction(rank) * 100);
        graphics.text(this.font, Component.translatable("gui.brightestday.spectrum.capacity", capacity, arsenal), x, y, LanternWidgets.TEXT_DIM, false);
        y += 14;

        graphics.text(this.font, Component.translatable("gui.brightestday.spectrum.milestones"), x, y, LanternWidgets.TEXT, true);
        y += 11;
        RingRanks.Ranks ranks = RingRanks.get(player);
        for (int tier = Milestones.FIRST_TIER; tier <= Milestones.LAST_TIER; tier++) {
            Milestone milestone = ranks.milestone(corps, tier).orElse(null);
            if (milestone == null) {
                graphics.text(this.font, Component.translatable("gui.brightestday.spectrum.unrevealed", tier), x + 9, y, LOCKED, false);
                y += 17;
                continue;
            }
            y = this.task(graphics, ranks, milestone, rank, accent, x, y);
        }
    }

    private int task(GuiGraphicsExtractor graphics, RingRanks.Ranks ranks, Milestone task, int rank, int accent, int x, int y) {
        boolean done = rank >= task.tier();
        boolean active = rank == task.tier() - 1;
        int progress = done ? task.goal() : active ? ranks.counter(task.key()) : 0;
        int color = done ? DONE : active ? LanternWidgets.TEXT : LOCKED;
        String glyph = done ? "✔" : active ? "▶" : "·";

        graphics.text(this.font, glyph, x, y, color, false);
        Component label = Component.translatable("gui.brightestday.spectrum.rank_short", task.tier()).append(" ")
                .append(Component.translatable(task.translationKey(), task.goal()));
        for (FormattedCharSequence line : this.font.split(label, DETAIL_WIDTH - 10)) {
            graphics.text(this.font, line, x + 9, y, color, false);
            y += 9;
        }

        int barWidth = DETAIL_WIDTH - 46;
        int filled = Math.round(barWidth * Math.min(1.0F, progress / (float) task.goal()));
        graphics.fill(x + 9, y + 1, x + 9 + barWidth, y + 1 + TASK_BAR_HEIGHT, LanternWidgets.BAR_EMPTY);
        graphics.fill(x + 9, y + 1, x + 9 + filled, y + 1 + TASK_BAR_HEIGHT, done ? DONE : accent);
        String count = progress + "/" + task.goal();
        graphics.text(this.font, count, x + DETAIL_WIDTH - this.font.width(count), y - 2, color, false);
        return y + 8;
    }

    private boolean pilgrimaging(Player player) {
        return player.getAttachedOrElse(Pilgrimage.STATE, Pilgrimage.State.NONE).active();
    }

    private boolean confirming(String key) {
        return key.equals(this.confirmKey) && Util.getMillis() < this.confirmUntil;
    }

    private List<Action> actions(Player player) {
        List<Action> actions = new ArrayList<>();
        if (this.pilgrimaging(player)) {
            actions.add(new Action("abandon", Component.translatable(this.confirming("abandon") ? "gui.brightestday.spectrum.abandon.confirm" : "gui.brightestday.spectrum.abandon"),
                    HOPE, true, true, () -> ClientPlayNetworking.send(AbandonPilgrimageC2SPayload.INSTANCE)));
        }
        RingBonds.Status status = player.getAttachedOrElse(RingBonds.STATUS, RingBonds.Status.NONE);
        status.corps().ifPresent(corps -> {
            int color = ARGB.opaque(corps.color());
            if (status.away()) {
                Component label = status.cooldown() > 0
                        ? Component.translatable("gui.brightestday.spectrum.recall.cooldown", status.cooldown())
                        : Component.translatable("gui.brightestday.spectrum.recall");
                actions.add(new Action("recall", label, color, status.cooldown() <= 0, false, () -> ClientPlayNetworking.send(new RingBondC2SPayload(false))));
            }
            actions.add(new Action("relinquish", Component.translatable(this.confirming("relinquish") ? "gui.brightestday.spectrum.relinquish.confirm" : "gui.brightestday.spectrum.relinquish"),
                    color, true, true, () -> ClientPlayNetworking.send(new RingBondC2SPayload(true))));
        });
        return actions;
    }

    private int actionWidth(Action action) {
        return this.font.width(action.label()) + BUTTON_PADDING * 2;
    }

    private @Nullable Action actionAt(Player player, double mouseX, double mouseY) {
        int x = this.left + WIDTH - MARGIN;
        int y = this.top + BUTTON_Y;
        for (Action action : this.actions(player)) {
            int width = this.actionWidth(action);
            x -= width;
            if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + BUTTON_HEIGHT) return action;
            x -= BUTTON_GAP;
        }
        return null;
    }

    private void actionButtons(GuiGraphicsExtractor graphics, Player player, int mouseX, int mouseY) {
        int x = this.left + WIDTH - MARGIN;
        int y = this.top + BUTTON_Y;
        Action hoveredAction = this.actionAt(player, mouseX, mouseY);
        for (Action action : this.actions(player)) {
            int width = this.actionWidth(action);
            x -= width;
            boolean hovered = hoveredAction != null && hoveredAction.key().equals(action.key()) && action.enabled();
            LanternWidgets.button(graphics, x, y, width, BUTTON_HEIGHT, action.color(), action.enabled(), hovered || this.confirming(action.key()), 1.0F);
            int color = !action.enabled() ? LanternWidgets.TEXT_DIM : hovered ? 0xFFFFFFFF : LanternWidgets.TEXT;
            graphics.text(this.font, action.label(), x + BUTTON_PADDING, y + (BUTTON_HEIGHT - this.font.lineHeight) / 2 + 1, color, true);
            x -= BUTTON_GAP;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            Action action = this.minecraft.player != null ? this.actionAt(this.minecraft.player, event.x(), event.y()) : null;
            if (action != null) {
                if (!action.enabled()) return true;
                if (!action.confirm() || this.confirming(action.key())) {
                    this.confirmUntil = 0L;
                    this.confirmKey = null;
                    action.run().run();
                } else {
                    this.confirmKey = action.key();
                    this.confirmUntil = Util.getMillis() + CONFIRM_MILLIS;
                }
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            int x = this.left + MARGIN;
            Emotion[] emotions = Emotion.values();
            for (int i = 0; i < emotions.length; i++) {
                int y = this.rowY(i);
                if (event.x() < x || event.x() >= x + LIST_WIDTH || event.y() < y || event.y() >= y + ROW_HEIGHT - 2) continue;
                this.selected = emotions[i];
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
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
