package dev.amble.core;

import dev.amble.BrightestDay;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BrightestDayEffects {

    public static final Holder<MobEffect> STUNNED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, BrightestDay.id("stunned"),
            (new MobEffect(MobEffectCategory.HARMFUL, 0x0) {}));

    public static void init() {

    }
}
