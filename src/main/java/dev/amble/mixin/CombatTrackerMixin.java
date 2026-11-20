package dev.amble.mixin;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayEffects;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CombatTracker.class)
public class CombatTrackerMixin {

    @Shadow
    @Final
    private LivingEntity mob;

    @Shadow
    private static boolean shouldEnterCombat(DamageSource source) {
        throw new UnsupportedOperationException("Implemented via mixin");
    }

    @Inject(method = "recordDamage", at = @At("TAIL"))
    public void recordDamage(DamageSource source, float damage, CallbackInfo ci) {
        //noinspection ConstantValue - schizo
        if (this.mob.isAlive() && shouldEnterCombat(source) && this.mob instanceof Player player
                && FlightRingPower.canFly(player))
            player.addEffect(new MobEffectInstance(BrightestDayEffects.STUNNED, BrightestDayConfig.get().flightSpeedStunDuration, 0, false, false));
    }
}
