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
import dev.amble.BrightestDay;
import dev.amble.client.effects.ArmedAimModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

public final class FlightAnimations {
    public static final Identifier LAYER = BrightestDay.id("flight");
    public static final Identifier HOVER = BrightestDay.id("hover");
    public static final Identifier FLIGHT = BrightestDay.id("flight");

    private static final int PRIORITY = 1500;
    private static final int STOP_FADE_TICKS = 8;

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
        Animation animation = PlayerAnimResources.getAnimation(id);
        if (animation == null) return;

        fadeTo(controller, RawAnimation.begin().thenLoop(animation), FlightAnimator.TRANSITION_TICKS);
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

    private static void fadeTo(PlayerAnimationController controller, @Nullable RawAnimation animation, int ticks) {
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE), animation);
    }

    private FlightAnimations() {}
}
