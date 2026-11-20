package dev.amble.core.ringpowers;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.amble.core.BrightestDayAttachments;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Unit;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public abstract class RingPower<D> implements Identifiable, Translatable {

    public static final Codec<RingPower<?>> CODEC = Identifier.CODEC.comapFlatMap(
            id -> RingPowerRegistry.get(id)
                    .<DataResult<RingPower<?>>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown ring power: " + id)),
            RingPower::id
    );

    private final Identifier id;
    private final Set<LanternCorps> corps;
    private final Codec<D> dataCodec;
    private final MapCodec<RingPowerInstance<D>> instanceCodec;

    protected RingPower(Identifier id, Set<LanternCorps> corps, Codec<D> dataCodec) {
        this.id = id;
        this.corps = Set.copyOf(corps);
        this.dataCodec = dataCodec;
        this.instanceCodec = dataCodec.lenientOptionalFieldOf("data").xmap(
                data -> new RingPowerInstance<>(this, data.orElseGet(this::createData)),
                instance -> Optional.of(instance.data())
        );
    }

    @Override
    public final Identifier id() {
        return this.id;
    }

    public Set<LanternCorps> corps() {
        return this.corps;
    }

    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    public boolean worksWithoutCharge() {
        return false;
    }

    public boolean isAvailableTo(LanternCorps corps) {
        return this.corps.contains(corps) && corps.canUse(this.category());
    }

    public Codec<D> dataCodec() {
        return this.dataCodec;
    }

    MapCodec<RingPowerInstance<D>> instanceCodec() {
        return this.instanceCodec;
    }

    public abstract D createData();

    public RingPowerInstance<D> createInstance() {
        return new RingPowerInstance<>(this, this.createData());
    }

    public boolean run(ServerPlayer player, D data) {
        return false;
    }

    public void tick(ServerPlayer player, D data) {}

    @Environment(EnvType.CLIENT)
    public void tick(AbstractClientPlayer clientPlayer, D data) {}

    public int baseCost() {
        return 0;
    }

    public int drainPerSecond(ServerPlayer player, D data) {
        return 0;
    }

    public void onDepleted(ServerPlayer player, D data) {}

    public void onGranted(ServerPlayer player, D data) {}

    public void onRevoked(ServerPlayer player, D data) {}

    protected void setData(Player player, D data) {
        BrightestDayAttachments.setData(player, this, data);
    }

    @Override
    public String getTranslationKey() {
        return this.id().getNamespace() + ".ring_power." + this.id().getPath();
    }

    @Override
    public int hashCode() {
        return this.id().hashCode();
    }

    public static class Builder {
        private final Identifier id;
        private final Set<LanternCorps> corps = EnumSet.noneOf(LanternCorps.class);
        private Consumer<ServerPlayer> run = player -> {};
        private Consumer<ServerPlayer> tick = player -> {};

        private Builder(Identifier id) {
            this.id = id;
        }

        public static Builder create(Identifier id) {
            return new Builder(id);
        }

        public Builder corps(LanternCorps... corps) {
            this.corps.addAll(Set.of(corps));
            return this;
        }

        public Builder run(Consumer<ServerPlayer> run) {
            this.run = run;
            return this;
        }

        public Builder tick(Consumer<ServerPlayer> tick) {
            this.tick = tick;
            return this;
        }

        public RingPower<Unit> build() {
            Consumer<ServerPlayer> run = this.run;
            Consumer<ServerPlayer> tick = this.tick;
            return new RingPower<>(this.id, this.corps, MapCodec.unitCodec(Unit.INSTANCE)) {
                @Override
                public Unit createData() {
                    return Unit.INSTANCE;
                }

                @Override
                public boolean run(ServerPlayer player, Unit data) {
                    run.accept(player);
                    return true;
                }

                @Override
                public void tick(ServerPlayer player, Unit data) {
                    tick.accept(player);
                }
            };
        }
    }
}
