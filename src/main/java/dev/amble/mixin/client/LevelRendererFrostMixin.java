package dev.amble.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.amble.client.render.ConstructPhase;
import dev.amble.client.render.FrostedGlass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererFrostMixin {
    @Unique
    private @Nullable RenderPass brightestday$resumed;

    @WrapOperation(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucentAfterTerrain(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void brightestday$frostAfterWater(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass, Operation<Void> original,
                                              @Local(argsOnly = true) LocalRef<RenderPass> passRef) {
        if (!FrostedGlass.shouldCapture()) {
            original.call(frame, pass);
            ConstructPhase.execute(frame, pass);
            return;
        }
        pass.close();
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        FrostedGlass.capture(target);
        RenderPass resumed = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "brightestday resumed after terrain",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty());
        RenderSystem.bindDefaultUniforms(resumed);
        this.brightestday$resumed = resumed;
        passRef.set(resumed);
        original.call(frame, resumed);
        ConstructPhase.execute(frame, resumed);
    }

    @Inject(method = "executeClassicTransparency", at = @At("RETURN"))
    private void brightestday$closeResumed(CallbackInfo ci) {
        if (this.brightestday$resumed == null) return;
        this.brightestday$resumed.close();
        this.brightestday$resumed = null;
    }

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOit(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V", shift = At.Shift.AFTER))
    private void brightestday$constructsAfterOit(CallbackInfo ci, @Local(argsOnly = true) FeatureRenderDispatcher.PreparedFrame frame) {
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (FrostedGlass.shouldCapture()) FrostedGlass.capture(target);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "brightestday constructs after oit",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            ConstructPhase.execute(frame, pass);
        }
    }
}
