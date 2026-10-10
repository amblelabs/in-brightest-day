package dev.amble.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.constructs.ConstructToolData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public class BrightestDayComponents {
    public static final int MAX_POWER = 5000;

    public static final DataComponentType<Integer> POWER_TYPE =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("power_type"),
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.INT)
                            .build()
            );

    public static final DataComponentType<LanternCorps> LANTERN_CORPS =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("lantern_corps"),
                    DataComponentType.<LanternCorps>builder()
                            .persistent(LanternCorps.CODEC)
                            .networkSynchronized(LanternCorps.STREAM_CODEC)
                            .build()
            );

    public static final DataComponentType<GlobalPos> BOUND_LANTERN =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("bound_lantern"),
                    DataComponentType.<GlobalPos>builder()
                            .persistent(GlobalPos.CODEC)
                            .networkSynchronized(GlobalPos.STREAM_CODEC)
                            .build()
            );

    public static final DataComponentType<Integer> RING_DEATHS =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("ring_deaths"),
                    DataComponentType.<Integer>builder()
                            .persistent(Codec.INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT)
                            .build()
            );

    public static final DataComponentType<Boolean> DORMANT =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("dormant"),
                    DataComponentType.<Boolean>builder()
                            .persistent(Codec.BOOL)
                            .networkSynchronized(ByteBufCodecs.BOOL)
                            .build()
            );

    public record Sworn(UUID owner, String name) {
        public static final Codec<Sworn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Sworn::owner),
                Codec.STRING.fieldOf("name").forGetter(Sworn::name)
        ).apply(instance, Sworn::new));

        public static final StreamCodec<ByteBuf, Sworn> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Sworn::owner,
                ByteBufCodecs.STRING_UTF8, Sworn::name,
                Sworn::new
        );
    }

    public static final DataComponentType<Sworn> SWORN_TO =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("sworn_to"),
                    DataComponentType.<Sworn>builder()
                            .persistent(Sworn.CODEC)
                            .networkSynchronized(Sworn.STREAM_CODEC)
                            .build()
            );

    public static final DataComponentType<Float> RING_SYNC =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("ring_sync"),
                    DataComponentType.<Float>builder()
                            .persistent(Codec.FLOAT)
                            .networkSynchronized(ByteBufCodecs.FLOAT)
                            .build()
            );

    public record ChargeCap(int capacity, int rank, int battery) {
        public static final Codec<ChargeCap> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("capacity").forGetter(ChargeCap::capacity),
                Codec.INT.fieldOf("rank").forGetter(ChargeCap::rank),
                Codec.INT.fieldOf("battery").forGetter(ChargeCap::battery)
        ).apply(instance, ChargeCap::new));

        public static final StreamCodec<ByteBuf, ChargeCap> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChargeCap::capacity,
                ByteBufCodecs.VAR_INT, ChargeCap::rank,
                ByteBufCodecs.VAR_INT, ChargeCap::battery,
                ChargeCap::new);
    }

    public static final DataComponentType<ChargeCap> CHARGE_CAP =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("charge_cap"),
                    DataComponentType.<ChargeCap>builder()
                            .persistent(ChargeCap.CODEC)
                            .networkSynchronized(ChargeCap.STREAM_CODEC)
                            .build()
            );

    public static final DataComponentType<String> SUCCESSOR =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("successor"),
                    DataComponentType.<String>builder()
                            .persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                            .build()
            );

    public record Bond(UUID id, int generation) {
        public static final Codec<Bond> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Bond::id),
                Codec.INT.optionalFieldOf("generation", 0).forGetter(Bond::generation)
        ).apply(instance, Bond::new));

        public static final StreamCodec<ByteBuf, Bond> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Bond::id,
                ByteBufCodecs.VAR_INT, Bond::generation,
                Bond::new
        );
    }

    public static final DataComponentType<Bond> RING_BOND =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("ring_bond"),
                    DataComponentType.<Bond>builder()
                            .persistent(Bond.CODEC)
                            .networkSynchronized(Bond.STREAM_CODEC)
                            .build()
            );

    public static final DataComponentType<ConstructToolData> CONSTRUCT_TOOL =
            Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
                    BrightestDay.id("construct_tool"),
                    DataComponentType.<ConstructToolData>builder()
                            .persistent(ConstructToolData.CODEC)
                            .networkSynchronized(ConstructToolData.STREAM_CODEC)
                            .build()
            );
}
