package dev.amble.core.hand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

public record HandPose(int index, int middle, int ring, int pinky, int thumbCurl, int thumbSpread) {
    public static final int MAX = 100;

    public static final Codec<HandPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(HandPose::index),
            Codec.INT.fieldOf("middle").forGetter(HandPose::middle),
            Codec.INT.fieldOf("ring").forGetter(HandPose::ring),
            Codec.INT.fieldOf("pinky").forGetter(HandPose::pinky),
            Codec.INT.fieldOf("thumb_curl").forGetter(HandPose::thumbCurl),
            Codec.INT.fieldOf("thumb_spread").forGetter(HandPose::thumbSpread)
    ).apply(instance, HandPose::new));

    public static final StreamCodec<ByteBuf, HandPose> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HandPose::index,
            ByteBufCodecs.VAR_INT, HandPose::middle,
            ByteBufCodecs.VAR_INT, HandPose::ring,
            ByteBufCodecs.VAR_INT, HandPose::pinky,
            ByteBufCodecs.VAR_INT, HandPose::thumbCurl,
            ByteBufCodecs.VAR_INT, HandPose::thumbSpread,
            HandPose::new
    );

    public int finger(int finger) {
        return switch (finger) {
            case 0 -> this.index;
            case 1 -> this.middle;
            case 2 -> this.ring;
            default -> this.pinky;
        };
    }

    public HandPose withFinger(int finger, int value) {
        return new HandPose(finger == 0 ? value : this.index, finger == 1 ? value : this.middle, finger == 2 ? value : this.ring,
                finger == 3 ? value : this.pinky, this.thumbCurl, this.thumbSpread);
    }

    public HandPose withThumb(int curl, int spread) {
        return new HandPose(this.index, this.middle, this.ring, this.pinky, curl, spread);
    }

    public HandPose sanitized() {
        return new HandPose(clamp(this.index), clamp(this.middle), clamp(this.ring), clamp(this.pinky), clamp(this.thumbCurl), clamp(this.thumbSpread));
    }

    private static int clamp(int value) {
        return Mth.clamp(value, 0, MAX);
    }
}
