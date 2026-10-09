package dev.amble.core;

import dev.amble.BrightestDay;
import dev.amble.core.items.ConstructMimics;
import dev.amble.core.items.LanternMannequinItem;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.ringpowers.constructs.ConstructTool;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.util.Unit;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class BrightestDayItems {
    private static final Map<LanternCorps, Item> RINGS = new EnumMap<>(LanternCorps.class);

    public static final Item GREEN_POWER_RING = registerRing("green_power_ring", LanternCorps.GREEN);
    public static final Item YELLOW_POWER_RING = registerRing("yellow_power_ring", LanternCorps.YELLOW);
    public static final Item RED_POWER_RING = registerRing("red_power_ring", LanternCorps.RED);
    public static final Item ORANGE_POWER_RING = registerRing("orange_power_ring", LanternCorps.ORANGE);
    public static final Item BLUE_POWER_RING = registerRing("blue_power_ring", LanternCorps.BLUE);
    public static final Item INDIGO_POWER_RING = registerRing("indigo_power_ring", LanternCorps.INDIGO);
    public static final Item STAR_SAPPHIRE_POWER_RING = registerRing("star_sapphire_power_ring", LanternCorps.STAR_SAPPHIRE);
    public static final Item WHITE_POWER_RING = registerRing("white_power_ring", LanternCorps.WHITE);
    public static final Item BLACK_POWER_RING = registerRing("black_power_ring", LanternCorps.BLACK);

    private static final Map<ConstructTool, Item> CONSTRUCT_TOOLS = new EnumMap<>(ConstructTool.class);

    public static final Item CONSTRUCT_PICKAXE = registerConstructTool(ConstructTool.PICKAXE, properties -> properties.pickaxe(ToolMaterial.DIAMOND, 1.0F, -2.8F), Item::new);
    public static final Item CONSTRUCT_AXE = registerConstructTool(ConstructTool.AXE, properties -> properties.axe(ToolMaterial.DIAMOND, 5.0F, -3.0F), Item::new);
    public static final Item CONSTRUCT_BATTLEAXE = registerConstructTool(ConstructTool.BATTLEAXE, properties -> properties.axe(ToolMaterial.NETHERITE, 8.0F, -3.2F), Item::new);
    public static final Item CONSTRUCT_SWORD = registerConstructTool(ConstructTool.SWORD, properties -> properties.sword(ToolMaterial.DIAMOND, 3.0F, -2.4F), Item::new);
    public static final Item CONSTRUCT_SHOVEL = registerConstructTool(ConstructTool.SHOVEL, properties -> properties.shovel(ToolMaterial.DIAMOND, 1.5F, -3.0F), Item::new);
    public static final Item CONSTRUCT_HOE = registerConstructTool(ConstructTool.HOE, properties -> properties.hoe(ToolMaterial.DIAMOND, -3.0F, 0.0F), Item::new);
    public static final Item CONSTRUCT_SPEAR = registerConstructTool(ConstructTool.SPEAR, properties -> properties.spear(ToolMaterial.DIAMOND, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F), Item::new);
    public static final Item CONSTRUCT_MACE = registerConstructTool(ConstructTool.MACE, properties -> properties
            .durability(500)
            .component(DataComponents.TOOL, MaceItem.createToolProperties())
            .attributes(MaceItem.createAttributes())
            .component(DataComponents.WEAPON, new Weapon(1)), MaceItem::new);
    public static final Item CONSTRUCT_FLINT_AND_STEEL = registerConstructTool(ConstructTool.FLINT_AND_STEEL, properties -> properties.durability(64), FlintAndSteelItem::new);
    public static final Item CONSTRUCT_SHEARS = registerConstructTool(ConstructTool.SHEARS, properties -> properties
            .durability(238)
            .component(DataComponents.TOOL, ShearsItem.createToolProperties()), ShearsItem::new);

    static {
        ConstructMimics.register(CONSTRUCT_SHEARS, Items.SHEARS);
        ConstructMimics.register(CONSTRUCT_FLINT_AND_STEEL, Items.FLINT_AND_STEEL);
    }

    public static final Item PARALLAX_SHARD = register("parallax_shard", id -> new Item(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id)).rarity(Rarity.RARE).fireResistant()));
    public static final Item LANTERN_MANNEQUIN = register("lantern_mannequin", id -> new LanternMannequinItem(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id)).stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final Item ZAMARON_CRYSTAL = register("zamaron_crystal", id -> new Item(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, id)).rarity(Rarity.RARE)));

    public static Item constructTool(ConstructTool tool) {
        return CONSTRUCT_TOOLS.get(tool);
    }

    private static Item registerConstructTool(ConstructTool tool, UnaryOperator<Item.Properties> configure, Function<Item.Properties, Item> factory) {
        Item item = register(tool.id(), id -> factory.apply(configure.apply(new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id))
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))));
        CONSTRUCT_TOOLS.put(tool, item);
        return item;
    }

    public static Item ring(LanternCorps corps) {
        return RINGS.get(corps);
    }

    public static Map<LanternCorps, Item> rings() {
        return Collections.unmodifiableMap(RINGS);
    }

    private static Item registerRing(String name, LanternCorps corps) {
        Item ring = register(name, id -> new PowerRingItem(
                new Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, id))
                        .fireResistant()
                        .stacksTo(1)
                        .component(BrightestDayComponents.POWER_TYPE, 0)
                        .component(BrightestDayComponents.LANTERN_CORPS, corps)
                        .component(DataComponents.TOOLTIP_STYLE, BrightestDay.id("ring"))
        ));
        RINGS.put(corps, ring);
        return ring;
    }

    public static Item register(String name, Function<Identifier, Item> factory) {
        Identifier id = BrightestDay.id(name);
        Item item = factory.apply(id);

        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    public static void init() {}
}
