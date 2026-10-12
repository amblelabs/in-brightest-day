package dev.amble.mixin;

import dev.amble.core.loyalty.RingSuspension;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import dev.amble.core.space.SpaceTravel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "shouldCheckPlayerMovement", at = @At("HEAD"), cancellable = true)
    private void brightestday$warpInSpace(boolean fallFlying, CallbackInfoReturnable<Boolean> cir) {
        if (SpaceTravel.isSpace(this.player.level()) && FlightRingPower.isFlying(this.player)) cir.setReturnValue(false);
    }

    @Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
    private void brightestday$allowRingFlight(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Player player && (FlightRingPower.canFly(player) || RingSuspension.suspended(player))) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
