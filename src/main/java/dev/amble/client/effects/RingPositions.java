package dev.amble.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class RingPositions {
    public static final RenderStateDataKey<Integer> PLAYER_ID = RenderStateDataKey.create(() -> "brightestday:ring_player");
    private static final long STALE_NANOS = 150_000_000L;
    private static final double MAX_REACH = 4.0;

    private static final Map<Integer, Entry> POSITIONS = new HashMap<>();

    private record Entry(Vec3 offset, long time) {}

    public static void capture(AvatarRenderState state, PoseStack poseStack) {
        Integer id = ((FabricRenderState) state).getData(PLAYER_ID);
        if (id == null || id < 0) return;
        Vector3f relative = poseStack.last().pose().transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
        Vec3 camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        Vec3 offset = new Vec3(relative.x + camera.x - state.x, relative.y + camera.y - state.y, relative.z + camera.z - state.z);
        if (offset.length() > MAX_REACH * Math.max(state.scale, 1.0F)) return;
        if (POSITIONS.size() > 256) POSITIONS.clear();
        POSITIONS.put(id, new Entry(offset, System.nanoTime()));
    }

    public static @Nullable Vec3 of(Player player, float partialTicks) {
        Entry entry = POSITIONS.get(player.getId());
        if (entry == null || System.nanoTime() - entry.time > STALE_NANOS) return null;
        return player.getPosition(partialTicks).add(entry.offset);
    }

    private RingPositions() {}
}
