package dev.amble.core.ringpowers.constructs;

import dev.amble.core.gravity.GravityBallManager;
import dev.amble.core.rings.ConstructRings;
import dev.amble.core.attacks.area.BarrageManager;
import dev.amble.core.attacks.area.SlamManager;
import dev.amble.core.attacks.projectile.DiscManager;
import dev.amble.core.attacks.utility.GrappleManager;
import dev.amble.core.attacks.utility.LumberjackManager;
import dev.amble.core.attacks.utility.OreProbeManager;
import dev.amble.core.attacks.weapon.TurretManager;
import dev.amble.core.beams.BeamManager;
import dev.amble.core.drill.DrillManager;
import dev.amble.core.drill.PlacedDrillManager;
import dev.amble.core.glide.GlideManager;
import dev.amble.core.beams.HealBeamManager;
import dev.amble.core.light.LightOrbManager;
import dev.amble.core.mounts.ConstructMounts;
import dev.amble.core.magnet.MagnetManager;
import dev.amble.core.sphere.ContainmentSphere;
import dev.amble.core.visuals.Insignia;
import dev.amble.core.sculpt.SculptManager;
import dev.amble.core.shields.ShieldManager;
import dev.amble.core.shields.BulwarkManager;
import dev.amble.core.walls.WallManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.stream.LongStream;

public final class ConstructDismissal {

    public static void init() {
        ServerPlayerEvents.LEAVE.register(ConstructDismissal::dismissAll);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> List.copyOf(server.getPlayerList().getPlayers()).forEach(ConstructDismissal::dismissAll));
    }

    public static void dismissLatest(ServerPlayer player) {
        long shieldCreated = ShieldManager.latestCreatedAt(player.getUUID());
        long wallCreated = WallManager.latestCreatedAt(player.getUUID());
        long sculptCreated = SculptManager.latestCreatedAt(player.getUUID());
        long orbCreated = LightOrbManager.latestCreatedAt(player.getUUID());
        long turretCreated = TurretManager.latestCreatedAt(player.getUUID());
        long glideCreated = GlideManager.latestCreatedAt(player.getUUID());
        long drillCreated = PlacedDrillManager.latestCreatedAt(player.getUUID());
        long lumberjackCreated = LumberjackManager.latestCreatedAt(player.getUUID());
        long probeCreated = OreProbeManager.latestCreatedAt(player.getUUID());
        long toolCreated = ConstructTools.latestCreatedAt(player);
        long mountCreated = ConstructMounts.latestCreatedAt(player.getUUID());
        long sphereCreated = ContainmentSphere.latestCreatedAt(player);
        long magnetCreated = MagnetManager.latestCreatedAt(player);
        long insigniaCreated = Insignia.latestCreatedAt(player.getUUID());
        long ringCreated = ConstructRings.latestCreatedAt(player);
        long gravityCreated = GravityBallManager.latestCreatedAt(player.getUUID());
        long newest = LongStream.of(shieldCreated, wallCreated, sculptCreated, orbCreated, turretCreated, glideCreated, drillCreated, lumberjackCreated, probeCreated, toolCreated, mountCreated, sphereCreated, magnetCreated, insigniaCreated, ringCreated, gravityCreated).max().getAsLong();
        if (newest == Long.MIN_VALUE) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.no_constructs_to_dismiss"));
            return;
        }

        if (newest == gravityCreated) {
            GravityBallManager.dismissAll(player);
        } else if (newest == ringCreated) {
            ConstructRings.dismissLatest(player);
        } else if (newest == insigniaCreated) {
            Insignia.hide(player);
        } else if (newest == magnetCreated) {
            MagnetManager.stop(player);
        } else if (newest == sphereCreated) {
            ContainmentSphere.stop(player);
        } else if (newest == mountCreated) {
            ConstructMounts.dismissLatest(player.getUUID());
        } else if (newest == toolCreated) {
            ConstructTools.dissolveAll(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.4F);
        } else if (newest == lumberjackCreated) {
            LumberjackManager.dismissLatest(player.getUUID());
        } else if (newest == probeCreated) {
            OreProbeManager.dismissLatest(player.getUUID());
        } else if (newest == drillCreated) {
            PlacedDrillManager.dismissLatest(player.getUUID());
        } else if (newest == glideCreated) {
            GlideManager.dismissLatest(player.getUUID());
        } else if (newest == turretCreated) {
            TurretManager.dismissLatest(player.getUUID());
        } else if (newest == orbCreated) {
            LightOrbManager.dismissLatest(player.getUUID());
        } else if (newest == sculptCreated) {
            SculptManager.dismissLatest(player.getUUID());
        } else if (newest == wallCreated) {
            WallManager.dismissLatest(player.getUUID());
        } else {
            ShieldManager.dismissLatest(player.getUUID());
        }
    }

    public static void dismissAll(ServerPlayer player) {
        BeamManager.stop(player);
        HealBeamManager.stop(player);
        SculptManager.stop(player, false);
        BarrageManager.stop(player);
        GravityBallManager.dismissAll(player);
        BulwarkManager.stop(player);
        DrillManager.stop(player);
        GrappleManager.dismiss(player);
        SlamManager.stop(player);
        DiscManager.dismiss(player);
        ShieldManager.dismissAll(player.getUUID());
        WallManager.dismissAll(player.getUUID());
        SculptManager.dismissAll(player.getUUID());
        LightOrbManager.dismissAll(player.getUUID());
        TurretManager.dismissAll(player.getUUID());
        GlideManager.dismissAll(player.getUUID());
        PlacedDrillManager.dismissAll(player.getUUID());
        LumberjackManager.dismissAll(player.getUUID());
        OreProbeManager.dismissAll(player.getUUID());
        ConstructTools.dissolveAll(player);
        ConstructMounts.dismissAll(player.getUUID());
        MagnetManager.stop(player);
        ContainmentSphere.stop(player);
        Insignia.hide(player);
    }

    private ConstructDismissal() {}
}
