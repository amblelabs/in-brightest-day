package dev.amble.core.blocks;

import net.minecraft.server.level.ServerPlayer;
import dev.amble.core.progression.Milestone;
import dev.amble.core.progression.Trigger;
import dev.amble.core.progression.RingRanks;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.BrightestDayComponents;
import dev.amble.core.blockentities.LanternBlockEntity;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.sync.RingSync;
import dev.amble.core.loyalty.RingLoyalty;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

public class LanternBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final int MAX = RotationSegment.getMaxSegmentIndex();
    private static final int ROTATIONS = MAX + 1;
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape SHAPE = Shapes.or(Block.column(2.0F, 8.2F, 9.2F), Block.column(4.0F, 0.0F, 9.25F));
    public static final BooleanProperty WATERLOGGED =BlockStateProperties.WATERLOGGED;
    private static final double FRONT_CONE = 0.5;

    private final LanternCorps corps;

    public LanternBlock(LanternCorps corps, Properties properties) {
        super(properties);
        this.corps = corps;
        this.registerDefaultState(this.defaultBlockState().setValue(ROTATION, 0).setValue(WATERLOGGED, false));
    }

    public LanternCorps corps() {
        return this.corps;
    }

    public @Nullable BlockState getStateForPlacement(final BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState replacedBlockState = context.getLevel().getBlockState(pos);
        if (replacedBlockState.is(this)) {
            return replacedBlockState.setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation())).setValue(WATERLOGGED, false);
        } else {
            FluidState replacedFluidState = context.getLevel().getFluidState(pos);
            return this.defaultBlockState().setValue(ROTATION, RotationSegment.convertToSegment(context.getRotation())).setValue(WATERLOGGED, replacedFluidState.is(Fluids.WATER));
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        if (!(level instanceof ServerLevel serverLevel) || !(placer instanceof Player player)) return;

        ItemStack ring = BrightestDayAttachments.getRing(player);
        if (PowerRingItem.getCorps(ring).orElse(null) != this.corps) return;
        RingLoyalty.bind(ring, serverLevel, pos);
        BrightestDayAttachments.setRing(player, ring);
    }

    protected FluidState getFluidState(final BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    protected BlockState updateShape(final BlockState state, final LevelReader level, final ScheduledTickAccess ticks, final BlockPos pos, final Direction directionToNeighbour, final BlockPos neighbourPos, final BlockState neighbourState, final RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    protected BlockState rotate(final BlockState state, final Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), ROTATIONS));
    }

    protected BlockState mirror(final BlockState state, final Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), ROTATIONS));
    }

    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        return SHAPE;
    }

    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, WATERLOGGED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new LanternBlockEntity(worldPosition, blockState);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(stack.getItem() instanceof PowerRingItem)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return this.recharge(state, level, pos, player, stack, false);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.isSecondaryUseActive()) {
            ItemStack slotted = BrightestDayAttachments.getRing(player);
            if (slotted.isEmpty()) return super.useWithoutItem(state, level, pos, player, hitResult);
            return this.recharge(state, level, pos, player, slotted, true);
        }

        if (!level.isClientSide()) {
            ItemStack stack = new ItemStack(this.asItem());

            if (!player.addItem(stack)) {
                player.drop(stack, false, Prediction.PREDICTED);
            }

            level.removeBlock(pos, false);

            level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
        }

        return InteractionResult.SUCCESS;
    }

    public static float facingYaw(BlockState state) {
        return RotationSegment.convertToDegrees(state.getValue(ROTATION));
    }

    public static boolean isInFront(BlockState state, BlockPos pos, Player player) {
        float yaw = RotationSegment.convertToDegrees(state.getValue(ROTATION)) * Mth.DEG_TO_RAD;
        Vec3 toPlayer = player.position().subtract(Vec3.atBottomCenterOf(pos)).multiply(1.0, 0.0, 1.0);
        if (toPlayer.lengthSqr() < 1.0E-4) return false;
        return toPlayer.normalize().dot(new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw))) >= FRONT_CONE;
    }

    public static boolean isLevel(BlockPos pos, Player player) {
        int rise = pos.getY() - player.getBlockY();
        return rise == 0 || rise == 1;
    }

    private InteractionResult recharge(BlockState state, Level level, BlockPos pos, Player player, ItemStack ring, boolean slotted) {
        if (!ArmedRingPower.isArmed(player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.brightestday.arm_to_charge"));
            return InteractionResult.FAIL;
        }

        if (PowerRingItem.getCorps(ring).orElse(null) != this.corps) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.brightestday.wrong_lantern", this.corps.displayName())
                        .withColor(this.corps.color()));
            }
            return InteractionResult.FAIL;
        }

        if (!isInFront(state, pos, player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.brightestday.face_lantern"));
            return InteractionResult.FAIL;
        }

        if (!isLevel(pos, player)) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.brightestday.lantern_level"));
            return InteractionResult.FAIL;
        }

        if (PowerRingItem.getRingPower(ring) >= BrightestDayComponents.MAX_POWER) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (player instanceof ServerPlayer server && LanternRitual.begin(server, pos, state, this.corps)) return InteractionResult.SUCCESS_SERVER;
        complete(level, pos, player, ring, slotted, true);
        return InteractionResult.SUCCESS_SERVER;
    }

    public void complete(Level level, BlockPos pos, Player player, ItemStack ring, boolean slotted, boolean announce) {
        PowerRingItem.setMaxPower(ring);
        if (this.corps == LanternCorps.ORANGE) RingSync.restore(ring);
        PowerRingItem.awaken(player, ring);
        PowerRingItem.swear(player, ring);
        ring.remove(BrightestDayComponents.RING_DEATHS);
        if (player instanceof ServerPlayer server) RingRanks.fire(server, Trigger.RECHARGE, Milestone.Context.of("lantern"));
        if (level instanceof ServerLevel serverLevel) RingLoyalty.bind(ring, serverLevel, pos);
        if (slotted) BrightestDayAttachments.setRing(player, ring);

        if (announce) {
            player.sendSystemMessage(Component.translatable(this.corps.oathKey())
                    .withStyle(ChatFormatting.BOLD)
                    .withColor(this.corps.color()));
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
