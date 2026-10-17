package dev.amble.mixin.client;

import dev.amble.core.space.Cosmos;
import dev.amble.core.space.SpaceTravel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class SpaceSkyRendererMixin {

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void brightestday$hideSun(RenderPass pass, float alpha, PoseStack poses, CallbackInfo ci) {
        if (brightestday$inSpace()) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void brightestday$hideMoon(RenderPass pass, MoonPhase phase, float alpha, PoseStack poses, CallbackInfo ci) {
        if (brightestday$inSpace()) ci.cancel();
    }

    @Unique
    private static boolean brightestday$inSpace() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        if (SpaceTravel.isSpace(mc.level)) return true;
        return mc.player != null && Cosmos.planetAt(mc.level.dimension(), mc.player.getX()) != null;
    }
}
