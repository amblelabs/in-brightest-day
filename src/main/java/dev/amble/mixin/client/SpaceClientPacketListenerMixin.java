package dev.amble.mixin.client;

import dev.amble.core.space.SpaceTravel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SpaceClientPacketListenerMixin {

    @Unique
    private boolean brightestday$seamless;

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void brightestday$checkSeamless(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;

        this.brightestday$seamless = player != null
                && !player.isDeadOrDying()
                && SpaceTravel.isSeamless(player.level().dimension(), packet.commonPlayerSpawnInfo().dimension());
    }

    @Redirect(method = "startWaitingForNewLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreenAndShow(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void brightestday$skipLoadingScreen(Minecraft minecraft, Screen screen) {
        if (this.brightestday$seamless) {
            this.brightestday$seamless = false;
            return;
        }

        minecraft.setScreenAndShow(screen);
    }
}
