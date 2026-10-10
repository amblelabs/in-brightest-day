package dev.amble.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.amble.client.compat.IrisCompat;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class SuitGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public SuitGlowLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        Identifier glow = state.getData(LanternSuit.GLOW);
        if (glow == null || state.isInvisible) return;

        submitNodeCollector.order(1).submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(glow), lightCoords, OverlayTexture.NO_OVERLAY, 0);

        Identifier flare = state.getData(LanternSuit.FLARE);
        if (flare == null || IrisCompat.shadersActive()) return;
        submitNodeCollector.order(2).submitModel(this.getParentModel(), state, poseStack, EyeFlareRenderTypes.flare(flare), lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }
}
