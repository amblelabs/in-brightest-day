package dev.amble.client.effects;

import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.core.forge.CentralPowerBattery;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.client.render.BatteryTextures;
import dev.amble.client.render.models.CentralPowerBatteryModel;
import dev.amble.core.networking.payloads.s2c.BatteriesS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import dev.amble.client.compat.IrisCompat;
import dev.amble.BrightestDay;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class BatteryEffects {
    private static final int LIGHT_SAMPLE_OFFSET = 4;
    private static final float EMISSION_WHITENESS = 0.25F;
    private static final float PANEL_DEPTH = 2.9F;
    private static final float PANEL_RADIUS = 1.15F;
    private static final int SIDES = 8;
    private static final float RAY_LENGTH = 6.5F;
    private static final float PANE_DEPTH = 2.38F;
    private static final float PANE_RADIUS = 1.25F;
    private static final float PULSE_SPEED = 0.09F;
    private static final float PANE_WHITE_MIN = 0.15F;
    private static final float PANE_WHITE_MAX = 0.75F;
    private static final float[][] BEAMS = {
            {0.55F, 1.25F, 0.55F, 0.8F},
            {0.9F, 1.7F, 0.32F, 0.45F},
            {1.2F, 2.2F, 0.16F, 0.15F}
    };
    private static final float[][] PANES = {
            {1.0F, 0.4F, 0.0F},
            {0.55F, 0.55F, 0.55F}
    };
    private static final double RENDER_DISTANCE = 160.0;
    private static final Identifier FADE_TEXTURE = BrightestDay.id("dynamic/battery_ray_fade");
    private static final int FADE_SIZE = 64;
    private static final float FADE_POWER = 1.6F;
    private static final float SHADER_DIM = 0.6F;
    private static boolean fadeReady;

    private static List<BatteriesS2CPayload.Entry> batteries = List.of();
    private static @Nullable CentralPowerBatteryModel model;

    public static boolean isBattery(BlockPos pos) {
        for (BatteriesS2CPayload.Entry battery : batteries) {
            BlockPos core = battery.pos();
            int dx = Math.abs(pos.getX() - core.getX());
            int dz = Math.abs(pos.getZ() - core.getZ());
            int reach = 2 + CentralPowerBattery.ARM;
            boolean alongX = battery.arms() == Direction.Axis.X;
            if (Math.abs(pos.getY() - core.getY()) <= 1 && (alongX ? dx <= reach && dz <= 1 : dz <= reach && dx <= 1)) return true;
        }
        return false;
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BatteriesS2CPayload.TYPE, (payload, context) -> batteries = payload.batteries());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> batteries = List.of());
        LevelRenderEvents.COLLECT_SUBMITS.register(BatteryEffects::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (batteries.isEmpty() || client.level == null || client.level.dimension() != Level.OVERWORLD || !BatteryTextures.ready()) return;
        if (model == null) model = new CentralPowerBatteryModel();

        Vec3 camera = context.levelState().cameraRenderState.pos;
        float time = client.level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = context.poseStack();
        for (BatteriesS2CPayload.Entry battery : batteries) {
            Vec3 center = Vec3.atCenterOf(battery.pos());
            if (center.distanceTo(camera) > RENDER_DISTANCE) continue;
            int color = ARGB.opaque(battery.color());
            boolean turned = battery.arms() == Direction.Axis.X;
            BlockPos sample = turned ? battery.pos().east(LIGHT_SAMPLE_OFFSET) : battery.pos().north(LIGHT_SAMPLE_OFFSET);
            int light = LightCoordsUtil.pack(client.level.getBrightness(LightLayer.BLOCK, sample), client.level.getBrightness(LightLayer.SKY, sample));
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.08F);

            boolean green = battery.color() == LanternCorps.GREEN.color();
            Identifier base = green ? BatteryTextures.BASE_SOURCE : BatteryTextures.BASE;
            Identifier emission = green ? BatteryTextures.EMISSION_SOURCE : BatteryTextures.EMISSION;
            int tint = green ? -1 : color;

            poseStack.pushPose();
            poseStack.translate(center.x - camera.x, center.y - camera.y, center.z - camera.z);
            if (turned) poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
            poseStack.pushPose();
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, CentralPowerBatteryModel.CORE_Y / 16.0F, 0.0F);
            context.submitNodeCollector().submitModelPart(model.body(), poseStack, RenderTypes.entityTranslucent(base), light, OverlayTexture.NO_OVERLAY, null, tint);
            if (battery.active()) {
                int glow = green ? ARGB.white(0.75F + 0.25F * pulse) : VoxelRenderer.toWhite(color, EMISSION_WHITENESS * pulse);
                context.submitNodeCollector().submitModelPart(model.body(), poseStack, RenderTypes.eyes(emission), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, null, glow);
            }
            poseStack.popPose();
            if (battery.active()) rays(context, poseStack, color, time);
            poseStack.popPose();
        }
    }

    private interface Emit {
        void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color, float u, float v);
    }

    private static final Emit GLOW = (pose, buffer, x, y, z, color, u, v) -> buffer.addVertex(pose, x, y, z).setColor(color);

    private static final Emit EMISSIVE = (pose, buffer, x, y, z, color, u, v) -> buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);

    private static void rays(LevelRenderContext context, PoseStack poseStack, int color, float time) {
        float pulse = 0.5F + 0.5F * Mth.sin(time * PULSE_SPEED);
        float flicker = 0.9F + 0.1F * Mth.sin(time * 0.37F);
        int paneColor = ARGB.srgbLerp(PANE_WHITE_MIN + (PANE_WHITE_MAX - PANE_WHITE_MIN) * pulse, color, 0xFFFFFFFF);
        boolean shaders = IrisCompat.shadersActive() && ensureFade();
        Emit emit = shaders ? EMISSIVE : GLOW;
        context.submitNodeCollector().submitCustomGeometry(poseStack, shaders ? RenderTypes.eyes(FADE_TEXTURE) : FlightRenderTypes.GLOW, (pose, buffer) -> {
            for (int side = -1; side <= 1; side += 2) {
                for (float[] layer : PANES) {
                    int tint = VoxelRenderer.toWhite(paneColor, layer[2]);
                    float strength = layer[1] * (0.55F + 0.45F * pulse);
                    pane(pose, buffer, emit, side * PANE_DEPTH, PANE_RADIUS * layer[0], shaders ? textured(tint, strength) : additive(tint, strength), shaders);
                }
                Vec3 from = new Vec3(0.0, 0.0, side * PANEL_DEPTH);
                Vec3 to = new Vec3(0.0, 0.0, side * (PANEL_DEPTH + RAY_LENGTH));
                for (float[] layer : BEAMS) {
                    float strength = layer[2] * flicker * (0.75F + 0.25F * pulse);
                    int tint = VoxelRenderer.toWhite(color, layer[3]);
                    frustum(pose, buffer, emit, from, to, PANEL_RADIUS * layer[0], PANEL_RADIUS * layer[1], shaders ? textured(tint, strength) : additive(tint, strength), shaders);
                }
            }
        });
    }

    private static void pane(PoseStack.Pose pose, VertexConsumer buffer, Emit emit, float z, float radius, int color, boolean textured) {
        int edge = textured ? color : 0;
        for (int i = 0; i < SIDES; i++) {
            float a0 = (i + 0.5F) * Mth.TWO_PI / SIDES;
            float a1 = (i + 1.5F) * Mth.TWO_PI / SIDES;
            float x0 = Mth.cos(a0) * radius, y0 = Mth.sin(a0) * radius, x1 = Mth.cos(a1) * radius, y1 = Mth.sin(a1) * radius;
            emit.vertex(pose, buffer, 0.0F, 0.0F, z, color, 0.5F, 0.0F);
            emit.vertex(pose, buffer, 0.0F, 0.0F, z, color, 0.5F, 0.0F);
            emit.vertex(pose, buffer, x0, y0, z, edge, 0.0F, 1.0F);
            emit.vertex(pose, buffer, x1, y1, z, edge, 1.0F, 1.0F);
            if (!textured) continue;
            emit.vertex(pose, buffer, x1, y1, z, edge, 1.0F, 1.0F);
            emit.vertex(pose, buffer, x0, y0, z, edge, 0.0F, 1.0F);
            emit.vertex(pose, buffer, 0.0F, 0.0F, z, color, 0.5F, 0.0F);
            emit.vertex(pose, buffer, 0.0F, 0.0F, z, color, 0.5F, 0.0F);
        }
    }

    private static void frustum(PoseStack.Pose pose, VertexConsumer buffer, Emit emit, Vec3 from, Vec3 to, float nearRadius, float farRadius, int near, boolean textured) {
        int far = textured ? near : 0;
        for (int i = 0; i < SIDES; i++) {
            float a0 = (i + 0.5F) * Mth.TWO_PI / SIDES;
            float a1 = (i + 1.5F) * Mth.TWO_PI / SIDES;
            float u0 = i / (float) SIDES;
            float u1 = (i + 1) / (float) SIDES;
            float nx0 = Mth.cos(a0) * nearRadius, ny0 = Mth.sin(a0) * nearRadius, nx1 = Mth.cos(a1) * nearRadius, ny1 = Mth.sin(a1) * nearRadius;
            float fx0 = Mth.cos(a0) * farRadius, fy0 = Mth.sin(a0) * farRadius, fx1 = Mth.cos(a1) * farRadius, fy1 = Mth.sin(a1) * farRadius;
            emit.vertex(pose, buffer, nx0, ny0, (float) from.z, near, u0, 0.0F);
            emit.vertex(pose, buffer, nx1, ny1, (float) from.z, near, u1, 0.0F);
            emit.vertex(pose, buffer, fx1, fy1, (float) to.z, far, u1, 1.0F);
            emit.vertex(pose, buffer, fx0, fy0, (float) to.z, far, u0, 1.0F);
            if (!textured) continue;
            emit.vertex(pose, buffer, fx0, fy0, (float) to.z, far, u0, 1.0F);
            emit.vertex(pose, buffer, fx1, fy1, (float) to.z, far, u1, 1.0F);
            emit.vertex(pose, buffer, nx1, ny1, (float) from.z, near, u1, 0.0F);
            emit.vertex(pose, buffer, nx0, ny0, (float) from.z, near, u0, 0.0F);
        }
    }

    private static int textured(int color, float strength) {
        return ARGB.color(Math.round(255 * Mth.clamp(strength * SHADER_DIM, 0.0F, 1.0F)), color);
    }

    private static boolean ensureFade() {
        if (fadeReady) return true;
        NativeImage image = new NativeImage(FADE_SIZE, FADE_SIZE, false);
        for (int y = 0; y < FADE_SIZE; y++) {
            float fade = (float) Math.pow(1.0F - y / (float) (FADE_SIZE - 1), FADE_POWER);
            int level = Math.round(255.0F * fade);
            for (int x = 0; x < FADE_SIZE; x++) image.setPixel(x, y, ARGB.color(level, level, level, level));
        }
        Minecraft.getInstance().getTextureManager().register(FADE_TEXTURE, new DynamicTexture(FADE_TEXTURE::toString, image));
        fadeReady = true;
        return true;
    }

    private static int additive(int color, float strength) {
        float root = Mth.sqrt(Mth.clamp(strength, 0.0F, 1.0F));
        return ARGB.color(Math.round(255 * root), ARGB.scaleRGB(color, root));
    }

    private BatteryEffects() {}
}
