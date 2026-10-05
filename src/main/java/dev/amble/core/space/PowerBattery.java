package dev.amble.core.space;

import com.mojang.serialization.Codec;
import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayBlocks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class PowerBattery {
    private static final int VERSION = 3;
    private static final int BASE_Y = OaTerrainFeature.SURFACE_Y + 1;
    private static final int CLEAR_HEIGHT = 96;

    private static final int TIER_HEIGHT = 4;
    private static final int[] TIER_APOTHEMS = {26, 22, 18, 14};
    private static final int PEDESTAL_TOP = TIER_HEIGHT * TIER_APOTHEMS.length;
    private static final int NECK_HEIGHT = 7;
    private static final double NECK_BOTTOM_RADIUS = 11.0;
    private static final double NECK_TOP_RADIUS = 7.0;
    private static final double SPHERE_RADIUS = 16.0;
    private static final double SHELL_THICKNESS = 2.0;
    private static final int SPHERE_CENTER = PEDESTAL_TOP + NECK_HEIGHT + 14;
    private static final double LENS_RADIUS = 10.0;
    private static final double LENS_RIM = 11.5;
    private static final double CAP_RADIUS = 11.0;
    private static final int CAP_BOTTOM = SPHERE_CENTER + 12;
    private static final int CAP_TOP = SPHERE_CENTER + 21;
    private static final double BOSS_RADIUS = 4.0;
    private static final int BOSS_REACH = 19;
    private static final double HANDLE_THICKNESS = 1.3;
    private static final int HANDLE_LANDING_X = 21;
    private static final int HANDLE_LANDING_Y = TIER_HEIGHT * 2;
    private static final int HANDLE_BULGE_X = 30;
    private static final int BATTERY_EXTENT = 34;

    private static final double WALL_APOTHEM = 58.0;
    private static final double WALL_THICKNESS = 2.5;
    private static final int WALL_HEIGHT = 12;
    private static final int WALL_LIGHT_ROW = 6;
    private static final int GATE_HALF_WIDTH = 5;
    private static final int GATE_HEIGHT = 9;
    private static final double TOWER_RADIUS = 62.8;
    private static final int TOWER_HALF = 4;
    private static final int TOWER_HEIGHT = 30;
    private static final int COMPOUND_EXTENT = 68;

    public static final AttachmentType<Integer> BUILT_VERSION =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .buildAndRegister(BrightestDay.id("power_battery_version"));

    public static void init() {}

    public static void ensure(ServerLevel level) {
        if (level.getAttachedOrElse(BUILT_VERSION, 0) >= VERSION) return;
        clear(level);
        build(level);
        level.setAttached(BUILT_VERSION, VERSION);
    }

    private static void clear(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double limit = OaTerrainFeature.PLAZA_RADIUS * OaTerrainFeature.PLAZA_RADIUS;
        for (int x = -COMPOUND_EXTENT; x <= COMPOUND_EXTENT; x++) {
            for (int z = -COMPOUND_EXTENT; z <= COMPOUND_EXTENT; z++) {
                if (x * x + z * z > limit) continue;
                level.setBlock(pos.set(x, OaTerrainFeature.SURFACE_Y, z), OaTerrainFeature.plaza(x, z), Block.UPDATE_CLIENTS);
                for (int y = BASE_Y; y < BASE_Y + CLEAR_HEIGHT; y++) {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void build(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
            for (int dx = -COMPOUND_EXTENT; dx <= COMPOUND_EXTENT; dx++) {
                for (int dz = -COMPOUND_EXTENT; dz <= COMPOUND_EXTENT; dz++) {
                    BlockState state = blockAt(dx, dy, dz);
                    if (state != null) level.setBlock(pos.set(dx, BASE_Y + dy, dz), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static BlockState blockAt(int dx, int dy, int dz) {
        if (Math.abs(dx) <= BATTERY_EXTENT && Math.abs(dz) <= BATTERY_EXTENT) {
            BlockState battery = battery(dx, dy, dz);
            if (battery != null) return battery;
        }
        return compound(dx, dy, dz);
    }

    private static BlockState battery(int dx, int dy, int dz) {
        BlockState shell = green();
        BlockState dark = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        BlockState light = BrightestDayBlocks.BATTERY_LIGHT.defaultBlockState();
        double radius = Math.sqrt(dx * dx + dz * dz);

        if (dy < PEDESTAL_TOP) {
            int tier = dy / TIER_HEIGHT;
            double oct = octagon(dx, dz);
            if (oct > TIER_APOTHEMS[tier]) return handle(dx, dy, dz);
            boolean groove = dy % TIER_HEIGHT == TIER_HEIGHT - 1 && oct > TIER_APOTHEMS[tier] - 1.0;
            return groove ? dark : shell;
        }

        int neckLevel = dy - PEDESTAL_TOP;
        if (neckLevel < NECK_HEIGHT) {
            double neck = NECK_BOTTOM_RADIUS + (NECK_TOP_RADIUS - NECK_BOTTOM_RADIUS) * neckLevel / (NECK_HEIGHT - 1.0);
            if (radius <= neck) return neckLevel == 0 ? dark : shell;
        }

        double dyc = dy - SPHERE_CENTER;
        double sphere = Math.sqrt(dx * dx + dyc * dyc + dz * dz);
        if (sphere <= SPHERE_RADIUS) {
            if (sphere < SPHERE_RADIUS - SHELL_THICKNESS) return light;
            double lens = Math.sqrt(dx * dx + dyc * dyc);
            if (lens <= LENS_RADIUS) return light;
            if (lens <= LENS_RIM && Math.abs(dz) > Math.abs(dx)) return dark;
            return shell;
        }

        if (dy >= CAP_BOTTOM && dy <= CAP_TOP) {
            double capRadius = dy == CAP_TOP ? CAP_RADIUS + 1.0 : CAP_RADIUS;
            if (radius <= capRadius) return dy == CAP_TOP - 3 ? dark : shell;
        }

        int ax = Math.abs(dx);
        if (ax > SPHERE_RADIUS - 2 && ax <= BOSS_REACH) {
            double boss = Math.sqrt(dyc * dyc + dz * dz);
            if (boss <= BOSS_RADIUS) return boss <= BOSS_RADIUS - 1.5 ? shell : dark;
        }

        return handle(dx, dy, dz);
    }

    private static BlockState handle(int dx, int dy, int dz) {
        if (Math.abs(dz) > 1 || dy < HANDLE_LANDING_Y) return null;
        double ax = Math.abs(dx);
        for (int step = 0; step <= 160; step++) {
            double t = step / 160.0;
            double u = 1.0 - t;
            double hx = u * u * BOSS_REACH + 2.0 * u * t * HANDLE_BULGE_X + t * t * HANDLE_LANDING_X;
            double hy = u * u * SPHERE_CENTER + 2.0 * u * t * (SPHERE_CENTER - 4.0) + t * t * HANDLE_LANDING_Y;
            if (Math.hypot(ax - hx, dy - hy) <= HANDLE_THICKNESS) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }
        return null;
    }

    private static BlockState compound(int dx, int dy, int dz) {
        BlockState bricks = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState light = BrightestDayBlocks.BATTERY_LIGHT.defaultBlockState();

        for (int corner = 0; corner < 8; corner++) {
            double angle = Math.PI / 8.0 + corner * Math.PI / 4.0;
            int tx = (int) Math.round(Math.cos(angle) * TOWER_RADIUS);
            int tz = (int) Math.round(Math.sin(angle) * TOWER_RADIUS);
            int lx = dx - tx;
            int lz = dz - tz;
            if (Math.max(Math.abs(lx), Math.abs(lz)) > TOWER_HALF || dy >= TOWER_HEIGHT + 3) continue;
            boolean edge = Math.max(Math.abs(lx), Math.abs(lz)) == TOWER_HALF;
            if (dy >= TOWER_HEIGHT) return Math.max(Math.abs(lx), Math.abs(lz)) <= TOWER_HALF - 1 - (dy - TOWER_HEIGHT) ? light : null;
            if (dy == TOWER_HEIGHT - 1) return green();
            if (edge && (lx == 0 || lz == 0) && dy % 6 >= 2 && dy % 6 <= 4) return light;
            return edge || dy == 0 ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : null;
        }

        double oct = octagon(dx, dz);
        if (oct < WALL_APOTHEM - WALL_THICKNESS || oct > WALL_APOTHEM || dy > WALL_HEIGHT) return null;
        boolean gate = (Math.abs(dx) <= GATE_HALF_WIDTH || Math.abs(dz) <= GATE_HALF_WIDTH) && dy < GATE_HEIGHT;
        if (gate) return null;
        if (dy == WALL_HEIGHT) return Math.floorMod(dx + dz, 2) == 0 ? green() : null;
        if (dy == WALL_HEIGHT - 1) return green();
        if (dy == WALL_LIGHT_ROW) return light;
        return bricks;
    }

    private static double octagon(int dx, int dz) {
        double ax = Math.abs(dx);
        double az = Math.abs(dz);
        return Math.max(Math.max(ax, az), (ax + az) / Math.sqrt(2.0));
    }

    private static BlockState green() {
        return Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN).defaultBlockState();
    }

    private PowerBattery() {}
}
