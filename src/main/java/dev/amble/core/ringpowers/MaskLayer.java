package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;
import java.util.function.IntFunction;

public enum MaskLayer implements StringRepresentable {
    AUTO,
    INNER,
    OUTER;

    public static final Codec<MaskLayer> CODEC = StringRepresentable.fromEnum(MaskLayer::values);

    private static final IntFunction<MaskLayer> BY_ID =
            ByIdMap.continuous(MaskLayer::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);

    public static final StreamCodec<ByteBuf, MaskLayer> STREAM_CODEC =
            ByteBufCodecs.idMapper(BY_ID, MaskLayer::ordinal);

    public MaskLayer next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public Component label() {
        return Component.translatable("gui.brightestday.mask_layer", Component.translatable("gui.brightestday.mask_layer." + this.getSerializedName()));
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
