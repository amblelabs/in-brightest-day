package dev.amble.mixin.client;

import dev.amble.client.flight.GroundLaunchClient;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientInput.class)
public abstract class ClientInputMixin {

    @Inject(method = "getMoveVector", at = @At("HEAD"), cancellable = true)
    private void brightestday$holdStillWhileCharging(CallbackInfoReturnable<Vec2> cir) {
        if (GroundLaunchClient.charging()) cir.setReturnValue(Vec2.ZERO);
    }

    @Inject(method = "hasForwardImpulse", at = @At("HEAD"), cancellable = true)
    private void brightestday$noSprintWhileCharging(CallbackInfoReturnable<Boolean> cir) {
        if (GroundLaunchClient.charging()) cir.setReturnValue(false);
    }
}
