package dev.amble.mixin.client.verification;

import dev.amble.config.BrightestDayConfig;
import dev.amble.verification.BetaPlayerLimitTracker;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "getMaxPlayers", at = @At("RETURN"), cancellable = true)
    public void getMaxPlayers(CallbackInfoReturnable<Integer> cir) {
        if (!BrightestDayConfig.get().verifiedOnly)
            cir.setReturnValue(Math.min(cir.getReturnValueI(), BetaPlayerLimitTracker.getMaxPlayerCount() + 1));
    }
}
