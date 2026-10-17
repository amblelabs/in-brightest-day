package dev.amble.core.progression;

import dev.amble.core.loyalty.RingBonds;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayBlocks;
import dev.amble.core.blocks.BlueShrineBlock;
import dev.amble.core.networking.payloads.s2c.SanctuaryS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

public final class BlueSanctuary {
    private static final int BLUE = LanternCorps.BLUE.color();
    private static final Identifier STRUCTURE = BrightestDay.id("blue_lantern_sanctuary");
    private static final BlockPos ANCHOR = new BlockPos(17, 1, 13);
    public static final Vec3 RING_OFFSET = new Vec3(0.0, 41.0, 0.0);
    private static final double RING_REACH = 2.0;
    private static final double BARRIER_RADIUS = 18.0;
    private static final double BARRIER_HEIGHT = 48.0;
    private static final double BARRIER_BELOW = 4.0;
    private static final double VISIBLE_RANGE = 64.0;
    private static final double BARRIER_PUSH = 0.9;
    private static final int SHELL_PARTICLES = 90;
    private static final int FOUNDATION_DEPTH = 16;
    private static final int PLACE_INTERVAL = 40;
    public static final long BLESSING_COOLDOWN = 24000L;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(BlueSanctuary::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, payload(server)));
    }

    private static SanctuaryS2CPayload payload(MinecraftServer server) {
        WorldProgress state = WorldProgress.get(server);
        return new SanctuaryS2CPayload(state.sanctuaryBuilt() ? state.sanctuary() : Optional.empty());
    }

    private static void broadcast(MinecraftServer server) {
        SanctuaryS2CPayload payload = payload(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, payload);
    }

    private static BlockPos spawn(ServerLevel level) {
        return level.getRespawnData().globalPos().pos();
    }

    public static BlockPos locate(ServerLevel level) {
        return sanctuary(level);
    }

    public static BlockPos buildNow(ServerLevel level) {
        BlockPos target = sanctuary(level);
        level.getChunk(target.getX() >> 4, target.getZ() >> 4);
        BlockPos built = buildShrine(level, target);
        WorldProgress.update(level.getServer(), current -> current.withSanctuary(built, true));
        broadcast(level.getServer());
        return built;
    }

    private static BlockPos sanctuary(ServerLevel level) {
        WorldProgress state = WorldProgress.get(level.getServer());
        if (state.sanctuary().isPresent()) return state.sanctuary().get();

        RandomSource random = RandomSource.create(level.getSeed() ^ 0xB1E5L);
        float angle = random.nextFloat() * Mth.TWO_PI;
        int distance = BrightestDayConfig.get().bluePathDistance;
        BlockPos origin = spawn(level);
        BlockPos pos = new BlockPos(origin.getX() + Mth.floor(Mth.cos(angle) * distance), 0, origin.getZ() + Mth.floor(Mth.sin(angle) * distance));
        WorldProgress.update(level.getServer(), current -> current.withSanctuary(pos, false));
        return pos;
    }

    public static int shrineCount() {
        return postCount(null);
    }

    private static int postCount(ServerLevel level) {
        return BrightestDayConfig.get().bluePathDistance / Math.max(8, BrightestDayConfig.get().bluePathSpacing);
    }

    private static BlockPos post(ServerLevel level, BlockPos target, int index) {
        BlockPos origin = spawn(level);
        double t = (index + 1) / (double) (postCount(level) + 1);
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        double wobble = Math.sin(index * 0.35) * 12.0 + Math.sin(index * 0.11) * 20.0;
        double x = origin.getX() + dx * t - dz / length * wobble * Math.sin(t * Math.PI);
        double z = origin.getZ() + dz * t + dx / length * wobble * Math.sin(t * Math.PI);
        return new BlockPos(Mth.floor(x), 0, Mth.floor(z));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (server.getTickCount() % PLACE_INTERVAL == 0) place(level);
        if (server.getTickCount() % 5 != 0) return;

        WorldProgress state = WorldProgress.get(server);
        if (!state.sanctuaryBuilt() || state.sanctuary().isEmpty()) return;
        Vec3 center = Vec3.atBottomCenterOf(state.sanctuary().get());
        Vec3 ring = ringCenter(state.sanctuary().get());
        boolean watched = false;
        for (ServerPlayer player : level.players()) {
            double horizontal = Math.sqrt(Mth.lengthSquared(player.getX() - center.x, player.getZ() - center.z));
            if (horizontal > VISIBLE_RANGE) continue;
            watched = true;
            if (player.isSpectator()) continue;

            boolean hopeful = SpectrumMeters.passes(player, Emotion.HOPE);
            boolean walked = Pilgrimage.complete(player);
            double height = player.getY() - center.y;
            boolean inside = horizontal < BARRIER_RADIUS + 0.5 && height > -BARRIER_BELOW && height < BARRIER_HEIGHT;
            if (!(hopeful && walked) && inside && !player.isCreative()) {
                Vec3 out = player.position().subtract(center).multiply(1.0, 0.0, 1.0);
                out = out.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
                player.setDeltaMovement(out.scale(BARRIER_PUSH).add(0.0, 0.3, 0.0));
                player.needsSync = true;
                player.sendOverlayMessage(Component.translatable(walked ? "message.brightestday.sanctuary.barred" : "message.brightestday.sanctuary.unwalked").withColor(BLUE));
            } else if (hopeful && walked && player.getBoundingBox().getCenter().distanceTo(ring) < RING_REACH && state.mayBeBlessed(player.getUUID(), server.overworld().getGameTime(), BLESSING_COOLDOWN)) {
                bless(player);
            }
        }

        if (watched && server.getTickCount() % 10 == 0) {
            DustParticleOptions dust = new DustParticleOptions(BLUE, 1.6F);
            RandomSource random = level.getRandom();
            for (int i = 0; i < SHELL_PARTICLES; i++) {
                double theta = random.nextDouble() * Mth.TWO_PI;
                double phi = Math.acos(random.nextDouble());
                Vec3 point = center.add(Math.sin(phi) * Math.cos(theta) * BARRIER_RADIUS, Math.cos(phi) * BARRIER_HEIGHT, Math.sin(phi) * Math.sin(theta) * BARRIER_RADIUS);
                level.sendParticles(dust, point.x, point.y, point.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    private static void bless(ServerPlayer player) {
        WorldProgress.update(player.level().getServer(), state -> state.withBlessed(player.getUUID(), player.level().getGameTime()));
        Pilgrimage.reset(player);
        player.sendSystemMessage(Component.translatable("message.brightestday.sanctuary.blessed").withStyle(ChatFormatting.BOLD).withColor(BLUE));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 1.2F);
        if (BrightestDayAttachments.getRing(player).isEmpty() && !RingBonds.bonded(player) && CorpsCaps.check(player, LanternCorps.BLUE) && EmotionGates.allows(player, LanternCorps.BLUE)) RingOffers.bestow(player, LanternCorps.BLUE);
    }

    private static void place(ServerLevel level) {
        BlockPos target = sanctuary(level);
        WorldProgress state = WorldProgress.get(level.getServer());
        int count = postCount(level);
        for (int i = 0; i < count; i++) {
            if (state.shrine(i).isPresent()) continue;
            BlockPos column = post(level, target, i);
            if (!level.isLoaded(column)) continue;
            BlockPos toward = i + 1 < count ? post(level, target, i + 1) : target;
            BlockPos existing = existingShrine(level, column);
            BlockPos built = existing != null ? existing : buildShrinePost(level, column, Direction.getApproximateNearest(toward.getX() - column.getX(), 0, toward.getZ() - column.getZ()));
            int index = i;
            WorldProgress.update(level.getServer(), current -> current.withShrine(index, built));
        }

        if (!state.sanctuaryBuilt() && level.isLoaded(target)) {
            BlockPos built = buildShrine(level, target);
            WorldProgress.update(level.getServer(), current -> current.withSanctuary(built, true));
            broadcast(level.getServer());
        }
    }

    private static BlockPos buildShrinePost(ServerLevel level, BlockPos column, Direction facing) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockPos ground = new BlockPos(column.getX(), y, column.getZ());
        if (!level.getFluidState(ground.below()).isEmpty()) {
            for (BlockPos pos : BlockPos.betweenClosed(ground.offset(-1, -1, -1), ground.offset(1, -1, 1))) {
                level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int dy = 0; dy < 2; dy++) level.setBlockAndUpdate(ground.above(dy), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(ground, BrightestDayBlocks.BLUE_LANTERN_SHRINE.defaultBlockState().setValue(BlueShrineBlock.FACING, facing));
        return ground;
    }

    private static @Nullable BlockPos existingShrine(ServerLevel level, BlockPos column) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(column.getX() - 1, y - 6, column.getZ() - 1, column.getX() + 1, y + 2, column.getZ() + 1)) {
            if (level.getBlockState(pos).is(BrightestDayBlocks.BLUE_LANTERN_SHRINE)) return pos.immutable();
        }
        return null;
    }

    public static Vec3 ringCenter(BlockPos anchor) {
        return Vec3.atBottomCenterOf(anchor).add(RING_OFFSET);
    }

    private static BlockPos buildShrine(ServerLevel level, BlockPos column) {
        Optional<StructureTemplate> found = level.getStructureTemplateManager().get(STRUCTURE);
        if (found.isEmpty()) {
            BrightestDay.LOGGER.error("Missing structure template {}", STRUCTURE);
            return column;
        }
        StructureTemplate template = found.get();
        Vec3i size = template.getSize();
        BlockPos origin = new BlockPos(column.getX() - ANCHOR.getX(), groundHeight(level, column, size) - ANCHOR.getY(), column.getZ() - ANCHOR.getZ());
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);

        BlockState foundation = Blocks.STONE.defaultBlockState();
        for (int dx = 0; dx < size.getX(); dx++) {
            for (int dz = 0; dz < size.getZ(); dz++) {
                if (level.getBlockState(origin.offset(dx, 0, dz)).isAir()) continue;
                for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
                    BlockPos below = origin.offset(dx, -depth, dz);
                    BlockState current = level.getBlockState(below);
                    if (!current.isAir() && current.getFluidState().isEmpty() && !current.canBeReplaced()) break;
                    level.setBlock(below, foundation, Block.UPDATE_CLIENTS);
                }
            }
        }
        return origin.offset(ANCHOR);
    }

    private static int groundHeight(ServerLevel level, BlockPos column, Vec3i size) {
        int[] heights = new int[9];
        int index = 0;
        for (int sx = 0; sx < 3; sx++) {
            for (int sz = 0; sz < 3; sz++) {
                int x = column.getX() - ANCHOR.getX() + sx * (size.getX() - 1) / 2;
                int z = column.getZ() - ANCHOR.getZ() + sz * (size.getZ() - 1) / 2;
                heights[index++] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            }
        }
        Arrays.sort(heights);
        return heights[heights.length / 2];
    }

    private BlueSanctuary() {}
}
