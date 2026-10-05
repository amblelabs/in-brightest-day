package dev.amble.client.space;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amble.BrightestDay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.List;

public final class SpaceRenderTypes {
    private static final RenderPipeline BODY_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/celestial_body"))
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withDepthStencilState(DepthStencilState.DEFAULT)
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/celestial_glow"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build()
    );

    private static final RenderPipeline HAZE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/celestial_haze"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build()
    );

    public static final RenderType BODY = RenderType.create("brightestday_celestial_body", RenderSetup.builder(BODY_PIPELINE).createRenderSetup());
    public static final RenderType GLOW = RenderType.create("brightestday_celestial_glow", RenderSetup.builder(GLOW_PIPELINE).createRenderSetup());
    public static final RenderType HAZE = RenderType.create("brightestday_celestial_haze", RenderSetup.builder(HAZE_PIPELINE).createRenderSetup());

    public static List<RenderPipeline> pipelines() {
        return List.of(BODY_PIPELINE, GLOW_PIPELINE, HAZE_PIPELINE);
    }

    private SpaceRenderTypes() {}
}
