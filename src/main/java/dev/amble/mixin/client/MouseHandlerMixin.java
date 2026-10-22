package dev.amble.mixin.client;

import dev.amble.client.effects.attacks.weapon.HandClient;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.amble.client.effects.ConstructClient;
import dev.amble.client.wheel.PowerWheel;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @WrapWithCondition(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private boolean brightestday$resizeAreaShield(Inventory inventory, int slot, @Local int wheel) {
        return !ConstructClient.onScroll(wheel);
    }

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void brightestday$steerPowerWheel(double mousea, CallbackInfo ci) {
        if (PowerWheel.onMouse(this.accumulatedDX, this.accumulatedDY)) {
            ci.cancel();
            return;
        }
        double scale = HandClient.onMouse(this.accumulatedDX, this.accumulatedDY);
        this.accumulatedDX *= scale;
        this.accumulatedDY *= scale;
    }
}
