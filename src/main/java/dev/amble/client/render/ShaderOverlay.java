package dev.amble.client.render;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import dev.amble.BrightestDay;
import dev.amble.client.compat.IrisCompat;
import dev.amble.client.flight.FlightRenderTypes;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class ShaderOverlay {
    private static final int INITIAL_SIZE = 1 << 20;

    private record Entry(RenderType type, PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer renderer) {}

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static @Nullable Matrix4f modelView;
    private static @Nullable GpuBufferSlice projection;
    private static ProjectionType projectionType = ProjectionType.PERSPECTIVE;
    private static @Nullable StagedVertexBuffer buffer;
    private static boolean failed;
    private static boolean inHand;

    public static void init() {
        LevelRenderEvents.START_MAIN.register(context -> {
            if (!active()) return;
            modelView = RenderSystem.getModelViewMatrixCopy();
            projection = RenderSystem.getProjectionMatrixBuffer();
            projectionType = RenderSystem.getProjectionType();
        });
    }

    public static boolean active() {
        // first-person submits use the hand projection, which the level-camera replay can't reproduce
        return !failed && !inHand && IrisCompat.shadersActive();
    }

    public static void inHand(boolean value) {
        inHand = value;
    }

    public static boolean inHand() {
        return inHand;
    }

    public static void record(RenderType type, PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer renderer) {
        ENTRIES.add(new Entry(type, pose, renderer));
    }

    public static void draw() {
        if (ENTRIES.isEmpty()) return;
        if (failed || modelView == null || projection == null || passOpen()) {
            ENTRIES.clear();
            return;
        }
        boolean previous = IrisCompat.bypass(true);
        try {
            render(modelView, projection);
        } catch (RuntimeException exception) {
            failed = true;
            BrightestDay.LOGGER.error("Construct shader overlay failed; constructs will draw without blur under shaders", exception);
        } finally {
            IrisCompat.bypass(previous);
            ENTRIES.clear();
        }
    }

    private static boolean passOpen() {
        return RenderSystem.getDevice().createCommandEncoder() instanceof FrontendCommandEncoder encoder && encoder.isInRenderPass();
    }

    private static void render(Matrix4f view, GpuBufferSlice projectionBuffer) {
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (FrostedGlass.enabled()) FrostedGlass.capture(target);
        if (buffer == null) buffer = new StagedVertexBuffer(() -> "brightestday shader overlay", INITIAL_SIZE);

        List<RenderType> order = List.of(FrostedGlass.GLASS, FlightRenderTypes.glass(), FlightRenderTypes.glassUnsorted(), FlightRenderTypes.GLOW, FlightRenderTypes.GLOW_UNSORTED);
        List<PreparedRenderType> prepared = new ArrayList<>();
        List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.set(view);
        try {
            for (RenderType type : order) {
                StagedVertexBuffer.Draw draw = null;
                VertexConsumer vertices = null;
                for (Entry entry : ENTRIES) {
                    if (entry.type() != type) continue;
                    if (draw == null) {
                        draw = buffer.appendDraw(type.format(), type.primitiveTopology());
                        vertices = buffer.getVertexBuilder(draw);
                    }
                    entry.renderer().render(entry.pose(), vertices);
                }
                if (draw == null) continue;
                buffer.requestIndexCount(draw);
                draws.add(draw);
                prepared.add(type.prepare());
            }
        } finally {
            stack.popMatrix();
        }
        if (draws.isEmpty()) return;

        buffer.upload();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer, projectionType);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "brightestday shader overlay",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            for (int i = 0; i < draws.size(); i++) {
                try {
                    prepared.get(i).drawFromBuffer(buffer.getExecuteInfo(draws.get(i)), pass);
                } catch (RuntimeException exception) {
                    // drawFromBuffer pushes a debug group before anything can throw and only pops it on success;
                    // left open, pass.close() throws before releasing the encoder and the next pass crashes the game
                    pass.popDebugGroup();
                    throw exception;
                }
            }
        } finally {
            RenderSystem.restoreProjectionMatrix();
            buffer.endDraw();
            if (!passOpen()) buffer.endFrame();
        }
    }

    private ShaderOverlay() {}
}
