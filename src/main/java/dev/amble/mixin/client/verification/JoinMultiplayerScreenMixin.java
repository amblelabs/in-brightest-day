package dev.amble.mixin.client.verification;

import dev.amble.core.official.OfficialServer;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public class JoinMultiplayerScreenMixin {

    @Inject(method = "join", at = @At("HEAD"), cancellable = true)
    public void join(ServerData data, CallbackInfo ci) {
        if (data.ip.strip().equals(OfficialServer.IP))
            ci.cancel();
    }
}
