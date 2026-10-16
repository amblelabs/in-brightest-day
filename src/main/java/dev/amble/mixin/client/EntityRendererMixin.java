package dev.amble.mixin.client;

import dev.amble.client.effects.CarriedEntities;
import dev.amble.client.render.ConstructMountRendering;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brightestday$markConstructMount(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        ConstructMountRendering.mark(entity, state);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brightestday$placeCarried(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        CarriedEntities.place(entity, state, partialTicks);
    }
}
