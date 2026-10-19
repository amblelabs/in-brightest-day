package dev.amble.client.hud;

import dev.amble.client.effects.VisualQuality;
import dev.amble.BrightestDay;
import dev.amble.client.config.BrightestDayClientConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.TintFlashS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RedBlood;
import dev.amble.core.team.LanternTeams;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class EmotionVignette {
    private static final double SENSE_RANGE = 24.0;
    private static final float CROWD_WEIGHT = 0.12F;
    private static final float SMOOTHING = 0.08F;
    private static final float REST_ALPHA = 0.12F;
    private static final float MAX_ALPHA = 0.55F;
    private static final float FLASH_ALPHA = 0.3F;
    private static final float BLOOD_DANGER = 0.85F;
    private static final int HAZE_STRIPS = 16;
    private static final float HAZE_MIN_BAND = 0.1F;
    private static final float HAZE_MAX_BAND = 0.22F;
    private static final int VEIN_COUNT = 12;
    private static final int VEIN_SEGMENTS = 22;
    private static final float VEIN_MIN_REACH = 0.3F;
    private static final long VEIN_SEED = 0x5EDL;

    private record Segment(float x0, float y0, float x1, float y1, int depth, float thickness) {}

    private static float threat;
    private static float oThreat;
    private static int veinWidth = -1;
    private static int veinHeight = -1;
    private static final List<Segment> VEINS = new ArrayList<>();
    private static int flashColor;
    private static int flashTicks;
    private static int flashTotal;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EmotionVignette::tick);
        ClientPlayNetworking.registerGlobalReceiver(TintFlashS2CPayload.TYPE, (payload, context) -> {
            flashColor = payload.color();
            flashTicks = payload.ticks();
            flashTotal = Math.max(1, payload.ticks());
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, BrightestDay.id("emotion_vignette"), EmotionVignette::extract);
    }

    private static LanternCorps corps(LocalPlayer player) {
        LanternCorps corps = PowerRingItem.getCorps(BrightestDayAttachments.getRing(player)).orElse(null);
        BrightestDayClientConfig config = BrightestDayClientConfig.get();
        if (corps == LanternCorps.RED) return config.showRageVignette ? corps : null;
        if (corps == LanternCorps.INDIGO) return config.showIndigoVignette ? corps : null;
        return null;
    }

    private static void tick(Minecraft client) {
        oThreat = threat;
        if (flashTicks > 0) flashTicks--;
        LocalPlayer player = client.player;
        if (player == null || client.level == null || corps(player) == null) {
            threat = 0.0F;
            return;
        }

        float target = 0.0F;
        int count = 0;
        for (Entity entity : client.level.getEntities(player, new AABB(player.position(), player.position()).inflate(SENSE_RANGE),
                entity -> entity.isAlive() && isEnemy(player, entity))) {
            double distance = entity.distanceTo(player);
            if (distance > SENSE_RANGE) continue;
            target = Math.max(target, (float) (1.0 - distance / SENSE_RANGE));
            count++;
        }
        target = Mth.clamp(target + Math.max(0, count - 1) * CROWD_WEIGHT, 0.0F, 1.0F);
        threat += (target - threat) * SMOOTHING;
    }

    private static boolean isEnemy(Player self, Entity entity) {
        if (entity instanceof Enemy) return true;
        return entity instanceof Player other && !other.isSpectator()
                && PowerRingItem.getWornCorps(other).isPresent()
                && !LanternTeams.areTeammates(self, other);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator()) return;
        if (flashTicks > 0) {
            float fade = flashTicks / (float) flashTotal;
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color(FLASH_ALPHA * fade, flashColor));
            haze(graphics, ARGB.opaque(flashColor), MAX_ALPHA * fade, 1.0F);
        }
        LanternCorps corps = corps(player);
        if (corps == null) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float danger = Mth.lerp(partial, oThreat, threat);
        float time = player.tickCount + partial;
        int color = ARGB.opaque(CorpsColors.apply(corps.color(), BrightestDayAttachments.getColorTweak(player)));

        if (corps == LanternCorps.RED) {
            danger = Math.max(danger, RedBlood.get(player) / (float) RedBlood.MAX * BLOOD_DANGER);
            float alpha = Mth.lerp(danger * heartbeat(time, danger), REST_ALPHA, MAX_ALPHA);
            haze(graphics, color, alpha * 0.6F, danger);
            veins(graphics, color, alpha, danger);
        } else {
            float alpha = Mth.lerp(danger * breathe(time, danger), REST_ALPHA, MAX_ALPHA);
            haze(graphics, color, alpha, danger);
        }
    }

    private static float heartbeat(float time, float danger) {
        float period = Mth.lerp(danger, 30.0F, 14.0F);
        float phase = (time % period) / period;
        float lub = (float) Math.exp(-Math.pow(phase / 0.07, 2));
        float dub = 0.7F * (float) Math.exp(-Math.pow((phase - 0.24) / 0.07, 2));
        return 0.45F + 0.55F * Math.max(lub, dub);
    }

    private static float breathe(float time, float danger) {
        float period = Mth.lerp(danger, 60.0F, 30.0F);
        return 0.6F + 0.4F * (0.5F + 0.5F * Mth.sin(time / period * Mth.TWO_PI));
    }

    private static void haze(GuiGraphicsExtractor graphics, int color, float alpha, float danger) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float bandScale = Mth.lerp(danger, HAZE_MIN_BAND, HAZE_MAX_BAND);
        int vertical = Math.max(1, Math.round(height * bandScale));
        int horizontal = Math.max(1, Math.round(width * bandScale));
        int edge = ARGB.color(alpha, color);
        int clear = ARGB.transparent(color);

        graphics.fillGradient(0, 0, width, vertical, edge, clear);
        graphics.fillGradient(0, height - vertical, width, height, clear, edge);
        for (int i = 0; i < HAZE_STRIPS; i++) {
            float fade = 1.0F - i / (float) HAZE_STRIPS;
            int strip = ARGB.color(alpha * fade * fade, color);
            int x0 = horizontal * i / HAZE_STRIPS;
            int x1 = horizontal * (i + 1) / HAZE_STRIPS;
            graphics.fill(x0, 0, x1, height, strip);
            graphics.fill(width - x1, 0, width - x0, height, strip);
        }
    }

    private static void veins(GuiGraphicsExtractor graphics, int color, float alpha, float danger) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width != veinWidth || height != veinHeight) grow(width, height);

        float reach = Mth.lerp(danger, VEIN_MIN_REACH, 1.0F);
        int dark = ARGB.srgbLerp(0.45F, 0xFF000000, color);
        for (Segment segment : VEINS) {
            float progress = segment.depth() / (float) VEIN_SEGMENTS;
            if (progress > reach) continue;
            float fade = 1.0F - progress / reach;
            line(graphics, segment, ARGB.color(alpha * (0.35F + 0.65F * fade), ARGB.srgbLerp(fade, dark, color)));
        }
    }

    private static void line(GuiGraphicsExtractor graphics, Segment segment, int color) {
        int size = Math.max(1, Math.round(segment.thickness()));
        float dx = segment.x1() - segment.x0();
        float dy = segment.y1() - segment.y0();
        int steps = Math.max(1, Math.round(Math.max(Math.abs(dx), Math.abs(dy)) / size));
        for (int i = 0; i <= steps; i++) {
            int x = Math.round(segment.x0() + dx * i / steps);
            int y = Math.round(segment.y0() + dy * i / steps);
            graphics.fill(x, y, x + size, y + size, color);
        }
    }

    private static void grow(int width, int height) {
        veinWidth = width;
        veinHeight = height;
        VEINS.clear();
        RandomSource random = RandomSource.create(VEIN_SEED);
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        float step = Math.min(width, height) / 2.2F / VEIN_SEGMENTS;

        for (int v = 0; v < VisualQuality.count(VEIN_COUNT); v++) {
            float angle = v / (float) VisualQuality.count(VEIN_COUNT) * Mth.TWO_PI + random.nextFloat() * 0.3F;
            float x = cx + Mth.cos(angle) * width;
            float y = cy + Mth.sin(angle) * height;
            x = Mth.clamp(x, 0, width - 1);
            y = Mth.clamp(y, 0, height - 1);
            branch(random, x, y, (float) Math.atan2(cy - y, cx - x), 0, VEIN_SEGMENTS, 3.0F, step);
        }
    }

    private static void branch(RandomSource random, float x, float y, float heading, int depth, int remaining, float thickness, float step) {
        for (int i = 0; i < remaining; i++) {
            heading += (random.nextFloat() - 0.5F) * 1.1F;
            float length = step * (0.7F + random.nextFloat() * 0.6F);
            float nx = x + Mth.cos(heading) * length;
            float ny = y + Mth.sin(heading) * length;
            VEINS.add(new Segment(x, y, nx, ny, depth + i, thickness));
            if (remaining > 4 && random.nextFloat() < 0.12F) {
                float side = random.nextBoolean() ? 0.8F : -0.8F;
                branch(random, nx, ny, heading + side, depth + i + 1, (remaining - i) / 2, Math.max(1.0F, thickness - 1.0F), step * 0.8F);
            }
            x = nx;
            y = ny;
            thickness = Math.max(1.0F, thickness - 0.1F);
        }
    }

    private EmotionVignette() {}
}
