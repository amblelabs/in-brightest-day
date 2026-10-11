package dev.amble.core.items;

import dev.amble.core.sync.RingSync;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.blocks.LanternBlock;
import dev.amble.core.blocks.LanternCharging;
import dev.amble.core.oath.OathCharge;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

public class LanternBlockItem extends BlockItem {
    public static final int CHARGE_TICKS = 90;
    public static final int INSERT_TICKS = 30;
    private static final int OATH_USE_TICKS = 20 * 60 * 5;
    private static final double MAX_DRIFT = 0.2;
    private static final int PARTICLE_INTERVAL = 2;
    private static final int SOUND_INTERVAL = 10;

    private static final Map<LivingEntity, Vec3> ANCHORS = new WeakHashMap<>();

    public LanternBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public LanternCorps corps() {
        return ((LanternBlock) this.getBlock()).corps();
    }

    public static boolean isChargingLantern(Player player) {
        return player.getOffhandItem().getItem() instanceof LanternBlockItem lantern
                && ArmedRingPower.isArmed(player)
                && PowerRingItem.getWornCorps(player).orElse(null) == lantern.corps();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && context.getHand() == InteractionHand.OFF_HAND && isChargingLantern(player)) {
            return this.startCharging(context.getLevel(), player, context.getHand());
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND && isChargingLantern(player)) {
            return this.startCharging(level, player, hand);
        }
        return super.use(level, player, hand);
    }

    private InteractionResult startCharging(Level level, Player player, InteractionHand hand) {
        ItemStack ring = PowerRingItem.getWornRing(player);
        if (PowerRingItem.getRingPower(ring) >= BrightestDayComponents.MAX_POWER) return InteractionResult.PASS;

        if (!isGrounded(player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.brightestday.stand_still_to_charge"));
            return InteractionResult.FAIL;
        }

        ANCHORS.put(player, player.position());
        if (player instanceof ServerPlayer server) OathCharge.begin(server, this.corps(), false);
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    private static boolean isGrounded(Player player) {
        return player.onGround() && !FlightRingPower.isFlying(player);
    }

    private static boolean isStill(LivingEntity entity) {
        Vec3 anchor = ANCHORS.get(entity);
        if (anchor == null) return false;
        Vec3 position = entity.position();
        return Mth.lengthSquared(position.x - anchor.x, position.z - anchor.z) <= MAX_DRIFT * MAX_DRIFT;
    }

    private static boolean reciting(LivingEntity entity) {
        return entity instanceof ServerPlayer player && OathCharge.active(player);
    }

    private static void cancel(Player player) {
        ANCHORS.remove(player);
        if (player instanceof ServerPlayer server) OathCharge.end(server);
        player.stopUsingItem();
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int ticksRemaining) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide() && !player.isLocalPlayer()) return;

        if (!isGrounded(player) || !isStill(player) || !isChargingLantern(player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.brightestday.stand_still_to_charge"));
            cancel(player);
            return;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer server)) return;

        boolean oath = reciting(server);
        if (oath && !OathCharge.tick(server, this.corps().color())) {
            server.sendOverlayMessage(Component.translatable("message.brightestday.oath.silent").withColor(this.corps().color()));
            cancel(player);
            return;
        }

        int elapsed = (oath ? OATH_USE_TICKS : CHARGE_TICKS) - ticksRemaining - INSERT_TICKS;
        if (elapsed < 0) return;
        if (elapsed == 0) LanternCharging.inserted(serverLevel, player.blockPosition());

        float progress;
        if (oath) {
            progress = OathCharge.progress(server);
        } else {
            progress = (float) elapsed / (CHARGE_TICKS - INSERT_TICKS);
            ItemStack ring = PowerRingItem.getWornRing(player);
            PowerRingItem.chargeRing(ring, Mth.ceil((float) BrightestDayComponents.MAX_POWER / (CHARGE_TICKS - INSERT_TICKS)));
            if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, ring);
        }

        if (elapsed % PARTICLE_INTERVAL == 0) {
            float angle = elapsed * 0.6F;
            double radius = 0.9 - progress * 0.5;
            DustParticleOptions dust = new DustParticleOptions(this.corps().color(), 1.2F);
            for (int strand = 0; strand < 2; strand++) {
                float a = angle + strand * Mth.PI;
                serverLevel.sendParticles(dust,
                        player.getX() + Mth.cos(a) * radius, player.getY() + progress * 2.0, player.getZ() + Mth.sin(a) * radius,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (elapsed > 0 && elapsed % SOUND_INTERVAL == 0) LanternCharging.chime(serverLevel, player.blockPosition(), progress);

        if (oath && OathCharge.complete(server)) {
            this.complete(serverLevel, player, false);
            cancel(player);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            ANCHORS.remove(entity);
            return stack;
        }
        this.complete(level, player, !reciting(player));
        ANCHORS.remove(player);
        if (player instanceof ServerPlayer server) OathCharge.end(server);
        return stack;
    }

    private void complete(Level level, Player player, boolean announce) {
        ItemStack ring = PowerRingItem.getWornRing(player);
        PowerRingItem.setMaxPower(ring);
        if (this.corps() == LanternCorps.ORANGE) RingSync.restore(ring);
        PowerRingItem.awaken(player, ring);
        boolean sworn = PowerRingItem.swear(player, ring);
        if (ring == BrightestDayAttachments.getRing(player)) BrightestDayAttachments.setRing(player, sworn ? ring : ItemStack.EMPTY);
        if (!sworn) return;

        if (announce) {
            player.sendSystemMessage(Component.translatable(this.corps().oathKey())
                    .withStyle(ChatFormatting.BOLD)
                    .withColor(this.corps().color()));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        ANCHORS.remove(entity);
        if (entity instanceof ServerPlayer server) OathCharge.end(server);
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return reciting(user) ? OATH_USE_TICKS : CHARGE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }
}
