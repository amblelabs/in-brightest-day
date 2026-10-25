package dev.amble.client.flight;

import dev.amble.client.config.BrightestDayClientConfig;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.items.PowerRingItem;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.math.Vec3f;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import dev.amble.client.effects.VisualQuality;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import dev.amble.client.effects.ArmedPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class FlightAnimator {

    private static final float MAX_HOVER_LEAN = 30.0F;
    private static final float HOVER_LEAN_PER_SPEED = 30.0F;
    private static final float MAX_HEAD_PITCH = 50.0F;
    private static final float MAX_HEAD_YAW = 70.0F;
    public static final int TRANSITION_TICKS = 16;
    private static final float FLIGHT_BODY_ANGLE = 87.5F;
    private static final float LIMB_SWING_FORWARD = 25.0F;
    private static final float LIMB_SWING_SIDE = 30.0F;
    private static final float LIMB_SWING_TURN = 1.5F;
    private static final float LIMB_SWING_PITCH = 3.0F;
    private static final float MAX_LIMB_SWING = 35.0F;
    private static final float ARM_SWING_SCALE = 0.6F;
    private static final float MAX_ROLL = 55.0F;
    private static final float CAMERA_ROLL_SCALE = 0.6F;
    private static final float FIRST_PERSON_FOV_BOOST = 0.4F;
    private static final float THIRD_PERSON_FOV_BOOST = 0.9F;
    public static final float MAX_FOV_MODIFIER = 2.0F;
    private static final float DEFAULT_MAX_FOV = 140.0F;
    private static final double SONIC_BOOM_SPEED = 3.0;
    private static final float DIVE_PIVOT = 0.75F;
    private static final float DIVE_MODEL_CENTER = 0.94F;
    private static final float DIVE_BOX_CENTER = 0.3F;
    private static final double REMOTE_VELOCITY_SMOOTHING = 0.35;
    private static final int LANDING_GRACE_TICKS = 4;
    private static final int MIN_PHASE_TICKS = 8;
    public static final int ROLL_TICKS = FlightRingPower.ROLL_TICKS;
    private static final double LOCAL_VELOCITY_SMOOTHING = 0.6;
    private static final float TURN_SMOOTHING = 0.3F;
    private static final float HOVER_CAPE_LEAN = 70.0F;
    private static final float FLIGHT_CAPE_LEAN = 22.0F;
    private static final float HOVER_CAPE_FLAP = 20.0F;
    private static final float FLIGHT_CAPE_FLAP = 4.0F;
    private static final float CAPE_SIDE_SCALE = 0.5F;
    private static final float CAPE_FLUTTER = 3.0F;
    public static final float WIND_VERTICAL_SPEED = 0.25F;
    private static final double FALL_SPEED = -0.45;
    private static final int FALL_DELAY = 8;
    private static final float FALL_LAND_SPEED = 1.1F;
    private static final float DIVE_LAND_SPEED = 1.5F;
    private static final double LAND_MIN_DROP = 15.0;
    private static final float FULL_IMPACT = 2.5F;
    private static final int LAND_HOLD = 30;
    private static final int FACE_PLANT_HOLD = 50;
    private static final int LAND_MIN_TICKS = 6;
    private static final double LAND_MOVE_SPEED = 0.06;
    private static final int FACE_PLANT_ODDS = 100;
    private static final int LAND_DEBRIS = 20;
    private static final int LAND_PLUMES = 10;
    private static final double FALL_FULL_SPEED = 2.5;
    private static final float FALL_LEAN = 30.0F;
    private static final float FALL_MAX_LEAN = 28.0F;
    private static final float FALL_ROLL = 35.0F;
    private static final float FALL_MAX_ROLL = 38.0F;
    private static final float FALL_FLAIL_MIN = 2.0F;
    private static final float FALL_FLAIL = 10.0F;
    private static final float FALL_LOOK_SWING = 0.8F;
    private static final float FALL_SWING = 70.0F;
    private static final float FALL_HEAD_PITCH = 0.55F;
    private static final float FALL_MAX_HEAD_PITCH = 40.0F;

    public static final RenderStateDataKey<Vec3> DIVE_OFFSET = RenderStateDataKey.create(() -> "brightestday:dive_offset");

    private static final Map<Player, Motion> MOTIONS = new WeakHashMap<>();
    private static @Nullable FlightWindSoundInstance windSound;

    private enum Phase { NONE, HOVER, FLIGHT, FALL, LAND }

    private static final class Motion {
        float flight, oFlight;
        float pitch, oPitch;
        float roll, oRoll;
        float turn;
        float speed, oSpeed;
        double forward;
        float bodyPitch, oBodyPitch;
        float flightBlend, oFlightBlend;
        float limbX, oLimbX;
        float limbZ, oLimbZ;
        Phase phase = Phase.NONE;
        Vec3 velocity = Vec3.ZERO;
        int landingTicks;
        int phaseTicks;
        float spin, oSpin;
        int rollTicks;
        int rollDirection;
        float fall, oFall;
        int fallTicks;
        float look;
        float descent;
        float impact;
        double peakY = Double.NEGATIVE_INFINITY;
        int landTicks;
        int landHold;
        int riseTicks;
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FlightAnimator::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused()) return;

        for (AbstractClientPlayer player : client.level.players()) {
            update(client, player);
        }
    }

    private static void update(Minecraft client, AbstractClientPlayer player) {
        boolean airborne = FlightRingPower.isFlying(player);
        Vec3 step = player.position().subtract(player.xo, player.yo, player.zo);
        boolean dropping = !airborne && freeFalling(player, step) && PowerRingItem.getWornCorps(player).isPresent();
        Motion motion = MOTIONS.get(player);
        if (motion == null) {
            if (!airborne && !dropping) return;
            motion = new Motion();
            motion.peakY = player.getY();
            MOTIONS.put(player, motion);
        }

        motion.landingTicks = airborne ? 0 : motion.landingTicks + 1;
        double drop = motion.peakY - player.getY();
        boolean landed = player.onGround() && drop >= LAND_MIN_DROP && (motion.phase == Phase.FALL && motion.descent >= FALL_LAND_SPEED
                || (motion.phase == Phase.HOVER || motion.phase == Phase.FLIGHT) && motion.descent >= DIVE_LAND_SPEED);
        motion.peakY = player.onGround() ? player.getY() : Math.max(motion.peakY, player.getY());
        if (landed) motion.impact = motion.descent;
        motion.descent = player.onGround() ? 0.0F : (float) -step.y;
        boolean flying = !landed && (motion.phase == Phase.LAND
                ? airborne && !player.onGround()
                : airborne || FlightRingPower.canFly(player) && motion.phase != Phase.NONE && motion.landingTicks < LANDING_GRACE_TICKS);

        Vec3 rawVelocity = player.position().subtract(player.xo, player.yo, player.zo);
        motion.velocity = motion.velocity.lerp(rawVelocity, player == client.player ? LOCAL_VELOCITY_SMOOTHING : REMOTE_VELOCITY_SMOOTHING);
        Vec3 velocity = motion.velocity;
        double speed = velocity.length();
        float bodyYaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(bodyYaw), 0.0, Mth.cos(bodyYaw));
        Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0.0, -Mth.sin(bodyYaw));
        double forward = velocity.dot(facing);
        double side = velocity.dot(right);
        motion.turn += (Mth.wrapDegrees(player.yBodyRot - player.yBodyRotO) - motion.turn) * TURN_SMOOTHING;
        float turn = motion.turn;

        motion.oFlight = motion.flight;
        motion.oPitch = motion.pitch;
        motion.oRoll = motion.roll;
        motion.oSpeed = motion.speed;
        motion.oBodyPitch = motion.bodyPitch;
        motion.oFlightBlend = motion.flightBlend;
        motion.oLimbX = motion.limbX;
        motion.oLimbZ = motion.limbZ;
        motion.oSpin = motion.spin;
        tickRoll(motion);
        motion.oFall = motion.fall;
        motion.fallTicks = dropping ? motion.fallTicks + 1 : 0;
        boolean falling = motion.fallTicks >= FALL_DELAY;
        motion.fall += ((falling ? 1.0F : 0.0F) - motion.fall) * 0.15F;
        motion.look += (Mth.wrapDegrees(player.getYHeadRot() - player.yHeadRotO) - motion.look) * TURN_SMOOTHING;

        motion.speed = (float) speed;
        motion.forward = forward;
        motion.flight += ((flying ? 1.0F : 0.0F) - motion.flight) * 0.25F;

        float targetPitch = speed > 0.05
                ? (float) Math.toDegrees(Math.atan2(-velocity.y, Math.max(forward, 0.0) + 0.05))
                : 0.0F;
        motion.pitch += (targetPitch - motion.pitch) * 0.25F;

        float targetRoll = flying
                ? Mth.clamp((float) (side * 20.0 + turn * 2.0 * Math.min(speed, 2.0)), -MAX_ROLL, MAX_ROLL)
                : 0.0F;
        motion.roll += (targetRoll - motion.roll) * 0.2F;

        motion.flightBlend = Mth.approach(motion.flightBlend, motion.phase == Phase.FLIGHT ? 1.0F : 0.0F, 1.0F / TRANSITION_TICKS);
        float blend = ease(motion.flightBlend);

        float hoverLean = Mth.clamp((float) forward * HOVER_LEAN_PER_SPEED, -MAX_HOVER_LEAN, MAX_HOVER_LEAN);
        float targetBodyPitch = motion.phase == Phase.NONE || motion.phase == Phase.LAND ? 0.0F : Mth.lerp(blend, hoverLean, motion.pitch);
        motion.bodyPitch += (targetBodyPitch - motion.bodyPitch) * 0.2F;

        float pitchRate = motion.pitch - motion.oPitch;
        float targetLimbX = flying ? Mth.lerp(blend, (float) forward * LIMB_SWING_FORWARD, pitchRate * LIMB_SWING_PITCH) : 0.0F;
        float targetLimbZ = flying ? -((float) side * LIMB_SWING_SIDE + turn * LIMB_SWING_TURN) : 0.0F;
        motion.limbX += (Mth.clamp(targetLimbX, -MAX_LIMB_SWING, MAX_LIMB_SWING) - motion.limbX) * 0.12F;
        motion.limbZ += (Mth.clamp(targetLimbZ, -MAX_LIMB_SWING, MAX_LIMB_SWING) - motion.limbZ) * 0.12F;

        if (motion.riseTicks > 0 && motion.phase != Phase.LAND) motion.riseTicks--;
        updateAnimation(player, motion, flying, falling, landed);

        if (!flying && !dropping && motion.phase != Phase.LAND && motion.riseTicks == 0 && motion.flight < 0.001F && motion.fall < 0.001F) {
            MOTIONS.remove(player);
            return;
        }

        if (flying) {
            int color = CorpsColors.of(player);
            if (speed > SONIC_BOOM_SPEED && motion.oSpeed <= SONIC_BOOM_SPEED) sonicBoom(client.level, player, velocity, color);
        }

        if (player == client.player && flying && (motion.phase == Phase.FLIGHT || Math.abs(velocity.y) > WIND_VERTICAL_SPEED) && (windSound == null || windSound.isStopped())) {
            windSound = new FlightWindSoundInstance(client.player);
            client.getSoundManager().play(windSound);
        }
    }

    private static void tickRoll(Motion motion) {
        if (motion.rollTicks <= 0) {
            motion.spin = motion.oSpin = 0.0F;
            return;
        }
        motion.rollTicks--;
        float progress = 1.0F - motion.rollTicks / (float) ROLL_TICKS;
        motion.spin = FlightRingPower.rollCurve(progress) * 360.0F * motion.rollDirection;
    }

    public static boolean aileronRoll(Player player, boolean right) {
        Motion motion = MOTIONS.get(player);
        if (motion == null || motion.rollTicks > 0) return false;
        motion.rollTicks = ROLL_TICKS;
        motion.rollDirection = right ? 1 : -1;
        return true;
    }

    private static void sonicBoom(ClientLevel level, Player player, Vec3 velocity, int color) {
        Vec3 direction = velocity.normalize();
        Vec3 reference = Math.abs(direction.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 a = direction.cross(reference).normalize();
        Vec3 b = direction.cross(a);
        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(direction.scale(1.5));
        DustParticleOptions dust = new DustParticleOptions(color, 2.0F);

        int points = 32;
        for (int i = 0; i < points; i++) {
            float angle = i * Mth.TWO_PI / points;
            Vec3 offset = a.scale(Mth.cos(angle)).add(b.scale(Mth.sin(angle)));
            Vec3 pos = center.add(offset.scale(1.4));
            Vec3 push = offset.scale(0.25);
            level.addParticle(dust, pos.x, pos.y, pos.z, push.x, push.y, push.z);
        }

        level.playLocalSound(center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(),
                SoundSource.PLAYERS, 1.0F, 0.6F, false);
    }

    private static void updateAnimation(Player player, Motion motion, boolean flying, boolean falling, boolean landed) {
        PlayerAnimationController controller = FlightAnimations.controller(player);
        if (controller == null) return;

        if (landed) {
            land(player, controller, motion);
            return;
        }

        if (!flying) {
            if (motion.phase == Phase.LAND) {
                tickLanding(player, controller, motion);
                return;
            }
            if (falling) {
                if (motion.phase != Phase.FALL) {
                    FlightAnimations.fall(controller);
                    FlightAnimations.setSpeed(controller, 1.0F);
                    motion.phase = Phase.FALL;
                }
                return;
            }
            if (motion.phase != Phase.NONE) FlightAnimations.stop(controller);
            motion.phase = Phase.NONE;
            return;
        }

        if (motion.phaseTicks > 0) motion.phaseTicks--;
        switch (motion.phase) {
            case NONE, FALL, LAND -> enterPhase(controller, motion, Phase.HOVER, FlightAnimations.HOVER);
            case HOVER -> {
                if (motion.phaseTicks == 0 && diveSpeed(motion) > FlightRingPower.DIVE_ENTER_SPEED) {
                    enterPhase(controller, motion, Phase.FLIGHT, FlightAnimations.FLIGHT);
                }
            }
            case FLIGHT -> {
                if (motion.phaseTicks == 0 && diveSpeed(motion) < FlightRingPower.DIVE_EXIT_SPEED) {
                    enterPhase(controller, motion, Phase.HOVER, FlightAnimations.HOVER);
                }
            }
        }

        float intensity = Mth.clamp(motion.speed / (float) FlightRingPower.BOOST_SPEED, 0.0F, 1.0F);
        float hoverSpeed = 1.0F + Math.min(motion.speed, 1.2F) * 1.2F;
        float flightSpeed = 0.7F + intensity * 1.3F;
        FlightAnimations.setSpeed(controller, Mth.lerp(ease(motion.flightBlend), hoverSpeed, flightSpeed));
    }

    public static void slam(Player player) {
        PlayerAnimationController controller = FlightAnimations.controller(player);
        if (controller == null) return;
        Motion motion = MOTIONS.computeIfAbsent(player, key -> new Motion());
        motion.peakY = player.getY();
        motion.descent = 0.0F;
        land(player, controller, motion, false);
    }

    private static void land(Player player, PlayerAnimationController controller, Motion motion) {
        land(player, controller, motion, true);
    }

    private static void land(Player player, PlayerAnimationController controller, Motion motion, boolean natural) {
        long roll = scramble(player.getUUID().getLeastSignificantBits() ^ player.blockPosition().asLong() * 31L ^ player.level().getGameTime() / 10L);
        boolean facePlant = natural && Long.remainderUnsigned(roll, FACE_PLANT_ODDS) == 0;
        Identifier pose = facePlant ? FlightAnimations.FACE_PLANT : FlightAnimations.HERO_LANDINGS.get((int) Long.remainderUnsigned(roll >>> 16, FlightAnimations.HERO_LANDINGS.size()));
        FlightAnimations.land(controller, pose);
        FlightAnimations.setSpeed(controller, 1.0F);
        motion.phase = Phase.LAND;
        motion.landTicks = 0;
        motion.landHold = facePlant ? FACE_PLANT_HOLD : LAND_HOLD;
        if (natural) impact(player, Mth.clamp((motion.impact - FALL_LAND_SPEED) / (FULL_IMPACT - FALL_LAND_SPEED), 0.0F, 1.0F));
    }

    private static void impact(Player player, float strength) {
        Level level = player.level();
        Vec3 feet = player.position();
        level.playLocalSound(feet.x, feet.y, feet.z, strength > 0.5F ? SoundEvents.MACE_SMASH_GROUND_HEAVY : SoundEvents.MACE_SMASH_GROUND,
                SoundSource.PLAYERS, 0.7F + 0.5F * strength, 0.95F - 0.15F * strength, false);

        BlockState ground = level.getBlockState(player.getOnPos());
        RandomSource random = player.getRandom();
        if (ground.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            int count = VisualQuality.count(Math.round(LAND_DEBRIS + LAND_DEBRIS * strength));
            for (int i = 0; i < count; i++) {
                float angle = random.nextFloat() * Mth.TWO_PI;
                double push = 0.12 + random.nextDouble() * (0.18 + 0.2 * strength);
                double cos = Mth.cos(angle), sin = Mth.sin(angle);
                level.addParticle(debris, feet.x + cos * 0.4, feet.y + 0.05, feet.z + sin * 0.4, cos * push, 0.08 + random.nextDouble() * 0.15, sin * push);
            }
        }
        int plumes = VisualQuality.count(Math.round(LAND_PLUMES + LAND_PLUMES * strength));
        for (int i = 0; i < plumes; i++) {
            float angle = i * Mth.TWO_PI / plumes + random.nextFloat() * 0.3F;
            double push = 0.08 + 0.1 * strength;
            double cos = Mth.cos(angle), sin = Mth.sin(angle);
            level.addParticle(ParticleTypes.DUST_PLUME, feet.x + cos * 0.5, feet.y + 0.1, feet.z + sin * 0.5, cos * push, 0.02, sin * push);
        }
    }

    public static float landWeight(Player player, float partialTicks) {
        if (LaunchAnimations.active(player)) return 1.0F;
        Motion motion = MOTIONS.get(player);
        if (motion == null) return 0.0F;
        if (motion.phase == Phase.LAND) return ease(Mth.clamp((motion.landTicks + partialTicks) / FlightAnimations.LAND_FADE_TICKS, 0.0F, 1.0F));
        if (motion.riseTicks > 0) return ease(Mth.clamp((motion.riseTicks - partialTicks) / FlightAnimations.RISE_FADE_TICKS, 0.0F, 1.0F));
        return 0.0F;
    }

    public static void extractLanding(Avatar entity, AvatarRenderState state, float partialTicks) {
        if (!(entity instanceof Player player)) return;
        float weight = landWeight(player, partialTicks);
        if (weight <= 0.0F) return;
        float keep = 1.0F - weight;
        state.xRot *= keep;
        state.yRot *= keep;
        state.walkAnimationSpeed *= keep;
        state.swingAnimation *= keep;
        state.setData(ArmedPose.AMOUNT, state.getDataOrDefault(ArmedPose.AMOUNT, 0.0F) * keep);
        state.setData(DIVE_OFFSET, Vec3.ZERO);
        if (weight < 0.5F) return;
        state.isCrouching = false;
        state.pose = Pose.STANDING;
        state.isUsingItem = false;
        state.currentSwing = null;
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
    }

    private static long scramble(long seed) {
        long z = seed * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static void tickLanding(Player player, PlayerAnimationController controller, Motion motion) {
        motion.landTicks++;
        double moved = player.position().subtract(player.xo, player.yo, player.zo).horizontalDistance();
        boolean moving = moved > LAND_MOVE_SPEED || !player.onGround();
        if (motion.landTicks >= motion.landHold || motion.landTicks > LAND_MIN_TICKS && moving) {
            FlightAnimations.rise(controller);
            motion.riseTicks = FlightAnimations.RISE_FADE_TICKS;
            motion.phase = Phase.NONE;
        }
    }

    private static double diveSpeed(Motion motion) {
        return Math.max(motion.forward, -motion.velocity.y);
    }

    private static boolean freeFalling(Player player, Vec3 step) {
        return step.y < FALL_SPEED && !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isPassenger()
                && !player.isFallFlying() && !player.getAbilities().flying && !player.isSpectator() && !player.onClimbable();
    }

    private static void enterPhase(PlayerAnimationController controller, Motion motion, Phase phase, Identifier animation) {
        FlightAnimations.loop(controller, animation);
        motion.phase = phase;
        motion.phaseTicks = MIN_PHASE_TICKS;
    }

    public static Optional<AdjustmentModifier.PartModifier> adjustment(Avatar avatar, String bone) {
        if (!(avatar instanceof Player player)) return Optional.empty();

        Motion motion = MOTIONS.get(player);
        if (motion == null) return Optional.empty();
        if (motion.phase == Phase.LAND || motion.riseTicks > 0) return Optional.empty();

        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float fall = Mth.lerp(partialTicks, motion.oFall, motion.fall);
        float flight = Mth.lerp(partialTicks, motion.oFlight, motion.flight);
        if (fall > flight) return fallAdjustment(player, motion, bone, partialTicks, fall);
        float blend = ease(Mth.lerp(partialTicks, motion.oFlightBlend, motion.flightBlend)) * flight;
        float limbX = Mth.lerp(partialTicks, motion.oLimbX, motion.limbX) * flight;
        float limbZ = Mth.lerp(partialTicks, motion.oLimbZ, motion.limbZ) * flight;

        return switch (bone) {
            case "body" -> rotation(
                    Mth.lerp(partialTicks, motion.oBodyPitch, motion.bodyPitch) * flight,
                    0.0F,
                    -(Mth.lerp(partialTicks, motion.oRoll, motion.roll) + Mth.lerp(partialTicks, motion.oSpin, motion.spin)) * flight);
            case "head" -> {
                float bodyPitch = Mth.lerp(partialTicks, motion.oBodyPitch, motion.bodyPitch);
                float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
                float lookPitch = Mth.clamp(player.getViewXRot(partialTicks) - bodyPitch, -MAX_HEAD_PITCH, MAX_HEAD_PITCH);
                float lookYaw = Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partialTicks) - bodyYaw), -MAX_HEAD_YAW, MAX_HEAD_YAW);
                yield rotation(lookPitch * flight, lookYaw * flight, 0.0F);
            }
            case "right_arm", "left_arm" -> rotation(limbX * ARM_SWING_SCALE, 0.0F, limbZ * ARM_SWING_SCALE);
            case "right_leg", "left_leg" -> rotation(limbX, 0.0F, limbZ);
            default -> Optional.empty();
        };
    }

    private static Optional<AdjustmentModifier.PartModifier> fallAdjustment(Player player, Motion motion, String bone, float partialTicks, float fall) {
        Vec3 velocity = motion.velocity;
        float bodyYaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot);
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        double forward = velocity.x * -Mth.sin(yaw) + velocity.z * Mth.cos(yaw);
        double side = velocity.x * -Mth.cos(yaw) + velocity.z * -Mth.sin(yaw);
        float intensity = Mth.clamp((float) (-velocity.y / FALL_FULL_SPEED), 0.0F, 1.0F);
        float amp = FALL_FLAIL_MIN + FALL_FLAIL * intensity;
        float time = player.tickCount + partialTicks;
        float rate = 1.0F + 0.5F * intensity;
        float look = motion.look * FALL_LOOK_SWING;

        return switch (bone) {
            case "body" -> rotation(
                    Mth.clamp((float) forward * FALL_LEAN, -FALL_MAX_LEAN, FALL_MAX_LEAN) * fall,
                    0.0F,
                    (Mth.clamp((float) -side * FALL_ROLL + motion.turn * 1.2F, -FALL_MAX_ROLL, FALL_MAX_ROLL) + Mth.sin(time * 0.2F) * 2.0F * intensity) * fall);
            case "head" -> {
                float lookYaw = Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partialTicks) - bodyYaw), -MAX_HEAD_YAW, MAX_HEAD_YAW);
                float lookPitch = Mth.clamp(player.getViewXRot(partialTicks) * FALL_HEAD_PITCH, -FALL_MAX_HEAD_PITCH, FALL_MAX_HEAD_PITCH);
                yield rotation(lookPitch * fall, lookYaw * fall, -look * 0.5F * fall);
            }
            case "right_arm", "left_arm" -> {
                boolean right = bone.equals("right_arm");
                float phase = right ? 0.0F : Mth.PI;
                float sign = right ? 1.0F : -1.0F;
                boolean main = (player.getMainArm() == HumanoidArm.RIGHT) == right;
                float swing = main ? -Mth.sin(player.getSwingAnimation(partialTicks) * Mth.PI) * FALL_SWING : 0.0F;
                yield rotation(
                        (Mth.sin(time * 0.35F * rate + phase) * amp + swing) * fall,
                        0.0F,
                        (Mth.sin(time * 0.45F * rate + phase + 1.0F) * amp * 0.6F + sign * Mth.clamp(look, -15.0F, 15.0F)) * fall);
            }
            case "right_leg", "left_leg" -> {
                float phase = bone.equals("right_leg") ? 2.0F : 2.0F + Mth.PI;
                yield rotation(
                        Mth.sin(time * 0.3F * rate + phase) * amp * 0.8F * fall,
                        0.0F,
                        Mth.sin(time * 0.38F * rate + phase) * amp * 0.4F * fall);
            }
            default -> Optional.empty();
        };
    }

    private static float ease(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static Optional<AdjustmentModifier.PartModifier> rotation(float x, float y, float z) {
        return Optional.of(new AdjustmentModifier.PartModifier(
                new Vec3f(x * Mth.DEG_TO_RAD, y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD),
                Vec3f.ZERO
        ));
    }

    public static float bodyPitch(Player player, float partialTicks) {
        Motion motion = MOTIONS.get(player);
        if (motion == null) return 0.0F;

        float flight = Mth.lerp(partialTicks, motion.oFlight, motion.flight);
        float blend = ease(Mth.lerp(partialTicks, motion.oFlightBlend, motion.flightBlend));
        return (blend * FLIGHT_BODY_ANGLE + Mth.lerp(partialTicks, motion.oBodyPitch, motion.bodyPitch)) * flight;
    }

    public static void extractDive(Avatar entity, AvatarRenderState state, float partialTicks) {
        ((FabricRenderState) state).setData(DIVE_OFFSET, entity instanceof Player player ? diveOffset(player, partialTicks) : Vec3.ZERO);
    }

    public static void adjustCape(Avatar entity, AvatarRenderState state, float partialTicks) {
        if (!(entity instanceof Player player)) return;
        Motion motion = MOTIONS.get(player);
        if (motion == null) return;

        float flight = Mth.lerp(partialTicks, motion.oFlight, motion.flight);
        if (flight <= 0.0F) return;

        float blend = ease(Mth.lerp(partialTicks, motion.oFlightBlend, motion.flightBlend));
        float speed = Mth.lerp(partialTicks, motion.oSpeed, motion.speed);
        float maxLean = Mth.lerp(blend, HOVER_CAPE_LEAN, FLIGHT_CAPE_LEAN);
        float lean = maxLean * (1.0F - (float) Math.exp(-state.capeLean / maxLean));
        float time = player.tickCount + partialTicks;
        float flutter = Mth.sin(time * (0.8F + Math.min(speed, 3.0F) * 0.5F)) * Math.min(speed, 2.0F) * CAPE_FLUTTER;
        float flap = Mth.clamp(state.capeFlap, -6.0F, Mth.lerp(blend, HOVER_CAPE_FLAP, FLIGHT_CAPE_FLAP));

        state.capeLean = Mth.lerp(flight, state.capeLean, Math.max(lean + flutter, 0.0F));
        state.capeFlap = Mth.lerp(flight, state.capeFlap, flap);
        state.capeLean2 = Mth.lerp(flight, state.capeLean2, state.capeLean2 * CAPE_SIDE_SCALE);
    }

    public static Vec3 diveOffset(Player player, float partialTicks) {
        Motion motion = MOTIONS.get(player);
        if (motion == null) return Vec3.ZERO;

        float flight = Mth.lerp(partialTicks, motion.oFlight, motion.flight);
        float dive = ease(Mth.lerp(partialTicks, motion.oFlightBlend, motion.flightBlend)) * flight;
        float pitch = bodyPitch(player, partialTicks) * Mth.DEG_TO_RAD;
        float arm = DIVE_MODEL_CENTER - DIVE_PIVOT;
        return new Vec3(0.0, DIVE_BOX_CENTER - DIVE_PIVOT - arm * Mth.cos(pitch), arm * Mth.sin(pitch)).scale(dive);
    }

    public static Vec3 worldDiveOffset(Player player, float partialTicks) {
        Vec3 local = diveOffset(player, partialTicks);
        float yaw = Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        return new Vec3(0.0, local.y, 0.0).add(facing.scale(-local.z));
    }

    public static boolean phaseActive(Player player) {
        Motion motion = MOTIONS.get(player);
        return motion != null && motion.phase != Phase.NONE;
    }

    public static boolean isAnimating(Player player) {
        return MOTIONS.containsKey(player);
    }

    public static float flightBlend(Player player) {
        Motion motion = MOTIONS.get(player);
        return motion == null ? 0.0F : ease(motion.flightBlend);
    }

    public static float verticalSpeed(Player player) {
        Motion motion = MOTIONS.get(player);
        return motion == null ? 0.0F : (float) Math.abs(motion.velocity.y);
    }

    public static float speed(Player player) {
        Motion motion = MOTIONS.get(player);
        return motion == null ? 0.0F : motion.speed;
    }

    public static float maxFov() {
        BrightestDayClientConfig config = BrightestDayClientConfig.get();
        return config == null ? DEFAULT_MAX_FOV : config.maxFlightFov;
    }

    public static float fovBoost(Player player, boolean firstPerson) {
        Motion motion = MOTIONS.get(player);
        if (motion == null) return 0.0F;
        float speedFactor = Mth.clamp((motion.speed - 0.3F) / (float) (FlightRingPower.BOOST_SPEED - 0.3), 0.0F, 1.0F);
        return motion.flight * speedFactor * (firstPerson ? FIRST_PERSON_FOV_BOOST : THIRD_PERSON_FOV_BOOST);
    }

    public static float cameraRoll(Player player, float partialTicks) {
        Motion motion = MOTIONS.get(player);
        if (motion == null) return 0.0F;
        float bank = Mth.lerp(partialTicks, motion.oRoll, motion.roll) * CAMERA_ROLL_SCALE * Mth.lerp(partialTicks, motion.oFlight, motion.flight);
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) return bank;
        return bank + Mth.lerp(partialTicks, motion.oSpin, motion.spin);
    }

    private FlightAnimator() {}
}
