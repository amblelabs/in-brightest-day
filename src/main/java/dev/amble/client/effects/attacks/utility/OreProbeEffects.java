package dev.amble.client.effects.attacks.utility;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.OreProbeS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OreProbeEffects {
    private static final float CELL = 2.0F * VoxelRenderer.PIXEL;
    private static final int POP_TICKS = 8;
    private static final int FADE_TICKS = 8;
    private static final int EXPIRE_GRACE = 20;
    private static final int WARN_TICKS = 60;
    private static final float ORBIT_RADIUS = 0.9F;
    private static final float ORBIT_SPEED = 0.12F;
    private static final float ORBIT_HEIGHT = 0.85F;
    private static final float BOB_AMPLITUDE = 0.08F;
    private static final float BOB_SPEED = 0.2F;
    private static final int SATELLITES = 4;
    private static final float SATELLITE_RADIUS = 2.0F;
    private static final float SATELLITE_SPEED = 0.35F;
    private static final int TAIL = 4;
    private static final float TAIL_SPACING = 0.12F;

    private static final int SCAN_RADIUS = 12;
    private static final int MAX_HIGHLIGHTS = 300;
    private static final int PING_INTERVAL = 60;
    private static final float SWEEP_SPEED = 1.5F;
    private static final int GLOW_TICKS = 30;
    private static final int PING_FADE_TICKS = 15;
    private static final float FILL_ALPHA = 0.22F;
    private static final float EDGE_ALPHA = 0.85F;
    private static final float EDGE_THICKNESS = VoxelRenderer.PIXEL;
    private static final float BOX_INSET = 0.01F;
    private static final float FAR_FADE = 0.45F;

    private static final int NONE = -1;
    private static final int CORPS = 0;
    private static final int COAL = 0xFF3A3A3A;
    private static final int IRON = 0xFFD8AF93;
    private static final int COPPER = 0xFFE0773B;
    private static final int GOLD = 0xFFFCEE4B;
    private static final int REDSTONE = 0xFFFF2020;
    private static final int LAPIS = 0xFF2457FF;
    private static final int DIAMOND = 0xFF4AEDD9;
    private static final int EMERALD = 0xFF17DD62;
    private static final int DEBRIS = 0xFF6B4A3A;
    private static final int QUARTZ = 0xFFF2EEE6;

    private static final TagKey<Block> COAL_ORES = BlockItemTags.COAL_ORES.block();
    private static final TagKey<Block> REDSTONE_ORES = BlockItemTags.REDSTONE_ORES.block();
    private static final TagKey<Block> LAPIS_ORES = BlockItemTags.LAPIS_ORES.block();
    private static final TagKey<Block> DIAMOND_ORES = BlockItemTags.DIAMOND_ORES.block();
    private static final TagKey<Block> EMERALD_ORES = BlockItemTags.EMERALD_ORES.block();

    private static final Map<Integer, ClientProbe> PROBES = new HashMap<>();
    private static final Map<Block, Integer> CLASSES = new IdentityHashMap<>();
    private static List<Highlight> highlights = List.of();
    private static @Nullable BlockPos pingCenter;
    private static int pingAge;
    private static int sincePing = PING_INTERVAL;

    private record Highlight(BlockPos pos, int color) {}

    private static final class ClientProbe {
        final int ownerId;
        final int color;
        final int remaining;
        final @Nullable ClientLevel level;
        int age;
        int fade = -1;

        ClientProbe(OreProbeS2CPayload payload, @Nullable ClientLevel level) {
            this.ownerId = payload.ownerId();
            this.color = ARGB.opaque(payload.color());
            this.remaining = payload.remaining();
            this.level = level;
        }
    }

    public static void init() {
        OreProbeRenderTypes.init();
        ClientPlayNetworking.registerGlobalReceiver(OreProbeS2CPayload.TYPE, (payload, context) -> {
            if (payload.present()) {
                PROBES.put(payload.id(), new ClientProbe(payload, context.client().level));
                return;
            }
            ClientProbe probe = PROBES.get(payload.id());
            if (probe != null && probe.fade < 0) probe.fade = 0;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> CLASSES.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PROBES.clear();
            CLASSES.clear();
            resetScan();
        });
        ClientTickEvents.END_CLIENT_TICK.register(OreProbeEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(OreProbeEffects::render);
    }

    public static void snapshot(Consumer<CustomPacketPayload> out) {
        PROBES.forEach((id, probe) -> {
            if (probe.fade < 0) out.accept(new OreProbeS2CPayload(id, probe.ownerId, probe.color, Math.max(probe.remaining - probe.age, 0), true));
        });
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;

        Iterator<ClientProbe> iterator = PROBES.values().iterator();
        while (iterator.hasNext()) {
            ClientProbe probe = iterator.next();
            if (probe.level != client.level) {
                iterator.remove();
                continue;
            }
            probe.age++;
            if (probe.fade < 0 && probe.age > probe.remaining + EXPIRE_GRACE) probe.fade = 0;
            if (probe.fade >= 0 && ++probe.fade > FADE_TICKS) iterator.remove();
        }

        if (!highlights.isEmpty() && ++pingAge > lifetime()) resetScan();
        if (scanning(client)) {
            if (++sincePing >= PING_INTERVAL) ping(client);
        } else {
            sincePing = PING_INTERVAL;
        }
    }

    private static boolean scanning(Minecraft client) {
        if (client.player == null || client.level == null) return false;
        int playerId = client.player.getId();
        for (ClientProbe probe : PROBES.values()) {
            if (probe.ownerId == playerId && probe.fade < 0) return true;
        }
        return false;
    }

    private static void ping(Minecraft client) {
        ClientLevel level = client.level;
        Player player = client.player;
        if (level == null || player == null) return;
        sincePing = 0;
        pingAge = 0;
        pingCenter = player.blockPosition();
        List<Highlight> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radiusSqr = SCAN_RADIUS * SCAN_RADIUS;
        int minY = Math.max(pingCenter.getY() - SCAN_RADIUS, level.getMinY());
        int maxY = Math.min(pingCenter.getY() + SCAN_RADIUS, level.getMaxY());
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int y = minY; y <= maxY; y++) {
                int dy = y - pingCenter.getY();
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSqr) continue;
                    cursor.set(pingCenter.getX() + dx, y, pingCenter.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) continue;
                    int color = CLASSES.computeIfAbsent(state.getBlock(), block -> classify(state));
                    if (color != NONE) found.add(new Highlight(cursor.immutable(), color));
                }
            }
        }
        BlockPos origin = pingCenter;
        found.sort(Comparator.comparingDouble(highlight -> highlight.pos().distSqr(origin)));
        highlights = List.copyOf(found.subList(0, Math.min(found.size(), MAX_HIGHLIGHTS)));
        level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, 1.6F, false);
        level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 0.6F, false);
    }

    private static int lifetime() {
        return Mth.ceil(SCAN_RADIUS / SWEEP_SPEED) + GLOW_TICKS + PING_FADE_TICKS;
    }

    private static int classify(BlockState state) {
        if (state.is(COAL_ORES)) return COAL;
        if (state.is(BlockTags.IRON_ORES)) return IRON;
        if (state.is(BlockTags.COPPER_ORES)) return COPPER;
        if (state.is(BlockTags.GOLD_ORES) || state.is(Blocks.NETHER_GOLD_ORE)) return GOLD;
        if (state.is(REDSTONE_ORES)) return REDSTONE;
        if (state.is(LAPIS_ORES)) return LAPIS;
        if (state.is(DIAMOND_ORES)) return DIAMOND;
        if (state.is(EMERALD_ORES)) return EMERALD;
        if (state.is(Blocks.ANCIENT_DEBRIS)) return DEBRIS;
        if (state.is(Blocks.NETHER_QUARTZ_ORE)) return QUARTZ;
        if (state.is(BlockTags.ORES)) return CORPS;
        return NONE;
    }

    private static void resetScan() {
        pingCenter = null;
        highlights = List.of();
        pingAge = 0;
    }

    private static void render(LevelRenderContext context) {
        if (PROBES.isEmpty() && highlights.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (ClientProbe probe : PROBES.values()) {
            if (!(client.level.getEntity(probe.ownerId) instanceof Player owner)) continue;
            float time = probe.age + partialTicks;
            float scale = Mth.clamp(time / POP_TICKS, 0.0F, 1.0F);
            if (probe.fade >= 0) scale *= 1.0F - Mth.clamp((probe.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (scale <= 0.01F) continue;

            float alpha = 1.0F;
            int left = probe.remaining - probe.age;
            if (probe.fade < 0 && left < WARN_TICKS) alpha = 0.55F + 0.45F * Math.abs(Mth.cos(time * 0.5F));
            ShieldEffects.submit(context, camera, probe(owner, probeColor(client, probe), time, partialTicks, scale), alpha);
        }

        if (highlights.isEmpty() || client.player == null || pingCenter == null) return;
        float since = pingAge + partialTicks;
        Vec3 origin = Vec3.atCenterOf(pingCenter);
        int corps = PowerRingItem.getWornCorps(client.player).isPresent() ? ARGB.opaque(CorpsColors.of(client.player)) : 0xFFFFFFFF;
        float pulse = 0.85F + 0.15F * Mth.sin((client.player.tickCount + partialTicks) * 0.15F);
        List<Highlight> drawn = highlights;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), OreProbeRenderTypes.XRAY,
                (pose, buffer) -> drawHighlights(pose, buffer, camera, origin, since, drawn, corps, pulse));
    }

    private static List<ShieldEffects.Voxel> probe(Player owner, int color, float time, float partialTicks, float scale) {
        Vec3 base = owner.getPosition(partialTicks).add(0.0, owner.getBbHeight() * ORBIT_HEIGHT, 0.0);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(1 + SATELLITES + TAIL);
        Vec3 center = orbit(base, time);
        float coreHalf = VoxelRenderer.snapSize(CELL * 0.8F * scale * (1.0F + 0.15F * Mth.sin(time * 0.4F)));
        voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center), coreHalf, VoxelRenderer.toWhite(color, 0.7F)));

        float half = VoxelRenderer.snapSize(CELL * 0.35F * scale);
        for (int i = 0; i < SATELLITES; i++) {
            float angle = time * SATELLITE_SPEED + i * Mth.TWO_PI / SATELLITES;
            float tilt = (i & 1) == 0 ? 0.6F : -0.6F;
            Vec3 offset = new Vec3(Mth.cos(angle), tilt * Mth.sin(angle), Mth.sin(angle)).scale(SATELLITE_RADIUS * CELL * scale);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(offset)), half, VoxelRenderer.toWhite(color, 0.35F)));
        }

        for (int i = 1; i <= TAIL; i++) {
            float t = (float) i / (TAIL + 1);
            Vec3 point = orbit(base, time - i * TAIL_SPACING / (ORBIT_RADIUS * ORBIT_SPEED));
            float tailHalf = VoxelRenderer.snapSize(CELL * 0.5F * scale * (1.0F - t * 0.7F));
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), tailHalf, VoxelRenderer.toWhite(color, 0.4F * (1.0F - t))));
        }
        return voxels;
    }

    private static Vec3 orbit(Vec3 base, float time) {
        float angle = time * ORBIT_SPEED;
        return base.add(Mth.cos(angle) * ORBIT_RADIUS, BOB_AMPLITUDE * Mth.sin(time * BOB_SPEED), Mth.sin(angle) * ORBIT_RADIUS);
    }

    private static int probeColor(Minecraft client, ClientProbe probe) {
        if (client.level != null && client.level.getEntity(probe.ownerId) instanceof Player owner && PowerRingItem.getWornCorps(owner).isPresent()) {
            return ARGB.opaque(CorpsColors.of(owner));
        }
        return probe.color;
    }

    private static float sweep(float since, float distance) {
        float lit = since - distance / SWEEP_SPEED;
        if (lit <= 0.0F) return 0.0F;
        if (lit < GLOW_TICKS) return 1.0F;
        return 1.0F - Mth.clamp((lit - GLOW_TICKS) / PING_FADE_TICKS, 0.0F, 1.0F);
    }

    private static void drawHighlights(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, Vec3 origin, float since, List<Highlight> list, int corps, float pulse) {
        float h = 0.5F - BOX_INSET;
        float t = EDGE_THICKNESS * 0.5F;
        for (Highlight highlight : list) {
            Vec3 world = Vec3.atCenterOf(highlight.pos());
            float spread = (float) world.distanceTo(origin);
            float alpha = sweep(since, spread) * pulse;
            if (alpha <= 0.01F) continue;
            Vec3 center = world.subtract(camera);
            float falloff = 1.0F - FAR_FADE * Mth.clamp(spread / SCAN_RADIUS, 0.0F, 1.0F);
            int rgb = highlight.color() == CORPS ? corps : highlight.color();
            int fill = ARGB.color(Math.round(Mth.clamp(FILL_ALPHA * alpha * falloff, 0.0F, 1.0F) * 255), rgb);
            int edge = ARGB.color(Math.round(Mth.clamp(EDGE_ALPHA * alpha * falloff, 0.0F, 1.0F) * 255), rgb);
            float x = (float) center.x, y = (float) center.y, z = (float) center.z;

            box(pose, buffer, x - h, y - h, z - h, x + h, y + h, z + h, fill);
            for (int sa = -1; sa <= 1; sa += 2) {
                for (int sb = -1; sb <= 1; sb += 2) {
                    box(pose, buffer, x - h, y + sa * h - t, z + sb * h - t, x + h, y + sa * h + t, z + sb * h + t, edge);
                    box(pose, buffer, x + sa * h - t, y - h, z + sb * h - t, x + sa * h + t, y + h, z + sb * h + t, edge);
                    box(pose, buffer, x + sa * h - t, y + sb * h - t, z - h, x + sa * h + t, y + sb * h + t, z + h, edge);
                }
            }
        }
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        quad(pose, buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, color);
        quad(pose, buffer, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, color);
        quad(pose, buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, color);
        quad(pose, buffer, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, color);
        quad(pose, buffer, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, color);
        quad(pose, buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        buffer.addVertex(pose, ax, ay, az).setColor(color);
        buffer.addVertex(pose, bx, by, bz).setColor(color);
        buffer.addVertex(pose, cx, cy, cz).setColor(color);
        buffer.addVertex(pose, dx, dy, dz).setColor(color);
    }

    private OreProbeEffects() {}
}
