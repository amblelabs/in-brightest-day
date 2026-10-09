package dev.amble.client.effects;

import dev.amble.core.ringpowers.CorpsColors;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.amble.client.effects.attacks.area.BarrageEffects;
import dev.amble.client.effects.drill.DrillEffects;
import dev.amble.client.forge.ForgeClient;
import dev.amble.client.flight.FlightRenderTypes;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.sync.RingSync;
import dev.amble.core.networking.payloads.c2s.ChargeC2SPayload;
import dev.amble.core.networking.payloads.c2s.FireConstructC2SPayload;
import dev.amble.core.networking.payloads.c2s.StopBeamC2SPayload;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.networking.payloads.s2c.BlastS2CPayload;
import dev.amble.core.networking.payloads.s2c.ChargeS2CPayload;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class BlastEffects {
    private static final int LIFETIME = 12;
    private static final float TRAVEL_TICKS = 2.0F;
    private static final float BEAM_SPACING = 3.0F * VoxelRenderer.PIXEL;
    private static final float BEAM_VOXEL_SIZE = 4.0F * VoxelRenderer.PIXEL;
    private static final float BEAM_SPREAD = 0.35F;
    private static final int BURST_VOXELS = 40;
    private static final float BURST_VOXEL_SIZE = 5.0F * VoxelRenderer.PIXEL;
    private static final float BURST_SPEED = 0.45F;
    private static final float GLASS_ALPHA = 0.8F;
    private static final float GLOW_SCALE = 2.0F;
    private static final float GLOW_ALPHA = 0.35F;
    private static final int WHITE_HOT = 0xFFF4E8;

    private static final float SHOULDER_HEIGHT = 1.4F;
    private static final float SHOULDER_OFFSET = 0.35F;
    private static final float ARM_LENGTH = 0.7F;

    private static final int LONG_CHARGE_TICKS = 25;
    private static final int SUSTAIN_GRACE_TICKS = 10;
    private static final int REMOTE_CHARGE_GRACE = 40;
    private static final float CHARGE_SOUND_PITCH = 1.2F;
    private static final int CHARGE_VOXELS = 16;
    private static final float CHARGE_RADIUS = 1.2F;
    private static final float CHARGE_VOXEL_SIZE = 2.5F * VoxelRenderer.PIXEL;
    private static final float MAX_CHARGE_SHAKE = 1.2F;
    private static final float FIRE_SHAKE = 3.0F;
    private static final float SHAKE_DECAY = 0.75F;

    private static final List<Blast> BLASTS = new ArrayList<>();
    private static int cooldown;
    private static boolean sustaining;
    private static int sustainTicks;
    private static boolean awaitRelease;
    private static int charge;
    private static int chargeGoal = LONG_CHARGE_TICKS;
    private static int oCharge;
    private static float kick;
    private static float oKick;
    private static @Nullable SoundInstance chargeSound;
    private static boolean announced;
    private static final Map<Integer, RemoteCharge> REMOTE_CHARGES = new HashMap<>();
    private static @Nullable ClientLevel chargeLevel;

    private static final class RemoteCharge {
        final int goal;
        final SoundInstance sound;
        int age;

        RemoteCharge(int goal, SoundInstance sound) {
            this.goal = goal;
            this.sound = sound;
        }
    }

    private static final class Blast {
        final Vec3 start;
        final Vec3 control;
        final Vec3 end;
        final int color;
        final long seed;
        final float scale;
        int age;

        Blast(Vec3 start, Vec3 control, Vec3 end, int color, long seed, float scale) {
            this.scale = scale;
            this.start = start;
            this.control = control;
            this.end = end;
            this.color = color;
            this.seed = seed;
        }
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BlastS2CPayload.TYPE, (payload, context) -> spawn(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(ChargeS2CPayload.TYPE, (payload, context) -> receiveCharge(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> REMOTE_CHARGES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(BlastEffects::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(BlastEffects::render);
    }

    public static boolean wantsToCharge(LocalPlayer player) {
        return ArmedRingPower.isArmed(player)
                && !ArmedRingPower.isAbilityMode(player)
                && PowerRingItem.hasCharge(player)
                && ArmedRingPower.selectedConstruct(player).map(construct -> !construct.usesGesture()).orElse(false)
                && !ConstructClient.isLookingAtLantern()
                && !LanternBlockItem.isChargingLantern(player);
    }

    private static void tickCharge(Minecraft client) {
        LocalPlayer player = client.player;
        oCharge = charge;
        oKick = kick;
        kick *= SHAKE_DECAY;
        if (cooldown > 0) cooldown--;

        boolean keyDown = player != null && client.gui.screen() == null && client.options.keyUse.isDown();
        if (sustaining) {
            sustainTicks++;
            boolean serverStopped = sustainTicks > SUSTAIN_GRACE_TICKS && (player == null || !SculptClient.isSelected(player) && !BeamEffects.isBeaming(player) && !HealBeamEffects.isHealing(player) && !BarrageEffects.isFiring(player) && !DrillEffects.isDrilling(player) && !MegaphoneClient.isActive(player));
            if (!keyDown || serverStopped) {
                if (!serverStopped) ClientPlayNetworking.send(StopBeamC2SPayload.INSTANCE);
                sustaining = false;
                awaitRelease = keyDown;
            }
            return;
        }
        if (awaitRelease) {
            if (keyDown) {
                cancelCharge(client);
                return;
            }
            awaitRelease = false;
        }

        boolean holding = keyDown && !RingInput.blocked() && wantsToCharge(player);
        if (!holding && cooldown == 0 && player != null && releasable(player)) {
            if (chargeSound != null) client.getSoundManager().stop(chargeSound);
            release(player);
            return;
        }
        if (!holding || cooldown > 0) {
            cancelCharge(client);
            return;
        }

        if (charge == 0) chargeGoal = RingSync.chargeTicks(player, ArmedRingPower.selectedConstruct(player).map(ConstructRingPower::chargeTicks).orElse(LONG_CHARGE_TICKS));
        if (charge == 0 && chargeGoal > 1) {
            chargeSound = new EntityBoundSoundInstance(SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0F, CHARGE_SOUND_PITCH, player, player.getRandom().nextLong());
            client.getSoundManager().play(chargeSound);
            ClientPlayNetworking.send(new ChargeC2SPayload(true, chargeGoal));
            announced = true;
        }

        if (++charge >= chargeGoal) {
            if (chargeSound != null && chargeGoal < LONG_CHARGE_TICKS) client.getSoundManager().stop(chargeSound);
            release(player);
        }
    }

    private static boolean releasable(LocalPlayer player) {
        if (charge <= 0) return false;
        int release = ArmedRingPower.selectedConstruct(player).map(ConstructRingPower::releaseTicks).orElse(-1);
        return release > 0 && charge >= release;
    }

    private static void release(LocalPlayer player) {
        retractCharge();
        boolean sustained = ArmedRingPower.selectedConstruct(player).map(construct -> construct.sustained(player)).orElse(false);
        ClientPlayNetworking.send(new FireConstructC2SPayload(ConstructClient.selectedSize(player)));
        if (sustained) {
            sustaining = true;
            sustainTicks = 0;
        }
        charge = 0;
        oCharge = 0;
        chargeSound = null;
        cooldown = ArmedRingPower.COOLDOWN_TICKS;
        kick = chargeGoal > 0 ? 1.0F : 0.0F;
    }

    private static void cancelCharge(Minecraft client) {
        retractCharge();
        if (chargeSound != null) client.getSoundManager().stop(chargeSound);
        chargeSound = null;
        charge = 0;
    }

    private static void retractCharge() {
        if (!announced) return;
        announced = false;
        ClientPlayNetworking.send(new ChargeC2SPayload(false, 0));
    }

    private static void receiveCharge(Minecraft client, ChargeS2CPayload payload) {
        if (client.player != null && client.player.getId() == payload.playerId()) return;
        RemoteCharge previous = REMOTE_CHARGES.remove(payload.playerId());
        if (previous != null) client.getSoundManager().stop(previous.sound);
        if (!payload.charging() || client.level == null) return;

        Entity entity = client.level.getEntity(payload.playerId());
        if (!(entity instanceof Player player)) return;
        SoundInstance sound = new EntityBoundSoundInstance(SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.0F, CHARGE_SOUND_PITCH, player, player.getRandom().nextLong());
        client.getSoundManager().play(sound);
        REMOTE_CHARGES.put(payload.playerId(), new RemoteCharge(payload.ticks(), sound));
    }

    private static void tickRemoteCharges(Minecraft client) {
        if (client.level != chargeLevel) {
            REMOTE_CHARGES.clear();
            chargeLevel = client.level;
        }
        REMOTE_CHARGES.values().removeIf(remote -> {
            boolean expired = ++remote.age > remote.goal + REMOTE_CHARGE_GRACE;
            if (expired) client.getSoundManager().stop(remote.sound);
            return expired;
        });
    }

    public static boolean isSustaining() {
        return sustaining;
    }

    public static float firingAmount(float partialTicks) {
        float chargeProgress = Mth.lerp(partialTicks, oCharge, charge) / Math.max(chargeGoal, 1);
        float easedCharge = chargeProgress * chargeProgress * (3.0F - 2.0F * chargeProgress);
        float forge = ForgeClient.drawAmount();
        float beam = sustaining ? 1.0F : 0.0F;
        return Math.max(Math.max(easedCharge, Mth.lerp(partialTicks, oKick, kick)), Math.max(Math.max(TractorEffects.holdAmount(partialTicks), ScanEffects.scanAmount(partialTicks)), Math.max(forge, beam)));
    }

    public static float cameraShake(float partialTicks) {
        float chargeProgress = Mth.lerp(partialTicks, oCharge, charge) / Math.max(chargeGoal, 1);
        return chargeProgress * chargeProgress * MAX_CHARGE_SHAKE + Mth.lerp(partialTicks, oKick, kick) * FIRE_SHAKE;
    }

    private static void spawn(Minecraft client, BlastS2CPayload payload) {
        if (client.level == null) return;

        Entity shooter = client.level.getEntity(payload.shooterId());
        Vec3 end = payload.impact();
        Vec3 start = shooter instanceof Player player ? hand(player) : end;
        Vec3 control = shooter instanceof Player player
                ? start.add(player.getViewVector(1.0F).scale(start.distanceTo(end) * 0.5))
                : start.lerp(end, 0.5);
        BLASTS.add(new Blast(start, control, end, ARGB.opaque(payload.color()), client.level.getRandom().nextLong(), payload.scale()));
    }

    private static Vec3 hand(Player player) {
        return hand(player, 1.0F);
    }

    public static Vec3 hand(Player player, float partialTicks) {
        Vec3 posed = PoseAnimations.ringPosition(player, partialTicks);
        if (posed != null) return posed;
        float yaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        return player.getPosition(partialTicks)
                .add(0.0, SHOULDER_HEIGHT, 0.0)
                .add(right.scale(side * SHOULDER_OFFSET))
                .add(RemoteAim.look(player, partialTicks).scale(ARM_LENGTH));
    }

    private static void tick(Minecraft client) {
        if (client.isPaused()) return;
        tickCharge(client);
        tickRemoteCharges(client);
        Iterator<Blast> iterator = BLASTS.iterator();
        while (iterator.hasNext()) {
            Blast blast = iterator.next();
            if (++blast.age >= LIFETIME) iterator.remove();
        }
    }

    private static void render(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        PoseStack poseStack = context.poseStack();

        if (charge > 0 && client.player != null) renderCharge(context, client.player, camera, partialTicks, Mth.lerp(partialTicks, oCharge, charge) / Math.max(chargeGoal, 1));
        if (client.level != null) {
            for (Map.Entry<Integer, RemoteCharge> entry : REMOTE_CHARGES.entrySet()) {
                if (!(client.level.getEntity(entry.getKey()) instanceof Player player)) continue;
                RemoteCharge remote = entry.getValue();
                renderCharge(context, player, camera, partialTicks, Math.min((remote.age + partialTicks) / remote.goal, 1.0F));
            }
        }

        for (Blast blast : BLASTS) {
            float time = blast.age + partialTicks;
            float life = 1.0F - Mth.clamp(time / LIFETIME, 0.0F, 1.0F);
            if (life <= 0.0F) continue;

            List<Vec3> centers = new ArrayList<>();
            List<Float> halves = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            beam(blast, time, life, camera, centers, halves, colors);
            burst(blast, time, life, camera, centers, halves, colors);
            if (centers.isEmpty()) continue;

            context.submitNodeCollector().submitCustomGeometry(poseStack, FlightRenderTypes.GLOW,
                    (pose, buffer) -> draw(pose, buffer, centers, halves, colors, GLOW_SCALE, GLOW_ALPHA * life, false));
            context.submitNodeCollector().submitCustomGeometry(poseStack, FlightRenderTypes.glass(),
                    (pose, buffer) -> draw(pose, buffer, centers, halves, colors, 1.0F, GLASS_ALPHA * life, true));
        }
    }

    private static void renderCharge(LevelRenderContext context, Player player, Vec3 camera, float partialTicks, float progress) {
        int color = ARGB.opaque(CorpsColors.of(player));
        int tint = VoxelRenderer.toWhite(color, progress * 0.6F);
        Vec3 hand = hand(player, partialTicks);
        float time = player.tickCount + partialTicks;

        List<Vec3> centers = new ArrayList<>();
        List<Float> halves = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        RandomSource random = RandomSource.create(player.getId());
        for (int i = 0; i < CHARGE_VOXELS; i++) {
            Vec3 axis = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            Vec3 side = axis.cross(new Vec3(0.0, 1.0, 0.0)).lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : axis.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
            Vec3 up = axis.cross(side);
            float angle = time * (0.3F + random.nextFloat() * 0.3F) + random.nextFloat() * Mth.TWO_PI;
            float radius = CHARGE_RADIUS * (1.0F - progress) + 0.1F;
            Vec3 offset = side.scale(Mth.cos(angle) * radius).add(up.scale(Mth.sin(angle) * radius));
            centers.add(VoxelRenderer.snap(hand.add(offset)).subtract(camera));
            halves.add(VoxelRenderer.snapSize(CHARGE_VOXEL_SIZE * (0.5F + progress) * 0.5F));
            colors.add(tint);
        }
        centers.add(VoxelRenderer.snap(hand).subtract(camera));
        halves.add(VoxelRenderer.snapSize(CHARGE_VOXEL_SIZE * 2.0F * progress * 0.5F));
        colors.add(VoxelRenderer.toWhite(color, 0.5F + progress * 0.5F));

        float alpha = 0.4F + progress * 0.5F;
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.GLOW,
                (pose, buffer) -> draw(pose, buffer, centers, halves, colors, GLOW_SCALE, GLOW_ALPHA * alpha, false));
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), FlightRenderTypes.glass(),
                (pose, buffer) -> draw(pose, buffer, centers, halves, colors, 1.0F, GLASS_ALPHA * alpha, true));
    }

    private static void beam(Blast blast, float time, float life, Vec3 camera, List<Vec3> centers, List<Float> halves, List<Integer> colors) {
        RandomSource random = RandomSource.create(blast.seed);
        float reach = Math.min(time / TRAVEL_TICKS, 1.0F);
        float length = (float) blast.start.distanceTo(blast.end);
        int count = Math.max(Mth.floor(length / BEAM_SPACING), 1);
        float spread = BEAM_SPREAD * (1.0F - life);
        int tint = VoxelRenderer.toWhite(blast.color, 1.0F - life);

        for (int i = 0; i <= count; i++) {
            float t = (float) i / count;
            Vec3 jitter = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(spread);
            if (t > reach) continue;

            float half = BEAM_VOXEL_SIZE * life * (1.0F - 0.4F * t) * 0.5F;
            if (half < VoxelRenderer.PIXEL * 0.25F) continue;

            Vec3 point = blast.start.lerp(blast.control, t).lerp(blast.control.lerp(blast.end, t), t);
            centers.add(VoxelRenderer.snap(point.add(jitter)).subtract(camera));
            halves.add(VoxelRenderer.snapSize(half));
            colors.add(tint);
        }
    }

    private static void burst(Blast blast, float time, float life, Vec3 camera, List<Vec3> centers, List<Float> halves, List<Integer> colors) {
        float burstTime = time - TRAVEL_TICKS;
        if (burstTime < 0.0F) return;

        RandomSource random = RandomSource.create(blast.seed ^ 0x5DEECE66DL);
        float heat = Mth.clamp(burstTime / 3.0F, 0.0F, 1.0F);
        int tint = VoxelRenderer.toWhite(ARGB.srgbLerp(heat, ARGB.opaque(WHITE_HOT), blast.color), 1.0F - life);

        int voxels = Math.round(BURST_VOXELS * blast.scale);
        for (int i = 0; i < voxels; i++) {
            Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
            float speed = BURST_SPEED * blast.scale * (0.4F + random.nextFloat() * 0.6F);
            float distance = speed * burstTime * (1.0F - 0.03F * burstTime);
            float half = BURST_VOXEL_SIZE * life * (0.6F + random.nextFloat() * 0.4F) * 0.5F;
            if (half < VoxelRenderer.PIXEL * 0.25F) continue;

            centers.add(VoxelRenderer.snap(blast.end.add(direction.scale(distance))).subtract(camera));
            halves.add(VoxelRenderer.snapSize(half));
            colors.add(tint);
        }
    }

    private static void draw(PoseStack.Pose pose, VertexConsumer buffer, List<Vec3> centers, List<Float> halves, List<Integer> colors,
                             float scale, float alpha, boolean shaded) {
        int alphaByte = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255);
        for (int i = 0; i < centers.size(); i++) {
            Vec3 center = centers.get(i);
            VoxelRenderer.cube(pose, buffer, center, halves.get(i) * scale, VoxelRenderer.nearFade(center, ARGB.color(alphaByte, colors.get(i))), shaded);
        }
    }

    private BlastEffects() {}
}
