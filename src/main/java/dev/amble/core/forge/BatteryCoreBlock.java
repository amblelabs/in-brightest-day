package dev.amble.core.forge;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class BatteryCoreBlock extends Block {
    private final LanternCorps corps;
    private final Supplier<Block> shell;

    public BatteryCoreBlock(LanternCorps corps, Supplier<Block> shell, Properties properties) {
        super(properties);
        this.corps = corps;
        this.shell = shell;
    }

    public LanternCorps corps() {
        return this.corps;
    }

    public Block shell() {
        return this.shell.get();
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!(level.getBlockState(pos).getBlock() instanceof BatteryCoreBlock)) CentralPowerBattery.forget(level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel server && server.dimension() == Level.OVERWORLD
                && CentralPowerBattery.atLimit(server.getServer(), context.getClickedPos(), this.corps)) {
            if (context.getPlayer() != null) {
                context.getPlayer().sendOverlayMessage(Component.translatable("message.brightestday.battery.limit", this.corps.displayName(), BrightestDayConfig.get().batteriesPerCorps).withColor(this.corps.color()));
            }
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, placer, itemStack);
        if (level instanceof ServerLevel server) CentralPowerBattery.track(server, pos, this.corps);
    }
}
