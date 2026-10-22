package dev.amble.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.amble.client.effects.InsigniaEffects;
import dev.amble.client.effects.ArmedPose;
import dev.amble.client.flight.FlightAnimator;
import dev.amble.client.render.GlowAura;
import dev.amble.client.render.Holograms;
import dev.amble.client.render.LanternSuit;
import dev.amble.client.render.SlottedRingRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void brightestday$extractSlottedRing(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        SlottedRingRenderer.extract(entity, state);
        ArmedPose.extract(entity, state, partialTicks);
        GlowAura.extract(entity, state);
        LanternSuit.extract(entity, state, partialTicks);
        InsigniaEffects.extract(entity, state, partialTicks);
        FlightAnimator.extractDive(entity, state, partialTicks);
        FlightAnimator.extractLanding(entity, state, partialTicks);
        Holograms.extract(entity, state);
    }

    @Inject(method = "extractCapeState", at = @At("TAIL"))
    private void brightestday$tameFlightCape(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        FlightAnimator.adjustCape(entity, state, partialTicks);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void brightestday$centerDivingModel(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
        Vec3 offset = state.getDataOrDefault(FlightAnimator.DIVE_OFFSET, Vec3.ZERO);
        if (offset.lengthSqr() > 1.0E-8) poseStack.translate(offset.x, offset.y, offset.z);
    }
}
