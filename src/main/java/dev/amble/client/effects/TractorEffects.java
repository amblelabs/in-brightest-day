package dev.amble.client.effects;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.TractorC2SPayload;
import dev.amble.core.networking.payloads.s2c.TractorS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.TractorBeamRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TractorEffects {
    private static final float BEAM_SPACING = 3.0F * VoxelRenderer.PIXEL;
    private static final float BEAM_VOXEL_SIZE = 2.0F * VoxelRenderer.PIXEL;
    private static final float BEAM_FLOW_SPEED = 0.06F;
    private static final float BEAM_WOBBLE = 0.06F;
    private static final float BEAM_ALPHA = 0.9F;
    private static final float WRAP_PADDING = 0.12F;
    private static final float WRAP_MIN_SPACING = 0.2F;
    private static final int WRAP_MAX_CELLS = 20;
    private static final float WRAP_VOXEL_SIZE = 1.5F * VoxelRenderer.PIXEL;
    private static final float WRAP_WARBLE = 0.04F;
    private static final float WRAP_ALPHA = 0.8F;
    private static final float HOLD_FADE = 0.25F;

    private static final Map<Integer, Integer> BEAMS = new HashMap<>();
    private static @Nullable ClientLevel beamLevel;
    private static boolean holding;
    private static float hold;
    private static float oHold;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(TractorS2CPayload.TYPE, (payload, context) -> {
            if (payload.targetId() == TractorS2CPayload.NO_TARGET) BEAMS.remove(payload.playerId());
            else BEAMS.put(payload.playerId(), payload.targetId());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BEAMS.clear();
            holding = false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(TractorEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(TractorEffects::render);
    }

    public static boolean isBeaming(Player player) {
        return BEAMS.containsKey(player.getId());
    }

    public static boolean isTractorMode(Player player) {
        return TractorBeamRingPower.isActive(player) && ArmedRingPower.isArmed(player) && PowerRingItem.hasCharge(player);
    }

    public static boolean onScroll(int wheel) {
        if (!holding) return false;
        ClientPlayNetworking.send(new TractorC2SPayload(TractorC2SPayload.ADJUST, Integer.signum(wheel)));
        return true;
    }

    public static float holdAmount(float partialTicks) {
        return Mth.lerp(partialTicks, oHold, hold);
    }

    private static void tick(Minecraft client) {
        if (client.level != beamLevel) {
            BEAMS.clear();
            beamLevel = client.level;
        }

        LocalPlayer player = client.player;
        boolean down = player != null && RingInput.useHeld(client) && isTractorMode(player);
        if (down && !holding) {
            ClientPlayNetworking.send(new TractorC2SPayload(TractorC2SPayload.GRAB, 0));
            holding = true;
        } else if (!down && holding) {
            ClientPlayNetworking.send(new TractorC2SPayload(TractorC2SPayload.RELEASE, 0));
            holding = false;
        }

        oHold = hold;
        boolean beaming = player != null && BEAMS.containsKey(player.getId());
        hold += ((beaming ? 1.0F : 0.0F) - hold) * HOLD_FADE;
    }

    private static void render(LevelRenderContext context) {
        if (BEAMS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        for (Map.Entry<Integer, Integer> entry : BEAMS.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            Entity target = client.level.getEntity(entry.getValue());
            if (target == null) continue;

            int color = ARGB.opaque(CorpsColors.of(player));
            float time = player.tickCount + partialTicks;
            AABB box = target.getBoundingBox().move(target.getPosition(partialTicks).subtract(target.position()));

            List<ShieldEffects.Voxel> voxels = new ArrayList<>();
            beam(BlastEffects.hand(player, partialTicks), box.getCenter(), time, color, voxels);
            wrap(box, time, color, voxels);
            ShieldEffects.submit(context, camera, voxels, BEAM_ALPHA);
        }
    }

    public static void beam(Vec3 from, Vec3 to, float time, int color, List<ShieldEffects.Voxel> out) {
        Vec3 path = to.subtract(from);
        double length = path.length();
        if (length < 1.0E-3) return;

        Vec3 direction = path.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        int count = Math.max(Mth.floor(length / BEAM_SPACING), 1);
        float flow = (time * BEAM_FLOW_SPEED) % 1.0F;
        for (int i = 0; i < count; i++) {
            float t = ((float) i / count + flow) % 1.0F;
            float envelope = Mth.sin(t * Mth.PI);
            float angle = t * Mth.TWO_PI * 3.0F - time * 0.4F;
            Vec3 offset = side.scale(Mth.cos(angle) * BEAM_WOBBLE * envelope).add(up.scale(Mth.sin(angle) * BEAM_WOBBLE * envelope));
            Vec3 point = VoxelRenderer.snap(from.add(path.scale(t)).add(offset));
            int tint = VoxelRenderer.toWhite(color, 0.25F + 0.25F * Mth.sin(time * 0.6F + i));
            out.add(new ShieldEffects.Voxel(point, VoxelRenderer.snapSize(BEAM_VOXEL_SIZE * 0.5F), tint));
        }
    }

    public static void wrap(AABB box, float time, int color, List<ShieldEffects.Voxel> out) {
        AABB shell = box.inflate(WRAP_PADDING);
        double maxSize = Math.max(shell.getXsize(), Math.max(shell.getYsize(), shell.getZsize()));
        double spacing = Math.max(WRAP_MIN_SPACING, maxSize / WRAP_MAX_CELLS);
        int nx = Math.max((int) Math.round(shell.getXsize() / spacing), 1);
        int ny = Math.max((int) Math.round(shell.getYsize() / spacing), 1);
        int nz = Math.max((int) Math.round(shell.getZsize() / spacing), 1);
        float half = VoxelRenderer.snapSize(WRAP_VOXEL_SIZE * 0.5F);
        Vec3 center = shell.getCenter();

        int index = 0;
        for (int x = 0; x <= nx; x++) {
            for (int y = 0; y <= ny; y++) {
                for (int z = 0; z <= nz; z++) {
                    boolean onSurface = x == 0 || x == nx || y == 0 || y == ny || z == 0 || z == nz;
                    if (!onSurface) continue;

                    Vec3 point = new Vec3(
                            Mth.lerp((double) x / nx, shell.minX, shell.maxX),
                            Mth.lerp((double) y / ny, shell.minY, shell.maxY),
                            Mth.lerp((double) z / nz, shell.minZ, shell.maxZ));
                    Vec3 outward = point.subtract(center);
                    if (outward.lengthSqr() > 1.0E-6) {
                        point = point.add(outward.normalize().scale(WRAP_WARBLE * Mth.sin(time * 0.3F + index * 0.7F)));
                    }
                    int tint = VoxelRenderer.toWhite(color, 0.1F + 0.15F * Mth.sin(time * 0.45F + index));
                    out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, tint));
                    index++;
                }
            }
        }
    }

    private TractorEffects() {}
}
