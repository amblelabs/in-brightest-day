package dev.amble.client.effects;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import com.zigythebird.playeranimcore.loading.UniversalAnimLoader;
import com.zigythebird.playeranimcore.math.Vec3f;
import dev.amble.BrightestDay;
import dev.amble.core.networking.payloads.s2c.AttackAnimS2CPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class AttackAnimations implements ResourceManagerReloadListener {
    public static final Identifier LAYER = BrightestDay.id("attack_animations");
    private static final String ROOT = "player_animations/attack_animations";
    private static final String CHARGE_SUFFIX = "_charge_up";
    private static final String LOOP_SUFFIX = "_loop";
    private static final int PRIORITY = 1650;
    private static final int FADE_IN_TICKS = 3;
    private static final int LOOP_BLEND_TICKS = 3;
    private static final int RELEASE_BLEND_TICKS = 2;
    private static final int FADE_OUT_TICKS = 6;
    private static final int STOP_GRACE_TICKS = 3;
    private static final Map<String, Vec3f> RIG_PIVOTS = Map.of("waist", new Vec3f(0.0F, 12.0F, 0.0F));
    private static final Map<String, String> RIG_PARENTS = Map.of(
            "head", "waist",
            "torso", "waist",
            "right_arm", "torso",
            "left_arm", "torso");

    private static final class Set {
        @Nullable Animation charge;
        @Nullable Animation loop;
        @Nullable Animation release;
    }

    private static final class State {
        final Set set;
        int loopIn = -1;
        int stopIn = -1;

        State(Set set) {
            this.set = set;
        }
    }

    private static Map<String, Set> sets = Map.of();
    private static final Map<Integer, State> STATES = new HashMap<>();

    public static void init() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BrightestDay.id("attack_animations"), new AttackAnimations());
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, avatar -> {
            PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, setter) -> PlayState.STOP);
            controller.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            controller.setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
            controller.addModifierLast(new MirrorModifier());
            return controller;
        });
        ClientPlayNetworking.registerGlobalReceiver(AttackAnimS2CPayload.TYPE, (payload, context) -> receive(context.client(), payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATES.clear());
        ClientTickEvents.END_CLIENT_TICK.register(AttackAnimations::tick);
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Map<String, Set> loaded = new HashMap<>();
        for (Map.Entry<Identifier, Resource> entry : manager.listResources(ROOT, id -> id.getPath().endsWith(".json")).entrySet()) {
            String relative = entry.getKey().getPath().substring(ROOT.length() + 1);
            int slash = relative.indexOf('/');
            if (slash < 0) continue;
            String attack = relative.substring(0, slash).toLowerCase();
            try (InputStream stream = entry.getValue().open()) {
                for (Map.Entry<String, Animation> animation : UniversalAnimLoader.loadAnimations(stream).entrySet()) {
                    String name = animation.getKey();
                    Animation rigged = animation.getValue();
                    if (rigged.bones().isEmpty()) rigged.bones().putAll(RIG_PIVOTS);
                    if (rigged.parents().isEmpty()) rigged.parents().putAll(RIG_PARENTS);
                    Set set = loaded.computeIfAbsent(attack, key -> new Set());
                    if (name.equals(attack + CHARGE_SUFFIX)) set.charge = rigged;
                    else if (name.equals(attack + LOOP_SUFFIX)) set.loop = rigged;
                    else if (name.equals(attack)) set.release = rigged;
                }
            } catch (Exception exception) {
                BrightestDay.LOGGER.warn("Failed to load attack animation file {}", entry.getKey(), exception);
            }
        }
        sets = Map.copyOf(loaded);
    }

    private static void receive(Minecraft client, AttackAnimS2CPayload payload) {
        Set set = sets.get(payload.attack().getPath());
        if (set == null || client.level == null || !(client.level.getEntity(payload.playerId()) instanceof Player player)) return;
        PlayerAnimationController controller = controller(player);
        if (controller == null) return;
        mirror(controller, player);

        switch (payload.phase()) {
            case AttackAnimS2CPayload.CHARGE -> {
                State state = new State(set);
                STATES.put(player.getId(), state);
                if (set.charge != null) {
                    play(controller, RawAnimation.begin().thenPlayAndHold(set.charge), FADE_IN_TICKS);
                    if (set.loop != null) state.loopIn = Math.max(1, Mth.ceil(set.charge.length()));
                } else if (set.loop != null) {
                    play(controller, RawAnimation.begin().thenLoop(set.loop), FADE_IN_TICKS);
                }
            }
            case AttackAnimS2CPayload.FIRE -> {
                STATES.remove(player.getId());
                if (set.release != null) play(controller, RawAnimation.begin().thenPlay(set.release), RELEASE_BLEND_TICKS);
                else stop(controller);
            }
            case AttackAnimS2CPayload.STOP -> {
                State state = STATES.get(player.getId());
                if (state != null) state.stopIn = STOP_GRACE_TICKS;
            }
            default -> {}
        }
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.isPaused() || STATES.isEmpty()) return;
        STATES.entrySet().removeIf(entry -> {
            State state = entry.getValue();
            if (!(client.level.getEntity(entry.getKey()) instanceof AbstractClientPlayer player)) return true;
            PlayerAnimationController controller = controller(player);
            if (controller == null) return true;
            if (state.stopIn >= 0 && state.stopIn-- == 0) {
                stop(controller);
                return true;
            }
            if (state.loopIn > 0 && --state.loopIn == 0 && state.set.loop != null) {
                play(controller, RawAnimation.begin().thenLoop(state.set.loop), LOOP_BLEND_TICKS);
            }
            return false;
        });
    }

    private static @Nullable PlayerAnimationController controller(Player player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof PlayerAnimationController controller ? controller : null;
    }

    private static void mirror(PlayerAnimationController controller, Player player) {
        for (AbstractModifier modifier : controller.getModifiers()) {
            if (modifier instanceof MirrorModifier mirror) mirror.enabled = player.getMainArm() == HumanoidArm.LEFT;
        }
    }

    private static void stop(PlayerAnimationController controller) {
        play(controller, null, FADE_OUT_TICKS);
        controller.stop();
    }

    private static void play(PlayerAnimationController controller, @Nullable RawAnimation animation, int ticks) {
        controller.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(ticks, EasingType.EASE_IN_OUT_SINE), animation);
    }

    private AttackAnimations() {}
}
