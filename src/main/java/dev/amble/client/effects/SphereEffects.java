package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.SphereS2CPayload;
import dev.amble.core.sphere.ContainmentSphere;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class SphereEffects {
    private static final int GROW_TICKS = 6;
    private static final int FADE_TICKS = 8;
    private static final float ALPHA = 0.8F;
    private static final float BEAM_ALPHA = 0.6F;

    private static final class ClientSphere {
        float radius;
        int color;
        int age;
        int fade = -1;

        ClientSphere(float radius, int color) {
            this.radius = radius;
            this.color = color;
        }
    }

    private static final Map<Integer, ClientSphere> SPHERES = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SphereS2CPayload.TYPE, (payload, context) -> {
            ClientSphere sphere = SPHERES.get(payload.playerId());
            if (payload.radius() <= 0.0F) {
                if (sphere != null && sphere.fade < 0) sphere.fade = 0;
            } else if (sphere == null || sphere.fade >= 0) {
                SPHERES.put(payload.playerId(), new ClientSphere(payload.radius(), ARGB.opaque(payload.color())));
            } else {
                sphere.radius = payload.radius();
                sphere.color = ARGB.opaque(payload.color());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SPHERES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(SphereEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(SphereEffects::render);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<ClientSphere> iterator = SPHERES.values().iterator();
        while (iterator.hasNext()) {
            ClientSphere sphere = iterator.next();
            sphere.age++;
            if (sphere.fade >= 0 && ++sphere.fade > FADE_TICKS) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (SPHERES.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Map.Entry<Integer, ClientSphere> entry : SPHERES.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            ClientSphere sphere = entry.getValue();
            float time = sphere.age + partialTicks;
            float scale = Mth.clamp(time / GROW_TICKS, 0.0F, 1.0F);
            if (sphere.fade >= 0) scale *= 1.0F - Mth.clamp((sphere.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (scale <= 0.01F) continue;

            Vec3 center = player.getEyePosition(partialTicks).add(player.getViewVector(partialTicks).scale(sphere.radius + ContainmentSphere.GAP));
            ShieldEffects.submitShell(context, camera, center, sphere.radius * scale, sphere.radius, time, sphere.color, ALPHA, ShieldEffects.opacityOf(player));

            List<ShieldEffects.Voxel> beam = new ArrayList<>();
            Vec3 hand = BlastEffects.hand(player, partialTicks);
            Vec3 toward = center.subtract(hand);
            double length = toward.length();
            if (length > sphere.radius) TractorEffects.beam(hand, hand.add(toward.scale((length - sphere.radius * scale) / length)), time, sphere.color, beam);
            if (!beam.isEmpty()) ShieldEffects.submit(context, camera, beam, BEAM_ALPHA * scale);
        }
    }

    private SphereEffects() {}
}
