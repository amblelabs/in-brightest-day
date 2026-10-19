package dev.amble.client.effects.attacks.projectile;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.LanceS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class LanceEffects {
    private static final int LIFETIME = 24;
    private static final float CORE_SPACING = 1.0F * VoxelRenderer.PIXEL;
    private static final float CORE_SIZE = 1.5F * VoxelRenderer.PIXEL;
    private static final float SPARK_SPACING = 0.6F;
    private static final float SPARK_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final float SPARK_DRIFT = 0.04F;
    private static final int IMPACT_VOXELS = 30;
    private static final float IMPACT_SPEED = 0.35F;
    private static final float IMPACT_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int IMPACT_LIFETIME = 10;
    private static final int HIT_VOXELS = 10;
    private static final float HIT_SPEED = 0.2F;
    private static final float HIT_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final int HIT_LIFETIME = 8;

    private static final List<Lance> LANCES = new ArrayList<>();

    private static final class Lance {
        final Vec3 start;
        final Vec3 end;
        final int color;
        final long seed;
        int age;

        Lance(Vec3 start, Vec3 end, int color, long seed) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(LanceS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LANCES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(LanceEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(LanceEffects::render);
    }

    private static void spawn(Minecraft client, LanceS2CPayload payload) {
        if (client.level == null) return;
        Entity shooter = client.level.getEntity(payload.shooterId());
        Vec3 start = shooter instanceof Player player ? BlastEffects.hand(player, 1.0F) : payload.end();
        int color = ARGB.opaque(payload.color());
        LANCES.add(new Lance(start, payload.end(), color, client.level.getRandom().nextLong()));
        ProjectileBursts.spawn(payload.end(), color, VisualQuality.count(IMPACT_VOXELS), IMPACT_SPEED, IMPACT_SIZE, IMPACT_LIFETIME);
        for (Vec3 hit : payload.hits()) ProjectileBursts.spawn(hit, color, HIT_VOXELS, HIT_SPEED, HIT_SIZE, HIT_LIFETIME);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        LANCES.removeIf(lance -> ++lance.age >= LIFETIME);
    }

    private static void render(LevelRenderContext context) {
        if (LANCES.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Lance lance : LANCES) {
            float time = lance.age + partialTicks;
            float t = Mth.clamp(time / LIFETIME, 0.0F, 1.0F);
            float life = 1.0F - t;
            if (life <= 0.0F) continue;

            Vec3 axis = lance.end.subtract(lance.start);
            float length = (float) axis.length();
            if (length < 1.0E-3F) continue;
            Vec3 direction = axis.scale(1.0 / length);
            Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
            side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
            Vec3 up = side.cross(direction).normalize();

            List<ShieldEffects.Voxel> voxels = new ArrayList<>();
            int core = VoxelRenderer.toWhite(lance.color, 0.85F * life + 0.15F);
            float coreHalf = VoxelRenderer.snapSize(CORE_SIZE * (0.4F + 0.6F * life) * 0.5F);
            int count = Math.max(Mth.floor(length / CORE_SPACING), 1);
            for (int i = 0; i <= count; i++) {
                Vec3 point = lance.start.add(direction.scale(length * i / count));
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), coreHalf, core));
            }

            RandomSource random = RandomSource.create(lance.seed);
            int sparks = Math.max(Mth.floor(length / SPARK_SPACING), 1);
            int sparkTint = VoxelRenderer.toWhite(lance.color, 0.3F * life);
            for (int i = 0; i < sparks; i++) {
                float along = (i + random.nextFloat()) / sparks;
                float angle = random.nextFloat() * Mth.TWO_PI;
                float drift = SPARK_DRIFT * time * (0.5F + random.nextFloat());
                float half = SPARK_SIZE * life * (0.5F + random.nextFloat() * 0.5F) * 0.5F;
                if (half < VoxelRenderer.PIXEL * 0.25F) continue;
                Vec3 offset = side.scale(Mth.cos(angle) * drift).add(up.scale(Mth.sin(angle) * drift));
                Vec3 point = lance.start.add(direction.scale(length * along)).add(offset);
                voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), VoxelRenderer.snapSize(half), sparkTint));
            }
            ShieldEffects.submit(context, camera, voxels, life * (2.0F - life));
        }
    }

    private LanceEffects() {}
}
