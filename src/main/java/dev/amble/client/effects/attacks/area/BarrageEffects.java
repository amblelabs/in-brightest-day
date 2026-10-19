package dev.amble.client.effects.attacks.area;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.networking.payloads.s2c.BarrageBoltS2CPayload;
import dev.amble.core.networking.payloads.s2c.BarrageS2CPayload;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BarrageEffects {
    private static final float BOLT_SPEED = 4.5F;
    private static final float TRACER_LENGTH = 1.4F;
    private static final float TRACER_SPACING = 2.0F * VoxelRenderer.PIXEL;
    private static final float TRACER_VOXEL_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final int IMPACT_TICKS = 5;
    private static final int IMPACT_VOXELS = 10;
    private static final float IMPACT_VOXEL_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float IMPACT_SPEED = 0.18F;
    private static final int MUZZLE_TICKS = 3;
    private static final float MUZZLE_VOXEL_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float MUZZLE_FLASH_SIZE = 7.0F * VoxelRenderer.PIXEL;

    private static final Map<Integer, Integer> FIRING = new HashMap<>();
    private static final Map<Integer, Integer> LAST_SHOT = new HashMap<>();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static @Nullable ClientLevel barrageLevel;

    private static final class Bolt {
        final Vec3 start;
        final Vec3 end;
        final int color;
        final boolean hit;
        final long seed;
        final float travel;
        int age;

        Bolt(Vec3 start, Vec3 end, int color, boolean hit, long seed) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.hit = hit;
            this.seed = seed;
            this.travel = Math.max((float) start.distanceTo(end) / BOLT_SPEED, 1.0F);
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BarrageS2CPayload.TYPE, (payload, context) -> {
            if (payload.active()) FIRING.put(payload.playerId(), ARGB.opaque(payload.color()));
            else FIRING.remove(payload.playerId());
        });
        ClientPlayNetworking.registerGlobalReceiver(BarrageBoltS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            FIRING.clear();
            LAST_SHOT.clear();
            BOLTS.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(BarrageEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(BarrageEffects::render);
    }

    public static boolean isFiring(Player player) {
        return FIRING.containsKey(player.getId());
    }

    private static void spawn(Minecraft client, BarrageBoltS2CPayload payload) {
        if (client.level == null) return;
        Entity shooter = client.level.getEntity(payload.playerId());
        Vec3 start = shooter instanceof Player player ? BlastEffects.hand(player, 1.0F) : shooter != null ? shooter.getEyePosition() : payload.end();
        BOLTS.add(new Bolt(start, payload.end(), ARGB.opaque(payload.color()), payload.hit(), client.level.getRandom().nextLong()));
        LAST_SHOT.put(payload.playerId(), 0);
    }

    private static void tick(Minecraft client) {
        if (client.level != barrageLevel) {
            FIRING.clear();
            LAST_SHOT.clear();
            BOLTS.clear();
            barrageLevel = client.level;
        }
        if (client.isPaused()) return;
        BOLTS.removeIf(bolt -> ++bolt.age > bolt.travel + IMPACT_TICKS);
        LAST_SHOT.replaceAll((id, age) -> age + 1);
        LAST_SHOT.values().removeIf(age -> age > MUZZLE_TICKS);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || FIRING.isEmpty() && BOLTS.isEmpty()) return;

        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Map.Entry<Integer, Integer> entry : FIRING.entrySet()) {
            if (client.level.getEntity(entry.getKey()) instanceof Player player) muzzle(context, camera, player, entry.getValue(), partialTicks);
        }
        for (Bolt bolt : BOLTS) {
            float time = bolt.age + partialTicks;
            tracer(context, camera, bolt, time);
            impact(context, camera, bolt, time - bolt.travel);
        }
    }

    private static void muzzle(LevelRenderContext context, Vec3 camera, Player player, int color, float partialTicks) {
        Vec3 hand = BlastEffects.hand(player, partialTicks);
        Integer since = LAST_SHOT.get(player.getId());
        float flash = since == null ? 0.0F : 1.0F - Mth.clamp((since + partialTicks) / MUZZLE_TICKS, 0.0F, 1.0F);
        float pulse = 0.5F + 0.5F * Mth.sin((player.tickCount + partialTicks) * 0.9F);

        List<ShieldEffects.Voxel> voxels = new ArrayList<>(1);
        float half = VoxelRenderer.snapSize((MUZZLE_VOXEL_SIZE + (MUZZLE_FLASH_SIZE - MUZZLE_VOXEL_SIZE) * flash) * 0.5F);
        voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(hand), half, VoxelRenderer.toWhite(color, 0.3F + 0.2F * pulse + 0.5F * flash)));
        ShieldEffects.submit(context, camera, voxels, 0.7F + 0.3F * flash);
    }

    private static void tracer(LevelRenderContext context, Vec3 camera, Bolt bolt, float time) {
        float length = (float) bolt.start.distanceTo(bolt.end);
        if (length < 1.0E-3) return;

        float head = Math.min(time * BOLT_SPEED, length);
        float tail = Math.max(head - TRACER_LENGTH, 0.0F);
        float fade = time > bolt.travel ? 1.0F - Mth.clamp((time - bolt.travel) / 2.0F, 0.0F, 1.0F) : 1.0F;
        if (fade <= 0.0F || head <= tail) return;

        Vec3 direction = bolt.end.subtract(bolt.start).scale(1.0 / length);
        int count = Math.max(Mth.ceil((head - tail) / TRACER_SPACING), 1);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) {
            float t = (float) i / count;
            float distance = tail + (head - tail) * t;
            float half = VoxelRenderer.snapSize(TRACER_VOXEL_SIZE * (0.4F + 0.6F * t) * 0.5F);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(bolt.start.add(direction.scale(distance))), half, VoxelRenderer.toWhite(bolt.color, 0.2F + 0.7F * t)));
        }
        ShieldEffects.submit(context, camera, voxels, fade);
    }

    private static void impact(LevelRenderContext context, Vec3 camera, Bolt bolt, float time) {
        if (!bolt.hit || time < 0.0F || time > IMPACT_TICKS) return;

        float life = 1.0F - time / IMPACT_TICKS;
        RandomSource random = RandomSource.create(bolt.seed);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(VisualQuality.count(IMPACT_VOXELS));
        for (int i = 0; i < VisualQuality.count(IMPACT_VOXELS); i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            float distance = IMPACT_SPEED * time * (0.5F + random.nextFloat() * 0.5F);
            float half = VoxelRenderer.snapSize(IMPACT_VOXEL_SIZE * life * 0.5F);
            voxels.add(new ShieldEffects.Voxel(VoxelRenderer.snap(bolt.end.add(direction.scale(distance))), half, VoxelRenderer.toWhite(bolt.color, 0.6F * life)));
        }
        ShieldEffects.submit(context, camera, voxels, life);
    }

    private BarrageEffects() {}
}
