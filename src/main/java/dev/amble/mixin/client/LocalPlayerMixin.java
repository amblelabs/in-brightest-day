package dev.amble.mixin.client;

import dev.amble.client.flight.GroundLaunchClient;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.amble.core.networking.payloads.c2s.SetFlightC2SPayload;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Unique private static final int DOUBLE_TAP_WINDOW = 7;

    @Unique private int brightestday$jumpTriggerTime;
    @Unique private boolean brightestday$wasJumping;
    @Unique private boolean brightestday$justToggled;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void brightestday$trackJump(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (this.brightestday$jumpTriggerTime > 0) this.brightestday$jumpTriggerTime--;
        this.brightestday$wasJumping = self.input.keyPresses.jump();
        this.brightestday$justToggled = false;
    }

    @Inject(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z", opcode = Opcodes.GETFIELD))
    private void brightestday$doubleTapToggle(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!this.brightestday$overridesCreativeFlight(self)) return;
        if (this.brightestday$wasJumping || !self.input.keyPresses.jump()) return;

        if (this.brightestday$jumpTriggerTime == 0) {
            this.brightestday$jumpTriggerTime = DOUBLE_TAP_WINDOW;
        } else if (!self.isSwimming() && !self.isPassenger()) {
            boolean enable = !FlightRingPower.canFly(self);
            this.brightestday$setFlight(self, enable);
            if (enable && self.onGround() && FlightRingPower.canFly(self)) self.jumpFromGround();

            this.brightestday$jumpTriggerTime = 0;
            this.brightestday$justToggled = true;
        }
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z", opcode = Opcodes.GETFIELD))
    private boolean brightestday$suppressCreativeFlight(boolean mayfly) {
        return mayfly && !this.brightestday$overridesCreativeFlight((LocalPlayer) (Object) this);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void brightestday$landing(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (!this.brightestday$justToggled && !GroundLaunchClient.launching() && self.onGround() && FlightRingPower.canFly(self)) {
            this.brightestday$setFlight(self, false);
        }
    }

    @Inject(method = "isCrouching", at = @At("HEAD"), cancellable = true)
    private void brightestday$noCrouchWhileFlying(CallbackInfoReturnable<Boolean> cir) {
        if (FlightRingPower.isFlying((LocalPlayer) (Object) this)) cir.setReturnValue(false);
    }

    @Unique
    private boolean brightestday$overridesCreativeFlight(LocalPlayer self) {
        return !self.isSpectator() && FlightRingPower.hasFlight(self);
    }

    @Unique
    private void brightestday$setFlight(LocalPlayer self, boolean enabled) {
        FlightRingPower.setEnabled(self, enabled);
        ClientPlayNetworking.send(new SetFlightC2SPayload(enabled));
    }
}
