package dev.amble.core.ringpowers.impl;

import dev.amble.config.BrightestDayConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.space.Cosmos;
import dev.amble.core.space.SpaceWarp;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.flight.FlightBoost;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.OrangeHoard;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class FlightRingPower extends RingPower<FlightRingPower.Data> {
    public static Consumer<Boolean> compactSender = compact -> {};

    public static final double CRUISE_SPEED = 1.2;
    public static final double BOOST_SPEED = 3.5;
    private static final double[] SPEED_STEPS = {0.2, 0.35, 0.5, 0.65, 0.9, 1.2, 1.6, 2.2, 3.0, 4.0};
    public static final int SPEED_LEVELS = SPEED_STEPS.length;
    public static final int DEFAULT_SPEED_LEVEL = 5;
    private static final double CRUISE_RESPONSE = 0.15;
    private static final double BOOST_RESPONSE = 0.1;
    private static final double BRAKE_RESPONSE = 0.12;
    private static final double DRAIN_SPEED_WEIGHT = 0.25;
    public static final double DIVE_ENTER_SPEED = 0.8;
    public static final double DIVE_EXIT_SPEED = 0.7;
    public static final int ROLL_TICKS = 13;

    private record Dodge(int direction, int start, double done) {}

    private static final Map<Player, Dodge> DODGES = new WeakHashMap<>();

    public record Data(boolean enabled, int speedLevel) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("enabled", false).forGetter(Data::enabled),
                Codec.INT.optionalFieldOf("speed_level", DEFAULT_SPEED_LEVEL).forGetter(Data::speedLevel)
        ).apply(instance, Data::new));
    }

    public FlightRingPower() {
        super(BrightestDay.id("flight"), EnumSet.allOf(LanternCorps.class), Data.CODEC);
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.MOVEMENT;
    }

    @Override
    public Data createData() {
        return new Data(false, DEFAULT_SPEED_LEVEL);
    }

    @Override
    public boolean run(ServerPlayer player, Data data) {
        setEnabled(player, !data.enabled());
        return true;
    }

    @Override
    public void tick(ServerPlayer player, Data data) {
        if (isFlying(player)) player.resetFallDistance();
    }

    @Override
    public int drainPerSecond(ServerPlayer player, Data data) {
        if (!isFlying(player)) return 0;
        double scale = 1.0 - DRAIN_SPEED_WEIGHT + DRAIN_SPEED_WEIGHT * speedForLevel(data.speedLevel()) / CRUISE_SPEED;
        double drain = (isBoosting(player) ? BrightestDayConfig.get().flightBoostDrainPerSecond : BrightestDayConfig.get().flightDrainPerSecond) * scale;
        int whole = Mth.floor(drain);
        return whole + (player.getRandom().nextDouble() < drain - whole ? 1 : 0);
    }

    @Override
    public void onDepleted(ServerPlayer player, Data data) {
        setEnabled(player, false);
    }

    public static boolean hasFlight(Player player) {
        return BrightestDayAttachments.has(player, RingPowerRegistry.FLIGHT);
    }

    public static boolean canFly(Player player) {
        return BrightestDayAttachments.get(player, RingPowerRegistry.FLIGHT)
                .map(instance -> instance.data().enabled())
                .orElse(false);
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (!hasFlight(player)) return;
        if (enabled && !PowerRingItem.hasCharge(player)) return;

        BrightestDayAttachments.setData(player, RingPowerRegistry.FLIGHT, new Data(enabled, speedLevel(player)));
        if (enabled && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    public static int speedLevel(Player player) {
        return BrightestDayAttachments.get(player, RingPowerRegistry.FLIGHT)
                .map(instance -> Mth.clamp(instance.data().speedLevel(), 0, SPEED_LEVELS - 1))
                .orElse(DEFAULT_SPEED_LEVEL);
    }

    public static void setSpeedLevel(Player player, int level) {
        if (!hasFlight(player)) return;
        BrightestDayAttachments.setData(player, RingPowerRegistry.FLIGHT, new Data(canFly(player), Mth.clamp(level, 0, SPEED_LEVELS - 1)));
    }

    public static double speedForLevel(int level) {
        return SPEED_STEPS[Mth.clamp(level, 0, SPEED_LEVELS - 1)];
    }

    public static double cruiseSpeed(Player player) {
        return speedForLevel(speedLevel(player));
    }

    public static double boostSpeed(double cruise) {
        double top = SPEED_STEPS[SPEED_LEVELS - 1];
        return Math.min(top + cruise * (BrightestDayConfig.get().flightBoostMultiplier - 1.0), Math.max(top, BrightestDayConfig.get().flightMaxBoostSpeed));
    }

    public static boolean isBoosting(Player player) {
        return FlightBoost.isBoosting(player);
    }

    public static boolean isFlying(Player player) {
        return canFly(player)
                && !player.onGround()
                && !player.isPassenger()
                && !player.isSpectator()
                && !player.isSleeping()
                && !player.isFallFlying()
                && !player.getAbilities().flying;
    }

    public static boolean isDiving(Player player, boolean wasDiving) {
        if (!isFlying(player)) return false;
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        double forward = player.position().subtract(player.xo, player.yo, player.zo).dot(facing);
        return forward > (wasDiving ? DIVE_EXIT_SPEED : DIVE_ENTER_SPEED);
    }

    public static boolean fitsStanding(Player player) {
        AABB box = EntityDimensions.scalable(0.6F, 1.8F).scale(player.getScale()).makeBoundingBox(player.position());
        return player.level().noCollision(player, box.deflate(1.0E-7));
    }

    public static void travel(Player player, Vec3 input, boolean ascending) {
        boolean boosting = isBoosting(player);
        double cruise = cruiseSpeed(player);
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0, Mth.sin(yaw));
        double vertical = (ascending ? 1.0 : 0.0) - (player.isShiftKeyDown() ? 1.0 : 0.0);

        Vec3 wish = player.getLookAngle().scale(input.z)
                .add(left.scale(input.x))
                .add(0.0, vertical, 0.0);

        Vec3 target;
        double response;
        if (wish.lengthSqr() < 1.0E-4) {
            target = Vec3.ZERO;
            response = BRAKE_RESPONSE;
        } else {
            double speed = boosting ? boostSpeed(cruise) : cruise;
            target = wish.normalize().scale(SpaceWarp.limit(player.level(), player.position(), Cosmos.knownMogo, speed) * (1.0F - OrangeHoard.slowdown(player)));
            response = boosting ? BOOST_RESPONSE : CRUISE_RESPONSE;
        }

        Vec3 velocity = player.getDeltaMovement();
        velocity = velocity.add(target.subtract(velocity).scale(response));
        double ceiling = SpaceWarp.limit(player.level(), player.position(), Cosmos.knownMogo, Math.max(cruise, boostSpeed(cruise)));
        if (Cosmos.isSector(player.level().dimension()) && velocity.length() > ceiling) velocity = velocity.normalize().scale(ceiling);

        player.setDeltaMovement(velocity);
        player.move(MoverType.SELF, velocity);
        dodge(player, left);
    }

    public static void startDodge(Player player, boolean right) {
        DODGES.put(player, new Dodge(right ? -1 : 1, player.tickCount, 0.0));
    }

    private static void dodge(Player player, Vec3 left) {
        Dodge dodge = DODGES.get(player);
        if (dodge == null) return;
        float progress = Math.min(1.0F, (player.tickCount - dodge.start()) / (float) ROLL_TICKS);
        double done = rollCurve(progress);
        double step = (done - dodge.done()) * BrightestDayConfig.get().aileronRollDistance;
        if (progress >= 1.0F) DODGES.remove(player);
        else DODGES.put(player, new Dodge(dodge.direction(), dodge.start(), done));
        if (step > 0.0) player.move(MoverType.SELF, left.scale(step * dodge.direction()));
    }

    public static float rollCurve(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    public static void registerEvents() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player player && source.is(DamageTypeTags.IS_FALL) && hasFlight(player)));
    }
}
