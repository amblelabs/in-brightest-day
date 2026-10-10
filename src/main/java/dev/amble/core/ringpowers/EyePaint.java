package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record EyePaint(long white, long corps, int brightness) {
    public static final int SIZE = 8;
    public static final int NONE = 0;
    public static final int WHITE = 1;
    public static final int CORPS = 2;
    public static final int MIN_BRIGHTNESS = 10;
    public static final int MAX_BRIGHTNESS = 100;
    public static final EyePaint EMPTY = new EyePaint(0L, 0L, MAX_BRIGHTNESS);

    public static final Codec<EyePaint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("white", 0L).forGetter(EyePaint::white),
            Codec.LONG.optionalFieldOf("corps", 0L).forGetter(EyePaint::corps),
            Codec.INT.optionalFieldOf("brightness", MAX_BRIGHTNESS).forGetter(EyePaint::brightness)
    ).apply(instance, EyePaint::new));

    public static final StreamCodec<ByteBuf, EyePaint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.LONG, EyePaint::white,
            ByteBufCodecs.LONG, EyePaint::corps,
            ByteBufCodecs.VAR_INT, EyePaint::brightness,
            EyePaint::new
    );

    public boolean isEmpty() {
        return this.white == 0L && this.corps == 0L;
    }

    public float glow() {
        return Mth.clamp(this.brightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS) / (float) MAX_BRIGHTNESS;
    }

    public int get(int column, int row) {
        long bit = bit(column, row);
        if ((this.white & bit) != 0L) return WHITE;
        if ((this.corps & bit) != 0L) return CORPS;
        return NONE;
    }

    public EyePaint with(int column, int row, int kind) {
        long bit = bit(column, row);
        long white = this.white & ~bit;
        long corps = this.corps & ~bit;
        if (kind == WHITE) white |= bit;
        if (kind == CORPS) corps |= bit;
        return new EyePaint(white, corps, this.brightness);
    }

    public EyePaint withBrightness(int brightness) {
        return new EyePaint(this.white, this.corps, brightness);
    }

    public EyePaint cleared() {
        return new EyePaint(0L, 0L, this.brightness);
    }

    public EyePaint sanitized() {
        return new EyePaint(this.white, this.corps & ~this.white, Mth.clamp(this.brightness, MIN_BRIGHTNESS, MAX_BRIGHTNESS));
    }

    private static long bit(int column, int row) {
        return 1L << (row * SIZE + column);
    }
}
