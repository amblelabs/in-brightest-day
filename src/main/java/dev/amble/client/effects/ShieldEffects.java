package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.BrightestDay;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.client.render.FrostedGlass;
import dev.amble.core.networking.payloads.s2c.ShieldRemoveS2CPayload;
import dev.amble.core.networking.payloads.s2c.ShieldSpawnS2CPayload;
import dev.amble.core.shields.ShieldManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import dev.amble.core.BrightestDayAttachments;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class ShieldEffects {
    private static final int FADE_TICKS = 6;
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));
    private static final float MIN_SPACING = 0.3F;
    private static final float SPACING_PER_RADIUS = 0.09F;
    private static final int MIN_VOXELS = 60;
    private static final int MAX_VOXELS = 2500;
    private static final float GLASS_ALPHA = 0.35F;
    private static final float GLOW_ALPHA = 0.22F;
    private static final float GLOW_SCALE = 1.8F;
    private static final float GLASS_FILL_ALPHA = 0.62F;
    public static final float GLASS_FILL = GLASS_FILL_ALPHA;
    public static final float HARD_LIGHT_FILL = 0.5F;
    private static final float SHELL_MIN_CELL = 0.25F;
    private static final float SHELL_CELL_PER_RADIUS = 0.05F;
    private static final float SHELL_AREA = 4.0F * Mth.PI * 1.3F;
    private static final float SHELL_MAX_CELLS = 9000.0F;
    private static final float SHELL_RIM = 0.12F;
    private static final float SHELL_WARBLE = 0.03F;
    private static final float SHELL_RIPPLE_WHITE = 0.28F;
    private static final float SHELL_RIPPLE_DENSITY = 0.2F;
    private static final int SHELL_CACHE = 32;
    private static final int SHELL_BIAS = 1 << 20;
    private static final boolean PROFILE_SHELLS = Boolean.getBoolean("brightestday.profileShells");
    private static final int PROFILE_PASSES = 240;
    private static long profiledNanos;
    private static long profiledQuads;
    private static int profiledPasses;
    private static final float RIM_ALPHA = 0.55F;
    private static final float RIM_GLOW_ALPHA = 0.12F;
    private static final float RIM_GLOW_WIDTH = 1.6F;
    private static final float RIM_WHITE = 0.4F;
    private static final float GLOW_FILL = 0.18F;
    private static final float GLOW_PASS = RIM_GLOW_ALPHA / RIM_ALPHA;
    private static final float GLOW_FILL_STRENGTH = GLOW_FILL / GLOW_PASS;
    private static final float MAX_FROST = 0.95F;
    private static final float MAX_FILL = 0.9F;

    private static final Map<Integer, ClientShield> SHIELDS = new HashMap<>();
    private static final Map<Long, List<ShellCell>> SHELLS = new HashMap<>();

    private static final class ClientShield {
        final int entityId;
        final Vec3 center;
        final float radius;
        final int color;
        final int duration;
        final @Nullable ClientLevel level;
        final int casterId;
        int age;
        int fade = -1;

        ClientShield(ShieldSpawnS2CPayload payload) {
            this.entityId = payload.entityId();
            this.center = payload.center();
            this.radius = payload.radius();
            this.color = ARGB.opaque(payload.color());
            this.duration = payload.duration();
            this.age = payload.age();
            this.casterId = payload.casterId();
            this.level = Minecraft.getInstance().level;
        }
    }

    public record Voxel(Vec3 center, float half, int color, int faces) {
        public Voxel(Vec3 center, float half, int color) {
            this(center, half, color, VoxelRenderer.ALL_FACES);
        }
    }

    private record ShellCell(int x, int y, int z, int faces, int outer, float nx, float ny, float nz) {}

    public record Hard(Vec3 center, float half, int color, int faces, int drawn) {}

    public record Oriented(Vec3 center, Vec3 ax, Vec3 ay, Vec3 az, int color, int faces) {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ShieldSpawnS2CPayload.TYPE, (payload, context) -> SHIELDS.put(payload.id(), new ClientShield(payload)));
        ClientPlayNetworking.registerGlobalReceiver(ShieldRemoveS2CPayload.TYPE, (payload, context) -> {
            ClientShield shield = SHIELDS.get(payload.id());
            if (shield != null && shield.fade < 0) shield.fade = 0;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SHIELDS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(ShieldEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(ShieldEffects::render);
    }

    public static void snapshot(Consumer<CustomPacketPayload> out) {
        SHIELDS.forEach((id, shield) -> {
            if (shield.fade < 0) out.accept(new ShieldSpawnS2CPayload(id, shield.entityId, shield.center, shield.radius, shield.color, shield.duration, shield.age, shield.casterId));
        });
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<ClientShield> iterator = SHIELDS.values().iterator();
        while (iterator.hasNext()) {
            ClientShield shield = iterator.next();
            if (shield.level != client.level) {
                iterator.remove();
                continue;
            }
            shield.age++;
            if (shield.fade < 0 && shield.duration >= 0 && shield.age > shield.duration + 20) shield.fade = 0;
            if (shield.fade >= 0 && ++shield.fade > FADE_TICKS) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (SHIELDS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (ClientShield shield : SHIELDS.values()) {
            Vec3 center = shield.center;
            float radius = shield.radius;
            if (shield.entityId != ShieldSpawnS2CPayload.NO_ENTITY) {
                Entity entity = client.level.getEntity(shield.entityId);
                if (entity == null) continue;
                center = entity.getPosition(partialTicks).add(0.0, entity.getBbHeight() * 0.5, 0.0);
                radius = ShieldManager.entityShieldRadius(entity) * shield.radius;
            }

            float time = shield.age + partialTicks;
            float scale = pop(time / ShieldManager.EXPAND_TICKS);
            if (shield.fade >= 0) scale *= 1.0F - Mth.clamp((shield.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (scale <= 0.01F) continue;

            submitShell(context, camera, center, radius * scale, radius, time, shield.color, 1.0F, opacityOf(client.level.getEntity(shield.casterId)));
        }
    }

    public static void submitHardLight(LevelRenderContext context, Vec3 camera, List<Hard> cells, float alpha, float rimWidth, float fillStrength) {
        submitHardLight(context, camera, cells, alpha, rimWidth, fillStrength, 1.0F);
    }

    public static void submitHardLight(LevelRenderContext context, Vec3 camera, List<Hard> cells, float alpha, float rimWidth, float fillStrength, float opacity) {
        if (cells.isEmpty()) return;
        if (FrostedGlass.ready()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FrostedGlass.GLASS,
                (pose, buffer) -> drawHard(pose, buffer, camera, cells, alpha, 0.0F, frost(opacity), true, false));
        if (VisualQuality.glow()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> drawHard(pose, buffer, camera, cells, GLOW_PASS * alpha, rimWidth * RIM_GLOW_WIDTH, GLOW_FILL_STRENGTH * opacity, true, true));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> drawHard(pose, buffer, camera, cells, alpha, rimWidth, fill(fillStrength, opacity), true, true));
    }

    private static float frost(float opacity) {
        return Math.min(FrostedGlass.FROST_ALPHA * opacity, MAX_FROST);
    }

    private static float fill(float strength, float opacity) {
        return Math.min(strength * opacity, MAX_FILL);
    }

    public static float opacityOf(@Nullable Entity entity) {
        return entity instanceof Player player ? BrightestDayAttachments.getColorTweak(player).opacityScale() : 1.0F;
    }

    private static void drawHard(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, List<Hard> cells, float alpha, float rimWidth, float fillStrength, boolean fill, boolean rims) {
        int fillAlpha = Math.round(Mth.clamp(fillStrength * alpha, 0.0F, 1.0F) * 255);
        int rimAlpha = Math.round(Mth.clamp(RIM_ALPHA * alpha, 0.0F, 1.0F) * 255);
        for (Hard cell : cells) {
            float x = (float) (cell.center().x - camera.x);
            float y = (float) (cell.center().y - camera.y);
            float z = (float) (cell.center().z - camera.z);
            float distance = Mth.sqrt(x * x + y * y + z * z);
            int body = VoxelRenderer.nearFade(distance, ARGB.color(fillAlpha, cell.color()));
            int rim = VoxelRenderer.nearFade(distance, ARGB.color(rimAlpha, VoxelRenderer.toWhite(cell.color(), RIM_WHITE)));
            boolean frost = fill && !rims;
            VoxelRenderer.glassBox(pose, buffer, x, y, z, cell.half(), body, rim, rimWidth, rims ? cell.faces() : 0, cell.drawn(), fill, frost, frost ? VoxelRenderer.FRESNEL_FROST : VoxelRenderer.FRESNEL_GLASS);
        }
    }

    public static void submitFrost(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float alpha) {
        if (!FrostedGlass.ready()) return;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FrostedGlass.GLASS,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, 1.0F, FrostedGlass.FROST_ALPHA * Math.min(alpha, 1.0F), false));
    }

    public static void submitGlow(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float glowScale, float glowAlpha) {
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, glowScale, glowAlpha, false));
    }

    public static void submit(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float glowScale, float glowAlpha, float glassAlpha) {
        submitFrost(context, camera, voxels, glassAlpha);
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, glowScale, glowAlpha, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> draw(pose, buffer, camera, voxels, 1.0F, glassAlpha, true));
    }

    public static void submit(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float alpha) {
        submitFrost(context, camera, voxels, alpha);
        if (VisualQuality.glow()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, GLOW_SCALE, GLOW_ALPHA * alpha, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> draw(pose, buffer, camera, voxels, 1.0F, GLASS_ALPHA * alpha, true));
    }

    public static void submitGlass(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float alpha, float rimWidth) {
        submitGlass(context, camera, voxels, alpha, rimWidth, GLASS_FILL_ALPHA);
    }

    public static void submitGlass(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float alpha, float rimWidth, float fillAlpha) {
        List<Oriented> cubes = new ArrayList<>(voxels.size());
        for (Voxel voxel : voxels) {
            float half = voxel.half();
            cubes.add(new Oriented(voxel.center(), new Vec3(half, 0.0, 0.0), new Vec3(0.0, half, 0.0), new Vec3(0.0, 0.0, half), voxel.color(), voxel.faces()));
        }
        submitGlassOriented(context, camera, cubes, alpha, rimWidth, fillAlpha);
    }

    public static void submitGlassOriented(LevelRenderContext context, Vec3 camera, List<Oriented> cubes, float alpha, float rimWidth) {
        submitGlassOriented(context, camera, cubes, alpha, rimWidth, GLASS_FILL_ALPHA);
    }

    public static void submitGlassOriented(LevelRenderContext context, Vec3 camera, List<Oriented> cubes, float alpha, float rimWidth, float fillAlpha) {
        submitGlassOriented(context, camera, cubes, alpha, rimWidth, fillAlpha, 1.0F);
    }

    public static void submitGlassOriented(LevelRenderContext context, Vec3 camera, List<Oriented> cubes, float alpha, float rimWidth, float fillAlpha, float opacity) {
        if (FrostedGlass.ready()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FrostedGlass.GLASS,
                (pose, buffer) -> drawGlass(pose, buffer, camera, cubes, alpha, 0.0F, frost(opacity), true));
        if (VisualQuality.glow()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> drawGlass(pose, buffer, camera, cubes, GLOW_PASS * alpha, rimWidth * RIM_GLOW_WIDTH, GLOW_FILL_STRENGTH * opacity, true));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> drawGlass(pose, buffer, camera, cubes, alpha, rimWidth, fill(fillAlpha, opacity), true));
    }

    private static void drawGlass(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, List<Oriented> cubes, float alpha, float rimWidth, float fillStrength, boolean fill) {
        int fillAlpha = Math.round(Mth.clamp(fillStrength * alpha, 0.0F, 1.0F) * 255);
        int rimAlpha = Math.round(Mth.clamp(RIM_ALPHA * alpha, 0.0F, 1.0F) * 255);
        for (Oriented cube : cubes) {
            Vec3 center = cube.center().subtract(camera);
            int body = VoxelRenderer.nearFade(center, ARGB.color(fillAlpha, cube.color()));
            int rim = VoxelRenderer.nearFade(center, ARGB.color(rimAlpha, VoxelRenderer.toWhite(cube.color(), RIM_WHITE)));
            boolean frost = fill && rimWidth <= 0.0F;
            VoxelRenderer.glass(pose, buffer, center, cube.ax(), cube.ay(), cube.az(), body, rim, rimWidth, cube.faces(), fill, frost, frost ? VoxelRenderer.FRESNEL_FROST : VoxelRenderer.FRESNEL_GLASS);
        }
    }

    public static void submitShell(LevelRenderContext context, Vec3 camera, Vec3 center, float radius, float layoutRadius, float time, int color, float alpha) {
        submitShell(context, camera, center, radius, layoutRadius, time, color, alpha, 1.0F);
    }

    public static void submitShell(LevelRenderContext context, Vec3 camera, Vec3 center, float radius, float layoutRadius, float time, int color, float alpha, float opacity) {
        float cell = shellCell(layoutRadius);
        List<ShellCell> cells = shellCells(layoutRadius, cell);
        float step = cell * radius / layoutRadius;
        float rim = step * SHELL_RIM;
        float cx = (float) (center.x - camera.x);
        float cy = (float) (center.y - camera.y);
        float cz = (float) (center.z - camera.z);
        if (FrostedGlass.ready()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FrostedGlass.GLASS,
                (pose, buffer) -> drawShell(pose, buffer, cells, cell, cx, cy, cz, step, rim, time, color, alpha, frost(opacity), true, false));
        if (VisualQuality.glow()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW_UNSORTED,
                (pose, buffer) -> drawShell(pose, buffer, cells, cell, cx, cy, cz, step, rim * RIM_GLOW_WIDTH, time, color, GLOW_PASS * alpha, GLOW_FILL_STRENGTH * opacity, true, true));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glassUnsorted(),
                (pose, buffer) -> drawShell(pose, buffer, cells, cell, cx, cy, cz, step, rim, time, color, alpha, fill(GLASS_FILL_ALPHA, opacity), true, true));
    }

    private static void drawShell(PoseStack.Pose pose, VertexConsumer buffer, List<ShellCell> cells, float cell, float cx, float cy, float cz, float step, float rimWidth,
                                  float time, int color, float alpha, float fillStrength, boolean fill, boolean rims) {
        if (!PROFILE_SHELLS) {
            emitShell(pose, buffer, cells, cell, cx, cy, cz, step, rimWidth, time, color, alpha, fillStrength, fill, rims);
            return;
        }
        long start = System.nanoTime();
        int quads = emitShell(pose, buffer, cells, cell, cx, cy, cz, step, rimWidth, time, color, alpha, fillStrength, fill, rims);
        profiledNanos += System.nanoTime() - start;
        profiledQuads += quads;
        if (++profiledPasses >= PROFILE_PASSES) {
            BrightestDay.LOGGER.info("Dome shells: {} quads and {} us per pass over {} passes", profiledQuads / profiledPasses, profiledNanos / 1000 / profiledPasses, profiledPasses);
            profiledNanos = 0L;
            profiledQuads = 0L;
            profiledPasses = 0;
        }
    }

    private static int emitShell(PoseStack.Pose pose, VertexConsumer buffer, List<ShellCell> cells, float cell, float cx, float cy, float cz, float step, float rimWidth,
                                  float time, int color, float alpha, float fillStrength, boolean fill, boolean rims) {
        int fillAlpha = Math.round(Mth.clamp(fillStrength * alpha, 0.0F, 1.0F) * 255);
        int rimAlpha = Math.round(Mth.clamp(RIM_ALPHA * alpha, 0.0F, 1.0F) * 255);
        int rimColor = VoxelRenderer.toWhite(color, RIM_WHITE);
        float waveA = time * 0.22F;
        float waveB = time * 0.31F;
        float tintWave = time * 0.1F;
        float tintStep = cell * 0.45F;
        float breath = 1.0F + SHELL_WARBLE * Mth.sin(waveA) + SHELL_WARBLE * 0.8F * Mth.sin(waveB);
        boolean frost = fill && !rims;
        int quads = 0;
        for (ShellCell shell : cells) {
            int drawn = fill ? shell.faces() : shell.faces() & shell.outer();
            if (drawn == 0) continue;
            float warble = breath;
            float spacing = step * warble;
            float x = cx + shell.x() * spacing;
            float y = cy + shell.y() * spacing;
            float z = cz + shell.z() * spacing;
            float distance = Mth.sqrt(x * x + y * y + z * z);
            float ripple = 0.5F + 0.3F * Mth.sin(waveA * 1.6F + shell.ny() * 6.0F + shell.nx() * 4.0F) + 0.2F * Mth.sin(waveB * 1.3F + shell.nz() * 5.0F - shell.nx() * 3.0F);
            int tint = VoxelRenderer.toWhite(color, 0.05F + SHELL_RIPPLE_WHITE * ripple + 0.05F * Mth.sin(tintWave + (shell.x() + shell.y() + shell.z()) * tintStep));
            int body = VoxelRenderer.nearFade(distance, ARGB.color(Math.round(Mth.clamp(fillAlpha * (1.0F - SHELL_RIPPLE_DENSITY + 2.0F * SHELL_RIPPLE_DENSITY * ripple), 0.0F, 255.0F)), tint));
            int rim = VoxelRenderer.nearFade(distance, ARGB.color(rimAlpha, rimColor));
            quads += VoxelRenderer.glassBox(pose, buffer, x, y, z, spacing * 0.5F, body, rim, rimWidth, rims ? shell.faces() : 0, drawn, fill, frost, frost ? VoxelRenderer.FRESNEL_FROST : VoxelRenderer.FRESNEL_GLASS);
        }
        return quads;
    }


    private static float shellCell(float layoutRadius) {
        float cell = Math.max(SHELL_MIN_CELL, layoutRadius * SHELL_CELL_PER_RADIUS) / Mth.sqrt(VisualQuality.level());
        return Math.max(cell, layoutRadius * Mth.sqrt(SHELL_AREA / SHELL_MAX_CELLS));
    }

    private static List<ShellCell> shellCells(float radius, float cell) {
        long key = ((long) Float.floatToIntBits(radius) << 32) | (Float.floatToIntBits(cell) & 0xFFFFFFFFL);
        List<ShellCell> cached = SHELLS.get(key);
        if (cached != null) return cached;
        if (SHELLS.size() > SHELL_CACHE) SHELLS.clear();

        int n = Mth.ceil(radius / cell) + 1;
        float limit = radius / cell;
        float limitSq = limit * limit;
        Set<Long> members = new HashSet<>();
        for (int x = -n; x <= n; x++) {
            for (int y = -n; y <= n; y++) {
                for (int z = -n; z <= n; z++) {
                    if (!inside(x, y, z, limitSq)) continue;
                    if (!inside(x + 1, y, z, limitSq) || !inside(x - 1, y, z, limitSq) || !inside(x, y + 1, z, limitSq)
                            || !inside(x, y - 1, z, limitSq) || !inside(x, y, z + 1, limitSq) || !inside(x, y, z - 1, limitSq)) {
                        members.add(pack(x, y, z));
                    }
                }
            }
        }

        List<ShellCell> cells = new ArrayList<>(members.size());
        for (long packed : members) {
            int x = (int) ((packed >> 42) & 0x1FFFFF) - SHELL_BIAS;
            int y = (int) ((packed >> 21) & 0x1FFFFF) - SHELL_BIAS;
            int z = (int) (packed & 0x1FFFFF) - SHELL_BIAS;
            int faces = 0;
            if (!members.contains(pack(x, y + 1, z))) faces |= VoxelRenderer.UP;
            if (!members.contains(pack(x, y - 1, z))) faces |= VoxelRenderer.DOWN;
            if (!members.contains(pack(x, y, z - 1))) faces |= VoxelRenderer.NORTH;
            if (!members.contains(pack(x, y, z + 1))) faces |= VoxelRenderer.SOUTH;
            if (!members.contains(pack(x - 1, y, z))) faces |= VoxelRenderer.WEST;
            if (!members.contains(pack(x + 1, y, z))) faces |= VoxelRenderer.EAST;
            float length = Mth.sqrt(x * x + y * y + z * z);
            float inverse = length > 0.0F ? 1.0F / length : 0.0F;
            int outer = (x > 0 ? VoxelRenderer.EAST : x < 0 ? VoxelRenderer.WEST : 0)
                    | (y > 0 ? VoxelRenderer.UP : y < 0 ? VoxelRenderer.DOWN : 0)
                    | (z > 0 ? VoxelRenderer.SOUTH : z < 0 ? VoxelRenderer.NORTH : 0);
            cells.add(new ShellCell(x, y, z, faces, outer, x * inverse, y * inverse, z * inverse));
        }
        SHELLS.put(key, cells);
        return cells;
    }

    private static boolean inside(int x, int y, int z, float limitSq) {
        return x * x + y * y + z * z <= limitSq;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + SHELL_BIAS) << 42) | ((long) (y + SHELL_BIAS) << 21) | (z + SHELL_BIAS);
    }

    public static List<Voxel> sphere(Vec3 center, float radius, float layoutRadius, float time, int color) {
        float spacing = Math.max(MIN_SPACING, layoutRadius * SPACING_PER_RADIUS);
        int count = Mth.clamp(Math.round(4.0F * Mth.PI * layoutRadius * layoutRadius / (spacing * spacing)), MIN_VOXELS, MAX_VOXELS);
        count = VisualQuality.count(count);
        float half = VoxelRenderer.snapSize(spacing * 0.3F);

        List<Voxel> voxels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            float y = 1.0F - 2.0F * (i + 0.5F) / count;
            float ring = Mth.sqrt(1.0F - y * y);
            float phi = i * GOLDEN_ANGLE;
            Vec3 direction = new Vec3(Mth.cos(phi) * ring, y, Mth.sin(phi) * ring);

            float warble = 1.0F
                    + 0.045F * Mth.sin(time * 0.22F + i * 0.61F)
                    + 0.035F * Mth.sin(time * 0.37F + (float) direction.y * 5.0F + (float) direction.x * 3.0F);
            Vec3 point = VoxelRenderer.snap(center.add(direction.scale(radius * warble)));
            int tint = VoxelRenderer.toWhite(color, 0.15F + 0.15F * Mth.sin(time * 0.5F + i));
            voxels.add(new Voxel(point, half, tint));
        }
        return voxels;
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, List<Voxel> voxels, float scale, float alpha, boolean shaded) {
        int alphaByte = Math.round(Mth.clamp(shaded ? VoxelRenderer.denser(alpha) : alpha, 0.0F, 1.0F) * 255);
        for (Voxel voxel : voxels) {
            Vec3 center = voxel.center().subtract(camera);
            VoxelRenderer.cube(pose, buffer, center, voxel.half() * scale, VoxelRenderer.nearFade(center, ARGB.color(alphaByte, voxel.color())), shaded, voxel.faces());
        }
    }

    private static float pop(float t) {
        if (t >= 1.0F) return 1.0F;
        if (t <= 0.0F) return 0.0F;
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private ShieldEffects() {}
}
