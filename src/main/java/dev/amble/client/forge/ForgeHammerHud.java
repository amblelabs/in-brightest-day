package dev.amble.client.forge;

import dev.amble.BrightestDay;
import dev.amble.core.forge.ForgeHammer;
import dev.amble.core.forge.SpectrumForgeBlock;
import dev.amble.core.networking.payloads.s2c.ForgeBeatS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;

public final class ForgeHammerHud {
    private static final float TARGET_RADIUS = 10.0F;
    private static final float START_RADIUS = 46.0F;
    private static final int SEGMENTS = 64;
    private static final int PIP_SIZE = 5;
    private static final int PIP_GAP = 3;
    private static final int PIP_OFFSET = 26;
    private static final int FEEDBACK_TICKS = 10;
    private static final int HIT_COLOR = 0xFF7DFF9A;
    private static final int MISS_COLOR = 0xFFFF5A5A;
    private static final int PENDING_COLOR = 0xFF505050;

    private static final int PERFECT_COLOR = 0xFFFFD65A;
    private static final int STREAK_SIZE = 3;
    private static final int STREAK_GAP = 3;
    private static final int STREAK_OFFSET = 9;
    private static final float PULSE = 4.0F;

    private static boolean active;
    private static long beatAt;
    private static int strike;
    private static int total;
    private static int perfects;
    private static int streak;
    private static int color;
    private static int feedback;
    private static int feedbackTicks;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ForgeBeatS2CPayload.TYPE, (payload, context) -> {
            active = payload.active();
            beatAt = payload.beatAt();
            strike = payload.strike();
            total = payload.total();
            perfects = payload.perfects();
            streak = payload.streak();
            color = ARGB.opaque(payload.color());
            if (payload.feedback() != ForgeBeatS2CPayload.NONE_FEEDBACK) {
                feedback = payload.feedback();
                feedbackTicks = FEEDBACK_TICKS;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active = false);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (feedbackTicks > 0) feedbackTicks--;
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!level.isClientSide() || !active || !(level.getBlockState(pos).getBlock() instanceof SpectrumForgeBlock)) return InteractionResult.PASS;
            return InteractionResult.SUCCESS;
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id("forge_hammer"), ForgeHammerHud::extract);
    }

    private static float radiusAt(float ticksBefore) {
        return TARGET_RADIUS + (START_RADIUS - TARGET_RADIUS) * ticksBefore / ForgeHammer.BEAT_TICKS;
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || (!active && feedbackTicks <= 0)) return;

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        boolean landed = feedbackTicks > 0 && feedback != ForgeBeatS2CPayload.MISS;
        float pulse = landed ? PULSE * feedbackTicks / (float) FEEDBACK_TICKS : 0.0F;
        if (active) {
            float now = client.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
            float before = Mth.clamp(beatAt - now, -ForgeHammer.GOOD_WINDOW, ForgeHammer.BEAT_TICKS);
            float radius = radiusAt(before);
            float good = radiusAt(ForgeHammer.GOOD_WINDOW) - TARGET_RADIUS;
            float perfect = radiusAt(ForgeHammer.PERFECT_WINDOW) - TARGET_RADIUS;
            for (float r = TARGET_RADIUS - good; r <= TARGET_RADIUS + good; r += 1.0F) {
                boolean inner = Math.abs(r - TARGET_RADIUS) <= perfect;
                circle(graphics, cx, cy, r, ARGB.color(inner ? 0x55 : 0x26, inner ? PERFECT_COLOR : color), 1);
            }
            boolean inWindow = Math.abs(before) <= ForgeHammer.GOOD_WINDOW;
            boolean inPerfect = Math.abs(before) <= ForgeHammer.PERFECT_WINDOW;
            circle(graphics, cx, cy, TARGET_RADIUS + pulse, ARGB.color(0xE0, landed && feedback == ForgeBeatS2CPayload.PERFECT ? PERFECT_COLOR : color), landed ? 2 : 1);
            int ringColor = inPerfect ? PERFECT_COLOR : inWindow ? color : ARGB.color(0xB0, 0xFFFFFF);
            circle(graphics, cx, cy, radius, ringColor, 2);
        } else if (landed) {
            circle(graphics, cx, cy, TARGET_RADIUS + pulse, ARGB.color(0xE0, feedback == ForgeBeatS2CPayload.PERFECT ? PERFECT_COLOR : color), 2);
        }

        int count = Math.max(1, total);
        int width = count * PIP_SIZE + (count - 1) * PIP_GAP;
        int x = cx - width / 2;
        int y = cy + PIP_OFFSET;
        for (int i = 0; i < count; i++) {
            int pip = i < strike ? ((perfects >> i & 1) != 0 ? PERFECT_COLOR : HIT_COLOR) : PENDING_COLOR;
            graphics.fill(x, y, x + PIP_SIZE, y + PIP_SIZE, pip);
            x += PIP_SIZE + PIP_GAP;
        }
        int streakWidth = ForgeHammer.MAX_STREAK * STREAK_SIZE + (ForgeHammer.MAX_STREAK - 1) * STREAK_GAP;
        int sx = cx - streakWidth / 2;
        for (int i = 0; i < ForgeHammer.MAX_STREAK; i++) {
            graphics.fill(sx, y + STREAK_OFFSET, sx + STREAK_SIZE, y + STREAK_OFFSET + STREAK_SIZE, i < streak ? MISS_COLOR : PENDING_COLOR);
            sx += STREAK_SIZE + STREAK_GAP;
        }

        if (feedbackTicks > 0) {
            Font font = client.font;
            String key = switch (feedback) {
                case ForgeBeatS2CPayload.PERFECT -> "hud.brightestday.forge.perfect";
                case ForgeBeatS2CPayload.HIT -> "hud.brightestday.forge.hit";
                default -> "hud.brightestday.forge.miss";
            };
            int tone = switch (feedback) {
                case ForgeBeatS2CPayload.PERFECT -> PERFECT_COLOR;
                case ForgeBeatS2CPayload.HIT -> HIT_COLOR;
                default -> MISS_COLOR;
            };
            Component text = Component.translatable(key);
            int alpha = Math.round(255 * feedbackTicks / (float) FEEDBACK_TICKS);
            graphics.text(font, text, cx - font.width(text) / 2, cy - PIP_OFFSET - font.lineHeight, ARGB.color(alpha, tone), true);
        }
    }

    private static void circle(GuiGraphicsExtractor graphics, int cx, int cy, float radius, int color, int thickness) {
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = i * Mth.TWO_PI / SEGMENTS;
            int x = Math.round(cx + Mth.cos(angle) * radius);
            int y = Math.round(cy + Mth.sin(angle) * radius);
            graphics.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    private ForgeHammerHud() {}
}
