package dev.amble.client.flight;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.ringpowers.CorpsColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class FlightTrail {
    private static final int MAX_AGE = 20;
    private static final int SMOOTHING_PASSES = 2;
    private static final double MIN_POINT_SPACING = 0.02;
    private static final double MAX_POINT_JUMP = 6.0;
    private static final double EMIT_SPEED = 0.5;
    private static final float PIXEL = VoxelRenderer.PIXEL;

    private static final float HIP_HEIGHT = 0.75F;
    private static final float LEG_LENGTH = 0.75F;
    private static final float FOOT_SPACING = 0.125F;

    private static final float VOXEL_SIZE = 3.0F * PIXEL;
    private static final float VOXEL_SPACING = 2.0F * PIXEL;
    private static final float TRAIL_START_GAP = 0.4F;
    private static final float MIN_VOXEL_SIZE = 0.5F * PIXEL;
    private static final float GLASS_ALPHA = 0.45F;
    private static final float GLOW_SCALE = 1.8F;
    private static final float GLOW_ALPHA = 0.18F;

    private static final double AURA_START_SPEED = 2.0;
    private static final int AURA_RINGS = 10;
    private static final int AURA_SEGMENTS = 20;
    private static final float AURA_RADIUS = 1.0F;
    private static final float AURA_LENGTH = 3.0F;
    private static final float AURA_NOSE_OFFSET = 1.4F;
    private static final float AURA_VOXEL_SIZE = 2.5F * PIXEL;
    private static final float AURA_ALPHA = 0.7F;
    private static final float FIRST_PERSON_AURA_ALPHA = 0.35F;
    private static final int WHITE_HOT = 0xFFF4E8;

    private static final Map<Player, Trail> TRAILS = new WeakHashMap<>();

    private record Point(Vec3 pos, int age) {}

    private record Voxel(Vec3 center, float half, float life) {}

    private static final class Trail {
        final List<ArrayDeque<Point>> feet = List.of(new ArrayDeque<>(), new ArrayDeque<>());
        int color;
        boolean emitting;
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        Vec3 oDirection = direction;
        float aura, oAura;

        boolean isEmpty() {
            return this.feet.stream().allMatch(ArrayDeque::isEmpty);
        }
    }

    public static void init() {
        FlightRenderTypes.init();
        ClientTickEvents.END_CLIENT_TICK.register(FlightTrail::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(FlightTrail::render);
    }

    private static void tick(Minecraft client) {
        if (client.level == null) {
            TRAILS.clear();
            return;
        }
        TRAILS.keySet().removeIf(player -> !isPresent(client, player));
        if (client.isPaused()) return;

        for (AbstractClientPlayer player : client.level.players()) {
            Vec3 velocity = player.position().subtract(player.xo, player.yo, player.zo);
            double speed = velocity.length();
            boolean flying = FlightRingPower.isFlying(player);
            boolean emit = flying && speed > EMIT_SPEED && BrightestDayConfig.get().showFlightTrails;
            float targetAura = flying ? Mth.clamp((float) ((speed - AURA_START_SPEED) / (FlightRingPower.BOOST_SPEED - AURA_START_SPEED)), 0.0F, 1.0F) : 0.0F;

            Trail trail = TRAILS.get(player);
            if (trail == null) {
                if (!emit && targetAura <= 0.0F) continue;
                trail = new Trail();
                TRAILS.put(player, trail);
            }

            for (int foot = 0; foot < trail.feet.size(); foot++) {
                ArrayDeque<Point> points = trail.feet.get(foot);
                List<Point> aged = new ArrayList<>(points.size());
                for (Point point : points) {
                    if (point.age() + 1 < MAX_AGE) aged.add(new Point(point.pos(), point.age() + 1));
                }
                points.clear();
                points.addAll(aged);
                Vec3 position = foot(player, 1.0F, footSide(foot));
                Point newest = points.peekFirst();
                double gap = newest == null ? 0.0 : newest.pos().distanceTo(position);
                if (gap > MAX_POINT_JUMP) points.clear();
                if (emit && (points.isEmpty() || gap > MIN_POINT_SPACING)) points.addFirst(new Point(position, 0));
            }

            trail.emitting = emit;
            trail.color = CorpsColors.of(player);
            trail.oDirection = trail.direction;
            if (speed > 0.05) trail.direction = trail.direction.lerp(velocity.normalize(), 0.35).normalize();
            trail.oAura = trail.aura;
            trail.aura += (targetAura - trail.aura) * 0.15F;

            if (trail.isEmpty() && trail.aura < 0.005F) TRAILS.remove(player);
        }
    }

    private static void render(LevelRenderContext context) {
        if (TRAILS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        PoseStack poseStack = context.poseStack();

        for (Map.Entry<Player, Trail> entry : TRAILS.entrySet()) {
            Player player = entry.getKey();
            if (!isPresent(client, player)) continue;
            Trail trail = entry.getValue();
            boolean ownView = firstPerson && player == client.player;
            int color = ARGB.opaque(trail.color);

            List<Voxel> voxels = new ArrayList<>();
            for (int foot = 0; foot < trail.feet.size(); foot++) {
                List<Vec3> positions = new ArrayList<>();
                List<Float> lives = new ArrayList<>();
                if (trail.emitting) {
                    positions.add(foot(player, partialTicks, footSide(foot)));
                    lives.add(1.0F);
                }
                for (Point point : trail.feet.get(foot)) {
                    if (point.age() == 0) continue;
                    positions.add(point.pos());
                    lives.add(1.0F - Math.min((point.age() + partialTicks) / MAX_AGE, 1.0F));
                }
                if (positions.size() >= 2) voxelize(positions, lives, camera, voxels);
            }

            if (!voxels.isEmpty()) {
                context.submitNodeCollector().submitCustomGeometry(poseStack, FlightRenderTypes.GLOW, (pose, buffer) -> {
                    for (Voxel voxel : voxels) {
                        int tint = VoxelRenderer.toWhite(color, 1.0F - voxel.life());
                        VoxelRenderer.cube(pose, buffer, voxel.center(), voxel.half() * GLOW_SCALE, VoxelRenderer.nearFade(voxel.center(), ARGB.color(Math.round(GLOW_ALPHA * voxel.life() * 255), tint)), false);
                    }
                });
                context.submitNodeCollector().submitCustomGeometry(poseStack, FlightRenderTypes.glass(), (pose, buffer) -> {
                    int glassColor = ARGB.srgbLerp(0.3F, color, 0xFFFFFFFF);
                    for (Voxel voxel : voxels) {
                        int tint = VoxelRenderer.toWhite(glassColor, 1.0F - voxel.life());
                        VoxelRenderer.cube(pose, buffer, voxel.center(), voxel.half(), VoxelRenderer.nearFade(voxel.center(), ARGB.color(Math.round(GLASS_ALPHA * voxel.life() * 255), tint)), true);
                    }
                });
            }

            float aura = Mth.lerp(partialTicks, trail.oAura, trail.aura) * (ownView ? FIRST_PERSON_AURA_ALPHA : 1.0F);
            if (aura > 0.005F) {
                Vec3 center = player.getPosition(partialTicks).add(0.0, player.getBbHeight() * 0.5, 0.0);
                Vec3 direction = trail.oDirection.lerp(trail.direction, partialTicks).normalize();
                float time = player.tickCount + partialTicks;
                context.submitNodeCollector().submitCustomGeometry(poseStack, FlightRenderTypes.GLOW,
                        (pose, buffer) -> aura(pose, buffer, center, camera, direction, aura, color, time));
            }
        }
    }

    private static void voxelize(List<Vec3> positions, List<Float> lives, Vec3 camera, List<Voxel> out) {
        List<Vec3> smooth = new ArrayList<>();
        List<Float> smoothLives = new ArrayList<>();
        subdivide(positions, lives, smooth, smoothLives);

        float travelled = 0.0F;
        float nextVoxel = TRAIL_START_GAP;
        Vec3 lastCell = null;
        for (int i = 0; i < smooth.size() - 1; i++) {
            Vec3 a = smooth.get(i);
            Vec3 b = smooth.get(i + 1);
            float segment = (float) a.distanceTo(b);
            if (segment > MAX_POINT_JUMP) {
                travelled += segment;
                nextVoxel = travelled + TRAIL_START_GAP;
                lastCell = null;
                continue;
            }
            while (segment > 0.0F && nextVoxel <= travelled + segment) {
                float t = (nextVoxel - travelled) / segment;
                float life = Mth.lerp(t, smoothLives.get(i), smoothLives.get(i + 1));
                float half = VOXEL_SIZE * life * 0.5F;
                Vec3 cell = VoxelRenderer.snap(a.lerp(b, t));
                if (half >= MIN_VOXEL_SIZE * 0.5F && !cell.equals(lastCell)) {
                    out.add(new Voxel(cell.subtract(camera), VoxelRenderer.snapSize(half), life));
                    lastCell = cell;
                }
                nextVoxel += VOXEL_SPACING;
            }
            travelled += segment;
        }
    }

    private static void aura(PoseStack.Pose pose, VertexConsumer buffer, Vec3 center, Vec3 camera, Vec3 direction, float intensity, int color, float time) {
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 a = direction.cross(reference).normalize();
        Vec3 b = direction.cross(a);
        Vec3 nose = center.add(direction.scale(AURA_NOSE_OFFSET));
        float length = AURA_LENGTH * (0.7F + 0.3F * intensity);

        for (int ring = 0; ring <= AURA_RINGS; ring++) {
            float s = (float) ring / AURA_RINGS;
            int segments = ring == 0 ? 1 : AURA_SEGMENTS;
            for (int segment = 0; segment < segments; segment++) {
                float angle = segment * Mth.TWO_PI / AURA_SEGMENTS + ring * 0.35F;
                float wobble = 1.0F + 0.08F * Mth.sin(angle * 3.0F + time * 0.9F + s * 6.0F);
                float radius = AURA_RADIUS * Mth.sqrt(s) * wobble;
                Vec3 point = nose.subtract(direction.scale(s * length))
                        .add(a.scale(Mth.cos(angle) * radius))
                        .add(b.scale(Mth.sin(angle) * radius));

                float flicker = 0.85F + 0.15F * Mth.sin(time * 1.7F + angle * 5.0F + s * 10.0F);
                float fade = (float) Math.pow(1.0F - s, 1.5F);
                int tint = ARGB.srgbLerp(Math.min(s * 2.5F, 1.0F), ARGB.opaque(WHITE_HOT), color);
                int voxelColor = ARGB.color(Math.round(AURA_ALPHA * intensity * fade * flicker * 255), tint);
                float half = VoxelRenderer.snapSize(AURA_VOXEL_SIZE * (1.0F - 0.5F * s) * 0.5F);
                Vec3 relative = VoxelRenderer.snap(point).subtract(camera);
                VoxelRenderer.cube(pose, buffer, relative, half, VoxelRenderer.nearFade(relative, voxelColor), false);
            }
        }
    }

    private static void subdivide(List<Vec3> points, List<Float> lives, List<Vec3> outPoints, List<Float> outLives) {
        List<Vec3> currentPoints = new ArrayList<>(points);
        List<Float> currentLives = new ArrayList<>(lives);
        for (int pass = 0; pass < SMOOTHING_PASSES && currentPoints.size() > 2; pass++) {
            List<Vec3> nextPoints = new ArrayList<>(currentPoints.size() * 2);
            List<Float> nextLives = new ArrayList<>(currentLives.size() * 2);
            nextPoints.add(currentPoints.getFirst());
            nextLives.add(currentLives.getFirst());
            for (int i = 0; i < currentPoints.size() - 1; i++) {
                Vec3 a = currentPoints.get(i);
                Vec3 b = currentPoints.get(i + 1);
                float la = currentLives.get(i);
                float lb = currentLives.get(i + 1);
                nextPoints.add(a.lerp(b, 0.25));
                nextLives.add(Mth.lerp(0.25F, la, lb));
                nextPoints.add(a.lerp(b, 0.75));
                nextLives.add(Mth.lerp(0.75F, la, lb));
            }
            nextPoints.add(currentPoints.getLast());
            nextLives.add(currentLives.getLast());
            currentPoints = nextPoints;
            currentLives = nextLives;
        }
        outPoints.addAll(currentPoints);
        outLives.addAll(currentLives);
    }

    private static boolean isPresent(Minecraft client, Player player) {
        return !player.isRemoved() && player.level() == client.level;
    }

    private static float footSide(int foot) {
        return foot == 0 ? 1.0F : -1.0F;
    }

    private static Vec3 foot(Player player, float partialTicks, float side) {
        float yaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        float pitch = FlightAnimator.bodyPitch(player, partialTicks) * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        Vec3 down = new Vec3(0.0, -Mth.cos(pitch), 0.0).add(facing.scale(-Mth.sin(pitch)));
        return player.getPosition(partialTicks)
                .add(FlightAnimator.worldDiveOffset(player, partialTicks))
                .add(0.0, HIP_HEIGHT, 0.0)
                .add(down.scale(LEG_LENGTH))
                .add(right.scale(side * FOOT_SPACING));
    }

    private FlightTrail() {}
}
