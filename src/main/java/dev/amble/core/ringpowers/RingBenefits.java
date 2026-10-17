package dev.amble.core.ringpowers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.tags.DamageTypeTags;
import dev.amble.BrightestDay;
import dev.amble.core.items.PowerRingItem;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import dev.amble.core.progression.RingRanks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.function.ToDoubleFunction;

public final class RingBenefits {
    private static final Identifier MODIFIER_ID = BrightestDay.id("ring_benefits");
    private static final Identifier BURDEN_ID = BrightestDay.id("orange_burden");
    private static final double[] RANK_ARMOR = {4.0, 8.0, 16.0, 20.0};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final int REGEN_INTERVAL = 40;
    private static final float REGEN_AMOUNT = 1.0F;
    private static final int WATER_DRAIN_PER_SECOND = 1;
    private static final int LAVA_DRAIN_PER_SECOND = 3;

    private record Bonus(Holder<Attribute> attribute, ToDoubleFunction<Player> amount, AttributeModifier.Operation operation) {
        Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
            this(attribute, player -> amount, operation);
        }
    }

    private static final List<Bonus> BONUSES = List.of(
            new Bonus(Attributes.ATTACK_DAMAGE, 3.0, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.ARMOR, RingBenefits::armor, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.ARMOR_TOUGHNESS, 2.0, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.KNOCKBACK_RESISTANCE, 0.25, AttributeModifier.Operation.ADD_VALUE),
            new Bonus(Attributes.MOVEMENT_SPEED, 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Bonus(Attributes.BLOCK_BREAK_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Bonus(Attributes.SUBMERGED_MINING_SPEED, 0.8, AttributeModifier.Operation.ADD_VALUE)
    );

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof Player player && source.is(DamageTypeTags.IS_FALL) && isActive(player)));
    }

    public static boolean isActive(Player player) {
        return !PowerRingItem.getWornRing(player).isEmpty() && PowerRingItem.hasCharge(player);
    }

    public static void tick(ServerPlayer player, int serverTick) {
        boolean active = isActive(player);
        for (Bonus bonus : BONUSES) {
            AttributeInstance instance = player.getAttribute(bonus.attribute());
            if (instance == null) continue;

            apply(instance, MODIFIER_ID, active ? bonus.amount().applyAsDouble(player) : 0.0, bonus.operation());
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) apply(speed, BURDEN_ID, -OrangeHoard.slowdown(player), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        if (!PowerRingItem.getWornRing(player).isEmpty()) shedArmor(player);
        if (!active) return;

        if (player.isOnFire()) player.clearFire();
        if (player.getFoodData().getFoodLevel() < 1) player.getFoodData().setFoodLevel(1);

        if (serverTick % REGEN_INTERVAL == 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
            player.heal(REGEN_AMOUNT);
        }

        if (serverTick % 20 == 0 && !player.hasInfiniteMaterials()) {
            if (player.isInLava() && !RedRage.wearsRed(player)) PowerRingItem.drainWorn(player, LAVA_DRAIN_PER_SECOND);
            else if (player.isUnderWater()) PowerRingItem.drainWorn(player, WATER_DRAIN_PER_SECOND);
        }
    }

    private static double armor(Player player) {
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
        int rank = corps == null ? 1 : RingRanks.rank(player, corps);
        return RANK_ARMOR[Mth.clamp(rank, 1, RANK_ARMOR.length) - 1];
    }

    private static void apply(AttributeInstance instance, Identifier id, double amount, AttributeModifier.Operation operation) {
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0.0) {
            if (current != null) instance.removeModifier(id);
            return;
        }
        if (current != null && current.amount() == amount && current.operation() == operation) return;
        if (current != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static void shedArmor(ServerPlayer player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !armored(stack)) continue;
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.addItem(stack)) player.drop(stack, false, Prediction.SERVER_ONLY);
            player.sendOverlayMessage(Component.translatable("message.brightestday.ring.no_armor"));
        }
    }

    private static boolean armored(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR) || entry.attribute().equals(Attributes.ARMOR_TOUGHNESS));
    }

    private RingBenefits() {}
}
