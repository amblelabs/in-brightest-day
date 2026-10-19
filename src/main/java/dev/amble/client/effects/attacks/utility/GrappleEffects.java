package dev.amble.client.effects.attacks.utility;

import dev.amble.client.effects.VisualQuality;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.client.effects.attacks.projectile.ProjectileBursts;
import dev.amble.core.networking.payloads.s2c.GrappleS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GrappleEffects {
    private static final int STALE_TICKS = 20;
    private static final float LINK_SPACING = 4.0F * VoxelRenderer.PIXEL;
    private static final float LINK_LENGTH = 1.5F * VoxelRenderer.PIXEL;
    private static final float LINK_WIDTH = 1.0F * VoxelRenderer.PIXEL;
    private static final float LINK_VOXEL_SIZE = 1.5F * VoxelRenderer.PIXEL;
    private static final float PULSE_SPEED = 0.35F;
    private static final float RIPPLE = 0.12F;
    private static final float RIPPLE_WAVES = 2.5F;
    private static final float HEAD_TIP_SIZE = 3.0F * VoxelRenderer.PIXEL;
    private static final float HEAD_VOXEL_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final float SHAFT_LENGTH = 0.4F;
    private static final int PRONGS = 3;
    private static final int PRONG_VOXELS = 5;
    private static final float PRONG_REACH = 0.28F;
    private static final float PRONG_CURL = 0.22F;
    private static final float SPIN_SPEED = 0.8F;
    private static final float ALPHA = 0.95F;
    private static final int LATCH_SPARKS = 18;
    private static final float LATCH_SPARK_SPEED = 0.22F;
    private static final float LATCH_SPARK_SIZE = 2.5F * VoxelRenderer.PIXEL;
    private static final int LATCH_SPARK_LIFETIME = 9;
    private static final int GONE_SPARKS = 8;

    private static final Map<Integer, Hook> HOOKS = new HashMap<>();
    private static @Nullable ClientLevel trackedLevel;

    private static final class Hook {
        final int ownerId;
        final int color;
        Vec3 previous;
        Vec3 position;
        int state;
        int targetId;
        int age;
        int idle;

        Hook(int ownerId, int color, Vec3 position, int state, int targetId) {
            this.ownerId = ownerId;
            this.color = color;
            this.previous = position;
            this.position = position;
            this.state = state;
            this.targetId = targetId;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GrappleS2CPayload.TYPE, (payload, context) -> receive(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HOOKS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(GrappleEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(GrappleEffects::render);
    }

    private static void receive(GrappleS2CPayload payload) {
        Hook hook = HOOKS.get(payload.hookId());
        int color = hook != null ? hook.color : ARGB.opaque(payload.color());
        if (payload.state() == GrappleS2CPayload.GONE) {
            HOOKS.remove(payload.hookId());
            ProjectileBursts.spawn(payload.position(), color, GONE_SPARKS, LATCH_SPARK_SPEED * 0.5F, LATCH_SPARK_SIZE, LATCH_SPARK_LIFETIME);
            return;
        }
        boolean latching = payload.state() == GrappleS2CPayload.LATCHED && (hook == null || hook.state != GrappleS2CPayload.LATCHED);
        if (latching) ProjectileBursts.spawn(payload.position(), color, VisualQuality.count(LATCH_SPARKS), LATCH_SPARK_SPEED, LATCH_SPARK_SIZE, LATCH_SPARK_LIFETIME);
        if (hook == null) {
            HOOKS.put(payload.hookId(), new Hook(payload.ownerId(), color, payload.position(), payload.state(), payload.targetId()));
            return;
        }
        hook.previous = hook.position;
        hook.position = payload.position();
        hook.state = payload.state();
        hook.targetId = payload.targetId();
        hook.idle = 0;
    }

    private static void tick(Minecraft client) {
        if (client.level != trackedLevel) {
            HOOKS.clear();
            trackedLevel = client.level;
        }
        if (client.isPaused()) return;
        HOOKS.values().removeIf(hook -> {
            hook.age++;
            return ++hook.idle > STALE_TICKS;
        });
    }

    private static void render(LevelRenderContext context) {
        if (HOOKS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        for (Hook hook : HOOKS.values()) {
            if (!(client.level.getEntity(hook.ownerId) instanceof Player owner)) continue;

            Vec3 head = hook.previous.lerp(hook.position, partialTicks);
            if (hook.targetId != GrappleS2CPayload.NO_TARGET) {
                Entity target = client.level.getEntity(hook.targetId);
                if (target != null) head = target.getBoundingBox().move(target.getPosition(partialTicks).subtract(target.position())).getCenter();
            }
            Vec3 hand = BlastEffects.hand(owner, partialTicks);
            float time = hook.age + partialTicks;
            boolean taut = hook.state == GrappleS2CPayload.LATCHED;

            Vec3 path = head.subtract(hand);
            Vec3 direction = path.lengthSqr() < 1.0E-6 ? owner.getLookAngle() : path.normalize();
            chain(hand, head, time, taut, hook.color, voxels);
            head(head, direction, taut ? 0.0F : time * SPIN_SPEED, hook.color, voxels);
        }
        if (!voxels.isEmpty()) ShieldEffects.submit(context, camera, voxels, ALPHA);
    }

    private static void chain(Vec3 from, Vec3 to, float time, boolean taut, int color, List<ShieldEffects.Voxel> out) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1.0E-3) return;

        Vec3 direction = path.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        float half = VoxelRenderer.snapSize(LINK_VOXEL_SIZE * 0.5F);
        float ripple = taut ? 0.0F : RIPPLE;
        int count = Math.max(Mth.floor(length / LINK_SPACING), 1);
        for (int i = 0; i <= count; i++) {
            float t = (float) i / count;
            float wave = Mth.sin(t * Mth.PI) * ripple * Mth.sin(t * Mth.TWO_PI * RIPPLE_WAVES - time * 0.9F);
            Vec3 center = from.add(path.scale(t)).add(up.scale(wave));
            Vec3 axis = i % 2 == 0 ? side : up;

            float pulse = Mth.sin(t * (float) length * 0.8F - time * PULSE_SPEED * Mth.TWO_PI);
            int tint = VoxelRenderer.toWhite(color, 0.2F + 0.25F * Math.max(pulse, 0.0F));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(axis.scale(LINK_WIDTH))), half, tint));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.subtract(axis.scale(LINK_WIDTH))), half, tint));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(direction.scale(LINK_LENGTH))), half, color));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.subtract(direction.scale(LINK_LENGTH))), half, color));
        }
    }

    private static void head(Vec3 tip, Vec3 direction, float spin, int color, List<ShieldEffects.Voxel> out) {
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        float half = VoxelRenderer.snapSize(HEAD_VOXEL_SIZE * 0.5F);
        int bright = VoxelRenderer.toWhite(color, 0.6F);
        out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(tip), VoxelRenderer.snapSize(HEAD_TIP_SIZE * 0.5F), VoxelRenderer.toWhite(color, 0.8F)));
        for (float s = HEAD_VOXEL_SIZE; s <= SHAFT_LENGTH; s += HEAD_VOXEL_SIZE) {
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(tip.subtract(direction.scale(s))), half, VoxelRenderer.toWhite(color, 0.35F)));
        }

        Vec3 base = tip.subtract(direction.scale(SHAFT_LENGTH));
        for (int prong = 0; prong < PRONGS; prong++) {
            float angle = spin + (float) prong / PRONGS * Mth.TWO_PI;
            Vec3 radial = side.scale(Mth.cos(angle)).add(up.scale(Mth.sin(angle)));
            for (int i = 1; i <= PRONG_VOXELS; i++) {
                float u = (float) i / PRONG_VOXELS;
                Vec3 point = base.add(radial.scale(PRONG_REACH * Mth.sin(u * Mth.HALF_PI))).add(direction.scale(PRONG_CURL * u * u));
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, i == PRONG_VOXELS ? bright : color));
            }
        }
    }

    private GrappleEffects() {}
}
