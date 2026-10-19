package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record ColorTweak(float brightness, float saturation, boolean aura, boolean auraFlightOnly, boolean suit, boolean mask, int maskOffset, MaskLayer maskLayer, String style) {
    public static final String MOVIE = "movie";
    public static final String COMIC = "comic";
    public static final int MAX_STYLE_LENGTH = 32;
    public static final float MIN_SATURATION = -0.6F;
    public static final int MAX_MASK_OFFSET = 3;
    public static final ColorTweak NONE = new ColorTweak(0.0F, 0.0F, false, true, false, true, 0, MaskLayer.AUTO, MOVIE);

    public static final Codec<ColorTweak> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(-1.0F, 1.0F).optionalFieldOf("brightness", 0.0F).forGetter(ColorTweak::brightness),
            Codec.floatRange(-1.0F, 1.0F).optionalFieldOf("saturation", 0.0F).forGetter(ColorTweak::saturation),
            Codec.BOOL.optionalFieldOf("aura", false).forGetter(ColorTweak::aura),
            Codec.BOOL.optionalFieldOf("aura_flight_only", true).forGetter(ColorTweak::auraFlightOnly),
            Codec.BOOL.optionalFieldOf("suit", false).forGetter(ColorTweak::suit),
            Codec.BOOL.optionalFieldOf("mask", true).forGetter(ColorTweak::mask),
            Codec.intRange(-MAX_MASK_OFFSET, MAX_MASK_OFFSET).optionalFieldOf("mask_offset", 0).forGetter(ColorTweak::maskOffset),
            MaskLayer.CODEC.optionalFieldOf("mask_layer", MaskLayer.AUTO).forGetter(ColorTweak::maskLayer),
            Codec.string(1, MAX_STYLE_LENGTH).optionalFieldOf("style", MOVIE).forGetter(ColorTweak::style)
    ).apply(instance, ColorTweak::new));

    public static final StreamCodec<ByteBuf, ColorTweak> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ColorTweak::brightness,
            ByteBufCodecs.FLOAT, ColorTweak::saturation,
            ByteBufCodecs.BOOL, ColorTweak::aura,
            ByteBufCodecs.BOOL, ColorTweak::auraFlightOnly,
            ByteBufCodecs.BOOL, ColorTweak::suit,
            ByteBufCodecs.BOOL, ColorTweak::mask,
            ByteBufCodecs.VAR_INT, ColorTweak::maskOffset,
            MaskLayer.STREAM_CODEC, ColorTweak::maskLayer,
            ByteBufCodecs.stringUtf8(MAX_STYLE_LENGTH), ColorTweak::style,
            ColorTweak::new
    );

    public ColorTweak withSuit(boolean suit) {
        return new ColorTweak(this.brightness, this.saturation, this.aura, this.auraFlightOnly, suit, this.mask, this.maskOffset, this.maskLayer, this.style);
    }

    public ColorTweak withMask(boolean mask) {
        return new ColorTweak(this.brightness, this.saturation, this.aura, this.auraFlightOnly, this.suit, mask, this.maskOffset, this.maskLayer, this.style);
    }

    public ColorTweak withMaskLayer(MaskLayer maskLayer) {
        return new ColorTweak(this.brightness, this.saturation, this.aura, this.auraFlightOnly, this.suit, this.mask, this.maskOffset, maskLayer, this.style);
    }

    public ColorTweak withStyle(String style) {
        return new ColorTweak(this.brightness, this.saturation, this.aura, this.auraFlightOnly, this.suit, this.mask, this.maskOffset, this.maskLayer, style);
    }

    public boolean comic() {
        return !MOVIE.equals(this.style);
    }

    public ColorTweak clamped() {
        return new ColorTweak(
                Mth.clamp(this.brightness, -1.0F, 1.0F),
                Mth.clamp(this.saturation, MIN_SATURATION, 1.0F),
                this.aura,
                this.auraFlightOnly,
                this.suit,
                this.mask,
                Mth.clamp(this.maskOffset, -MAX_MASK_OFFSET, MAX_MASK_OFFSET),
                this.maskLayer,
                this.style.isEmpty() || this.style.length() > MAX_STYLE_LENGTH ? MOVIE : this.style
        );
    }
}
