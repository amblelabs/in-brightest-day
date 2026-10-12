package dev.amble.mixin;

import dev.amble.core.space.SpaceTravel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class SpaceGravityMixin {
    @Inject(method = "getEffectiveGravity", at = @At("RETURN"), cancellable = true)
    private void brightestday$spaceGravity(CallbackInfoReturnable<Double> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        double scale = SpaceTravel.gravityScale(self.level());
        if (scale != 1.0) cir.setReturnValue(cir.getReturnValueD() * scale);
    }
}
