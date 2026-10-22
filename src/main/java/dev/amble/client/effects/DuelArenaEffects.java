package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.DuelArenaS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class DuelArenaEffects {
    private static final int GROW_TICKS = 16;
    private static final int FADE_TICKS = 16;
    private static final float ALPHA = 0.45F;

    private static final class Arena {
        final Vec3 center;
        final float radius;
        final int color;
        int age;
        int fade = -1;

        Arena(Vec3 center, float radius, int color) {
            this.center = center;
            this.radius = radius;
            this.color = color;
        }
    }

    private static final Map<Integer, Arena> ARENAS = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DuelArenaS2CPayload.TYPE, (payload, context) -> {
            Arena arena = ARENAS.get(payload.id());
            if (payload.radius() <= 0.0F) {
                if (arena != null && arena.fade < 0) arena.fade = 0;
            } else if (arena == null || arena.fade >= 0) {
                ARENAS.put(payload.id(), new Arena(new Vec3(payload.center()), payload.radius(), ARGB.opaque(payload.color())));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ARENAS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(DuelArenaEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(DuelArenaEffects::render);
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        Iterator<Arena> iterator = ARENAS.values().iterator();
        while (iterator.hasNext()) {
            Arena arena = iterator.next();
            arena.age++;
            if (arena.fade >= 0 && ++arena.fade > FADE_TICKS) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        if (ARENAS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Arena arena : ARENAS.values()) {
            float time = arena.age + partialTicks;
            float scale = Mth.clamp(time / GROW_TICKS, 0.0F, 1.0F);
            if (arena.fade >= 0) scale *= 1.0F - Mth.clamp((arena.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (scale <= 0.01F) continue;
            ShieldEffects.submitShell(context, camera, arena.center, arena.radius * scale, arena.radius, time, arena.color, ALPHA * scale);
        }
    }

    private DuelArenaEffects() {}
}
