package dev.amble.mixin.client;

import dev.amble.client.render.ShaderOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisPipelineMixin {

    @Inject(method = "finalizeLevelRendering", at = @At("TAIL"), remap = false, require = 0)
    private void brightestday$constructOverlay(CallbackInfo ci) {
        ShaderOverlay.draw();
    }
}
