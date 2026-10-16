package dev.amble.client.effects;

import dev.amble.core.magnet.MagnetManager;
import dev.amble.core.networking.payloads.s2c.MagnetS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class MagnetEffects {
    private static final int GROW_TICKS = 6;
    private static final int FADE_TICKS = 8;
    private static final float ALPHA = 0.9F;
    private static final float STREAK_ALPHA = 0.45F;
    private static final float TIP_SHARE = 0.3F;
    private static final float TIP_WHITE = 0.6F;
    private static final int MAX_STREAKS = 10;
    private static final double FRONT = 0.2;

    private static final class ClientMagnet {
        int size;
        int color;
        int age;
        int fade = -1;

        ClientMagnet(int size, int color) {
            this.size = size;
            this.color = color;
        }
    }

    private static final Map<Integer, ClientMagnet> MAGNETS = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MagnetS2CPayload.TYPE, (payload, context) -> {
            ClientMagnet magnet = MAGNETS.get(payload.playerId());
            if (payload.size() <= 0) {
                if (magnet != null && magnet.fade < 0) magnet.fade = 0;
            } else if (magnet == null || magnet.fade >= 0) {
                MAGNETS.put(payload.playerId(), new ClientMagnet(payload.size(), ARGB.opaque(payload.color())));
            } else {
                magnet.size = payload.size();
                magnet.color = ARGB.opaque(payload.color());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MAGNETS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(MagnetEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(MagnetEffects::render);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<ClientMagnet> iterator = MAGNETS.values().iterator();
        while (iterator.hasNext()) {
            ClientMagnet magnet = iterator.next();
            magnet.age++;
            if (magnet.fade >= 0 && ++magnet.fade > FADE_TICKS) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (MAGNETS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Map.Entry<Integer, ClientMagnet> entry : MAGNETS.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            ClientMagnet magnet = entry.getValue();
            float time = magnet.age + partialTicks;
            float scale = Mth.clamp(time / GROW_TICKS, 0.0F, 1.0F);
            if (magnet.fade >= 0) scale *= 1.0F - Mth.clamp((magnet.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (scale <= 0.01F) continue;

            Vec3 eye = player.getEyePosition(partialTicks);
            Vec3 forward = player.getViewVector(partialTicks);
            Vec3 tips = MagnetManager.tips(player, magnet.size, partialTicks);
            ShieldEffects.submit(context, camera, horseshoe(tips, forward, magnet.size, scale, time, magnet.color), ALPHA * scale);

            if (magnet.fade >= 0) continue;
            List<ShieldEffects.Voxel> streaks = new ArrayList<>();
            for (Entity pulled : pulled(client, player, eye, forward, magnet.size)) {
                TractorEffects.beam(pulled.getBoundingBox().getCenter(), tips, time, magnet.color, streaks);
            }
            if (!streaks.isEmpty()) ShieldEffects.submit(context, camera, streaks, STREAK_ALPHA);
        }
    }

    private static List<Entity> pulled(Minecraft client, Player owner, Vec3 eye, Vec3 forward, int size) {
        double range = MagnetManager.range(size);
        List<Entity> found = client.level.getEntities(owner, new AABB(eye, eye).inflate(range), entity -> {
            if (entity.isSpectator() || !entity.isAlive() || entity == owner.getVehicle()) return false;
            if (!(entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb)) return false;
            if (entity instanceof Player player && player.isCreative()) return false;
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = offset.length();
            return distance <= range && distance > 1.0E-3 && offset.scale(1.0 / distance).dot(forward) >= FRONT;
        });
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)));
        return found.size() > MAX_STREAKS ? found.subList(0, MAX_STREAKS) : found;
    }

    private static List<ShieldEffects.Voxel> horseshoe(Vec3 tips, Vec3 forward, int size, float scale, float time, int color) {
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1.0, 0.0, 0.0);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();

        double radius = (0.25 + 0.08 * size) * scale;
        double leg = (0.5 + 0.12 * size) * scale;
        float half = (float) ((0.05 + 0.02 * size) * scale);
        double step = half * 1.5;
        Vec3 bend = tips.subtract(forward.scale(leg));
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.3F);
        int tip = VoxelRenderer.toWhite(color, TIP_WHITE * pulse);

        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        int arc = Math.max(4, Mth.ceil(Math.PI * radius / step));
        for (int i = 0; i <= arc; i++) {
            double angle = Math.PI * i / arc;
            Vec3 point = bend.add(right.scale(Math.cos(angle) * radius)).subtract(forward.scale(Math.sin(angle) * radius));
            tube(voxels, point, up, half, color);
        }
        int legs = Math.max(2, Mth.ceil(leg / step));
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 1; i <= legs; i++) {
                double along = (double) i / legs;
                Vec3 point = bend.add(right.scale(side * radius)).add(forward.scale(along * leg));
                tube(voxels, point, up, half, along > 1.0 - TIP_SHARE ? tip : color);
            }
        }
        return voxels;
    }

    private static void tube(List<ShieldEffects.Voxel> voxels, Vec3 point, Vec3 up, float half, int color) {
        voxels.add(new ShieldEffects.Voxel(point.add(up.scale(half * 0.8)), half, color));
        voxels.add(new ShieldEffects.Voxel(point.subtract(up.scale(half * 0.8)), half, color));
    }

    private MagnetEffects() {}
}
