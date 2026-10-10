package dev.amble.core.ringpowers;

import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.BrightestDaySounds;
import dev.amble.core.items.PowerRingItem;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class RingJumpstart {
    private static final int JUMPSTART_CHARGE = BrightestDayComponents.MAX_POWER * 5 / 100;

    public static void init() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) tryJumpstart(serverPlayer, serverLevel, pos);
            return InteractionResult.PASS;
        });
    }

    private static void tryJumpstart(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.isSpectator()) return;

        ItemStack ring = deadRing(player);
        if (ring.isEmpty()) return;

        float hardness = level.getBlockState(pos).getDestroySpeed(level, pos);
        if (hardness >= 0.0F && hardness < Blocks.STONE.defaultDestroyTime()) return;

        ring.set(BrightestDayComponents.POWER_TYPE, JUMPSTART_CHARGE);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);

        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.5F, 0.7F);
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), BrightestDaySounds.RING_CHARGE_5_PERCENT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static ItemStack deadRing(ServerPlayer player) {
        ItemStack slotted = BrightestDayAttachments.getRing(player);
        ItemStack ring = slotted.getItem() instanceof PowerRingItem ? slotted : player.getMainHandItem();
        if (!(ring.getItem() instanceof PowerRingItem) || PowerRingItem.getRingPower(ring) > 0) return ItemStack.EMPTY;
        return ring;
    }

    private RingJumpstart() {}
}
