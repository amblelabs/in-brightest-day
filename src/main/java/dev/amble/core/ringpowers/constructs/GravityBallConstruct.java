package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.gravity.GravityBallManager;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class GravityBallConstruct extends ConstructRingPower {
    public static final double RANGE = 48.0;
    private static final double LIFT = 1.5;
    private static final int BASE_COST = 150;
    private static final int COST_PER_LEVEL = 80;
    private static final int COOLDOWN_TICKS = 14;

    private final boolean repels;

    public GravityBallConstruct() {
        this(BrightestDay.id("gravity_ball"), CorpsArsenal.exclusive(LanternCorps.GREEN, LanternCorps.STAR_SAPPHIRE), false);
    }

    protected GravityBallConstruct(Identifier id, Set<LanternCorps> corps, boolean repels) {
        super(id, corps);
        this.repels = repels;
    }

    @Override
    public int useCost() {
        return BASE_COST;
    }

    @Override
    public int cost(int radius) {
        return BASE_COST + COST_PER_LEVEL * (this.costSize(radius) - GravityBallManager.MIN_LEVEL);
    }

    @Override
    public int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    @Override
    public boolean usesSize() {
        return true;
    }

    @Override
    public int minSize() {
        return GravityBallManager.MIN_LEVEL;
    }

    @Override
    public int maxSize() {
        return GravityBallManager.MAX_LEVEL;
    }

    public static Vec3 target(Vec3 hit, Vec3 eye) {
        Vec3 back = eye.subtract(hit);
        Vec3 offset = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize().scale(LIFT);
        return hit.add(offset).add(0.0, LIFT * 0.5, 0.0);
    }

    @Override
    public void fire(ServerPlayer player, int size, int color) {
        Vec3 center = target(aim(player, RANGE).end(), player.getEyePosition());
        GravityBallManager.spawn(player, center, this.clampSize(player, size), color, this.repels);
    }
}
