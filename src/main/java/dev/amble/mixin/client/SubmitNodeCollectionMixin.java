package dev.amble.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.amble.client.render.ConstructPhase;
import dev.amble.client.render.FrostedGlass;
import dev.amble.client.render.ShaderOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin implements ConstructPhase.Holder {
    @Shadow
    @Final
    @Mutable
    private List<FeatureRenderPhase<?>> allPhases;

    @Unique
    private final SimpleFeatureRenderPhase brightestday$constructs = new SimpleFeatureRenderPhase();

    @Override
    public SimpleFeatureRenderPhase brightestday$constructs() {
        return this.brightestday$constructs;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brightestday$addConstructPhase(CallbackInfo ci) {
        List<FeatureRenderPhase<?>> phases = new ArrayList<>(this.allPhases);
        phases.add(this.brightestday$constructs);
        this.allPhases = List.copyOf(phases);
    }

    @Inject(method = "submitCustomGeometry", at = @At("HEAD"), cancellable = true)
    private void brightestday$overlayUnderShaders(PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer, CallbackInfo ci) {
        if (!FrostedGlass.afterWater(type) || !ShaderOverlay.active()) return;
        ShaderOverlay.record(type, poseStack.last().copy(), renderer);
        ci.cancel();
    }

    @WrapOperation(method = "submitCustomGeometry", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;submit(Lnet/minecraft/client/renderer/feature/submit/SubmitNode;)V", ordinal = 1))
    private void brightestday$constructsLast(SimpleFeatureRenderPhase phase, SubmitNode node, Operation<Void> original, @Local(argsOnly = true) RenderType type) {
        // the hand frame runs through renderAllFeatures, which only executes vanilla's phases
        original.call(FrostedGlass.afterWater(type) && !ShaderOverlay.inHand() ? this.brightestday$constructs : phase, node);
    }
}
