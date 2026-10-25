package dev.amble.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.amble.BrightestDay;
import dev.amble.client.compat.IrisCompat;
import dev.amble.client.config.BrightestDayClientConfig;
import dev.amble.client.flight.FlightRenderTypes;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class FrostedGlass {
    public static final Identifier BACKDROP_ID = BrightestDay.id("frosted_backdrop");
    private static final int DOWNSCALE = 2;
    public static final float FROST_ALPHA = 0.95F;

    private static final RenderPipeline BLUR_HORIZONTAL = blur("frosted_blur_horizontal", false);
    private static final RenderPipeline BLUR_VERTICAL = blur("frosted_blur_vertical", true);

    private static final RenderPipeline GLASS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/frosted_glass"))
                    .withVertexShader(BrightestDay.id("core/frosted_glass"))
                    .withFragmentShader(BrightestDay.id("core/frosted_glass"))
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withCull(false)
                    .build()
    );

    private static final OitPipelineSet OIT_GLASS = RenderPipelines.register(
            OitPipelineSet.builder("brightestday_frosted_glass", RenderPipeline.builder(RenderPipelines.OIT_DEBUG_FILLED_SNIPPET)
                            .withVertexShader(BrightestDay.id("core/frosted_glass"))
                            .withFragmentShader(BrightestDay.id("core/frosted_glass"))
                            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                            .withCull(false))
                    .withDepthBoundsModifier(builder -> builder.withCull(false))
                    .withTransmittanceModifier(builder -> builder.withCull(false))
                    .withAccumulateModifier(builder -> builder.withCull(false))
                    .build()
    );

    public static final RenderType GLASS = RenderType.create(
            "brightestday_frosted_glass",
            RenderSetup.builder(GLASS_PIPELINE).setOitPipelines(OIT_GLASS).withTexture("Sampler0", BACKDROP_ID).createRenderSetup()
    );

    private static final Backdrop BACKDROP = new Backdrop();
    private static @Nullable TextureTarget horizontal;
    private static @Nullable TextureTarget vertical;
    private static boolean registered;
    private static boolean requested;
    private static boolean wanted;

    public static List<RenderPipeline> pipelines() {
        return List.of(GLASS_PIPELINE, BLUR_HORIZONTAL, BLUR_VERTICAL);
    }

    private static RenderPipeline blur(String name, boolean vertical) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(BrightestDay.id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(BrightestDay.id("core/frosted_blur"))
                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_COLOR))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
        if (vertical) builder.withShaderDefine("BLUR_VERTICAL");
        return RenderPipelines.register(builder.build());
    }

    public static void init() {
        LevelRenderEvents.END_MAIN.register(context -> {
            wanted = requested;
            requested = false;
        });
    }

    public static boolean enabled() {
        BrightestDayClientConfig config = BrightestDayClientConfig.get();
        return config == null || config.frostedConstructs;
    }

    public static boolean ready() {
        requested = true;
        return registered && enabled();
    }

    public static boolean afterWater(RenderType type) {
        return type == GLASS || type == FlightRenderTypes.glass() || type == FlightRenderTypes.glassUnsorted() || type == FlightRenderTypes.GLOW || type == FlightRenderTypes.GLOW_UNSORTED;
    }

    public static boolean shouldCapture() {
        return wanted && enabled() && !IrisCompat.shadersActive();
    }

    public static void capture(RenderTarget main) {
        int width = Math.max(1, main.width / DOWNSCALE);
        int height = Math.max(1, main.height / DOWNSCALE);
        horizontal = sized(horizontal, "brightestday frosted horizontal", width, height);
        vertical = sized(vertical, "brightestday frosted vertical", width, height);

        draw(BLUR_HORIZONTAL, main.getColorTextureView(), horizontal);
        draw(BLUR_VERTICAL, horizontal.getColorTextureView(), vertical);

        BACKDROP.point(vertical);
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(BACKDROP_ID, BACKDROP);
            registered = true;
        }
    }

    private static TextureTarget sized(@Nullable TextureTarget target, String label, int width, int height) {
        if (target == null) return new TextureTarget(label, width, height, GpuFormat.RGBA8_UNORM, null);
        if (target.width != width || target.height != height) target.resize(width, height);
        return target;
    }

    private static void draw(RenderPipeline pipeline, @Nullable GpuTextureView source, RenderTarget target) {
        if (source == null || target.getColorTextureView() == null) return;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "brightestday frosted blur", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }

    private static final class Backdrop extends AbstractTexture {
        void point(RenderTarget target) {
            this.texture = target.getColorTexture();
            this.textureView = target.getColorTextureView();
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        @Override
        public void close() {}
    }

    private FrostedGlass() {}
}
