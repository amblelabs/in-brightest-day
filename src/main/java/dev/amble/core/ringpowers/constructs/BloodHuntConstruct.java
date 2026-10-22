package dev.amble.core.ringpowers.constructs;

import dev.amble.core.mannequin.Mannequins;
import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.BloodHunt;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class BloodHuntConstruct extends ConstructRingPower {
    private static final int USE_COST = 80;
    private static final int COOLDOWN_TICKS = 10;
    private static final double RANGE = 48.0;

    public BloodHuntConstruct() {
        super(BrightestDay.id("blood_hunt"), CorpsArsenal.exclusive(LanternCorps.RED));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        AABB area = player.getBoundingBox().expandTowards(reach.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, reach, area,
                entity -> !Mannequins.isHologram(entity) && entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator(), 0.5F);

        if (hit == null || !(hit.getEntity() instanceof LivingEntity prey)) {
            PowerRingItem.refund(player, this.cost(radius));
            player.sendOverlayMessage(Component.translatable("message.brightestday.hunt.no_target").withColor(LanternCorps.RED.color()));
            return;
        }
        BloodHunt.mark(player, prey);
    }
}
