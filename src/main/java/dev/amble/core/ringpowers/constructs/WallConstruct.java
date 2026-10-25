package dev.amble.core.ringpowers.constructs;

import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.walls.WallGeometry;
import dev.amble.core.walls.WallLines;
import dev.amble.core.walls.WallManager;
import net.minecraft.core.BlockPos;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public class WallConstruct extends ConstructRingPower {
    public static final double RANGE = 24.0;
    private static final int BASE_COST = 80;
    private static final int COST_PER_SIZE = 25;
    private static final int COST_PER_COLUMN = 6;
    private static final int HOLD_TICKS = 200;
    public static final double MAX_LENGTH = 24.0;

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
    public int chargeTicks() {
        return HOLD_TICKS;
    }

    @Override
    public int releaseTicks() {
        return 1;
    }

    @Override
    public int maxSize() {
        return 8;
    }

    @Override
    public int defaultSize() {
        return 3;
    }

    @Override
    public int empoweredMaxSize() {
        return 12;
    }

    @Override
    public Component describeSize(int size) {
        return Component.literal(this.costSize(size) + "↑");
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        WallLines.Line line = WallLines.take(player, RANGE);
        Vec3 start;
        Vec3 end;
        if (line != null) {
            start = line.start();
            end = WallGeometry.clampLength(start, line.end(), MAX_LENGTH);
        } else {
            start = WallGeometry.anchor(player.level(), player, player.getEyePosition(), player.getLookAngle(), RANGE);
            end = start;
        }
        int height = this.clampSize(player, size);
        List<BlockPos> cells = start == null ? List.of() : WallGeometry.line(start, end, height);
        int extra = Math.max(0, cells.size() / height - 1) * COST_PER_COLUMN;
        if (start != null && extra > 0 && !player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, extra)) {
            PowerRingItem.refund(player, this.cost(size));
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }
        Vec3 base = start == null ? player.position() : start.lerp(end, 0.5);
        boolean raised = start != null && WallManager.raise(player.level(), cells, color, player);
        if (!raised) {
            PowerRingItem.refund(player, this.cost(size));
            player.sendOverlayMessage(Component.translatable("message.brightestday.wall_blocked"));
            return;
        }
        player.level().playSound(null, base.x, base.y, base.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        player.level().playSound(null, base.x, base.y, base.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
