package dev.amble.core.ringpowers.impl;

import dev.amble.core.networking.payloads.s2c.AttackAnimS2CPayload;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.ringpowers.CorpsMimicry;
import dev.amble.core.attacks.projectile.CrystalManager;
import dev.amble.core.ringpowers.CorpsColors;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import dev.amble.core.ringpowers.RingPowerInstance;
import dev.amble.core.comms.Megaphone;
import dev.amble.core.ringpowers.RedRage;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.beams.BeamManager;
import dev.amble.core.drill.DrillManager;
import dev.amble.core.beams.HealBeamManager;
import dev.amble.core.acid.AcidManager;
import dev.amble.core.attacks.area.BarrageManager;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.sculpt.SculptManager;
import dev.amble.core.tractor.TractorManager;
import dev.amble.core.sync.RingSync;
import dev.amble.core.poses.Poses;
import dev.amble.core.sphere.ContainmentSphere;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public class ArmedRingPower extends RingPower<ArmedRingPower.Data> {

    public static final int COOLDOWN_TICKS = 10;
    private static final int AUTO_LOWER_TICKS = 100;

    private static final Map<ServerPlayer, Long> LAST_FIRED = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> CHARGE_STARTED = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> LAST_USED = new WeakHashMap<>();

    public record Data(boolean active, boolean manual, Optional<Identifier> construct, Optional<Identifier> ability, boolean abilityMode) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(Data::active),
                Codec.BOOL.optionalFieldOf("manual", false).forGetter(Data::manual),
                Identifier.CODEC.optionalFieldOf("construct").forGetter(Data::construct),
                Identifier.CODEC.optionalFieldOf("ability").forGetter(Data::ability),
                Codec.BOOL.optionalFieldOf("ability_mode", false).forGetter(Data::abilityMode)
        ).apply(instance, Data::new));

        public Data withActive(boolean active, boolean manual) {
            return new Data(active, manual, this.construct, this.ability, this.abilityMode);
        }

        public Data withConstruct(Identifier construct) {
            return new Data(this.active, this.manual, Optional.of(construct), this.ability, false);
        }

        public Data withAbility(Identifier ability) {
            return new Data(this.active, this.manual, this.construct, Optional.of(ability), true);
        }
    }

    public ArmedRingPower() {
        super(BrightestDay.id("armed"), EnumSet.allOf(LanternCorps.class), Data.CODEC);
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.STANCE;
    }

    @Override
    public Data createData() {
        return new Data(false, false, Optional.empty(), Optional.empty(), false);
    }

    @Override
    public boolean worksWithoutCharge() {
        return true;
    }

    @Override
    public boolean run(ServerPlayer player, Data data) {
        if (data.active()) {
            lower(player);
        } else {
            this.setData(player, data.withActive(true, true));
        }
        return true;
    }

    @Override
    public void tick(ServerPlayer player, Data data) {
        if (!data.active()) return;

        if (data.manual()) return;

        long now = player.level().getGameTime();
        boolean busy = BeamManager.isBeaming(player) || HealBeamManager.isHealing(player) || SculptManager.isSculpting(player)
                || TractorManager.isHolding(player) || AcidManager.isSpewing(player) || LightRingPower.isEmitting(player) || BarrageManager.isFiring(player) || DrillManager.isDrilling(player) || Megaphone.isActive(player) || ContainmentSphere.isActive(player);
        Long last = LAST_USED.get(player);
        if (busy || last == null) {
            LAST_USED.put(player, now);
        } else if (now - last > AUTO_LOWER_TICKS) {
            lower(player);
        }
    }

    public static void startCharge(ServerPlayer player) {
        CHARGE_STARTED.put(player, player.level().getGameTime());
        if (!Poses.channeling(player)) raise(player);
    }

    public static float chargeFraction(ServerPlayer player, int fullTicks) {
        Long started = CHARGE_STARTED.get(player);
        if (started == null || fullTicks <= 0) return 1.0F;
        return Mth.clamp((player.level().getGameTime() - started) / (float) fullTicks, 0.0F, 1.0F);
    }

    public static void raise(ServerPlayer player) {
        LAST_USED.put(player, player.level().getGameTime());
        data(player).filter(data -> !data.active()).ifPresent(data -> BrightestDayAttachments.setData(player, RingPowerRegistry.ARMED, data.withActive(true, false)));
    }

    public static void lower(ServerPlayer player) {
        BeamManager.stop(player);
        HealBeamManager.stop(player);
        SculptManager.stop(player, false);
        BarrageManager.stop(player);
        DrillManager.stop(player);
        data(player).ifPresent(data -> BrightestDayAttachments.setData(player, RingPowerRegistry.ARMED, data.withActive(false, false)));
        AcidManager.stop(player);
        TractorManager.release(player);
        ContainmentSphere.stop(player);
    }

    public static boolean isArmed(Player player) {
        return data(player).map(Data::active).orElse(false);
    }

    public static Optional<ConstructRingPower> selectedConstruct(Player player) {
        List<ConstructRingPower> constructs = constructs(player);
        if (constructs.isEmpty()) return Optional.empty();

        Optional<Identifier> selected = data(player).flatMap(Data::construct);
        return Optional.of(constructs.stream()
                .filter(construct -> selected.isPresent() && construct.id().equals(selected.get()))
                .findFirst()
                .orElse(constructs.getFirst()));
    }

    public static void cycle(ServerPlayer player) {
        if (!PowerRingItem.hasCharge(player)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }

        List<ConstructRingPower> constructs = constructs(player);
        Optional<Data> data = data(player);
        if (constructs.isEmpty() || data.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.no_constructs"));
            return;
        }

        ConstructRingPower current = selectedConstruct(player).orElse(constructs.getFirst());
        ConstructRingPower next = constructs.get((constructs.indexOf(current) + 1) % constructs.size());
        BrightestDayAttachments.setData(player, RingPowerRegistry.ARMED, data.get().withConstruct(next.id()));
        player.sendOverlayMessage(Component.translatable("message.brightestday.construct_selected", Component.translatable(next.getTranslationKey())));
    }

    public static void select(ServerPlayer player, Identifier id) {
        if (!PowerRingItem.hasCharge(player)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }

        Optional<Data> data = data(player);
        Optional<ConstructRingPower> construct = constructs(player).stream().filter(entry -> entry.id().equals(id)).findFirst();
        if (data.isEmpty() || construct.isEmpty()) return;
        BrightestDayAttachments.setData(player, RingPowerRegistry.ARMED, data.get().withConstruct(construct.get().id()));
    }

    public static void selectAbility(ServerPlayer player, Identifier id) {
        Optional<Data> data = data(player);
        Optional<RingPower<?>> ability = abilities(player).stream().filter(entry -> entry.id().equals(id)).findFirst();
        if (data.isEmpty() || ability.isEmpty()) return;
        if (ability.get() != RingPowerRegistry.TRACTOR_BEAM) TractorManager.release(player);
        BrightestDayAttachments.setData(player, RingPowerRegistry.ARMED, data.get().withAbility(ability.get().id()));
    }

    public static Optional<RingPower<?>> selectedAbility(Player player) {
        List<RingPower<?>> abilities = abilities(player);
        if (abilities.isEmpty()) return Optional.empty();

        Optional<Identifier> selected = data(player).flatMap(Data::ability);
        return Optional.of(abilities.stream()
                .filter(ability -> selected.isPresent() && ability.id().equals(selected.get()))
                .findFirst()
                .orElse(abilities.getFirst()));
    }

    public static boolean isAbilityMode(Player player) {
        return data(player).map(Data::abilityMode).orElse(false) && !abilities(player).isEmpty();
    }

    public static Optional<RingPower<?>> activeAbility(Player player) {
        return isAbilityMode(player) ? selectedAbility(player) : Optional.empty();
    }

    public static List<RingPower<?>> abilities(Player player) {
        return List.<RingPower<?>>of(RingPowerRegistry.TRACTOR_BEAM, RingPowerRegistry.SCAN, RingPowerRegistry.CONCUSSIVE, RingPowerRegistry.ACID, RingPowerRegistry.CONVERSION, RingPowerRegistry.BERSERK, RingPowerRegistry.GATHER, RingPowerRegistry.COMMS).stream()
                .filter(power -> BrightestDayAttachments.get(player, power).isPresent())
                .toList();
    }

    public static void fire(ServerPlayer player, int radius) {
        if (player.isSpectator() || isAbilityMode(player) || CrystalManager.isEncased(player)) return;
        if (RedRage.isBerserk(player)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.berserk.no_constructs").withColor(LanternCorps.RED.color()));
            return;
        }

        ServerLevel level = player.level();
        Optional<ConstructRingPower> construct = selectedConstruct(player);
        if (construct.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.no_constructs"));
            return;
        }
        radius = construct.get().clampSize(player, radius);

        long now = level.getGameTime();
        Long last = LAST_FIRED.get(player);
        if (last != null && now - last < COOLDOWN_TICKS) return;

        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, construct.get().cost(radius))) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
            return;
        }
        LAST_FIRED.put(player, now);
        if (!Poses.channeling(player)) raise(player);
        if (RingSync.misfire(player, construct.get())) return;

        int color = CorpsColors.of(player);
        construct.get().fire(player, radius, color);
        AttackAnimS2CPayload.broadcast(player, construct.get().id(), AttackAnimS2CPayload.FIRE);
        CHARGE_STARTED.remove(player);
        RingRanks.fire(player, Trigger.CONSTRUCT, Milestone.Context.of(construct.get().id().getPath()));
        if (PowerRingItem.getWornCorps(player).orElse(null) == LanternCorps.INDIGO && !construct.get().isAvailableTo(LanternCorps.INDIGO)) {
            for (LanternCorps source : CorpsMimicry.mimicked(player, LanternCorps.INDIGO)) {
                if (!construct.get().isAvailableTo(source)) continue;
                RingRanks.fire(player, Trigger.MIMIC, Milestone.Context.NONE, source.ordinal() + 1);
                break;
            }
        }
    }

    private static Optional<Data> data(Player player) {
        return BrightestDayAttachments.get(player, RingPowerRegistry.ARMED).map(RingPowerInstance::data);
    }

    public static List<ConstructRingPower> constructs(Player player) {
        return BrightestDayAttachments.get(player).stream()
                .map(RingPowerInstance::power)
                .filter(ConstructRingPower.class::isInstance)
                .filter(power -> power != RingPowerRegistry.INSIGNIA)
                .map(ConstructRingPower.class::cast)
                .toList();
    }
}
