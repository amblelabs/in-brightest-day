package dev.amble.core.ringpowers.impl;

import com.mojang.serialization.MapCodec;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.progression.IndigoOne;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPower;
import dev.amble.core.ringpowers.RingPowerCategory;
import dev.amble.core.ringpowers.RingPowerRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public class GatherRingPower extends RingPower<Unit> {
    public GatherRingPower() {
        super(BrightestDay.id("gather_tribe"), EnumSet.of(LanternCorps.INDIGO), MapCodec.unitCodec(Unit.INSTANCE));
    }

    @Override
    public RingPowerCategory category() {
        return RingPowerCategory.UTILITY;
    }

    @Override
    public Unit createData() {
        return Unit.INSTANCE;
    }

    public static void request(ServerPlayer player) {
        if (player.isSpectator() || !BrightestDayAttachments.has(player, RingPowerRegistry.GATHER)) return;
        IndigoOne.roster(player);
    }

    public static void fire(ServerPlayer player, Optional<UUID> target) {
        if (player.isSpectator() || !BrightestDayAttachments.has(player, RingPowerRegistry.GATHER)) return;
        IndigoOne.gather(player, target);
    }
}
