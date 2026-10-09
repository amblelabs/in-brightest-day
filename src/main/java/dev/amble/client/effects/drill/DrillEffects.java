package dev.amble.client.effects.drill;

import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.RemoteAim;
import dev.amble.client.effects.ConstructClient;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.client.effects.WallEffects;
import dev.amble.core.drill.DrillGeometry;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.DrillS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.ringpowers.constructs.DrillConstruct;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DrillEffects {
    private static final float CORE_SPACING = 1.5F * VoxelRenderer.PIXEL;
    private static final float CORE_HALF = 1.0F * VoxelRenderer.PIXEL;
    private static final float BODY_SPACING = 2.5F * VoxelRenderer.PIXEL;
    private static final float BODY_HALF = 1.5F * VoxelRenderer.PIXEL;
    private static final float FLIGHT_HALF = 2.0F * VoxelRenderer.PIXEL;
    private static final float BIT_LENGTH = 0.8F;
    private static final float BIT_LENGTH_PER_STEP = 0.5F;
    private static final float BIT_RADIUS = 0.2F;
    private static final float BIT_RADIUS_PER_STEP = 0.3F;
    private static final float BIT_TURNS = 2.5F;
    private static final float SPIN = 1.1F;
    static final float TIP_BITE = 0.15F;
    private static final float HELD_LENGTH = 1.1F;
    private static final float HELD_LENGTH_PER_STEP = 0.45F;
    private static final int COLLAR_VOXELS = 6;
    private static final int CHIP_VOXELS = 10;
    private static final float CHIP_HALF = 1.5F * VoxelRenderer.PIXEL;
    private static final float PREVIEW_ALPHA = 0.22F;
    private static final float DRILLING_PREVIEW_ALPHA = 0.1F;

    private static final Map<Integer, ClientDrill> DRILLS = new HashMap<>();
    private static final Map<Integer, DrillSound> SOUNDS = new HashMap<>();
    private static @Nullable ClientLevel drillLevel;

    record ClientDrill(int color, int size) {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(DrillS2CPayload.TYPE, (payload, context) -> {
            if (payload.active()) DRILLS.put(payload.playerId(), new ClientDrill(ARGB.opaque(payload.color()), payload.size()));
            else DRILLS.remove(payload.playerId());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> DRILLS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(DrillEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(DrillEffects::render);
    }

    public static boolean isDrilling(Player player) {
        return DRILLS.containsKey(player.getId());
    }

    private static void tick(Minecraft client) {
        if (client.level != drillLevel) {
            DRILLS.clear();
            drillLevel = client.level;
        }
        if (client.level == null) return;

        for (Integer playerId : DRILLS.keySet()) {
            DrillSound sound = SOUNDS.get(playerId);
            if ((sound == null || sound.isStopped()) && client.level.getEntity(playerId) instanceof Player player) {
                sound = new DrillSound(player);
                SOUNDS.put(playerId, sound);
                client.getSoundManager().play(sound);
            }
        }
        SOUNDS.values().removeIf(DrillSound::isStopped);
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        preview(context, client, camera, partialTicks);

        for (Map.Entry<Integer, ClientDrill> entry : DRILLS.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;

            ClientDrill drill = entry.getValue();
            Vec3 eye = player.getEyePosition(partialTicks);
            Vec3 look = RemoteAim.look(player, partialTicks);
            BlockHitResult hit = DrillGeometry.target(client.level, player, eye, look);
            Vec3 start = BlastEffects.hand(player, partialTicks);
            Vec3 tip = hit != null ? hit.getLocation().add(look.scale(TIP_BITE)) : start.add(look.scale(HELD_LENGTH + HELD_LENGTH_PER_STEP * (drill.size() - 1)));
            float time = player.tickCount + partialTicks;

            List<ShieldEffects.Voxel> voxels = new ArrayList<>();
            drill(start, tip, time, drill, true, voxels);
            if (hit != null) chips(client.level, hit, look, time, drill, voxels);
            ShieldEffects.submit(context, camera, voxels, 1.0F);
        }
    }

    private static void preview(LevelRenderContext context, Minecraft client, Vec3 camera, float partialTicks) {
        LocalPlayer player = client.player;
        if (player == null || !isReady(player) || ConstructClient.isLookingAtLantern()) return;

        ConstructRingPower construct = ArmedRingPower.selectedConstruct(player).orElse(null);
        if (construct == null) return;

        BlockHitResult hit = DrillGeometry.target(client.level, player, player.getEyePosition(partialTicks), player.getViewVector(partialTicks));
        if (hit == null) return;

        ClientDrill active = DRILLS.get(player.getId());
        int size = active != null ? active.size() : ConstructClient.size(construct);
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos pos : DrillGeometry.face(hit.getBlockPos(), hit.getDirection(), size)) {
            if (DrillGeometry.drillable(client.level, pos, client.level.getBlockState(pos))) cells.add(pos);
        }
        if (cells.isEmpty()) return;
        WallEffects.submitPreview(context, camera, cells, CorpsColors.of(player), active != null ? DRILLING_PREVIEW_ALPHA : PREVIEW_ALPHA);
    }

    private static boolean isReady(LocalPlayer player) {
        return ArmedRingPower.isArmed(player)
                && ArmedRingPower.isArmed(player)
                && !ArmedRingPower.isAbilityMode(player)
                && PowerRingItem.hasCharge(player)
                && ArmedRingPower.selectedConstruct(player).orElse(null) instanceof DrillConstruct;
    }

    static void drill(Vec3 start, Vec3 tip, float time, ClientDrill drill, boolean held, List<ShieldEffects.Voxel> out) {
        Vec3 path = tip.subtract(start);
        double length = path.length();
        if (length < 1.0E-3) return;

        Vec3 direction = path.scale(1.0 / length);
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(reference).normalize();
        Vec3 up = direction.cross(side);

        int step = drill.size() - 1;
        float bitLength = held ? (float) length : (float) Math.min(BIT_LENGTH + BIT_LENGTH_PER_STEP * step, length * 0.85);
        float radius = BIT_RADIUS + BIT_RADIUS_PER_STEP * step;
        Vec3 base = tip.subtract(direction.scale(bitLength));
        double shaft = length - bitLength;
        int color = drill.color();
        float spin = time * SPIN;

        int coreColor = VoxelRenderer.toWhite(color, 0.75F);
        float coreHalf = VoxelRenderer.snapSize(CORE_HALF);
        for (double d = held ? length * 0.5 : 0.0; d <= length; d += CORE_SPACING) {
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(direction.scale(d))), coreHalf, coreColor));
        }

        float collarRadius = Math.min(radius, 0.12F + 0.08F * step);
        float collarHalf = VoxelRenderer.snapSize(BODY_HALF);
        for (double d = 0.0; d < shaft; d += BODY_SPACING * 3.0F) {
            for (int i = 0; i < COLLAR_VOXELS; i++) {
                float angle = spin * 0.6F + i * Mth.TWO_PI / COLLAR_VOXELS + (float) d * 2.0F;
                Vec3 offset = side.scale(Mth.cos(angle) * collarRadius * 0.5F).add(up.scale(Mth.sin(angle) * collarRadius * 0.5F));
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(start.add(direction.scale(d)).add(offset)), collarHalf, VoxelRenderer.toWhite(color, 0.2F)));
            }
        }

        float bodyHalf = VoxelRenderer.snapSize(BODY_HALF);
        for (float d = 0.0F; d <= bitLength; d += BODY_SPACING) {
            float t = d / bitLength;
            float r = radius * (1.0F - t);
            Vec3 center = base.add(direction.scale(d));
            int ring = Math.max(Mth.ceil(Mth.TWO_PI * r / (BODY_SPACING * 1.5F)), 4);
            for (int i = 0; i < ring; i++) {
                float angle = -spin + i * Mth.TWO_PI / ring;
                Vec3 offset = side.scale(Mth.cos(angle) * r).add(up.scale(Mth.sin(angle) * r));
                int tint = VoxelRenderer.toWhite(color, 0.05F + 0.15F * Mth.sin(angle * 3.0F + d * 6.0F));
                out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(offset)), bodyHalf, tint));
            }

            for (int strand = 0; strand < 2; strand++) {
                float angle = t * BIT_TURNS * Mth.TWO_PI - spin + strand * Mth.PI;
                float cos = Mth.cos(angle);
                float sin = Mth.sin(angle);
                float flightHalf = VoxelRenderer.snapSize(FLIGHT_HALF * (1.0F - 0.5F * t));
                for (float k = 0.35F; k <= 1.15F; k += 0.4F) {
                    Vec3 offset = side.scale(cos * r * k).add(up.scale(sin * r * k));
                    out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(center.add(offset)), flightHalf, VoxelRenderer.toWhite(color, 0.35F + 0.3F * k)));
                }
            }
        }
        out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(tip), VoxelRenderer.snapSize(FLIGHT_HALF), VoxelRenderer.toWhite(color, 0.9F)));
    }

    static void chips(ClientLevel level, BlockHitResult hit, Vec3 look, float time, ClientDrill drill, List<ShieldEffects.Voxel> out) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!DrillGeometry.drillable(level, hit.getBlockPos(), state)) return;

        int mapColor = state.getMapColor(level, hit.getBlockPos()).col;
        int chipColor = mapColor == 0 ? VoxelRenderer.toWhite(drill.color(), 0.5F) : ARGB.opaque(mapColor);
        int side = DrillGeometry.side(drill.size());
        RandomSource random = RandomSource.create(Mth.floor(time * 3.0F));
        float phase = time * 3.0F - Mth.floor(time * 3.0F);
        Vec3 point = hit.getLocation();
        Vec3 back = look.scale(-1.0);
        for (int i = 0; i < CHIP_VOXELS + side * 2; i++) {
            Vec3 spray = back.add(new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.6)).normalize();
            float distance = (0.1F + 0.25F * side * random.nextFloat()) * (0.4F + phase);
            float half = VoxelRenderer.snapSize(CHIP_HALF * (0.5F + random.nextFloat()) * (1.2F - phase));
            int tint = random.nextFloat() < 0.3F ? VoxelRenderer.toWhite(drill.color(), 0.4F) : chipColor;
            out.add(new ShieldEffects.Voxel(VoxelRenderer.snap(point.add(spray.scale(distance))), half, tint));
        }
    }

    private static final class DrillSound extends AbstractTickableSoundInstance {
        private final Player player;

        DrillSound(Player player) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.8F;
            this.pitch = 2.0F;
            this.x = player.getX();
            this.y = player.getEyeY();
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (this.player.isRemoved() || !isDrilling(this.player)) {
                this.stop();
                return;
            }
            this.x = this.player.getX();
            this.y = this.player.getEyeY();
            this.z = this.player.getZ();
        }
    }

    private DrillEffects() {}
}
