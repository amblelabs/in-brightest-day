package dev.amble.client.hud;

import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.CorpsSynergy;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RedBlood;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;

public final class RingChargeHud {
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int OFFHAND_SLOT_WIDTH = 29;
    private static final int BAR_WIDTH = 2;
    private static final int ICON_SIZE = 16;
    private static final float LOW_CHARGE = 0.2F;
    private static final int BLOOD_COLOR = 0xFF8A0A12;

    public static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, BrightestDay.id("ring_charge"), RingChargeHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator()) return;

        ItemStack ring = PowerRingItem.getWornRing(player);
        if (ring.isEmpty()) return;

        float charge = PowerRingItem.getChargeFraction(ring);
        int color = ARGB.opaque(CorpsColors.apply(PowerRingItem.getCorps(ring).orElse(LanternCorps.GREEN).color(), BrightestDayAttachments.getColorTweak(player)));
        if (charge < LOW_CHARGE && (player.tickCount / 10) % 2 == 0) {
            color = ARGB.color(96, color);
        }

        int x = graphics.guiWidth() / 2 - HOTBAR_HALF_WIDTH - OFFHAND_SLOT_WIDTH - ICON_SIZE - BAR_WIDTH - 6;
        int y = graphics.guiHeight() - ICON_SIZE - 3;

        graphics.item(ring, x, y);
        if (!PowerRingItem.usesPower(ring)) return;

        int barX = x + ICON_SIZE + 2;
        int filled = Math.round(ICON_SIZE * charge);
        graphics.fill(barX, y, barX + BAR_WIDTH, y + ICON_SIZE, 0x80000000);
        graphics.fill(barX, y + ICON_SIZE - filled, barX + BAR_WIDTH, y + ICON_SIZE, color);

        int blood = RedBlood.get(player);
        if (blood > 0) {
            int bloodX = barX + BAR_WIDTH + 1;
            int bloodFilled = Math.max(1, Math.round(ICON_SIZE * blood / (float) RedBlood.MAX));
            int bloodColor = blood < RedBlood.MAX / 5 && (player.tickCount / 6) % 2 == 0 ? ARGB.color(110, BLOOD_COLOR) : BLOOD_COLOR;
            graphics.fill(bloodX, y, bloodX + BAR_WIDTH, y + ICON_SIZE, 0x80000000);
            graphics.fill(bloodX, y + ICON_SIZE - bloodFilled, bloodX + BAR_WIDTH, y + ICON_SIZE, bloodColor);
        }

        CorpsSynergy.State synergy = CorpsSynergy.get(player);
        if (synergy.hope() || synergy.will()) {
            int link = ARGB.opaque((synergy.hope() ? LanternCorps.BLUE : LanternCorps.GREEN).color());
            graphics.outline(barX - 1, y - 1, BAR_WIDTH + 2, ICON_SIZE + 2, link);
        }
    }

    private RingChargeHud() {}
}
