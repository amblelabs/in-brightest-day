package dev.amble.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public interface PreparedFrameAccessor {
    @Accessor("context")
    FeatureFrameContext brightestday$context();

    @Accessor("submitNodeStorage")
    SubmitNodeStorage brightestday$storage();

    @Invoker("executePhase")
    void brightestday$executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext context, RenderPass pass);
}
