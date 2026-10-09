package dev.amble.core.forge;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.progression.CorpsCaps;
import dev.amble.core.progression.Emotion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class SpectrumForgeBlock extends Block {
    public static final int MAX_LAVA = 4;
    private static final int RITUAL_DARKNESS = 7;
    public static final IntegerProperty LAVA = IntegerProperty.create("lava", 0, MAX_LAVA);
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, 3);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Emotion emotion;
    private final boolean fedByLava;
    private final Supplier<List<ForgeRecipe>> recipes;

    public SpectrumForgeBlock(Emotion emotion, boolean fedByLava, Supplier<List<ForgeRecipe>> recipes, Properties properties) {
        super(properties);
        this.emotion = emotion;
        this.fedByLava = fedByLava;
        this.recipes = recipes;
        this.registerDefaultState(this.stateDefinition.any().setValue(LAVA, 0).setValue(MODE, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAVA, MODE, FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private List<ForgeRecipe> available(Level level) {
        return this.recipes.get().stream().filter(recipe -> ForgeRecipes.enabled(recipe, level)).toList();
    }

    private ForgeRecipe recipe(BlockState state, Level level) {
        List<ForgeRecipe> recipes = this.available(level);
        return recipes.get(state.getValue(MODE) % recipes.size());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!this.fedByLava || !stack.is(Items.LAVA_BUCKET)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (state.getValue(LAVA) >= MAX_LAVA) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        level.setBlockAndUpdate(pos, state.setValue(LAVA, state.getValue(LAVA) + 1));
        if (!player.hasInfiniteMaterials()) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 0.8F);
        player.sendOverlayMessage(Component.translatable("forge.brightestday.lava_level", state.getValue(LAVA) + 1, MAX_LAVA));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int color = this.emotion.corps().color();

        if (player.isSecondaryUseActive()) {
            BlockState next = state.setValue(MODE, (state.getValue(MODE) + 1) % this.available(level).size());
            level.setBlockAndUpdate(pos, next);
            player.sendOverlayMessage(this.recipe(next, level).describe().copy().withColor(color));
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.6F, 1.2F);
            return InteractionResult.SUCCESS_SERVER;
        }

        ForgeRecipe recipe = this.recipe(state, level);
        if (!ForgeRecipes.worthy(recipe, this.emotion, player)) {
            player.sendOverlayMessage(Component.translatable("forge.brightestday.unworthy." + this.emotion.getSerializedName()).withColor(color));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (this.emotion == Emotion.FEAR && !player.hasInfiniteMaterials() && (!level.isDarkOutside() || level.getBrightness(LightLayer.SKY, pos.above()) - level.getSkyDarken() > RITUAL_DARKNESS)) {
            player.sendOverlayMessage(Component.translatable("forge.brightestday.ritual.fear").withColor(color));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (this.fedByLava && state.getValue(LAVA) < recipe.lava()) {
            player.sendOverlayMessage(Component.translatable("forge.brightestday.needs_lava", recipe.lava()).withColor(color));
            return InteractionResult.SUCCESS_SERVER;
        }
        for (ItemStack output : recipe.outputs().apply(player)) {
            if (output.getItem() instanceof PowerRingItem && !CorpsCaps.check(player, output)) return InteractionResult.SUCCESS_SERVER;
        }
        if (!recipe.affordable(player)) {
            player.sendOverlayMessage(recipe.describe().copy().withColor(color));
            return InteractionResult.SUCCESS_SERVER;
        }

        if (!(player instanceof ServerPlayer smith) || !(level instanceof ServerLevel server) || ForgeHammer.forging(smith)) return InteractionResult.SUCCESS_SERVER;

        recipe.consume(player);
        if (this.fedByLava) level.setBlockAndUpdate(pos, state.setValue(LAVA, state.getValue(LAVA) - recipe.lava()));
        ForgeHammer.begin(smith, server, pos, recipe, color, this.fedByLava);
        return InteractionResult.SUCCESS_SERVER;
    }

    static void craft(ServerLevel level, BlockPos pos, ForgeRecipe recipe, Player player, int color, boolean lava) {
        for (ItemStack output : recipe.outputs().apply(player)) {
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, output);
            item.setDeltaMovement(0.0, 0.2, 0.0);
            level.addFreshEntity(item);
        }
        level.sendParticles(new DustParticleOptions(color, 2.0F), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.0);
        level.sendParticles(lava ? ParticleTypes.LAVA : ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.05);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
    }
}
