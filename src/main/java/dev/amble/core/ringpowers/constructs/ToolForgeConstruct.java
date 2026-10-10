package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.CorpsArsenal;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public class ToolForgeConstruct extends ConstructRingPower {
    private static final int USE_COST = 150;
    private static final int COOLDOWN_TICKS = 20;

    private static final Map<ServerPlayer, Long> LAST_FORGED = new WeakHashMap<>();

    public ToolForgeConstruct() {
        super(BrightestDay.id("tool_forge"), CorpsArsenal.shared(LanternCorps.BLUE));
    }

    @Override
    public int useCost() {
        return USE_COST;
    }

    @Override
    public boolean usesGesture() {
        return true;
    }

    @Override
    public void fire(ServerPlayer player, int radius, int color) {}

    public static void forge(ServerPlayer player, ConstructTool tool) {
        if (ArmedRingPower.isAbilityMode(player) || ArmedRingPower.selectedConstruct(player).orElse(null) != RingPowerRegistry.TOOL_FORGE) return;
        ArmedRingPower.raise(player);

        long now = player.level().getGameTime();
        Long last = LAST_FORGED.get(player);
        if (last != null && now - last < COOLDOWN_TICKS) return;

        if (!player.hasInfiniteMaterials() && !PowerRingItem.consumeCharge(player, USE_COST)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring_depleted"));
            return;
        }
        LAST_FORGED.put(player, now);

        ConstructTools.dissolveAll(player);
        ItemStack stack = ConstructTools.create(player, tool);
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            player.sendOverlayMessage(Component.translatable("message.brightestday.forge_no_room"));
            return;
        }

        player.sendOverlayMessage(Component.translatable("message.brightestday.forged", stack.getHoverName()));
        ArmedRingPower.lower(player);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
    }
}
