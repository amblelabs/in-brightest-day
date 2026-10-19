package dev.amble.core.team;

import dev.amble.core.mannequin.Mannequins;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RingTargets {
    public static boolean isHittable(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || Mannequins.isHologram(entity)) return false;
        if (entity instanceof EnderDragonPart part) return part.parentMob.isAlive();
        if (entity instanceof EnderDragon) return false;
        return entity instanceof LivingEntity || entity instanceof EndCrystal;
    }

    public static boolean isTarget(Entity attacker, Entity entity) {
        Entity root = root(entity);
        return entity != attacker && root != attacker && isHittable(entity) && !allied(attacker, root);
    }

    public static boolean allied(Entity attacker, Entity target) {
        if (!(attacker instanceof Player first) || !(target instanceof Player second)) return attacker.isAlliedTo(target);
        if (LanternTeams.areTeammates(first, second)) return true;
        Team team = first.getTeam();
        return team != null && team == second.getTeam() && !team.isAllowFriendlyFire();
    }

    public static Entity root(Entity entity) {
        return entity instanceof EnderDragonPart part ? part.parentMob : entity;
    }

    public static int rootId(Entity entity) {
        return root(entity).getId();
    }

    public static Entity nearestPart(Entity entity, Vec3 from) {
        if (!(entity instanceof EnderDragon dragon)) return entity;
        Entity best = entity;
        double bestDistance = Double.MAX_VALUE;
        for (EnderDragonPart part : dragon.getSubEntities()) {
            double distance = part.getBoundingBox().getCenter().distanceToSqr(from);
            if (distance >= bestDistance) continue;
            best = part;
            bestDistance = distance;
        }
        return best;
    }

    public static boolean hurt(ServerLevel level, Entity target, DamageSource source, float amount) {
        return isHittable(target) && target.hurtServer(level, source, amount);
    }

    public static boolean strike(ServerLevel level, Entity target, DamageSource source, float amount) {
        if (!isHittable(target)) return false;
        if (root(target) instanceof LivingEntity living) living.setInvulnerableTime(0);
        return target.hurtServer(level, source, amount);
    }

    public static boolean excluded(Collection<? extends Entity> exclude, Entity entity) {
        if (exclude.isEmpty()) return false;
        Entity root = root(entity);
        for (Entity other : exclude) {
            if (root(other) == root) return true;
        }
        return false;
    }

    public static <T extends Entity> List<T> distinct(List<T> entities) {
        Set<Entity> seen = new HashSet<>();
        List<T> result = new ArrayList<>(entities.size());
        for (T entity : entities) {
            if (seen.add(root(entity))) result.add(entity);
        }
        return result;
    }

    private RingTargets() {}
}
