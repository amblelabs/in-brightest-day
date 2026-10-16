package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.CarriedS2CPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CarriedEntities {
    private record Key(int ownerId, int source) {}

    private record Hold(Key key, float reach, Vec3 offset) {}

    private static final Map<Key, List<Integer>> GROUPS = new HashMap<>();
    private static final Map<Integer, Hold> HELD = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CarriedS2CPayload.TYPE, (payload, context) -> {
            Key key = new Key(payload.ownerId(), payload.source());
            List<Integer> previous = GROUPS.remove(key);
            if (previous != null) previous.forEach(id -> HELD.computeIfPresent(id, (unused, hold) -> hold.key().equals(key) ? null : hold));
            if (payload.carried().isEmpty() || payload.reach() <= 0.0F) return;
            GROUPS.put(key, payload.carried().stream().map(CarriedS2CPayload.Entry::entityId).toList());
            for (CarriedS2CPayload.Entry entry : payload.carried()) {
                HELD.put(entry.entityId(), new Hold(key, payload.reach(), new Vec3(entry.offset())));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GROUPS.clear();
            HELD.clear();
        });
    }

    public static void place(Entity entity, EntityRenderState state, float partialTicks) {
        if (HELD.isEmpty()) return;
        Hold hold = HELD.get(entity.getId());
        if (hold == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || entity == client.getCameraEntity()) return;
        if (!(client.level.getEntity(hold.key().ownerId()) instanceof Player owner)) return;

        Vec3 anchor = owner.getEyePosition(partialTicks).add(owner.getViewVector(partialTicks).scale(hold.reach()));
        Vec3 goal = anchor.add(hold.offset()).subtract(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 from = new Vec3(state.x, state.y, state.z);
        Vec3 wanted = goal.subtract(from);
        if (wanted.lengthSqr() < 1.0E-8) return;
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(from);
        Vec3 allowed = Entity.collideBoundingBox(entity, wanted, box, client.level, List.of());
        state.x = from.x + allowed.x;
        state.y = from.y + allowed.y;
        state.z = from.z + allowed.z;
    }

    private CarriedEntities() {}
}
