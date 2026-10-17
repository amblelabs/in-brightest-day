package dev.amble.mixin.client;

import dev.amble.core.space.SpaceTravel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class SpaceWeatherMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void brightestday$thinAir(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState, CallbackInfo ci) {
        float thin = SpaceTravel.atmosphereFade(level, cameraPos.y);
        if (thin <= 0.0F) return;
        if (thin >= 1.0F) {
            renderState.rainColumns.clear();
            renderState.snowColumns.clear();
            renderState.intensity = 0.0F;
            return;
        }
        renderState.intensity *= 1.0F - thin;
    }
}
