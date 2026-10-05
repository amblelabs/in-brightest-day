package dev.amble.client.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amble.BrightestDay;
import dev.amble.client.effects.attacks.utility.OreProbeRenderTypes;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.client.space.SpaceRenderTypes;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class IrisCompat {
    private static final String IRIS = "iris";

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(IRIS)) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Class<? extends Enum> program = (Class<? extends Enum>) Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method assign = api.getMethod("assignPipeline", RenderPipeline.class, program);
            Object basic = Enum.valueOf(program, "BASIC");
            for (RenderPipeline pipeline : FlightRenderTypes.pipelines()) assign.invoke(instance, pipeline, basic);
            for (RenderPipeline pipeline : OreProbeRenderTypes.pipelines()) assign.invoke(instance, pipeline, basic);
            for (RenderPipeline pipeline : SpaceRenderTypes.pipelines()) assign.invoke(instance, pipeline, basic);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            BrightestDay.LOGGER.warn("Couldn't register construct render pipelines with Iris; constructs may be invisible with shaders", exception);
        }
    }

    private IrisCompat() {}
}
