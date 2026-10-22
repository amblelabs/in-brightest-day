package dev.amble.client.flight;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import dev.amble.client.effects.SafeAdjustmentModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.math.Vec3f;
import dev.amble.BrightestDay;
import dev.amble.client.effects.ArmedAimModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public final class FlightAnimations {
    public static final Identifier LAYER = BrightestDay.id("flight");
    public static final Identifier HOVER = BrightestDay.id("hover");
    public static final Identifier FLIGHT = BrightestDay.id("flight");
    public static final Identifier FALLING = BrightestDay.id("falling.json");
    public static final Identifier START_FALLING = BrightestDay.id("start_falling.json");
    public static final Identifier FACE_PLANT = BrightestDay.id("face_plant");
    public static final List<Identifier> HERO_LANDINGS = List.of(
            BrightestDay.id("hero_landing_head_up"),
            BrightestDay.id("hero_landing_head_down"),
            BrightestDay.id("hero_landing_hands_down_head_up"),
            BrightestDay.id("hero_landing_hands_down_head_up2"));

    private static final Map<String, Vec3f> RIG_PIVOTS = Map.of("waist", new Vec3f(0.0F, 12.0F, 0.0F));
    private static final Map<String, String> RIG_PARENTS = Map.of(
            "head", "waist",
            "torso", "waist",
            "right_arm", "torso",
            "left_arm", "torso");

    private static final int PRIORITY = 1500;
    private static final int STOP_FADE_TICKS = 8;
    private static final int FALL_FADE_TICKS = 8;
    static final int LAND_FADE_TICKS = 9;
    static final int RISE_FADE_TICKS = 14;

    public static void init() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, setter) -> PlayState.STOP);
            controller.addModifierLast(new SafeAdjustmentModifier(bone -> FlightAnimator.adjustment(avatar, bone)));
            controller.addModifierLast(new SpeedModifier(1.0F));
            controller.addModifierLast(new ArmedAimModifier(avatar));
            return controller;
        });
    }

    static @Nullable PlayerAnimationController controller(Player player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller
                ? controller
                : null;
    }

    static void loop(PlayerAnimationController controller, Identifier id) {
        Animation animation = animation(id);
        if (animation == null) return;

        fadeTo(controller, RawAnimation.begin().thenLoop(animation), FlightAnimator.TRANSITION_TICKS);
    }

    static void fall(PlayerAnimationController controller) {
        Animation loop = animation(FALLING);
        if (loop == null) return;
        Animation start = animation(START_FALLING);
        RawAnimation animation = start == null ? RawAnimation.begin().thenLoop(loop) : RawAnimation.begin().thenPlay(start).thenLoop(loop);
        fadeTo(controller, animation, FALL_FADE_TICKS);
    }

    static void land(PlayerAnimationController controller, Identifier id) {
        Animation animation = animation(id);
        if (animation == null) return;
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(LAND_FADE_TICKS, EasingType.EASE_OUT_CUBIC), RawAnimation.begin().thenLoop(animation));
    }

    static void rise(PlayerAnimationController controller) {
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(RISE_FADE_TICKS, EasingType.EASE_IN_OUT_SINE), (RawAnimation) null);
        controller.stopTriggeredAnimation();
        controller.stop();
    }

    static void stop(PlayerAnimationController controller) {
        fadeTo(controller, null, STOP_FADE_TICKS);
        controller.stopTriggeredAnimation();
        controller.stop();
    }

    static void setSpeed(PlayerAnimationController controller, float speed) {
        for (AbstractModifier modifier : controller.getModifiers()) {
            if (modifier instanceof SpeedModifier speedModifier) speedModifier.speed = speed;
        }
    }

    private static @Nullable Animation animation(Identifier id) {
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) return null;
        if (animation.bones().isEmpty()) animation.bones().putAll(RIG_PIVOTS);
        if (animation.parents().isEmpty()) animation.parents().putAll(RIG_PARENTS);
        return animation;
    }

    private static void fadeTo(PlayerAnimationController controller, @Nullable RawAnimation animation, int ticks) {
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE), animation);
    }

    private FlightAnimations() {}
}
