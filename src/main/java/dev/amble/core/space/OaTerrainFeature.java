package dev.amble.core.space;

import com.mojang.serialization.MapCodec;
import dev.amble.core.BrightestDayBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

public record OaTerrainFeature() implements Feature {
    public static final MapCodec<OaTerrainFeature> CODEC = MapCodec.unit(OaTerrainFeature::new);

    public static final int SURFACE_Y = 63;
    public static final double PLAZA_RADIUS = 68.0;
    private static final double LIGHT_RING = 30.0;
    private static final double RAMP_END = 130.0;
    private static final double HILL_SCALE = 72.0;
    private static final double HILL_DETAIL_SCALE = 26.0;
    private static final float HILL_HEIGHT = 14.0F;
    private static final float HILL_DETAIL = 3.0F;
    private static final int SPIRE_CELL = 36;
    private static final int PILLAR_CELL = 24;
    private static final int BAND_HEIGHT = 6;

    @Override
    public MapCodec<OaTerrainFeature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        ChunkPos chunk = ChunkPos.containing(origin);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                column(level, pos, x, z);
            }
        }
        return true;
    }

    private static void column(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        double radius = Math.sqrt((double) x * x + (double) z * z);
        if (radius <= PLAZA_RADIUS) {
            level.setBlock(pos.set(x, SURFACE_Y, z), plaza(x, z), 2);
            return;
        }

        float ramp = (float) Mth.clamp((radius - PLAZA_RADIUS) / (RAMP_END - PLAZA_RADIUS), 0.0, 1.0);
        ramp = ramp * ramp * (3.0F - 2.0F * ramp);
        int hill = Math.round(hillHeight(x, z) * ramp);
        int spire = Math.round(spireHeight(x, z) * ramp);
        int pillar = Math.round(pillarHeight(x, z) * ramp);

        for (int y = SURFACE_Y; y <= SURFACE_Y + hill; y++) {
            level.setBlock(pos.set(x, y, z), groundBlock(y, SURFACE_Y + hill), 2);
        }

        int top = SURFACE_Y + Math.max(hill, Math.max(spire, pillar));
        if (pillar > spire && pillar > hill) {
            for (int y = SURFACE_Y + hill + 1; y <= top; y++) {
                level.setBlock(pos.set(x, y, z), y == top ? Blocks.POLISHED_BASALT.defaultBlockState() : Blocks.BASALT.defaultBlockState(), 2);
            }
        } else if (spire > hill) {
            int offset = Math.round(hash(Math.floorDiv(x, SPIRE_CELL), Math.floorDiv(z, SPIRE_CELL), 9) * BAND_HEIGHT);
            for (int y = SURFACE_Y + hill + 1; y <= top; y++) {
                level.setBlock(pos.set(x, y, z), spireBlock(y + offset, top - y), 2);
            }
        }
    }

    private static float hillHeight(int x, int z) {
        float broad = valueNoise(x / HILL_SCALE, z / HILL_SCALE, 3);
        float detail = valueNoise(x / HILL_DETAIL_SCALE, z / HILL_DETAIL_SCALE, 4);
        return broad * broad * HILL_HEIGHT + detail * HILL_DETAIL;
    }

    private static float spireHeight(int x, int z) {
        int cellX = Math.floorDiv(x, SPIRE_CELL);
        int cellZ = Math.floorDiv(z, SPIRE_CELL);
        float best = 0.0F;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (hash(cx, cz, 11) > 0.6F) continue;
                double centerX = (cx + 0.2 + hash(cx, cz, 12) * 0.6) * SPIRE_CELL;
                double centerZ = (cz + 0.2 + hash(cx, cz, 13) * 0.6) * SPIRE_CELL;
                float height = 50.0F + (float) Math.pow(hash(cx, cz, 14), 1.3) * 140.0F;
                float reach = 7.0F + hash(cx, cz, 15) * 9.0F;
                double distance = Math.hypot(x + 0.5 - centerX, z + 0.5 - centerZ);
                if (distance >= reach) continue;
                double t = 1.0 - distance / reach;
                best = Math.max(best, (float) (height * Math.pow(t, 1.6)));
            }
        }
        return best;
    }

    private static float pillarHeight(int x, int z) {
        int cellX = Math.floorDiv(x, PILLAR_CELL);
        int cellZ = Math.floorDiv(z, PILLAR_CELL);
        float best = 0.0F;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                if (hash(cx, cz, 21) > 0.28F) continue;
                double centerX = (cx + 0.3 + hash(cx, cz, 22) * 0.4) * PILLAR_CELL;
                double centerZ = (cz + 0.3 + hash(cx, cz, 23) * 0.4) * PILLAR_CELL;
                float reach = 4.0F + hash(cx, cz, 24) * 4.0F;
                double distance = Math.hypot(x + 0.5 - centerX, z + 0.5 - centerZ);
                if (distance >= reach) continue;
                float tall = 14.0F + hash(cx, cz, 25) * 30.0F;
                float column = 0.6F + 0.4F * hash(Math.floorDiv(x, 2), Math.floorDiv(z, 2), 26);
                best = Math.max(best, tall * column * (float) (1.0 - 0.4 * distance / reach));
            }
        }
        return best;
    }

    private static BlockState spireBlock(int y, int fromTop) {
        if (fromTop < 3) return Blocks.TUFF.defaultBlockState();
        return switch (Math.floorMod(Math.floorDiv(y, BAND_HEIGHT), 5)) {
            case 1 -> Blocks.BLACKSTONE.defaultBlockState();
            case 3 -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            default -> Blocks.DEEPSLATE.defaultBlockState();
        };
    }

    private static BlockState groundBlock(int y, int top) {
        if (y == top) return top - SURFACE_Y > 6 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
        return Blocks.DEEPSLATE.defaultBlockState();
    }

    public static BlockState plaza(int x, int z) {
        double radius = Math.sqrt((double) x * x + (double) z * z);
        if (Math.abs(radius - LIGHT_RING) < 0.6) return BrightestDayBlocks.BATTERY_LIGHT.defaultBlockState();
        double angle = Math.atan2(z, x) / (Math.PI / 4.0);
        boolean spoke = radius > LIGHT_RING && Math.abs(angle - Math.round(angle)) * radius * Math.PI / 4.0 < 1.0;
        if (spoke) return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        return Math.floorMod((int) (radius / 5.0), 2) == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    private static float valueNoise(double x, double z, int seed) {
        int x0 = Mth.floor(x);
        int z0 = Mth.floor(z);
        float fx = (float) (x - x0);
        float fz = (float) (z - z0);
        fx = fx * fx * (3.0F - 2.0F * fx);
        fz = fz * fz * (3.0F - 2.0F * fz);
        return Mth.lerp(fz, Mth.lerp(fx, hash(x0, z0, seed), hash(x0 + 1, z0, seed)), Mth.lerp(fx, hash(x0, z0 + 1, seed), hash(x0 + 1, z0 + 1, seed)));
    }

    private static float hash(int x, int z, int salt) {
        int h = x * 374761393 + z * 668265263 + salt * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0F;
    }
}
