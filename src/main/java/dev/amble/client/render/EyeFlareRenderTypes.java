package dev.amble.client.render;

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
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class EyeFlareRenderTypes {

    private static final RenderPipeline FLARE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.EYES_SNIPPET)
                    .withLocation(BrightestDay.id("pipeline/eye_flare"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .build()
    );

    private static final OitPipelineSet OIT_FLARE = RenderPipelines.register(
            OitPipelineSet.builder("brightestday_eye_flare", RenderPipeline.builder(RenderPipelines.OIT_ENTITY_SNIPPET, RenderPipelines.EYES_SNIPPET).withShaderDefine("OIT_ADDITIVE"))
                    .build()
    );

    private static final Map<Identifier, RenderType> FLARES = new HashMap<>();

    public static RenderType flare(Identifier texture) {
        return FLARES.computeIfAbsent(texture, id -> RenderType.create(
                "brightestday_eye_flare",
                RenderSetup.builder(FLARE_PIPELINE).setOitPipelines(OIT_FLARE).withTexture("Sampler0", id).sortOnUpload().createRenderSetup()
        ));
    }

    public static void init() {}

    private EyeFlareRenderTypes() {}
}
