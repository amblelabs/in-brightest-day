package dev.amble.client.effects.attacks.weapon;

import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.hand.HandPose;
import dev.amble.core.hand.HandPoses;
import dev.amble.core.hand.HandPreset;
import dev.amble.core.networking.payloads.c2s.HandSelectC2SPayload;
import dev.amble.core.networking.payloads.s2c.HandS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HandEffects {
    private static final float CELL = 0.25F;
    private static final int HOLD_TICKS = 2;
    private static final int FADE_TICKS = 6;
    private static final int GHOSTS = 3;
    private static final float GHOST_SPACING = 0.6F;
    private static final int SHARDS = 24;
    private static final int SHARD_TICKS = 9;
    private static final float SHARD_SPEED = 0.35F;
    private static final float SHARD_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float SHIFT = 6.0F;
    private static final float RIM_SHARE = 0.16F;
    private static final float FRONT = 9.0F;
    private static final float SAMPLE = 0.7F;

    private static final int PALM = 0;
    private static final int FINGER = 1;
    private static final int KNUCKLE = 2;
    private static final int THUMB = 3;
    public static final int ARM = 4;

    private static final float[] FINGER_X = {-3.5F, -1.2F, 1.2F, 3.5F};
    private static final float[][] SEGMENTS = {{3.0F, 2.5F, 2.0F}, {3.5F, 2.5F, 2.0F}, {3.0F, 2.5F, 2.0F}, {2.5F, 2.0F, 1.5F}};
    private static final float[] JOINT_BEND = {85.0F, 100.0F, 65.0F};
    private static final float KNUCKLE_Z = 6.5F;
    private static final float THUMB_SPREAD = 70.0F;
    private static final Vec3 THUMB_TUCK = new Vec3(0.75, -0.65, 0.15).normalize();
    private static final Vec3 THUMB_BASE = new Vec3(-4.5, -0.5, 1.5);
    private static final float[] THUMB_LENGTHS = {3.0F, 2.5F};
    private static final float BLEND_SHARE = 0.6F;
    private static final int BLEND_STEP = 5;
    private static final int KEY_BIAS = 1024;

    public record Cell(float x, float y, float z, int kind, int faces) {
        public Cell(float x, float y, float z, int kind) {
            this(x, y, z, kind, VoxelRenderer.ALL_FACES);
        }
    }

    private static final Map<HandPose, List<Cell>> SHAPES = new ConcurrentHashMap<>();
    private static final int SHAPE_CACHE = 256;
    private static final List<Hand> HANDS = new ArrayList<>();

    private static final class Hand {
        final Vec3 origin;
        final Vec3 direction;
        final Vec3 side;
        final Vec3 up;
        final float reach;
        final int color;
        final int windup;
        final int travel;
        final boolean impact;
        final float size;
        final HandPose pose;
        final long seed;
        final @Nullable ClientLevel level;
        int age;

        Hand(HandS2CPayload payload, @Nullable ClientLevel level, long seed) {
            this.origin = payload.origin();
            this.direction = payload.direction().normalize();
            Vec3 side = this.direction.cross(payload.up());
            if (side.lengthSqr() < 1.0E-4) side = this.direction.cross(new Vec3(1.0, 0.0, 0.0));
            this.side = side.normalize();
            this.up = this.side.cross(this.direction).normalize();
            this.reach = payload.reach();
            this.color = ARGB.opaque(payload.color());
            this.windup = payload.windup();
            this.travel = payload.travel();
            this.impact = payload.impact();
            this.size = payload.scale();
            this.pose = payload.pose().sanitized();
            this.seed = seed;
            this.level = level;
        }

        int arrival() {
            return this.windup + this.travel;
        }

        int lifetime() {
            return this.arrival() + Math.max(HOLD_TICKS + FADE_TICKS, this.impact ? SHARD_TICKS : 0);
        }

        List<Cell> shape(float time) {
            float t = Mth.clamp(time / (this.windup + this.travel * BLEND_SHARE), 0.0F, 1.0F);
            return HandEffects.shape(blend(HandPreset.OPEN.pose(), this.pose, t * t * (3.0F - 2.0F * t)));
        }

        float distance(float time) {
            float t = Mth.clamp((time - this.windup) / this.travel, 0.0F, 1.0F);
            return this.reach * (1.0F - (1.0F - t) * (1.0F - t));
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(HandS2CPayload.TYPE, (payload, context) -> {
            ClientLevel level = context.client().level;
            if (level == null) return;
            HANDS.add(new Hand(payload, level, level.getRandom().nextLong()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HANDS.clear());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HandPoses.Selection selection = HandPoses.client();
            if (ClientPlayNetworking.canSend(HandSelectC2SPayload.TYPE)) ClientPlayNetworking.send(new HandSelectC2SPayload(selection.direction().ordinal(), selection.pose()));
        });
        ClientTickEvents.END_CLIENT_TICK.register(HandEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(HandEffects::render);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<Hand> iterator = HANDS.iterator();
        while (iterator.hasNext()) {
            Hand hand = iterator.next();
            if (hand.level != client.level || ++hand.age > hand.lifetime()) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (HANDS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Hand hand : HANDS) {
            float time = hand.age + partialTicks;
            float grow = Mth.clamp(0.3F + 0.7F * time / Math.max(hand.windup, 1), 0.3F, 1.0F);
            float fade = 1.0F - Mth.clamp((time - hand.arrival() - HOLD_TICKS) / FADE_TICKS, 0.0F, 1.0F);
            float scale = grow * hand.size;
            if (fade > 0.01F) {
                Vec3 center = hand.origin.add(hand.direction.scale(hand.distance(time)));
                ShieldEffects.submitGlassOriented(context, camera, voxels(hand, hand.shape(time), center, scale * (0.6F + 0.4F * fade), time, 0.0F), fade, rim(scale));

                boolean moving = time > hand.windup && time < hand.arrival();
                if (moving) {
                    for (int ghost = 1; ghost <= GHOSTS; ghost++) {
                        float lag = hand.distance(time) - hand.distance(time - ghost * GHOST_SPACING);
                        if (lag < 0.2F) continue;
                        Vec3 trail = center.subtract(hand.direction.scale(lag));
                        ShieldEffects.submitGlassOriented(context, camera, voxels(hand, hand.shape(time), trail, scale, time, 0.3F + 0.15F * ghost), 0.45F / ghost, rim(scale));
                    }
                }
            }

            if (hand.impact && time > hand.arrival()) {
                float t = (time - hand.arrival()) / SHARD_TICKS;
                if (t < 1.0F) {
                    Vec3 impact = hand.origin.add(hand.direction.scale(hand.reach + (FRONT - SHIFT) * CELL * hand.size));
                    ShieldEffects.submit(context, camera, shards(hand, impact, t), 1.0F - t * t);
                }
            }
        }
    }

    public static List<ShieldEffects.Oriented> voxels(Vec3 center, Vec3 forward, Vec3 side, Vec3 up, List<Cell> shape, float scale, int color, float time, float whiten) {
        float half = CELL * 0.5F * scale;
        float step = CELL * scale;
        Vec3 ax = side.scale(half);
        Vec3 ay = up.scale(half);
        Vec3 az = forward.scale(half);
        List<ShieldEffects.Oriented> voxels = new ArrayList<>(shape.size());
        for (Cell cell : shape) {
            Vec3 point = center
                    .add(side.scale(cell.x() * step))
                    .add(up.scale(cell.y() * step))
                    .add(forward.scale((cell.z() - SHIFT) * step));
            float shine = 0.12F + 0.1F * Mth.sin(time * 0.15F + (cell.x() + cell.y() + cell.z()) * 0.35F);
            voxels.add(new ShieldEffects.Oriented(point, ax, ay, az, VoxelRenderer.toWhite(color, shine + whiten), cell.faces()));
        }
        return voxels;
    }

    public static float rim(float scale) {
        return CELL * scale * RIM_SHARE;
    }

    private static List<ShieldEffects.Oriented> voxels(Hand hand, List<Cell> shape, Vec3 center, float scale, float time, float whiten) {
        return voxels(center, hand.direction, hand.side, hand.up, shape, scale, hand.color, time, whiten);
    }

    private static List<ShieldEffects.Voxel> shards(Hand hand, Vec3 impact, float t) {
        RandomSource random = RandomSource.create(hand.seed);
        float eased = 1.0F - (1.0F - t) * (1.0F - t);
        float half = VoxelRenderer.snapSize(SHARD_SIZE * (1.0F - 0.6F * t) * 0.5F);
        int count = VisualQuality.count(SHARDS);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextFloat() * Mth.TWO_PI;
            double spread = 0.5 + random.nextFloat() * 0.8;
            Vec3 velocity = hand.side.scale(Math.cos(angle) * spread)
                    .add(hand.up.scale(Math.sin(angle) * spread))
                    .add(hand.direction.scale(-0.3 - random.nextFloat() * 0.5));
            Vec3 point = impact.add(velocity.scale(SHARD_SPEED * SHARD_TICKS * eased * hand.size)).add(0.0, -0.4 * t * t, 0.0);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, VoxelRenderer.toWhite(hand.color, 0.6F * (1.0F - t))));
        }
        return voxels;
    }

    public static HandPose blend(HandPose from, HandPose to, float t) {
        return new HandPose(step(from.index(), to.index(), t), step(from.middle(), to.middle(), t), step(from.ring(), to.ring(), t),
                step(from.pinky(), to.pinky(), t), step(from.thumbCurl(), to.thumbCurl(), t), step(from.thumbSpread(), to.thumbSpread(), t));
    }

    private static int step(int from, int to, float t) {
        return Math.round(Mth.lerp(t, from, to) / BLEND_STEP) * BLEND_STEP;
    }

    public static Vec3 local(double x, double y, double z) {
        return new Vec3(x * CELL, y * CELL, (z - SHIFT) * CELL);
    }

    public static Vec3 tip(HandPose pose, int finger) {
        if (finger >= 4) {
            Vec3 base = THUMB_BASE;
            double spreadAngle = Math.toRadians(THUMB_SPREAD * pose.thumbSpread() / (float) HandPose.MAX);
            Vec3 open = new Vec3(-Math.sin(spreadAngle), 0.0, Math.cos(spreadAngle));
            float curl = pose.thumbCurl() / (float) HandPose.MAX;
            base = base.add(open.lerp(THUMB_TUCK, curl * 0.5F).normalize().scale(THUMB_LENGTHS[0]));
            return base.add(open.lerp(THUMB_TUCK, curl).normalize().scale(THUMB_LENGTHS[1]));
        }
        float curl = pose.finger(finger) / (float) HandPose.MAX;
        double y = 0.0;
        double z = KNUCKLE_Z;
        double angle = 0.0;
        for (int joint = 0; joint < 3; joint++) {
            angle += Math.toRadians(JOINT_BEND[joint] * curl);
            y -= Math.sin(angle) * SEGMENTS[finger][joint];
            z += Math.cos(angle) * SEGMENTS[finger][joint];
        }
        return new Vec3(FINGER_X[finger], y, z);
    }

    public static List<Cell> shape(HandPose pose) {
        List<Cell> cached = SHAPES.get(pose);
        if (cached != null) return cached;
        if (SHAPES.size() >= SHAPE_CACHE) SHAPES.clear();
        List<Cell> built = build(pose);
        SHAPES.put(pose, built);
        return built;
    }

    private static List<Cell> build(HandPose pose) {
        List<Cell> cells = new ArrayList<>();
        box(cells, -4, 4, -1, 1, 0, 6, PALM);
        box(cells, -3, 3, -1, 1, -6, -1, ARM);
        for (int finger = 0; finger < 4; finger++) finger(cells, FINGER_X[finger], SEGMENTS[finger], pose.finger(finger) / (float) HandPose.MAX);
        thumb(cells, pose.thumbCurl() / (float) HandPose.MAX, pose.thumbSpread() / (float) HandPose.MAX);
        return cull(cells);
    }

    private static List<Cell> cull(List<Cell> raw) {
        Map<Long, Integer> kinds = new java.util.LinkedHashMap<>();
        for (Cell cell : raw) {
            long key = key(Math.round(cell.x()), Math.round(cell.y()), Math.round(cell.z()));
            Integer kind = kinds.get(key);
            if (kind == null || cell.kind() == KNUCKLE) kinds.put(key, cell.kind());
        }
        List<Cell> cells = new ArrayList<>(kinds.size());
        for (Map.Entry<Long, Integer> entry : kinds.entrySet()) {
            int x = unpack(entry.getKey(), 0);
            int y = unpack(entry.getKey(), 1);
            int z = unpack(entry.getKey(), 2);
            int faces = 0;
            if (!kinds.containsKey(key(x, y + 1, z))) faces |= VoxelRenderer.UP;
            if (!kinds.containsKey(key(x, y - 1, z))) faces |= VoxelRenderer.DOWN;
            if (!kinds.containsKey(key(x, y, z - 1))) faces |= VoxelRenderer.NORTH;
            if (!kinds.containsKey(key(x, y, z + 1))) faces |= VoxelRenderer.SOUTH;
            if (!kinds.containsKey(key(x - 1, y, z))) faces |= VoxelRenderer.WEST;
            if (!kinds.containsKey(key(x + 1, y, z))) faces |= VoxelRenderer.EAST;
            if (faces != 0) cells.add(new Cell(x, y, z, entry.getValue(), faces));
        }
        return List.copyOf(cells);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + KEY_BIAS) << 32) | ((long) (y + KEY_BIAS) << 16) | (z + KEY_BIAS);
    }

    private static int unpack(long key, int axis) {
        return (int) (key >> (32 - axis * 16) & 0xFFFF) - KEY_BIAS;
    }

    private static void box(List<Cell> cells, int x0, int x1, int y0, int y1, int z0, int z1, int kind) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    boolean surface = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    if (surface) cells.add(new Cell(x, y, z, kind));
                }
            }
        }
    }

    private static void finger(List<Cell> cells, float x, float[] lengths, float curl) {
        double y = 0.0;
        double z = KNUCKLE_Z;
        double angle = 0.0;
        for (int joint = 0; joint < lengths.length; joint++) {
            angle += Math.toRadians(JOINT_BEND[joint] * curl);
            double dy = -Math.sin(angle);
            double dz = Math.cos(angle);
            double ny = Math.cos(angle) * 0.5;
            double nz = Math.sin(angle) * 0.5;
            for (float t = 0.0F; t <= lengths[joint]; t += SAMPLE) {
                double py = y + dy * t;
                double pz = z + dz * t;
                int kind = joint == 0 && t == 0.0F ? KNUCKLE : FINGER;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sn = -1; sn <= 1; sn += 2) {
                        cells.add(new Cell(x + sx * 0.5F, (float) (py + sn * ny), (float) (pz + sn * nz), kind));
                    }
                }
            }
            y += dy * lengths[joint];
            z += dz * lengths[joint];
        }
    }

    private static void thumb(List<Cell> cells, float curl, float spread) {
        double spreadAngle = Math.toRadians(THUMB_SPREAD * spread);
        Vec3 open = new Vec3(-Math.sin(spreadAngle), 0.0, Math.cos(spreadAngle));
        Vec3 base = THUMB_BASE;
        float[] lengths = THUMB_LENGTHS;
        float[] bends = {0.5F, 1.0F};
        for (int joint = 0; joint < lengths.length; joint++) {
            Vec3 dir = open.lerp(THUMB_TUCK, curl * bends[joint]).normalize();
            for (float t = 0.0F; t <= lengths[joint]; t += SAMPLE) {
                Vec3 p = base.add(dir.scale(t));
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        cells.add(new Cell((float) p.x + sx * 0.5F, (float) p.y + sy * 0.5F, (float) p.z, THUMB));
                    }
                }
            }
            base = base.add(dir.scale(lengths[joint]));
        }
    }

    private HandEffects() {}
}
