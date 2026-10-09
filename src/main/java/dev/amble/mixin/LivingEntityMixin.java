package dev.amble.mixin;

import dev.amble.core.attacks.projectile.CrystalManager;
import dev.amble.core.loyalty.RingSuspension;
import dev.amble.core.progression.EmotionSources;
import dev.amble.core.ringpowers.CorpsCombat;
import dev.amble.core.ringpowers.RedRage;
import dev.amble.core.ringpowers.RingBenefits;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "travelInAir", at = @At("TAIL"))
    private void brightestday$ringSuspension(Vec3 input, CallbackInfo ci) {
        if ((Object) this instanceof Player player) RingSuspension.hold(player);
    }

    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void brightestday$ringWaterBreathing(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player player && RingBenefits.isActive(player)) cir.setReturnValue(true);
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float brightestday$corpsDamage(float damage, ServerLevel level, DamageSource source) {
        return CrystalManager.protect((LivingEntity) (Object) this, RedRage.scale((LivingEntity) (Object) this, source, CorpsCombat.scaleDamage(source, damage)));
    }

    @Inject(method = "hurtServer", at = @At("RETURN"))
    private void brightestday$corpsOnHit(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        CorpsCombat.afterHit((LivingEntity) (Object) this, source, damage);
        RedRage.onHurt((LivingEntity) (Object) this, source, damage);
        EmotionSources.onHurt((LivingEntity) (Object) this, source, damage);
    }
}
