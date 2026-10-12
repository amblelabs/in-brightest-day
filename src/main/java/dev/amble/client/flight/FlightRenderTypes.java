package dev.amble.client.flight;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amble.BrightestDay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.List;

public final class FlightRenderTypes {

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/flight_glow"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build()
    );

    private static final OitPipelineSet OIT_GLOW = RenderPipelines.register(
            OitPipelineSet.builder("brightestday_flight_glow", RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET).withShaderDefine("OIT_ADDITIVE").withCull(false))
                    .withDepthBoundsModifier(builder -> builder.withCull(false))
                    .withTransmittanceModifier(builder -> builder.withCull(false))
                    .withAccumulateModifier(builder -> builder.withCull(false))
                    .build()
    );

    public static final RenderType GLOW = RenderType.create(
            "brightestday_flight_glow",
            RenderSetup.builder(GLOW_PIPELINE).setOitPipelines(OIT_GLOW).sortOnUpload().createRenderSetup()
    );

    private static final RenderPipeline GLASS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/construct_glass"))
                    .withCull(false)
                    .build()
    );

    private static final RenderType GLASS = RenderType.create(
            "brightestday_construct_glass",
            RenderSetup.builder(GLASS_PIPELINE).setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS).sortOnUpload().createRenderSetup()
    );

    private static final RenderPipeline SKY_GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/sky_glow"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .build()
    );

    private static final OitPipelineSet OIT_SKY_GLOW = RenderPipelines.register(
            OitPipelineSet.builder("brightestday_sky_glow", RenderPipeline.builder(RenderPipelines.OIT_DEBUG_FILLED_SNIPPET).withShaderDefine("OIT_ADDITIVE")).build()
    );

    public static final RenderType SKY_GLOW = RenderType.create(
            "brightestday_sky_glow",
            RenderSetup.builder(SKY_GLOW_PIPELINE).setOitPipelines(OIT_SKY_GLOW).sortOnUpload().createRenderSetup()
    );

    private static final RenderPipeline SKY_SOLID_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/sky_solid"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .build()
    );

    public static final RenderType SKY_SOLID = RenderType.create(
            "brightestday_sky_solid",
            RenderSetup.builder(SKY_SOLID_PIPELINE).sortOnUpload().createRenderSetup()
    );

    public static RenderType glass() {
        return GLASS;
    }

    public static List<RenderPipeline> pipelines() {
        return List.of(GLOW_PIPELINE, GLASS_PIPELINE, SKY_GLOW_PIPELINE, SKY_SOLID_PIPELINE);
    }

    public static void init() {}

    private FlightRenderTypes() {}
}
