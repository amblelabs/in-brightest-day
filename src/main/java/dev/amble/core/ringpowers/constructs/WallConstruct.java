package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.walls.WallGeometry;
import dev.amble.core.walls.WallManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class WallConstruct extends ConstructRingPower {
    public static final double RANGE = 24.0;
    private static final int BASE_COST = 80;
    private static final int COST_PER_SIZE = 25;

    public WallConstruct() {
        super(BrightestDay.id("wall"), CorpsArsenal.shared(LanternCorps.BLUE));
    }

    @Override
    public int cost(int size) {
        return BASE_COST + COST_PER_SIZE * this.costSize(size);
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int cooldownTicks() {
        return 6;
    }

    @Override
    public int maxSize() {
        return 6;
    }

    @Override
    public int defaultSize() {
        return 2;
    }

    @Override
    public int empoweredMaxSize() {
        return 9;
    }

    @Override
    public Component describeSize(int size) {
        int clamped = this.costSize(size);
        return Component.literal(WallGeometry.width(clamped) + "×" + WallGeometry.height(clamped));
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        Vec3 base = aim(player, RANGE).end();
        boolean raised = WallManager.raise(player.level(), WallGeometry.cells(base, player.getYRot(), this.clampSize(player, size)), color, player);
        if (!raised) {
            PowerRingItem.refund(player, this.cost(size));
            player.sendOverlayMessage(Component.translatable("message.brightestday.wall_blocked"));
            return;
        }
        player.level().playSound(null, base.x, base.y, base.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.level().playSound(null, base.x, base.y, base.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
