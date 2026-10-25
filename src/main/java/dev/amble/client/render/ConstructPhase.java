package dev.amble.client.render;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.amble.mixin.client.PreparedFrameAccessor;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;

public final class ConstructPhase {
    public interface Holder {
        SimpleFeatureRenderPhase brightestday$constructs();
    }

    public static void execute(FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass) {
        PreparedFrameAccessor accessor = (PreparedFrameAccessor) frame;
        if (accessor.brightestday$storage() == null || accessor.brightestday$context() == null) return;
        for (SubmitNodeCollection collection : accessor.brightestday$storage().getSubmitsPerOrder().values()) {
            accessor.brightestday$executePhase(((Holder) collection).brightestday$constructs(), accessor.brightestday$context(), pass);
        }
    }

    private ConstructPhase() {}
}
