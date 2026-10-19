package dev.amble.core.mannequin;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.EyePaint;
import dev.amble.core.ringpowers.MaskLayer;
import dev.amble.core.visuals.InsigniaAnchor;
import net.minecraft.util.Mth;

public record HologramSettings(String skin, int pose, boolean glow, boolean suit, boolean aura, boolean insignia, boolean hardLight,
                               boolean hideName, boolean locked, float yaw, EyePaint eyes, InsigniaAnchor anchor, boolean mask, boolean pad,
                               boolean leftHanded, int maskOffset, MaskLayer maskLayer, String style) {
    private record Look(int maskOffset, MaskLayer maskLayer, String style) {
        static final MapCodec<Look> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("mask_offset", 0).forGetter(Look::maskOffset),
                MaskLayer.CODEC.optionalFieldOf("mask_layer", MaskLayer.AUTO).forGetter(Look::maskLayer),
                Codec.STRING.optionalFieldOf("style", ColorTweak.MOVIE).forGetter(Look::style)
        ).apply(instance, Look::new));
    }

    public static final int MAX_NAME = 16;
    public static final int MAX_POSE = 256;
    public static final HologramSettings DEFAULT = new HologramSettings("", 0, false, true, false, false, false, false, false, 0.0F, EyePaint.EMPTY, InsigniaAnchor.DEFAULT, false, true, false, 0, MaskLayer.AUTO, ColorTweak.MOVIE);

    public static final Codec<HologramSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("skin", "").forGetter(HologramSettings::skin),
            Codec.INT.optionalFieldOf("pose", 0).forGetter(HologramSettings::pose),
            Codec.BOOL.optionalFieldOf("glow", false).forGetter(HologramSettings::glow),
            Codec.BOOL.optionalFieldOf("suit", true).forGetter(HologramSettings::suit),
            Codec.BOOL.optionalFieldOf("aura", false).forGetter(HologramSettings::aura),
            Codec.BOOL.optionalFieldOf("insignia", false).forGetter(HologramSettings::insignia),
            Codec.BOOL.optionalFieldOf("hard_light", false).forGetter(HologramSettings::hardLight),
            Codec.BOOL.optionalFieldOf("hide_name", false).forGetter(HologramSettings::hideName),
            Codec.BOOL.optionalFieldOf("locked", false).forGetter(HologramSettings::locked),
            Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(HologramSettings::yaw),
            EyePaint.CODEC.optionalFieldOf("eyes", EyePaint.EMPTY).forGetter(HologramSettings::eyes),
            InsigniaAnchor.CODEC.optionalFieldOf("anchor", InsigniaAnchor.DEFAULT).forGetter(HologramSettings::anchor),
            Codec.BOOL.optionalFieldOf("mask", false).forGetter(HologramSettings::mask),
            Codec.BOOL.optionalFieldOf("pad", true).forGetter(HologramSettings::pad),
            Codec.BOOL.optionalFieldOf("left_handed", false).forGetter(HologramSettings::leftHanded),
            Look.CODEC.forGetter(settings -> new Look(settings.maskOffset, settings.maskLayer, settings.style))
    ).apply(instance, (skin, pose, glow, suit, aura, insignia, hardLight, hideName, locked, yaw, eyes, anchor, mask, pad, leftHanded, look) ->
            new HologramSettings(skin, pose, glow, suit, aura, insignia, hardLight, hideName, locked, yaw, eyes, anchor, mask, pad, leftHanded, look.maskOffset(), look.maskLayer(), look.style())));

    public HologramSettings sanitized() {
        String name = this.skin.length() > MAX_NAME ? this.skin.substring(0, MAX_NAME) : this.skin;
        name = name.replaceAll("[^A-Za-z0-9_]", "");
        return new HologramSettings(name, Mth.clamp(this.pose, 0, MAX_POSE), this.glow, this.suit, this.aura, this.insignia, this.hardLight,
                this.hideName, this.locked, Mth.wrapDegrees(Float.isFinite(this.yaw) ? this.yaw : 0.0F), this.eyes.sanitized(), this.anchor.sanitized(), this.mask, this.pad, this.leftHanded, Mth.clamp(this.maskOffset, -ColorTweak.MAX_MASK_OFFSET, ColorTweak.MAX_MASK_OFFSET), this.maskLayer,
                this.style.isEmpty() || this.style.length() > ColorTweak.MAX_STYLE_LENGTH ? ColorTweak.MOVIE : this.style);
    }

    public HologramSettings withSkin(String skin) {
        return new HologramSettings(skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withPose(int pose) {
        return new HologramSettings(this.skin, pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withGlow(boolean glow) {
        return new HologramSettings(this.skin, this.pose, glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withSuit(boolean suit) {
        return new HologramSettings(this.skin, this.pose, this.glow, suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withAura(boolean aura) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withInsignia(boolean insignia) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withHardLight(boolean hardLight) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withHideName(boolean hideName) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withLocked(boolean locked) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withYaw(float yaw) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withEyes(EyePaint eyes) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withAnchor(InsigniaAnchor anchor) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withMask(boolean mask) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withPad(boolean pad) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, pad, this.leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withLeftHanded(boolean leftHanded) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, leftHanded, this.maskOffset, this.maskLayer, this.style);
    }

    public HologramSettings withStyle(String style) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, this.maskLayer, style);
    }

    public HologramSettings withMaskLayer(MaskLayer maskLayer) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, this.maskOffset, maskLayer, this.style);
    }

    public HologramSettings withMaskOffset(int maskOffset) {
        return new HologramSettings(this.skin, this.pose, this.glow, this.suit, this.aura, this.insignia, this.hardLight, this.hideName, this.locked, this.yaw, this.eyes, this.anchor, this.mask, this.pad, this.leftHanded, maskOffset, this.maskLayer, this.style);
    }
}
