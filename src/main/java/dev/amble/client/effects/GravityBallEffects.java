package dev.amble.client.effects;

import dev.amble.core.gravity.GravityBallManager;
import dev.amble.core.networking.payloads.s2c.GravityBallS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class GravityBallEffects {
    private static final int GROW_TICKS = 8;
    private static final float PULSE = 0.06F;

    private static final class Ball {
        final Vec3 center;
        final int strength;
        final int color;
        int age;

        Ball(Vec3 center, int strength, int color) {
            this.center = center;
            this.strength = strength;
            this.color = color;
        }
    }

    private static final Map<Integer, Ball> BALLS = new HashMap<>();
    private static final int STALE_GRACE = 40;
    private static @Nullable ClientLevel level;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GravityBallS2CPayload.TYPE, (payload, context) -> {
            if (payload.active()) BALLS.put(payload.id(), new Ball(new Vec3(payload.x(), payload.y(), payload.z()), payload.strength(), ARGB.opaque(payload.color())));
            else BALLS.remove(payload.id());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BALLS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != level) {
                BALLS.clear();
                level = client.level;
            }
            if (!client.isPaused()) BALLS.values().removeIf(ball -> ++ball.age > GravityBallManager.LIFETIME + STALE_GRACE);
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(GravityBallEffects::render);
    }

    private static void render(LevelRenderContext context) {
        if (BALLS.isEmpty()) return;
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        for (Ball ball : BALLS.values()) {
            float time = ball.age + partialTicks;
            float grow = Mth.clamp(time / GROW_TICKS, 0.0F, 1.0F);
            float radius = GravityBallManager.visualRadius(ball.strength) * grow * (1.0F + PULSE * Mth.sin(time * 0.3F));
            ShieldEffects.submit(context, camera, ShieldEffects.sphere(ball.center, radius, radius, time, ball.color), 1.0F);
        }
    }

    public static void preview(LevelRenderContext context, Vec3 camera, Vec3 center, int strength, int color, float time, float alpha) {
        float radius = GravityBallManager.visualRadius(strength);
        ShieldEffects.submit(context, camera, ShieldEffects.sphere(center, radius, radius, time, color), alpha);
    }

    private GravityBallEffects() {}
}
