package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.flight.FlightRenderTypes;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

    private static final Map<Integer, ClientShield> SHIELDS = new HashMap<>();

    private static final class ClientShield {
        final int entityId;
        final Vec3 center;
        final float radius;
        final int color;
        final int duration;
        final @Nullable ClientLevel level;
        int age;
        int fade = -1;

        ClientShield(ShieldSpawnS2CPayload payload) {
            this.entityId = payload.entityId();
            this.center = payload.center();
            this.radius = payload.radius();
            this.color = ARGB.opaque(payload.color());
            this.duration = payload.duration();
            this.age = payload.age();
            this.level = Minecraft.getInstance().level;
        }
    }

    public record Voxel(Vec3 center, float half, int color) {}

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
            if (shield.fade < 0) out.accept(new ShieldSpawnS2CPayload(id, shield.entityId, shield.center, shield.radius, shield.color, shield.duration, shield.age));
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
            if (shield.fade < 0 && shield.age > shield.duration + 20) shield.fade = 0;
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

            submit(context, camera, sphere(center, radius * scale, radius, time, shield.color), 1.0F);
        }
    }

    public static void submitGlow(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float glowScale, float glowAlpha) {
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, glowScale, glowAlpha, false));
    }

    public static void submit(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float glowScale, float glowAlpha, float glassAlpha) {
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, glowScale, glowAlpha, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> draw(pose, buffer, camera, voxels, 1.0F, glassAlpha, true));
    }

    public static void submit(LevelRenderContext context, Vec3 camera, List<Voxel> voxels, float alpha) {
        if (VisualQuality.glow()) context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, camera, voxels, GLOW_SCALE, GLOW_ALPHA * alpha, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> draw(pose, buffer, camera, voxels, 1.0F, GLASS_ALPHA * alpha, true));
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
        int alphaByte = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255);
        for (Voxel voxel : voxels) {
            Vec3 center = voxel.center().subtract(camera);
            VoxelRenderer.cube(pose, buffer, center, voxel.half() * scale, VoxelRenderer.nearFade(center, ARGB.color(alphaByte, voxel.color())), shaded);
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
