package dev.amble.core.progression;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.loyalty.RingBonds;
import dev.amble.core.rings.ConstructRings;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public final class CorpsCaps {
    public static int cap(LanternCorps corps) {
        BrightestDayConfig config = BrightestDayConfig.get();
        return switch (corps) {
            case GREEN -> config.greenCap;
            case ORANGE -> config.orangeCap;
            case YELLOW -> config.yellowCap;
            case RED -> config.redCap;
            case BLUE -> config.blueCap;
            case INDIGO -> config.indigoCap;
            case STAR_SAPPHIRE -> config.starSapphireCap;
            default -> Integer.MAX_VALUE;
        };
    }

    public static List<UUID> bearers(MinecraftServer server, LanternCorps corps) {
        return RingBonds.bearers(server, corps);
    }

    public static boolean admits(Player player, LanternCorps corps) {
        if (!(player instanceof ServerPlayer server) || player.hasInfiniteMaterials()) return true;
        List<UUID> bearers = bearers(server.level().getServer(), corps);
        return bearers.contains(player.getUUID()) || bearers.size() < cap(corps);
    }

    public static boolean admits(Player player, ItemStack ring) {
        if (ConstructRings.temporary(ring)) return true;
        return PowerRingItem.getCorps(ring).map(corps -> admits(player, corps)).orElse(true);
    }

    public static boolean check(Player player, LanternCorps corps) {
        if (admits(player, corps)) return true;
        refuse(player, corps);
        return false;
    }

    public static boolean check(Player player, ItemStack ring) {
        if (ConstructRings.temporary(ring)) return true;
        return PowerRingItem.getCorps(ring).map(corps -> check(player, corps)).orElse(true);
    }

    public static void refuse(Player player, LanternCorps corps) {
        player.sendOverlayMessage(Component.translatable("message.brightestday.corps_full", corps.displayName()).withColor(corps.color()));
    }

    private CorpsCaps() {}
}
