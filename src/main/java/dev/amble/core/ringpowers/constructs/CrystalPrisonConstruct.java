package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.attacks.projectile.CrystalManager;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.server.level.ServerPlayer;

public class CrystalPrisonConstruct extends ConstructRingPower {
    public CrystalPrisonConstruct() {
        super(BrightestDay.id("crystal_prison"), CorpsArsenal.exclusive(LanternCorps.STAR_SAPPHIRE));
    }

    @Override
    public int useCost() {
        return BrightestDayConfig.get().crystalCost;
    }

    @Override
    public int cooldownTicks() {
        return 15;
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        CrystalManager.launch(player, color);
    }
}
