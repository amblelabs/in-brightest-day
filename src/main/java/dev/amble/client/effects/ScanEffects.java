package dev.amble.client.effects;

import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.networking.payloads.c2s.ScanC2SPayload;
import dev.amble.core.networking.payloads.s2c.ScanS2CPayload;
import dev.amble.core.networking.payloads.s2c.ScanStartS2CPayload;
import dev.amble.core.ringpowers.impl.ScanRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class ScanEffects {
    private static final int RESULT_TICKS = 140;
    private static final int RESULT_TIMEOUT = 40;
    private static final int PANEL_FADE_TICKS = 20;
    private static final int BURST_TICKS = 10;
    private static final int BURST_VOXELS = 28;
    private static final float CHARS_PER_TICK = 3.0F;
    private static final int CHIME_INTERVAL = 4;
    private static final float EDGE_SPACING = 0.12F;
    private static final float PLANE_SPACING = 0.2F;
    private static final float EDGE_VOXEL = 1.5F * VoxelRenderer.PIXEL;
    private static final float PLANE_VOXEL = 1.0F * VoxelRenderer.PIXEL;
    private static final float BURST_VOXEL = 2.0F * VoxelRenderer.PIXEL;
    private static final float PADDING = 0.06F;

    private static final int PANEL_MARGIN = 6;
    private static final int PANEL_PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final int PROGRESS_WIDTH = 48;
    private static final int PANEL_BACKGROUND = 0x0A140C;

    private static @Nullable Scan scan;
    private static boolean wasHolding;

    private static final class Scan {
        final int entityId;
        final BlockPos pos;
        final int color;
        final @Nullable ClientLevel level;
        final long seed;
        @Nullable Component title;
        List<Component> lines = List.of();
        boolean completeSent;
        int age;
        int resultAge = -1;

        Scan(int entityId, BlockPos pos, int color, @Nullable ClientLevel level) {
            this.entityId = entityId;
            this.pos = pos;
            this.color = ARGB.opaque(color);
            this.level = level;
            this.seed = RandomSource.create().nextLong();
        }

        boolean scanning() {
            return this.resultAge < 0;
        }

        @Nullable AABB box(ClientLevel level, float partialTicks) {
            if (this.entityId == ScanS2CPayload.NO_ENTITY) return new AABB(this.pos).inflate(PADDING);
            Entity entity = level.getEntity(this.entityId);
            if (entity == null) return null;
            return entity.getBoundingBox().move(entity.getPosition(partialTicks).subtract(entity.position())).inflate(PADDING);
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ScanStartS2CPayload.TYPE, (payload, context) -> {
            scan = new Scan(payload.entityId(), payload.pos(), payload.color(), context.client().level);
            play(context.client(), SoundEvents.BEACON_POWER_SELECT, 1.8F);
        });
        ClientPlayNetworking.registerGlobalReceiver(ScanS2CPayload.TYPE, (payload, context) -> {
            if (scan == null) scan = new Scan(payload.entityId(), payload.pos(), payload.color(), context.client().level);
            scan.title = payload.title();
            scan.lines = payload.lines();
            scan.resultAge = 0;
            play(context.client(), SoundEvents.BEACON_ACTIVATE, 2.0F);
            play(context.client(), SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> scan = null);
        ClientTickEvents.END_CLIENT_TICK.register(ScanEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ScanEffects::render);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id("scan_readout"), ScanEffects::extractHud);
    }

    public static boolean isScanning() {
        return scan != null && scan.scanning();
    }

    public static float scanAmount(float partialTicks) {
        if (scan == null || !scan.scanning()) return 0.0F;
        return Mth.clamp((scan.age + partialTicks) / 4.0F, 0.0F, 1.0F);
    }

    private static void tick(Minecraft client) {
        boolean holding = client.player != null && AbilityClient.holding(client.player, RingPowerRegistry.SCAN);
        if (holding && !wasHolding && (scan == null || !scan.scanning())) ClientPlayNetworking.send(new ScanC2SPayload(ScanC2SPayload.START));
        wasHolding = holding;

        if (scan == null || client.isPaused()) return;
        if (scan.level != client.level || client.level == null) {
            scan = null;
            return;
        }

        if (scan.scanning()) {
            if (!holding || scan.box(client.level, 1.0F) == null || scan.age > ScanRingPower.SCAN_TICKS + RESULT_TIMEOUT) {
                if (!scan.completeSent) ClientPlayNetworking.send(new ScanC2SPayload(ScanC2SPayload.CANCEL));
                scan = null;
                return;
            }

            scan.age++;
            if (scan.age < ScanRingPower.SCAN_TICKS && scan.age % CHIME_INTERVAL == 0) {
                play(client, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F + 1.2F * scan.age / ScanRingPower.SCAN_TICKS);
            }
            if (scan.age >= ScanRingPower.SCAN_TICKS && !scan.completeSent) {
                scan.completeSent = true;
                ClientPlayNetworking.send(new ScanC2SPayload(ScanC2SPayload.COMPLETE));
            }
        } else if (++scan.resultAge > RESULT_TICKS) {
            scan = null;
        }
    }

    private static void play(Minecraft client, SoundEvent sound, float pitch) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) return;
        client.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, 0.6F, pitch, false);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (scan == null || client.level == null || client.player == null) return;
        if (!scan.scanning() && scan.resultAge > BURST_TICKS) return;

        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        AABB box = scan.box(client.level, partialTicks);
        if (box == null) return;

        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        if (scan.scanning()) {
            float progress = Mth.clamp((scan.age + partialTicks) / ScanRingPower.SCAN_TICKS, 0.0F, 1.0F);
            TractorEffects.beam(BlastEffects.hand(client.player, partialTicks), box.getCenter(), client.player.tickCount + partialTicks, scan.color, voxels);
            edges(box, Math.min(progress * 1.3F, 1.0F), voxels);
            plane(box, progress, voxels);
        } else {
            burst(box, scan.resultAge + partialTicks, voxels);
        }

        ShieldEffects.submit(context, context.levelState().cameraRenderState.pos, voxels, 1.0F);
    }

    private static void edges(AABB box, float drawn, List<ShieldEffects.Voxel> out) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        List<Vec3[]> segments = new ArrayList<>();
        for (double y : ys) for (double z : zs) segments.add(new Vec3[]{new Vec3(box.minX, y, z), new Vec3(box.maxX, y, z)});
        for (double x : xs) for (double z : zs) segments.add(new Vec3[]{new Vec3(x, box.minY, z), new Vec3(x, box.maxY, z)});
        for (double x : xs) for (double y : ys) segments.add(new Vec3[]{new Vec3(x, y, box.minZ), new Vec3(x, y, box.maxZ)});

        float half = VoxelRenderer.snapSize(EDGE_VOXEL * 0.5F);
        int edgeColor = VoxelRenderer.toWhite(scan.color, 0.35F);
        for (Vec3[] segment : segments) {
            double length = segment[0].distanceTo(segment[1]);
            int count = Math.max(Mth.floor(length * drawn / EDGE_SPACING), 1);
            for (int i = 0; i <= count; i++) {
                double t = Math.min(i * EDGE_SPACING / length, drawn);
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(segment[0].lerp(segment[1], t)), half, edgeColor));
            }
        }
    }

    private static void plane(AABB box, float progress, List<ShieldEffects.Voxel> out) {
        float sweep = progress * 2.0F;
        float height = sweep <= 1.0F ? 1.0F - sweep : sweep - 1.0F;
        double y = Mth.lerp(height, box.minY, box.maxY);
        int nx = Math.max((int) Math.round(box.getXsize() / PLANE_SPACING), 1);
        int nz = Math.max((int) Math.round(box.getZsize() / PLANE_SPACING), 1);
        float half = VoxelRenderer.snapSize(PLANE_VOXEL * 0.5F);
        int planeColor = VoxelRenderer.toWhite(scan.color, 0.5F);

        for (int x = 0; x <= nx; x++) {
            for (int z = 0; z <= nz; z++) {
                Vec3 point = new Vec3(Mth.lerp((double) x / nx, box.minX, box.maxX), y, Mth.lerp((double) z / nz, box.minZ, box.maxZ));
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, planeColor));
            }
        }
    }

    private static void burst(AABB box, float time, List<ShieldEffects.Voxel> out) {
        float life = 1.0F - Mth.clamp(time / BURST_TICKS, 0.0F, 1.0F);
        if (life <= 0.0F) return;

        RandomSource random = RandomSource.create(scan.seed);
        Vec3 center = box.getCenter();
        double reach = Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize())) * 0.5;
        int tint = VoxelRenderer.toWhite(scan.color, 1.0F - life);
        for (int i = 0; i < VisualQuality.count(BURST_VOXELS); i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            double distance = reach + time * (0.12 + random.nextFloat() * 0.12);
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(direction.scale(distance))), VoxelRenderer.snapSize(BURST_VOXEL * life * 0.5F), tint));
        }
    }

    private static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (scan == null) return;

        Font font = client.font;
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);

        if (scan.scanning()) {
            float progress = Mth.clamp((scan.age + partialTicks) / ScanRingPower.SCAN_TICKS, 0.0F, 1.0F);
            int centerX = graphics.guiWidth() / 2;
            int x = centerX - PROGRESS_WIDTH / 2;
            int y = graphics.guiHeight() / 2 + 10;
            graphics.fill(x - 1, y - 1, x + PROGRESS_WIDTH + 1, y + 3, 0x90000000);
            graphics.fill(x, y, x + Math.round(PROGRESS_WIDTH * progress), y + 2, scan.color);
            Component label = Component.translatable("scan.brightestday.scanning");
            graphics.text(font, label, centerX - font.width(label) / 2, y + 5, ARGB.color(200, scan.color), true);
            return;
        }
        if (scan.title == null) return;

        float shown = scan.resultAge + partialTicks;
        float alpha = Mth.clamp(Math.min(shown / 5.0F, (RESULT_TICKS - shown) / PANEL_FADE_TICKS), 0.0F, 1.0F);
        if (alpha <= 0.01F) return;
        alpha *= 0.92F + 0.08F * Mth.sin(shown * 1.7F);

        List<String> text = new ArrayList<>();
        text.add(scan.title.getString());
        for (Component line : scan.lines) text.add(line.getString());

        int width = 0;
        for (String line : text) width = Math.max(width, font.width(line));
        width += PANEL_PADDING * 2;
        int height = text.size() * LINE_HEIGHT + PANEL_PADDING * 2 + 2;
        int left = PANEL_MARGIN;
        int top = PANEL_MARGIN;

        int border = ARGB.color(Math.round(255 * alpha), scan.color);
        graphics.fill(left, top, left + width, top + height, ARGB.color(Math.round(170 * alpha), PANEL_BACKGROUND));
        brackets(graphics, left, top, width, height, border);

        int scanlineY = top + Math.floorMod(Math.round(shown * 2.0F), height);
        graphics.fill(left + 1, scanlineY, left + width - 1, scanlineY + 1, ARGB.color(Math.round(60 * alpha), scan.color));

        int revealed = Math.round(shown * CHARS_PER_TICK);
        int y = top + PANEL_PADDING;
        for (int i = 0; i < text.size(); i++) {
            String line = text.get(i);
            if (revealed <= 0) break;
            String visible = line.substring(0, Math.min(line.length(), revealed));
            revealed -= line.length();

            int color = i == 0 ? ARGB.color(Math.round(255 * alpha), VoxelRenderer.toWhite(scan.color, 0.2F)) : ARGB.color(Math.round(230 * alpha), 0xE8F4EA);
            boolean cursor = revealed < 0 && (scan.resultAge / 4) % 2 == 0;
            graphics.text(font, cursor ? visible + "_" : visible, left + PANEL_PADDING, y, color, true);
            y += LINE_HEIGHT + (i == 0 ? 2 : 0);
        }
    }

    private static void brackets(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        int length = 6;
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, x + length, y + 1, color);
        graphics.fill(x, y, x + 1, y + length, color);
        graphics.fill(right - length, y, right, y + 1, color);
        graphics.fill(right - 1, y, right, y + length, color);
        graphics.fill(x, bottom - 1, x + length, bottom, color);
        graphics.fill(x, bottom - length, x + 1, bottom, color);
        graphics.fill(right - length, bottom - 1, right, bottom, color);
        graphics.fill(right - 1, bottom - length, right, bottom, color);
        graphics.fill(x, y + 1, x + 1, bottom - 1, ARGB.color(ARGB.alpha(color) / 4, color));
    }

    private ScanEffects() {}
}
