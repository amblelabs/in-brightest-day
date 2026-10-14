package dev.amble.mixin;

import dev.amble.core.BrightestDayItems;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ConstructContainerItemMixin {

    @Inject(method = "canFitInsideContainerItems", at = @At("HEAD"), cancellable = true)
    private void brightestday$keepConstructsOut(CallbackInfoReturnable<Boolean> cir) {
        if (BrightestDayItems.isConstructTool((Item) (Object) this)) cir.setReturnValue(false);
    }
}
