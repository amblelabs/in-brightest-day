package dev.amble.core.items;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public class LoreBookItem extends Item {
    private static Consumer<Player> opener = player -> {};

    public LoreBookItem(Properties properties) {
        super(properties);
    }

    public static void setOpener(Consumer<Player> open) {
        opener = open;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            opener.accept(player);
            player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
