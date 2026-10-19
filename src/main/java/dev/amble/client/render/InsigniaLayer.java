package dev.amble.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.effects.InsigniaEffects;
import dev.amble.core.visuals.InsigniaAnchor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;

public class InsigniaLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public static final float SIZE = 0.46F;
    private static final float OPACITY = 0.35F;
    private static final float LAYER_GAP = 0.025F;
    private static final float ECHO_SHRINK = 0.7F;
    private static final float ECHO_FADE = 0.8F;

    public InsigniaLayer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
        InsigniaEffects.Projection projection = state.getData(InsigniaEffects.PROJECTION);
        if (projection == null || state.isInvisible) return;

        poseStack.pushPose();
        InsigniaAnchor anchor = projection.anchor();
        InsigniaAnchor.Region region = anchor.region();
        this.part(region).translateAndRotate(poseStack);
        float fade = projection.fade();
        float half = SIZE * anchor.scale() * (0.7F + 0.3F * fade) * 0.5F;
        float x = region.localX(anchor.x()) / 16.0F;
        float y = region.localY(anchor.y()) / 16.0F;
        float front = region.front / 16.0F;
        RenderType glow = RenderTypes.energySwirl(projection.texture(), 0.0F, 0.0F);
        RenderType normal = RenderTypes.entityTranslucentEmissive(projection.texture());
        int echoes = anchor.echoes();
        for (int step = echoes; step >= 0; step--) {
            float along = 1.0F - step / (float) (echoes + 1);
            float scale = (float) Math.pow(ECHO_SHRINK, step);
            float strength = OPACITY * fade * (float) Math.pow(ECHO_FADE, step);
            float z = front - anchor.distance() * along;
            stack(poseStack, submitNodeCollector, glow, normal, x, y, half * scale, z, projection.color(), strength);
        }
        poseStack.popPose();
    }

    private ModelPart part(InsigniaAnchor.Region region) {
        PlayerModel model = this.getParentModel();
        return switch (region) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    private static void stack(PoseStack poseStack, SubmitNodeCollector collector, RenderType glow, RenderType normal, float x, float y, float half, float z, int color, float strength) {
        int translucent = ARGB.color(Math.round(255 * strength), color);
        int additive = ARGB.scaleRGB(color, strength);
        quad(poseStack, collector, glow, x, y, half, z + LAYER_GAP, additive);
        quad(poseStack, collector, normal, x, y, half, z, translucent);
        quad(poseStack, collector, glow, x, y, half, z - LAYER_GAP, additive);
    }

    private static void quad(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, float x, float y, float half, float z, int color) {
        float top = y - half;
        float bottom = y + half;
        float left = x - half;
        float right = x + half;
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            vertex(pose, buffer, left, top, z, 0.0F, 0.0F, color);
            vertex(pose, buffer, left, bottom, z, 0.0F, 1.0F, color);
            vertex(pose, buffer, right, bottom, z, 1.0F, 1.0F, color);
            vertex(pose, buffer, right, top, z, 1.0F, 0.0F, color);
            vertex(pose, buffer, right, top, z, 1.0F, 0.0F, color);
            vertex(pose, buffer, right, bottom, z, 1.0F, 1.0F, color);
            vertex(pose, buffer, left, bottom, z, 0.0F, 1.0F, color);
            vertex(pose, buffer, left, top, z, 0.0F, 0.0F, color);
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }
}
