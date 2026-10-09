package dev.amble.core.ringpowers.constructs;

import dev.amble.BrightestDay;
import dev.amble.core.BrightestDayItems;
import net.minecraft.world.item.Item;

public enum ConstructTool {
    PICKAXE("pickaxe", "∧", "iron_pickaxe"),
    AXE("axe", "7", "iron_axe"),
    BATTLEAXE("battleaxe", "Z", "netherite_axe"),
    SWORD("sword", "|", "iron_sword"),
    SHOVEL("shovel", "U", "iron_shovel"),
    HOE("hoe", "L", "iron_hoe"),
    SPEAR("spear", "—", "iron_spear"),
    MACE("mace", "O", "mace"),
    FLINT_AND_STEEL("flint_and_steel", "C", "flint_and_steel"),
    SHEARS("shears", "V", "shears");

    private final String name;
    private final String glyph;
    private final String vanillaModel;

    ConstructTool(String name, String glyph, String vanillaModel) {
        this.name = name;
        this.glyph = glyph;
        this.vanillaModel = vanillaModel;
    }

    public String id() {
        return "construct_" + this.name;
    }

    public String glyph() {
        return this.glyph;
    }

    public String vanillaModel() {
        return this.vanillaModel;
    }

    public String translationKey() {
        return "item." + BrightestDay.MOD_ID + "." + this.id();
    }

    public Item item() {
        return BrightestDayItems.constructTool(this);
    }
}
