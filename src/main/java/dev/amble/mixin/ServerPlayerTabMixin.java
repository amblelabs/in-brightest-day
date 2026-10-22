package dev.amble.mixin;

import dev.amble.core.progression.CorpsLeaders;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTabMixin {
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void brightestday$leaderMark(CallbackInfoReturnable<Component> cir) {
        Component name = CorpsLeaders.tabName((ServerPlayer) (Object) this);
        if (name != null) cir.setReturnValue(name);
    }
}
