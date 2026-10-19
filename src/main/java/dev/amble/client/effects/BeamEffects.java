package dev.amble.client.effects;

import dev.amble.core.beams.BeamManager;
import dev.amble.core.networking.payloads.s2c.BeamS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BeamEffects {
    private static final float CORE_SPACING = 1.5F * VoxelRenderer.PIXEL;
    private static final float CORE_HALF = 1.0F * VoxelRenderer.PIXEL;
    private static final float BODY_SPACING = 3.0F * VoxelRenderer.PIXEL;
    private static final float BODY_HALF = 2.5F * VoxelRenderer.PIXEL;
    private static final float HELIX_RADIUS = 0.14F;
    private static final float HELIX_TURNS_PER_BLOCK = 1.5F;
    private static final float HELIX_SPIN = 0.5F;
    private static final int IMPACT_VOXELS = 14;
    private static final float IMPACT_SPREAD = 0.35F;

    private static final Map<Integer, Integer> BEAMS = new HashMap<>();
    private static final Map<Integer, BeamSound> SOUNDS = new HashMap<>();
    private static ClientLevel beamLevel;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BeamS2CPayload.TYPE, (payload, context) -> {
            if (payload.active()) BEAMS.put(payload.playerId(), ARGB.opaque(payload.color()));
            else BEAMS.remove(payload.playerId());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BEAMS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(BeamEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(BeamEffects::render);
    }

    public static boolean isBeaming(Player player) {
        return BEAMS.containsKey(player.getId());
    }

    private static void tick(Minecraft client) {
        if (client.level != beamLevel) {
            BEAMS.clear();
            beamLevel = client.level;
        }
        if (client.level == null) return;

        for (Integer playerId : BEAMS.keySet()) {
            BeamSound sound = SOUNDS.get(playerId);
            if ((sound == null || sound.isStopped()) && client.level.getEntity(playerId) instanceof Player player) {
                sound = new BeamSound(player);
                SOUNDS.put(playerId, sound);
                client.getSoundManager().play(sound);
            }
        }
        SOUNDS.values().removeIf(BeamSound::isStopped);
    }

    private static void render(LevelRenderContext context) {
        if (BEAMS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Map.Entry<Integer, Integer> entry : BEAMS.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;

            int color = entry.getValue();
            float time = player.tickCount + partialTicks;
            Vec3 start = BlastEffects.hand(player, partialTicks);
            Vec3 end = impact(client.level, player, partialTicks);

            List<ShieldEffects.Voxel> voxels = new ArrayList<>();
            beam(start, end, time, color, voxels);
            impactBurst(end, time, color, voxels);
            ShieldEffects.submit(context, camera, voxels, 1.0F);
        }
    }

    private static Vec3 impact(ClientLevel level, Player player, float partialTicks) {
        Vec3 eye = player.getEyePosition(partialTicks);
        Vec3 look = RemoteAim.look(player, partialTicks);
        Vec3 end = eye.add(look.scale(BeamManager.range()));

        HitResult blockHit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) end = blockHit.getLocation();

        AABB searchArea = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, eye, end, searchArea,
                entity -> entity != player && !entity.isSpectator() && entity.isPickable(), 0.3F);
        return entityHit != null ? entityHit.getLocation() : end;
    }

    private static void beam(Vec3 start, Vec3 end, float time, int color, List<ShieldEffects.Voxel> out) {
        Vec3 path = end.subtract(start);
        double length = path.length();
        if (length < 1.0E-3) return;

        Vec3 direction = path.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        int coreColor = VoxelRenderer.toWhite(color, 0.75F);
        float coreHalf = VoxelRenderer.snapSize(CORE_HALF);
        for (double d = 0.0; d <= length; d += CORE_SPACING) {
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(direction.scale(d))), coreHalf, coreColor));
        }

        float flicker = 0.85F + 0.15F * Mth.sin(time * 1.9F);
        float bodyHalf = VoxelRenderer.snapSize(BODY_HALF * flicker);
        for (double d = 0.0; d <= length; d += BODY_SPACING) {
            int tint = VoxelRenderer.toWhite(color, 0.15F + 0.15F * Mth.sin(time * 0.8F - (float) d * 3.0F));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(direction.scale(d))), bodyHalf, tint));
        }

        float helixHalf = VoxelRenderer.snapSize(CORE_HALF);
        for (double d = 0.0; d <= length; d += CORE_SPACING * 2.0) {
            for (int strand = 0; strand < 2; strand++) {
                float angle = (float) d * HELIX_TURNS_PER_BLOCK * Mth.TWO_PI - time * HELIX_SPIN + strand * Mth.PI;
                Vec3 offset = side.scale(Mth.cos(angle) * HELIX_RADIUS).add(up.scale(Mth.sin(angle) * HELIX_RADIUS));
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(direction.scale(d)).add(offset)), helixHalf, color));
            }
        }
    }

    private static void impactBurst(Vec3 end, float time, int color, List<ShieldEffects.Voxel> out) {
        RandomSource random = RandomSource.create(Mth.floor(time * 2.0F));
        int tint = VoxelRenderer.toWhite(color, 0.5F);
        for (int i = 0; i < VisualQuality.count(IMPACT_VOXELS); i++) {
            Vec3 offset = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(IMPACT_SPREAD * random.nextFloat());
            float half = VoxelRenderer.snapSize(BODY_HALF * (0.4F + random.nextFloat() * 0.6F));
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(end.add(offset)), half, tint));
        }
    }

    private static final class BeamSound extends AbstractTickableSoundInstance {
        private final Player player;

        BeamSound(Player player) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.9F;
            this.pitch = 1.8F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !isBeaming(this.player)) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    private BeamEffects() {}
}
