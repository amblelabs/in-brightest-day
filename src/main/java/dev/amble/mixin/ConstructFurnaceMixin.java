package dev.amble.mixin;

import dev.amble.core.ringpowers.constructs.ConstructTools;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class ConstructFurnaceMixin {

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void brightestday$noSmeltingConstructs(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ConstructTools.isConstruct(stack)) cir.setReturnValue(false);
    }
}
