package dev.amble.mixin.client;

import dev.amble.client.render.ShaderOverlay;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void brightestday$enterHand(CallbackInfo ci) {
        ShaderOverlay.inHand(true);
    }

    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void brightestday$exitHand(CallbackInfo ci) {
        ShaderOverlay.inHand(false);
    }
}
