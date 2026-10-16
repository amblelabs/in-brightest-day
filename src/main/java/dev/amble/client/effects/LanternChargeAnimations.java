package dev.amble.client.effects;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.keyframe.BoneAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.math.Vec3f;
import dev.amble.BrightestDay;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.networking.payloads.s2c.LanternRitualS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class LanternChargeAnimations {
    public static final Identifier LAYER = BrightestDay.id("handheld_charge");
    private static final Identifier START = BrightestDay.id("handheld_charge");
    private static final Identifier LOOP = BrightestDay.id("handheld_charge_loop");
    private static final Identifier DONE = BrightestDay.id("handheld_charge_done");
    private static final Identifier FLOOR_START = BrightestDay.id("charge_ring_floor_start");
    private static final Identifier FLOOR_LOOP = BrightestDay.id("charge_ring_floor_loop");
    private static final Identifier FLOOR_FINISH = BrightestDay.id("charge_ring_floor_finish");
    private static final Identifier TOP_START = BrightestDay.id("charge_ring_top_start");
    private static final Identifier TOP_LOOP = BrightestDay.id("charge_ring_top_loop");
    private static final Identifier TOP_FINISH = BrightestDay.id("charge_ring_top_finish");
    private static final int PRIORITY = 1600;
    private static final int FADE_IN_TICKS = 4;
    private static final int FADE_OUT_TICKS = 6;
    private static final int LOOP_BLEND_TICKS = 6;
    private static final int RITUAL_FADE_TICKS = 8;
    private static final float MODEL_SCALE = 0.9375F;
    private static final double FINISH_DRIFT = 0.3;
    private static final float MAX_HEAD_YAW = 70.0F;

    private static final Map<String, Vec3f> RIG_PIVOTS = Map.of("waist", new Vec3f(0.0F, 12.0F, 0.0F));
    private static final Map<String, String> RIG_PARENTS = Map.of(
            "head", "waist",
            "torso", "waist",
            "right_arm", "torso",
            "left_arm", "torso");

    private enum Mode {
        NONE,
        HANDHELD,
        FLOOR,
        TOP,
        FLOOR_FINISH,
        TOP_FINISH
    }

    private static final Map<Player, Mode> MODES = new WeakHashMap<>();
    private static final Map<Player, Integer> LOOP_IN = new WeakHashMap<>();
    private static final Map<Player, Identifier> LOOP_TARGET = new WeakHashMap<>();
    private static final Map<Player, Boolean> FREE_HEAD = new WeakHashMap<>();
    private record Ritual(int mode, float yaw, @Nullable Vec3 finishingAt) {}

    private static final Map<Integer, Ritual> RITUALS = new HashMap<>();
    private static boolean bareTransform;

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, setter) -> PlayState.STOP);
            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            controller.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
            controller.addModifierLast(new SafeAdjustmentModifier(bone -> head(avatar, bone)));
            controller.addModifierLast(new MirrorModifier());
            return controller;
        });
        ClientPlayNetworking.registerGlobalReceiver(LanternRitualS2CPayload.TYPE, (payload, context) -> {
            if (payload.mode() == LanternRitualS2CPayload.NONE) {
                RITUALS.remove(payload.playerId());
            } else if (payload.mode() == LanternRitualS2CPayload.COMPLETE) {
                Ritual ritual = RITUALS.get(payload.playerId());
                Entity entity = context.client().level != null ? context.client().level.getEntity(payload.playerId()) : null;
                if (ritual != null && entity != null) RITUALS.put(payload.playerId(), new Ritual(ritual.mode(), ritual.yaw(), entity.position()));
                else RITUALS.remove(payload.playerId());
            } else {
                RITUALS.put(payload.playerId(), new Ritual(payload.mode(), payload.yaw(), null));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RITUALS.clear());
        ClientTickEvents.END_CLIENT_TICK.register(LanternChargeAnimations::tick);
    }

    public static boolean bareTransform() {
        return bareTransform;
    }

    public static void setBareTransform(boolean bare) {
        bareTransform = bare;
    }

    public static boolean playing(Player player) {
        PlayerAnimationController controller = controller(player);
        return controller != null && controller.isActive();
    }

    public static boolean handheld(Player player) {
        Mode mode = MODES.get(player);
        return playing(player) && (mode == Mode.HANDHELD || mode == null && charging(player));
    }

    private static boolean charging(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof LanternBlockItem;
    }

    private static Mode desired(Player player) {
        if (charging(player)) return Mode.HANDHELD;
        Ritual ritual = RITUALS.get(player.getId());
        if (ritual == null) return Mode.NONE;
        boolean top = ritual.mode() == LanternRitualS2CPayload.TOP;
        if (ritual.finishingAt() != null) return top ? Mode.TOP_FINISH : Mode.FLOOR_FINISH;
        return top ? Mode.TOP : Mode.FLOOR;
    }

    private static @Nullable PlayerAnimationController controller(Player player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller ? controller : null;
    }

    private static @Nullable Animation animation(Identifier id) {
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) return null;
        if (animation.bones().isEmpty()) animation.bones().putAll(RIG_PIVOTS);
        if (animation.parents().isEmpty()) animation.parents().putAll(RIG_PARENTS);
        return animation;
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused()) return;

        for (AbstractClientPlayer player : client.level.players()) {
            Ritual ritual = RITUALS.get(player.getId());
            if (ritual != null) {
                if (ritual.finishingAt() != null && finished(player, ritual.finishingAt())) {
                    RITUALS.remove(player.getId());
                    ritual = null;
                }
            }
            if (ritual != null) {
                player.setYBodyRot(ritual.yaw());
                player.yBodyRotO = ritual.yaw();
            }
            Mode next = desired(player);
            Mode previous = MODES.getOrDefault(player, Mode.NONE);
            if (next == previous) {
                advance(player);
                continue;
            }
            MODES.put(player, next);

            PlayerAnimationController controller = controller(player);
            if (controller == null) continue;
            mirror(controller, player);
            LOOP_IN.remove(player);
            LOOP_TARGET.remove(player);
            switch (next) {
                case HANDHELD -> startLooping(player, controller, START, LOOP, FADE_IN_TICKS);
                case FLOOR -> startLooping(player, controller, FLOOR_START, FLOOR_LOOP, RITUAL_FADE_TICKS);
                case TOP -> startLooping(player, controller, TOP_START, TOP_LOOP, RITUAL_FADE_TICKS);
                case FLOOR_FINISH -> once(player, controller, animation(FLOOR_FINISH), LOOP_BLEND_TICKS);
                case TOP_FINISH -> once(player, controller, animation(TOP_FINISH), LOOP_BLEND_TICKS);
                case NONE -> finish(player, controller, previous);
            }
        }
    }

    public static @Nullable Float ritualYaw(Player player) {
        Ritual ritual = RITUALS.get(player.getId());
        return ritual == null ? null : ritual.yaw();
    }

    public static Vec3 headOffset(Player player) {
        PlayerAnimationController controller = controller(player);
        if (controller == null || !controller.isActive()) return Vec3.ZERO;
        PlayerAnimBone head = new PlayerAnimBone("head");
        head.setToInitialPose();
        controller.get3DTransform(head);

        float yaw = (180.0F - player.yBodyRot) * Mth.DEG_TO_RAD;
        double x = -head.position.x * MODEL_SCALE / 16.0;
        double y = head.position.y * MODEL_SCALE / 16.0;
        double z = head.position.z * MODEL_SCALE / 16.0;
        return new Vec3(x * Mth.cos(yaw) + z * Mth.sin(yaw), y, -x * Mth.sin(yaw) + z * Mth.cos(yaw));
    }

    private static Optional<AdjustmentModifier.PartModifier> head(Avatar avatar, String bone) {
        if (!"head".equals(bone) || !(avatar instanceof Player player)) return Optional.empty();
        boolean free = FREE_HEAD.getOrDefault(player, false);
        if (!free && !RITUALS.containsKey(player.getId())) return Optional.empty();
        float partialTicks = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float pitch = Mth.lerp(partialTicks, player.xRotO, player.getXRot());
        float yaw = free ? Mth.clamp(Mth.wrapDegrees(player.getViewYRot(partialTicks) - Mth.rotLerp(partialTicks, player.yBodyRotO, player.yBodyRot)), -MAX_HEAD_YAW, MAX_HEAD_YAW) : 0.0F;
        return Optional.of(new AdjustmentModifier.PartModifier(new Vec3f(pitch * Mth.DEG_TO_RAD, yaw * Mth.DEG_TO_RAD, 0.0F), Vec3f.ZERO));
    }

    private static void track(Player player, @Nullable Animation animation) {
        boolean animated = animation != null && animation.getBoneOptional("head").map(BoneAnimation::hasKeyframes).orElse(false);
        FREE_HEAD.put(player, !animated);
    }

    private static void startLooping(Player player, PlayerAnimationController controller, Identifier startId, Identifier loopId, int fadeTicks) {
        Animation start = animation(startId);
        if (start == null) return;
        track(player, start);
        play(controller, RawAnimation.begin().thenPlayAndHold(start), fadeTicks);
        LOOP_IN.put(player, Math.max(1, Mth.ceil(start.length())));
        LOOP_TARGET.put(player, loopId);
    }

    private static void finish(Player player, PlayerAnimationController controller, Mode previous) {
        Animation done = previous == Mode.HANDHELD ? animation(DONE) : null;
        track(player, done);
        if (done != null) {
            play(controller, RawAnimation.begin().thenPlay(done), FADE_OUT_TICKS);
            return;
        }
        play(controller, null, previous == Mode.HANDHELD ? FADE_OUT_TICKS : RITUAL_FADE_TICKS);
        controller.stop();
    }

    private static boolean finished(Player player, Vec3 anchor) {
        if (!playing(player)) return true;
        Vec3 position = player.position();
        return Mth.lengthSquared(position.x - anchor.x, position.z - anchor.z) > FINISH_DRIFT * FINISH_DRIFT;
    }

    private static void once(Player player, PlayerAnimationController controller, @Nullable Animation animation, int ticks) {
        if (animation == null) return;
        track(player, animation);
        play(controller, RawAnimation.begin().thenPlay(animation), ticks);
    }

    private static void loop(Player player, PlayerAnimationController controller, @Nullable Animation animation, int ticks) {
        if (animation == null) return;
        track(player, animation);
        play(controller, RawAnimation.begin().thenLoop(animation), ticks);
    }

    private static void advance(Player player) {
        Integer remaining = LOOP_IN.get(player);
        if (remaining == null) return;
        if (remaining > 1) {
            LOOP_IN.put(player, remaining - 1);
            return;
        }
        LOOP_IN.remove(player);
        Identifier target = LOOP_TARGET.remove(player);

        PlayerAnimationController controller = controller(player);
        if (controller != null && target != null) loop(player, controller, animation(target), LOOP_BLEND_TICKS);
    }

    private static void mirror(PlayerAnimationController controller, Player player) {
        for (AbstractModifier modifier : controller.getModifiers()) {
            if (modifier instanceof MirrorModifier mirror) mirror.enabled = player.getMainArm() == HumanoidArm.LEFT;
        }
    }

    private static void play(PlayerAnimationController controller, @Nullable RawAnimation animation, int ticks) {
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE), animation);
    }

    private LanternChargeAnimations() {}
}
