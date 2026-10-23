package dev.amble.client.compat;

import com.google.common.base.Suppliers;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.amble.BrightestDay;
import dev.amble.client.effects.attacks.utility.OreProbeRenderTypes;
import dev.amble.client.flight.FlightRenderTypes;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class IrisCompat {

    private static final Supplier<Class<?>> IRIS_API = Suppliers.memoize(() -> getIrisClass("IrisApi"));
    private static final String IRIS = "iris";

    @SuppressWarnings("unchecked")
    private static <T> Class<? extends T> getIrisClass(String name) {
        try {
            return (Class<? extends T>) Class.forName("net.irisshaders.iris.api.v0." + name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(IRIS)) return;

        try {
            Class<?> apiClass = IRIS_API.get();
            api = apiClass.getMethod("getInstance").invoke(null);

            Class<? extends Enum> program = getIrisClass("IrisProgram");
            Method assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, program);

            Enum<?> basic = Enum.valueOf(program, "BASIC");
            for (RenderPipeline pipeline : FlightRenderTypes.pipelines()) assign.invoke(api, pipeline, basic);
            for (RenderPipeline pipeline : OreProbeRenderTypes.pipelines()) assign.invoke(api, pipeline, basic);

            MethodHandles.Lookup baseLookup = MethodHandles.lookup();
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(apiClass, baseLookup);

            MethodType methodType = MethodType.methodType(boolean.class);
            MethodHandle targetHandle = privateLookup.findVirtual(apiClass, "isShaderPackInUse", methodType);

            CallSite callSite = LambdaMetafactory.metafactory(
                    privateLookup,
                    "test",
                    MethodType.methodType(Predicate.class),
                    MethodType.methodType(boolean.class, Object.class),
                    targetHandle,
                    MethodType.methodType(boolean.class, apiClass)
            );

            Predicate<Object> shaderPackInUsePredicate = (Predicate<Object>) callSite.getTarget().invokeExact();
            shaderPackInUse = () -> shaderPackInUsePredicate.test(api);
        } catch (Throwable exception) {
            BrightestDay.LOGGER.warn("Couldn't register construct render pipelines with Iris; constructs may be invisible with shaders", exception);
        }
    }

    private static @Nullable Object api;
    private static @Nullable BooleanSupplier shaderPackInUse;

    // TODO(rewrite): should use API directly
    public static boolean shadersActive() {
        return shaderPackInUse != null && shaderPackInUse.getAsBoolean();
    }

    private IrisCompat() {}
}
