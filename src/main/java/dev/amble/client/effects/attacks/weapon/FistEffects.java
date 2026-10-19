package dev.amble.client.effects.attacks.weapon;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.FistS2CPayload;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class FistEffects {
    private static final float CELL = 0.25F;
    private static final float VOXEL_FILL = 0.9F;
    private static final int HOLD_TICKS = 2;
    private static final int FADE_TICKS = 6;
    private static final int GHOSTS = 3;
    private static final float GHOST_SPACING = 0.6F;
    private static final int SHARDS = 24;
    private static final int SHARD_TICKS = 9;
    private static final float SHARD_SPEED = 0.35F;
    private static final float SHARD_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int SHAPE_FRONT = 6;
    private static final int SHAPE_SHIFT = 1;

    private static final int BODY = 0;
    private static final int FINGER = 1;
    private static final int KNUCKLE = 2;
    private static final int THUMB = 3;
    private static final int ARM = 4;

    private static final List<int[]> SHAPE = buildShape();
    private static final List<Fist> FISTS = new ArrayList<>();

    private static final class Fist {
        final Vec3 origin;
        final Vec3 direction;
        final Vec3 side;
        final Vec3 up;
        final float reach;
        final int color;
        final int windup;
        final int travel;
        final boolean impact;
        final long seed;
        final @Nullable ClientLevel level;
        int age;

        Fist(FistS2CPayload payload, @Nullable ClientLevel level, long seed) {
            this.origin = payload.origin();
            this.direction = payload.direction().normalize();
            Vec3 side = this.direction.cross(new Vec3(0.0, 1.0, 0.0));
            this.side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            this.up = this.side.cross(this.direction).normalize();
            this.reach = payload.reach();
            this.color = ARGB.opaque(payload.color());
            this.windup = payload.windup();
            this.travel = payload.travel();
            this.impact = payload.impact();
            this.seed = seed;
            this.level = level;
        }

        int arrival() {
            return this.windup + this.travel;
        }

        int lifetime() {
            return this.arrival() + Math.max(HOLD_TICKS + FADE_TICKS, this.impact ? SHARD_TICKS : 0);
        }

        float distance(float time) {
            float t = Mth.clamp((time - this.windup) / this.travel, 0.0F, 1.0F);
            return this.reach * (1.0F - (1.0F - t) * (1.0F - t));
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(FistS2CPayload.TYPE, (payload, context) -> {
            ClientLevel level = context.client().level;
            if (level == null) return;
            FISTS.add(new Fist(payload, level, level.getRandom().nextLong()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FISTS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(FistEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(FistEffects::render);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<Fist> iterator = FISTS.iterator();
        while (iterator.hasNext()) {
            Fist fist = iterator.next();
            if (fist.level != client.level || ++fist.age > fist.lifetime()) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (FISTS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Fist fist : FISTS) {
            float time = fist.age + partialTicks;
            float scale = Mth.clamp(0.3F + 0.7F * time / Math.max(fist.windup, 1), 0.3F, 1.0F);
            float fade = 1.0F - Mth.clamp((time - fist.arrival() - HOLD_TICKS) / FADE_TICKS, 0.0F, 1.0F);
            if (fade > 0.01F) {
                Vec3 center = fist.origin.add(fist.direction.scale(fist.distance(time)));
                ShieldEffects.submit(context, camera, fist(fist, center, scale * (0.6F + 0.4F * fade), time, 0.0F), fade);

                boolean moving = time > fist.windup && time < fist.arrival();
                if (moving) {
                    for (int ghost = 1; ghost <= GHOSTS; ghost++) {
                        float lag = fist.distance(time) - fist.distance(time - ghost * GHOST_SPACING);
                        if (lag < 0.2F) continue;
                        Vec3 trail = center.subtract(fist.direction.scale(lag));
                        ShieldEffects.submit(context, camera, fist(fist, trail, scale, time, 0.3F + 0.15F * ghost), 0.45F / ghost);
                    }
                }
            }

            if (fist.impact && time > fist.arrival()) {
                float t = (time - fist.arrival()) / SHARD_TICKS;
                if (t < 1.0F) {
                    Vec3 impact = fist.origin.add(fist.direction.scale(fist.reach + (SHAPE_FRONT - SHAPE_SHIFT) * CELL));
                    ShieldEffects.submit(context, camera, shards(fist, impact, t), 1.0F - t * t);
                }
            }
        }
    }

    private static List<ShieldEffects.Voxel> fist(Fist fist, Vec3 center, float scale, float time, float whiten) {
        float half = VoxelRenderer.snapSize(CELL * VOXEL_FILL * 0.5F * scale);
        float step = CELL * scale;
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(SHAPE.size());
        for (int[] cell : SHAPE) {
            Vec3 point = center
                    .add(fist.side.scale(cell[0] * step))
                    .add(fist.up.scale(cell[1] * step))
                    .add(fist.direction.scale((cell[2] - SHAPE_SHIFT) * step));
            float shine = switch (cell[3]) {
                case KNUCKLE -> 0.55F;
                case FINGER -> (cell[0] & 1) == 0 ? 0.25F : 0.15F;
                case THUMB -> 0.3F;
                case ARM -> 0.0F;
                default -> 0.1F;
            };
            shine += 0.08F * Mth.sin(time * 0.7F + cell[0] + cell[1] * 0.5F);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, VoxelRenderer.toWhite(fist.color, shine + whiten)));
        }
        return voxels;
    }

    private static List<ShieldEffects.Voxel> shards(Fist fist, Vec3 impact, float t) {
        RandomSource random = RandomSource.create(fist.seed);
        float eased = 1.0F - (1.0F - t) * (1.0F - t);
        float half = VoxelRenderer.snapSize(SHARD_SIZE * (1.0F - 0.6F * t) * 0.5F);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(VisualQuality.count(SHARDS));
        for (int i = 0; i < VisualQuality.count(SHARDS); i++) {
            double angle = random.nextFloat() * Mth.TWO_PI;
            double spread = 0.5 + random.nextFloat() * 0.8;
            Vec3 velocity = fist.side.scale(Math.cos(angle) * spread)
                    .add(fist.up.scale(Math.sin(angle) * spread))
                    .add(fist.direction.scale(-0.3 - random.nextFloat() * 0.5));
            Vec3 point = impact.add(velocity.scale(SHARD_SPEED * SHARD_TICKS * eased)).add(0.0, -0.4 * t * t, 0.0);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, VoxelRenderer.toWhite(fist.color, 0.6F * (1.0F - t))));
        }
        return voxels;
    }

    private static List<int[]> buildShape() {
        Set<Long> filled = new HashSet<>();
        List<int[]> cells = new ArrayList<>();
        for (int x = -5; x <= 5; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -1; z <= 3; z++) {
                    boolean corner = (Math.abs(x) == 5 && Math.abs(y) == 3) || (Math.abs(x) == 5 && z == -1) || (Math.abs(y) == 3 && z == -1);
                    if (!corner) add(filled, cells, x, y, z, BODY);
                }
            }
        }
        for (int finger = 0; finger < 4; finger++) {
            int x0 = -5 + finger * 3;
            for (int x = x0; x <= x0 + 1; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = 4; z <= 5; z++) {
                        if (y == 3 && z == 5) continue;
                        add(filled, cells, x, y, z, FINGER);
                    }
                }
                add(filled, cells, x, 1, SHAPE_FRONT, KNUCKLE);
                add(filled, cells, x, 2, SHAPE_FRONT, KNUCKLE);
            }
        }
        for (int x = -5; x <= 1; x++) {
            for (int y = -3; y <= -2; y++) {
                for (int z = 4; z <= 5; z++) {
                    add(filled, cells, x, y, z, THUMB);
                }
            }
        }
        for (int y = -2; y <= 1; y++) {
            for (int z = 0; z <= 3; z++) {
                add(filled, cells, -6, y, z, THUMB);
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 2; y++) {
                for (int z = -7; z <= -2; z++) {
                    boolean corner = Math.abs(x) == 3 && (y == -3 || y == 2);
                    if (!corner) add(filled, cells, x, y, z, ARM);
                }
            }
        }

        List<int[]> shell = new ArrayList<>();
        for (int[] cell : cells) {
            if (!surrounded(filled, cell[0], cell[1], cell[2])) shell.add(cell);
        }
        return List.copyOf(shell);
    }

    private static void add(Set<Long> filled, List<int[]> cells, int x, int y, int z, int kind) {
        if (filled.add(key(x, y, z))) cells.add(new int[]{x, y, z, kind});
    }

    private static boolean surrounded(Set<Long> filled, int x, int y, int z) {
        return filled.contains(key(x + 1, y, z)) && filled.contains(key(x - 1, y, z))
                && filled.contains(key(x, y + 1, z)) && filled.contains(key(x, y - 1, z))
                && filled.contains(key(x, y, z + 1)) && filled.contains(key(x, y, z - 1));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 64) << 16) | ((long) (y + 64) << 8) | (z + 64);
    }

    private FistEffects() {}
}
