package dev.amble.client.render;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.mannequin.HologramSettings;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.EyePaint;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingBenefits;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LanternSuit {
    public static final RenderStateDataKey<Identifier> GLOW = RenderStateDataKey.create(() -> "brightestday:suit_glow");
    public static final RenderStateDataKey<Identifier> FLARE = RenderStateDataKey.create(() -> "brightestday:eye_flare");

    private static final float FADE_TICKS = 24.0F;
    private static final float EYE_FADE_TICKS = 6.0F;
    private static final Map<Integer, Fade> FADES = new HashMap<>();
    private static @Nullable ClientLevel level;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LanternSuit::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level != level) {
            level = client.level;
            FADES.clear();
            SuitTextures.clear();
        }
        if (level == null) return;

        for (AbstractClientPlayer player : level.players()) {
            Optional<LanternCorps> corps = PowerRingItem.getWornCorps(player);
            ColorTweak tweak = BrightestDayAttachments.getColorTweak(player);
            boolean wanted = corps.isPresent()
                    && tweak.suit()
                    && PowerRingItem.hasCharge(player);
            EyePaint eyes = BrightestDayAttachments.getEyes(player);
            boolean eyesWanted = !eyes.isEmpty() && RingBenefits.isActive(player);
            track(player.getId(), corps.orElse(null), CorpsColors.of(player), wanted, tweak.mask(), tweak.maskOffset(), eyes, eyesWanted);
        }

        for (Mannequin mannequin : Holograms.all(level)) {
            HologramSettings settings = Holograms.of(mannequin).settings();
            boolean lit = Holograms.lit(mannequin);
            track(mannequin.getId(), Holograms.corps(mannequin).orElse(null), Holograms.color(mannequin), lit && settings.suit(), settings.mask(), settings.maskOffset(),
                    settings.eyes(), lit && !settings.eyes().isEmpty());
        }

        FADES.entrySet().removeIf(entry -> {
            boolean done = !(level.getEntity(entry.getKey()) instanceof Avatar) || entry.getValue().hidden();
            if (done) SuitTextures.release(entry.getKey());
            return done;
        });
    }

    private static void track(int id, @Nullable LanternCorps corps, int color, boolean wanted, boolean mask, int maskOffset, EyePaint eyes, boolean eyesWanted) {
        Fade fade = FADES.get(id);
        if (fade == null) {
            if (!wanted && !eyesWanted) return;
            fade = new Fade(mask);
            FADES.put(id, fade);
        }
        if (wanted && corps != null) {
            fade.corps = corps;
            fade.color = color;
            fade.maskOffset = maskOffset;
        }
        if (eyesWanted) {
            fade.eyes = eyes;
            fade.eyeColor = color;
        }
        fade.tick(wanted, mask, eyesWanted);
    }

    public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
        FabricRenderState data = (FabricRenderState) state;
        data.setData(GLOW, null);
        data.setData(FLARE, null);

        Fade fade = FADES.get(entity.getId());
        if (fade == null) return;

        float progress = fade.progress(partialTicks);
        float eyeProgress = fade.eyeProgress(partialTicks);
        if (progress <= 0.0F && eyeProgress <= 0.0F) return;

        float maskProgress = fade.maskProgress(partialTicks);
        SuitTextures.Entry entry = SuitTextures.update(entity.getId(), state.skin, fade.corps, state.mainArm, fade.maskOffset, maskProgress, progress, fade.color,
                fade.eyes, eyeProgress, fade.eyeColor);
        if (entry == null) return;

        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(new ClientAsset.ResourceTexture(entry.bodyId, entry.bodyId), skin.cape(), skin.elytra(), skin.model(), skin.secure());
        boolean suitShimmer = progress > 0.0F && (progress < 1.0F || (maskProgress > 0.0F && maskProgress < 1.0F));
        if (suitShimmer || (eyeProgress > 0.0F && !fade.eyes.isEmpty())) data.setData(GLOW, entry.glowId);
        if (eyeProgress > 0.0F && !fade.eyes.isEmpty()) data.setData(FLARE, entry.flareId);
    }

    private static final class Fade {
        private float previous;
        private float current;
        private LanternCorps corps = LanternCorps.GREEN;
        private int color = LanternCorps.GREEN.color();
        private int maskOffset;
        private float maskPrevious;
        private float maskCurrent;
        private float eyePrevious;
        private float eyeCurrent;
        private EyePaint eyes = EyePaint.EMPTY;
        private int eyeColor = LanternCorps.GREEN.color();

        private Fade(boolean mask) {
            this.maskPrevious = this.maskCurrent = mask ? 1.0F : 0.0F;
        }

        private void tick(boolean wanted, boolean mask, boolean eyesWanted) {
            this.previous = this.current;
            this.current = Mth.approach(this.current, wanted ? 1.0F : 0.0F, 1.0F / FADE_TICKS);
            this.maskPrevious = this.maskCurrent;
            this.maskCurrent = Mth.approach(this.maskCurrent, mask ? 1.0F : 0.0F, 1.0F / FADE_TICKS);
            this.eyePrevious = this.eyeCurrent;
            this.eyeCurrent = Mth.approach(this.eyeCurrent, eyesWanted ? 1.0F : 0.0F, 1.0F / EYE_FADE_TICKS);
        }

        private float progress(float partialTicks) {
            return Mth.lerp(partialTicks, this.previous, this.current);
        }

        private float eyeProgress(float partialTicks) {
            return Mth.lerp(partialTicks, this.eyePrevious, this.eyeCurrent);
        }

        private float maskProgress(float partialTicks) {
            return Mth.lerp(partialTicks, this.maskPrevious, this.maskCurrent);
        }

        private boolean hidden() {
            return this.previous <= 0.0F && this.current <= 0.0F && this.eyePrevious <= 0.0F && this.eyeCurrent <= 0.0F;
        }
    }

    private LanternSuit() {}
}
