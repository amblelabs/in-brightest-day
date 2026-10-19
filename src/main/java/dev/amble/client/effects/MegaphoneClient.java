package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.MegaphoneS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class MegaphoneClient {
    private static final float FADE_SPEED = 0.25F;
    private static final float VOXEL_HALF = 0.025F;
    private static final double FORWARD = 0.75;
    private static final double DROP = 0.2;
    private static final double SIDE = 0.12;
    private static final double LENGTH = 0.55;
    private static final double MOUTH_RADIUS = 0.07;
    private static final double BELL_RADIUS = 0.26;
    private static final int RINGS = 11;
    private static final double SPACING = 0.055;
    private static final double BOB = 0.02;
    private static final float ALPHA = 0.9F;

    private static final Set<Integer> ACTIVE = new HashSet<>();
    private static final Map<Player, float[]> FADES = new WeakHashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MegaphoneS2CPayload.TYPE, (payload, context) -> {
            if (payload.active()) ACTIVE.add(payload.playerId());
            else ACTIVE.remove(payload.playerId());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ACTIVE.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && client.level.getGameTime() % 20 == 0) ACTIVE.removeIf(id -> client.level.getEntity(id) == null);
        });
        ClientTickEvents.END_CLIENT_TICK.register(MegaphoneClient::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(MegaphoneClient::render);
    }

    public static boolean isActive(Player player) {
        return ACTIVE.contains(player.getId());
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused()) return;
        for (AbstractClientPlayer player : client.level.players()) {
            boolean active = isActive(player);
            float[] fade = FADES.get(player);
            if (fade == null) {
                if (!active) continue;
                fade = new float[2];
                FADES.put(player, fade);
            }
            fade[1] = fade[0];
            fade[0] = Mth.approach(fade[0], active ? 1.0F : 0.0F, FADE_SPEED);
            if (!active && fade[1] <= 0.0F) FADES.remove(player);
        }
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || FADES.isEmpty()) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = client.level.getGameTime() + partialTicks;
        List<ShieldEffects.Voxel> voxels = new ArrayList<>();
        for (Map.Entry<Player, float[]> entry : FADES.entrySet()) {
            Player player = entry.getKey();
            float amount = Mth.lerp(partialTicks, entry.getValue()[1], entry.getValue()[0]);
            if (amount <= 0.01F || player.isRemoved()) continue;
            build(player, partialTicks, time, amount, voxels);
        }
        if (!voxels.isEmpty()) ShieldEffects.submit(context, camera, voxels, ALPHA);
    }

    private static void build(Player player, float partialTicks, float time, float amount, List<ShieldEffects.Voxel> voxels) {
        Vec3 forward = player.getViewVector(partialTicks);
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0 : -1.0;
        Vec3 mouth = player.getEyePosition(partialTicks)
                .add(forward.scale(FORWARD * amount))
                .add(up.scale(-DROP + Math.sin(time * 0.15) * BOB))
                .add(right.scale(SIDE * side));

        int color = ARGB.opaque(CorpsColors.of(player));
        float half = VOXEL_HALF * amount;
        for (int ring = 0; ring < VisualQuality.count(RINGS); ring++) {
            double t = ring / (double) (VisualQuality.count(RINGS) - 1);
            double radius = Mth.lerp(t * t, MOUTH_RADIUS, BELL_RADIUS) * amount;
            Vec3 center = mouth.add(forward.scale(t * LENGTH * amount));
            int points = Math.max(6, Mth.ceil(Mth.TWO_PI * radius / SPACING));
            float shimmer = 0.15F + 0.35F * (0.5F + 0.5F * Mth.sin(time * 0.3F - ring * 0.6F));
            int tint = VoxelRenderer.toWhite(color, ring == VisualQuality.count(RINGS) - 1 ? 0.55F : shimmer);
            for (int i = 0; i < points; i++) {
                double angle = i * Mth.TWO_PI / points;
                Vec3 offset = right.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
                voxels.add(new ShieldEffects.Voxel(center.add(offset), half, tint));
            }
        }
    }

    private MegaphoneClient() {}
}
