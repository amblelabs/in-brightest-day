package dev.amble.client.effects;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.keyframe.Keyframe;
import com.zigythebird.playeranimcore.animation.keyframe.KeyframeStack;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.Axis;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.math.Vec3f;
import dev.amble.BrightestDay;
import dev.amble.client.render.Holograms;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.PoseC2SPayload;
import dev.amble.core.networking.payloads.s2c.PoseS2CPayload;
import dev.amble.client.poses.PoseLibrary;
import dev.amble.core.poses.Poses;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

public final class PoseAnimations {
    public static final Identifier LAYER = BrightestDay.id("poses");
    private static final int PRIORITY = 1550;
    private static final int FADE_TICKS = 6;
    private static final float MAX_HEAD_YAW = 70.0F;
    private static final float MODEL_SCALE = 0.9375F;
    private static final float MODEL_TOP = 1.501F;
    private static final float SHOULDER_X = 5.0F;
    private static final float SHOULDER_Y = 2.0F;
    private static final float HAND_X = 1.0F;
    private static final float HAND_Y = 10.0F;

    private static final Map<Integer, Integer> POSES = new HashMap<>();
    private static final Map<Avatar, String> PLAYING = new WeakHashMap<>();
    private static final Map<Avatar, Boolean> FREE_HEAD = new WeakHashMap<>();
    private static final Map<Player, Boolean> AIRBORNE = new WeakHashMap<>();
    private static final Map<Player, Integer> AIRBORNE_PENDING = new WeakHashMap<>();
    private static final int AIRBORNE_SETTLE_TICKS = 8;

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, setter) -> PlayState.STOP);
            controller.addModifierLast(new AdjustmentModifier(bone -> head(avatar, bone)));
            controller.addModifierLast(new MirrorModifier());
            return controller;
        });
        ClientPlayNetworking.registerGlobalReceiver(PoseS2CPayload.TYPE, (payload, context) -> {
            if (payload.pose() <= 0) POSES.remove(payload.playerId());
            else POSES.put(payload.playerId(), payload.pose());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> POSES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(PoseAnimations::tick);
    }

    public static int pose(Player player) {
        return POSES.getOrDefault(player.getId(), 0);
    }

    public static boolean glowing(Player player) {
        int pose = pose(player);
        return pose > 0 && pose % 2 == 0;
    }

    public static @Nullable Vec3 ringPosition(Player player, float partialTicks) {
        if (!posing(player)) return null;
        PlayerAnimationController controller = controller(player);
        if (controller == null || !controller.isActive()) return null;

        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        float side = left ? 1.0F : -1.0F;
        PlayerAnimBone arm = new PlayerAnimBone(left ? "left_arm" : "right_arm");
        arm.setToInitialPose();
        controller.get3DTransform(arm);

        Vector3f hand = new Vector3f(side * HAND_X, HAND_Y, 0.0F).rotate(new Quaternionf().rotationZYX(arm.rotation.z, arm.rotation.y, arm.rotation.x));
        Vector3f model = new Vector3f(side * SHOULDER_X + arm.position.x, SHOULDER_Y - arm.position.y, arm.position.z).add(hand);
        double x = -model.x * MODEL_SCALE / 16.0;
        double y = MODEL_TOP * MODEL_SCALE - model.y * MODEL_SCALE / 16.0;
        double z = model.z * MODEL_SCALE / 16.0;
        float yaw = (180.0F - Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot)) * Mth.DEG_TO_RAD;
        return player.getPosition(partialTicks).add(x * Mth.cos(yaw) + z * Mth.sin(yaw), y, -x * Mth.sin(yaw) + z * Mth.cos(yaw));
    }

    public static boolean posing(Entity player) {
        return PLAYING.containsKey(player);
    }

    public static void cycle(LocalPlayer player) {
        if (PowerRingItem.getWornCorps(player).isEmpty() || ArmedRingPower.isArmed(player)) return;
        int states = Math.min(poses(player, airborne(player)).size() * 2, Poses.MAX_STATE);
        if (states == 0) return;
        ClientPlayNetworking.send(new PoseC2SPayload(pose(player) % states + 1));
    }

    public static List<PoseLibrary.Pose> available(LocalPlayer player) {
        if (PowerRingItem.getWornCorps(player).isEmpty() || ArmedRingPower.isArmed(player)) return List.of();
        List<PoseLibrary.Pose> poses = poses(player, airborne(player));
        return poses.subList(0, Math.min(poses.size(), Poses.MAX_STATE / 2));
    }

    public static int struck(Player player) {
        int pose = pose(player);
        return pose > 0 ? (pose - 1) / 2 : -1;
    }

    public static void strike(LocalPlayer player, int index) {
        if (index < 0 || index >= available(player).size()) return;
        int base = index * 2 + 1;
        int pose = pose(player);
        ClientPlayNetworking.send(new PoseC2SPayload(struck(player) == index && pose == base ? base + 1 : base));
    }

    public static List<PoseLibrary.Pose> mannequinPoses(@Nullable LanternCorps corps) {
        Map<String, PoseLibrary.Pose> poses = new LinkedHashMap<>();
        for (PoseLibrary.Pose pose : PoseLibrary.poses(corps, false)) poses.putIfAbsent(pose.key(), pose);
        for (PoseLibrary.Pose pose : PoseLibrary.poses(corps, true)) poses.putIfAbsent(pose.key(), pose);
        return List.copyOf(poses.values());
    }

    public static List<PoseLibrary.Pose> poses(Player player, boolean hover) {
        return PoseLibrary.poses(PowerRingItem.getWornCorps(player).orElse(null), hover);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused()) return;
        LocalPlayer local = client.player;
        if (local != null && pose(local) > 0 && (moving(local.input.keyPresses) || ArmedRingPower.isArmed(local))) {
            POSES.remove(local.getId());
            ClientPlayNetworking.send(new PoseC2SPayload(0));
        }

        for (AbstractClientPlayer player : client.level.players()) {
            int state = pose(player);
            settle(player, state > 0);
            List<PoseLibrary.Pose> available = state > 0 ? poses(player, airborne(player)) : List.of();
            play(player, available.isEmpty() ? null : available.get((state - 1) / 2 % available.size()));
        }

        for (Mannequin mannequin : Holograms.all(client.level)) {
            int state = Holograms.of(mannequin).settings().pose();
            List<PoseLibrary.Pose> available = state > 0 ? mannequinPoses(Holograms.corps(mannequin).orElse(null)) : List.of();
            play(mannequin, available.isEmpty() ? null : available.get((state - 1) % available.size()));
        }
    }

    private static void play(Avatar avatar, PoseLibrary.@Nullable Pose desired) {
        String current = PLAYING.get(avatar);
        PlayerAnimationController controller = controller(avatar);
        if (desired == null ? current == null : desired.key().equals(current)) {
            if (desired != null && controller != null) mirror(controller, avatar);
            return;
        }
        if (controller == null) return;
        if (desired == null) {
            PLAYING.remove(avatar);
            FREE_HEAD.remove(avatar);
            controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), (RawAnimation) null);
            controller.stop();
            return;
        }
        Animation animation = desired.animation();
        PLAYING.put(avatar, desired.key());
        FREE_HEAD.put(avatar, animation.getBoneOptional("head").map(bone -> still(bone.rotationKeyFrames())).orElse(true));
        mirror(controller, avatar);
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_TICKS, EasingType.EASE_IN_OUT_SINE), RawAnimation.begin().thenLoop(animation));
    }

    private static void mirror(PlayerAnimationController controller, Avatar avatar) {
        for (AbstractModifier modifier : controller.getModifiers()) {
            if (modifier instanceof MirrorModifier mirror) mirror.enabled = avatar.getMainArm() == HumanoidArm.LEFT;
        }
    }

    private static boolean still(KeyframeStack rotation) {
        for (Axis axis : Axis.values()) {
            Set<String> values = new HashSet<>();
            for (Keyframe keyframe : rotation.getKeyFramesForAxis(axis)) {
                values.add(String.valueOf(keyframe.startValue()));
                values.add(String.valueOf(keyframe.endValue()));
            }
            if (values.size() > 1) return false;
        }
        return true;
    }

    private static boolean airborne(Player player) {
        return AIRBORNE.getOrDefault(player, FlightRingPower.isFlying(player));
    }

    private static void settle(Player player, boolean posing) {
        boolean raw = FlightRingPower.isFlying(player);
        Boolean stable = AIRBORNE.get(player);
        if (!posing || stable == null) {
            AIRBORNE.put(player, raw);
            AIRBORNE_PENDING.remove(player);
            return;
        }
        if (raw == stable) {
            AIRBORNE_PENDING.remove(player);
            return;
        }
        int pending = AIRBORNE_PENDING.getOrDefault(player, 0) + 1;
        if (pending >= AIRBORNE_SETTLE_TICKS) {
            AIRBORNE.put(player, raw);
            AIRBORNE_PENDING.remove(player);
        } else {
            AIRBORNE_PENDING.put(player, pending);
        }
    }

    private static boolean moving(Input input) {
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || input.shift();
    }

    private static @Nullable PlayerAnimationController controller(Avatar player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller ? controller : null;
    }

    private static Optional<AdjustmentModifier.PartModifier> head(Avatar avatar, String bone) {
        if (!"head".equals(bone) || !(avatar instanceof Player player) || !FREE_HEAD.getOrDefault(avatar, false)) return Optional.empty();
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
        float yaw = Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partialTicks) - Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot)), -MAX_HEAD_YAW, MAX_HEAD_YAW);
        return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(pitch * Mth.DEG_TO_RAD, yaw * Mth.DEG_TO_RAD, 0.0F), Vec3f.ZERO));
    }

    private PoseAnimations() {}
}
