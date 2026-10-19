package dev.amble.client.effects;

import dev.amble.core.networking.payloads.s2c.HealBeamS2CPayload;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HealBeamEffects {
    private static final float BEAM_SPACING = 2.5F * VoxelRenderer.PIXEL;
    private static final float BEAM_HALF = 1.5F * VoxelRenderer.PIXEL;
    private static final float BEAM_SWAY = 0.18F;
    private static final float BEAM_FLOW_SPEED = 0.08F;
    private static final float BEAM_ALPHA = 0.75F;
    private static final int RIBBONS = 2;
    private static final float RIBBON_PADDING = 0.25F;
    private static final float RIBBON_TURNS = 2.5F;
    private static final float RIBBON_SPIN = 0.12F;
    private static final int RIBBON_STEPS = 48;
    private static final float RIBBON_HALF = 1.25F * VoxelRenderer.PIXEL;
    private static final float RIBBON_ALPHA = 0.55F;
    private static final int MOTES = 10;
    private static final float MOTE_HALF = 1.0F * VoxelRenderer.PIXEL;
    private static final float SHROUD_ALPHA = 0.2F;

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    private static final Map<Integer, HealSound> SOUNDS = new HashMap<>();
    private static @Nullable ClientLevel beamLevel;

    private record Beam(int targetId, int color) {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(HealBeamS2CPayload.TYPE, (payload, context) -> {
            if (payload.targetId() == HealBeamS2CPayload.NO_TARGET) BEAMS.remove(payload.playerId());
            else BEAMS.put(payload.playerId(), new Beam(payload.targetId(), ARGB.opaque(payload.color())));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BEAMS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(HealBeamEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(HealBeamEffects::render);
    }

    public static boolean isHealing(Player player) {
        return BEAMS.containsKey(player.getId());
    }

    private static void tick(Minecraft client) {
        if (client.level != beamLevel) {
            BEAMS.clear();
            beamLevel = client.level;
        }
        if (client.level == null) return;

        for (Integer playerId : BEAMS.keySet()) {
            HealSound sound = SOUNDS.get(playerId);
            if ((sound == null || sound.isStopped()) && client.level.getEntity(playerId) instanceof Player player) {
                sound = new HealSound(player);
                SOUNDS.put(playerId, sound);
                client.getSoundManager().play(sound);
            }
        }
        SOUNDS.values().removeIf(HealSound::isStopped);
    }

    private static void render(LevelRenderContext context) {
        if (BEAMS.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Map.Entry<Integer, Beam> entry : BEAMS.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;
            Entity target = client.level.getEntity(entry.getValue().targetId());
            if (target == null) continue;

            int color = entry.getValue().color();
            float time = player.tickCount + partialTicks;
            AABB box = target.getBoundingBox().move(target.getPosition(partialTicks).subtract(target.position()));

            List<ShieldEffects.Voxel> beam = new ArrayList<>();
            beam(BlastEffects.hand(player, partialTicks), box.getCenter(), time, color, beam);
            ShieldEffects.submit(context, camera, beam, BEAM_ALPHA);

            List<ShieldEffects.Voxel> ribbons = new ArrayList<>();
            ribbons(box, time, color, ribbons);
            motes(box, time, color, ribbons);
            ShieldEffects.submit(context, camera, ribbons, RIBBON_ALPHA);

            List<ShieldEffects.Voxel> shroud = new ArrayList<>();
            TractorEffects.wrap(box, time, color, shroud);
            ShieldEffects.submit(context, camera, shroud, SHROUD_ALPHA);
        }
    }

    private static void beam(Vec3 start, Vec3 end, float time, int color, List<ShieldEffects.Voxel> out) {
        Vec3 path = end.subtract(start);
        double length = path.length();
        if (length < 1.0E-3) return;

        Vec3 direction = path.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        float half = VoxelRenderer.snapSize(BEAM_HALF);
        int steps = Math.max((int) (length / BEAM_SPACING), 1);
        float flow = time * BEAM_FLOW_SPEED;
        for (int i = 0; i <= steps; i++) {
            float t = (float) i / steps;
            float envelope = Mth.sin(t * Mth.PI);
            for (int strand = 0; strand < 2; strand++) {
                float angle = t * Mth.TWO_PI * 2.0F - time * 0.2F + strand * Mth.PI;
                Vec3 offset = side.scale(Mth.cos(angle) * BEAM_SWAY * envelope).add(up.scale(Mth.sin(angle) * BEAM_SWAY * envelope));
                float pulse = Mth.frac(t * 4.0F - flow);
                int tint = VoxelRenderer.toWhite(color, 0.2F + 0.5F * pulse * pulse);
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(path.scale(t)).add(offset)), half, tint));
            }
        }
    }

    private static void ribbons(AABB box, float time, int color, List<ShieldEffects.Voxel> out) {
        Vec3 center = box.getCenter();
        double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5 + RIBBON_PADDING;
        double height = box.getYsize();
        float half = VoxelRenderer.snapSize(RIBBON_HALF);

        for (int ribbon = 0; ribbon < RIBBONS; ribbon++) {
            float phase = ribbon * Mth.TWO_PI / RIBBONS;
            for (int i = 0; i <= VisualQuality.count(RIBBON_STEPS); i++) {
                float t = (float) i / VisualQuality.count(RIBBON_STEPS);
                float climb = Mth.frac(t + time * 0.01F);
                float angle = climb * Mth.TWO_PI * RIBBON_TURNS + time * RIBBON_SPIN + phase;
                double squeeze = radius * (0.85 + 0.15 * Mth.sin(climb * Mth.PI));
                Vec3 point = new Vec3(
                        center.x + Mth.cos(angle) * squeeze,
                        box.minY + climb * height,
                        center.z + Mth.sin(angle) * squeeze);
                int tint = VoxelRenderer.toWhite(color, 0.3F + 0.4F * climb);
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, tint));
            }
        }
    }

    private static void motes(AABB box, float time, int color, List<ShieldEffects.Voxel> out) {
        Vec3 center = box.getCenter();
        double radius = Math.max(box.getXsize(), box.getZsize()) * 0.5;
        float half = VoxelRenderer.snapSize(MOTE_HALF);
        int tint = VoxelRenderer.toWhite(color, 0.7F);

        for (int i = 0; i < MOTES; i++) {
            float seed = i * 2.399F;
            float rise = Mth.frac(time * 0.025F + i / (float) MOTES);
            double spread = radius * (0.4 + 0.6 * Mth.frac(seed * 1.7F));
            Vec3 point = new Vec3(
                    center.x + Mth.cos(seed * Mth.TWO_PI) * spread,
                    box.minY + rise * (box.getYsize() + 0.5),
                    center.z + Mth.sin(seed * Mth.TWO_PI) * spread);
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point), half, tint));
        }
    }

    private static final class HealSound extends AbstractTickableSoundInstance {
        private final Player player;

        HealSound(Player player) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.7F;
            this.pitch = 1.3F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !isHealing(this.player)) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    private HealBeamEffects() {}
}
