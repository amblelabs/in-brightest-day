package dev.amble.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.amble.client.effects.ArmedPose;
import dev.amble.client.effects.RingPositions;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;

public class SlottedRingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    public SlottedRingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        SlottedRingRenderer.submit(this.getParentModel(), state, state.mainArm, poseStack, submitNodeCollector, lightCoords, state.outlineColor);
        ArmedPose.submitRingGlow(this.getParentModel(), state, state.mainArm, poseStack, submitNodeCollector);
        capture(state, poseStack);
    }

    private void capture(AvatarRenderState state, PoseStack poseStack) {
        poseStack.pushPose();
        this.getParentModel().translateToHand(state, state.mainArm, poseStack);
        poseStack.rotateDegrees(Axis.XP, -90.0F);
        poseStack.rotateDegrees(Axis.YP, 180.0F);
        poseStack.translate((state.mainArm == HumanoidArm.LEFT ? -1.0F : 1.0F) / 16.0F, 0.5 / 16.0F, -12.5F / 16.0F);
        RingPositions.capture(state, poseStack);
        poseStack.popPose();
    }
}
