package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.weapon.FistManager;
import net.minecraft.server.level.ServerPlayer;

public class GiantFistConstruct extends ConstructRingPower {
    public GiantFistConstruct() {
        super(BrightestDay.id("giant_fist"), CorpsArsenal.exclusive(LanternCorps.GREEN, LanternCorps.YELLOW, LanternCorps.BLUE));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().fistCost;
    }

    @Override
    public int chargeTicks() {
        return BrightestDayConfig.get().fistChargeTicks;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        FistManager.punch(player, color);
    }
}
