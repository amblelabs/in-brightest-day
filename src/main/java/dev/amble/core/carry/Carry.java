package dev.amble.core.carry;

import dev.amble.core.networking.payloads.s2c.CarriedS2CPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Carry {
    private static final Set<Relative> RELATIVE = EnumSet.of(Relative.X, Relative.Y, Relative.Z, Relative.Y_ROT, Relative.X_ROT);

    public static void toward(Entity entity, Vec3 center) {
        Vec3 feet = center.subtract(0.0, entity.getBbHeight() * 0.5, 0.0);
        Vec3 wanted = feet.subtract(entity.position());
        Vec3 delta = wanted.lengthSqr() < 1.0E-8 ? Vec3.ZERO : Entity.collideBoundingBox(entity, wanted, entity.getBoundingBox(), entity.level(), List.of());
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(player.level(), delta.x, delta.y, delta.z, RELATIVE, 0.0F, 0.0F, false);
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 to = entity.position().add(delta);
            entity.setPos(to.x, to.y, to.z);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.needsSync = true;
            if (entity instanceof Mob mob) mob.getNavigation().stop();
        }
        entity.resetFallDistance();
    }

    public static void broadcast(ServerPlayer owner, int source, float reach, List<CarriedS2CPayload.Entry> carried) {
        CarriedS2CPayload payload = new CarriedS2CPayload(owner.getId(), source, reach, carried);
        ServerPlayNetworking.send(owner, payload);
        for (ServerPlayer watcher : PlayerLookup.tracking(owner)) {
            if (watcher != owner) ServerPlayNetworking.send(watcher, payload);
        }
    }

    private Carry() {}
}
