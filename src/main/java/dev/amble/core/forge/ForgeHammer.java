package dev.amble.core.forge;

import dev.amble.core.networking.payloads.s2c.ForgeBeatS2CPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Prediction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ForgeHammer {
    public static final int BEAT_TICKS = 24;
    public static final int GOOD_WINDOW = 6;
    public static final int PERFECT_WINDOW = 2;
    private static final int GAP = 8;
    private static final double REACH = 8.0;

    private static final class Session {
        final ServerLevel level;
        final BlockPos pos;
        final ForgeRecipe recipe;
        final int color;
        final boolean lava;
        final int total;
        final List<ItemStack> taken;
        long beatAt;
        int strike;
        int perfects;
        int combo;

        Session(ServerLevel level, BlockPos pos, ForgeRecipe recipe, int color, boolean lava, List<ItemStack> taken, long beatAt) {
            this.taken = taken;
            this.level = level;
            this.pos = pos;
            this.recipe = recipe;
            this.color = color;
            this.lava = lava;
            this.total = recipe.strikes();
            this.beatAt = beatAt;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(ForgeHammer::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Session session = SESSIONS.remove(handler.player.getUUID());
            if (session != null) returnAt(session);
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!(player instanceof ServerPlayer smith)) return InteractionResult.PASS;
            Session session = SESSIONS.get(smith.getUUID());
            if (session == null || !session.pos.equals(pos)) return InteractionResult.PASS;
            strike(smith, session);
            return InteractionResult.SUCCESS;
        });
    }

    public static boolean forging(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void begin(ServerPlayer player, ServerLevel level, BlockPos pos, ForgeRecipe recipe, int color, boolean lava, List<ItemStack> taken) {
        Session session = new Session(level, pos.immutable(), recipe, color, lava, taken, level.getGameTime() + GAP + BEAT_TICKS);
        SESSIONS.put(player.getUUID(), session);
        player.sendOverlayMessage(Component.translatable("forge.brightestday.hammer.begin").withColor(color));
        level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
        sync(player, session, ForgeBeatS2CPayload.NONE_FEEDBACK);
    }

    private static void strike(ServerPlayer player, Session session) {
        long offset = Math.abs(session.level.getGameTime() - session.beatAt);
        if (offset > GOOD_WINDOW) {
            miss(player, session);
            return;
        }
        boolean perfect = offset <= PERFECT_WINDOW;
        if (perfect) session.perfects |= 1 << session.strike;
        session.combo++;
        session.strike++;

        Vec3 top = Vec3.atCenterOf(session.pos).add(0.0, 0.6, 0.0);
        float pitch = 0.8F + Math.min(session.combo, 8) * 0.07F;
        session.level.playSound(null, session.pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, pitch);
        session.level.sendParticles(ParticleTypes.CRIT, top.x, top.y, top.z, perfect ? 24 : 12, 0.25, 0.1, 0.25, 0.4);
        session.level.sendParticles(new DustParticleOptions(session.color, perfect ? 2.0F : 1.4F), top.x, top.y, top.z, perfect ? 18 : 8, 0.35, 0.15, 0.35, 0.0);
        session.level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, perfect ? 4 : 1, 0.2, 0.05, 0.2, 0.0);
        if (perfect) {
            session.level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 10, 0.2, 0.2, 0.2, 0.08);
            session.level.playSound(null, session.pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.4F, pitch + 0.3F);
        }

        if (session.strike >= session.total) {
            complete(player, session, perfect ? ForgeBeatS2CPayload.PERFECT : ForgeBeatS2CPayload.HIT);
            return;
        }
        session.beatAt = session.level.getGameTime() + GAP + BEAT_TICKS;
        sync(player, session, perfect ? ForgeBeatS2CPayload.PERFECT : ForgeBeatS2CPayload.HIT);
    }

    private static void miss(ServerPlayer player, Session session) {
        session.combo = 0;
        session.level.playSound(null, session.pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 0.6F);
        session.beatAt = session.level.getGameTime() + GAP + BEAT_TICKS;
        sync(player, session, ForgeBeatS2CPayload.MISS);
    }

    private static void complete(ServerPlayer player, Session session, int feedback) {
        SESSIONS.remove(player.getUUID());
        ServerPlayNetworking.send(player, payload(session, false, feedback));
        Vec3 top = Vec3.atCenterOf(session.pos).add(0.0, 0.8, 0.0);
        session.level.sendParticles(new DustParticleOptions(session.color, 2.2F), top.x, top.y, top.z, 40, 0.5, 0.4, 0.5, 0.0);
        session.level.sendParticles(ParticleTypes.FIREWORK, top.x, top.y, top.z, 30, 0.2, 0.2, 0.2, 0.15);
        session.level.playSound(null, session.pos, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.0F, 1.2F);
        session.level.playSound(null, session.pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.4F, 1.0F);
        SpectrumForgeBlock.craft(session.level, session.pos, session.recipe, player, session.color, session.lava);
    }

    private static void cancel(ServerPlayer player, Session session) {
        SESSIONS.remove(player.getUUID());
        ServerPlayNetworking.send(player, payload(session, false, ForgeBeatS2CPayload.NONE_FEEDBACK));
        for (ItemStack stack : session.taken) {
            if (!player.addItem(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
        }
        refundLava(session);
        Vec3 top = Vec3.atCenterOf(session.pos).add(0.0, 0.7, 0.0);
        session.level.sendParticles(ParticleTypes.SMOKE, top.x, top.y, top.z, 12, 0.3, 0.2, 0.3, 0.02);
        session.level.playSound(null, session.pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 0.6F);
        player.sendOverlayMessage(Component.translatable("forge.brightestday.hammer.cancelled").withColor(session.color));
    }

    private static void returnAt(Session session) {
        Vec3 top = Vec3.atCenterOf(session.pos).add(0.0, 0.7, 0.0);
        for (ItemStack stack : session.taken) {
            ItemEntity item = new ItemEntity(session.level, top.x, top.y, top.z, stack);
            item.setDeltaMovement(0.0, 0.2, 0.0);
            session.level.addFreshEntity(item);
        }
        refundLava(session);
    }

    private static void refundLava(Session session) {
        if (!session.lava || session.recipe.lava() <= 0) return;
        BlockState state = session.level.getBlockState(session.pos);
        if (!(state.getBlock() instanceof SpectrumForgeBlock) || !state.hasProperty(SpectrumForgeBlock.LAVA)) return;
        int lava = Math.min(SpectrumForgeBlock.MAX_LAVA, state.getValue(SpectrumForgeBlock.LAVA) + session.recipe.lava());
        session.level.setBlockAndUpdate(session.pos, state.setValue(SpectrumForgeBlock.LAVA, lava));
    }

    private static ForgeBeatS2CPayload payload(Session session, boolean active, int feedback) {
        return new ForgeBeatS2CPayload(active, session.beatAt, session.strike, session.total, session.perfects, session.color, feedback);
    }

    private static void sync(ServerPlayer player, Session session, int feedback) {
        ServerPlayNetworking.send(player, payload(session, true, feedback));
    }

    private static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (Map.Entry<UUID, Session> entry : Map.copyOf(SESSIONS).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null) {
                SESSIONS.remove(entry.getKey());
                continue;
            }
            if (player.level() != session.level || player.position().distanceTo(Vec3.atCenterOf(session.pos)) > REACH
                    || !(session.level.getBlockState(session.pos).getBlock() instanceof SpectrumForgeBlock)) {
                cancel(player, session);
                continue;
            }
            if (session.level.getGameTime() > session.beatAt + GOOD_WINDOW) miss(player, session);
        }
    }

    private ForgeHammer() {}
}
