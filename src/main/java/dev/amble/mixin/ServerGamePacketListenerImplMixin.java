package dev.amble.mixin;

import dev.amble.core.loyalty.RingSuspension;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    // prevents "moved too quickly!"
    @ModifyVariable(method = "handlePlayerPositionChange", at = @At(value = "STORE"), name = "metersPerTick")
    public float modifyMetersPerTick(float metersPerTick) {
        if (FlightRingPower.isFlying(player)) {
            double cruiseSpeed = FlightRingPower.cruiseSpeed(player);
            metersPerTick = (float) (metersPerTick + (FlightRingPower.isBoosting(player)
                    ? FlightRingPower.boostSpeed(cruiseSpeed) : cruiseSpeed));
        }

        return metersPerTick;
    }

    // prevents "Player X standing on air - force-sending blocks below"
    @Inject(method = "forceSendPlayerSupportBlocks", at = @At("HEAD"), cancellable = true)
    public void forceSendPlayerSupportBlocks(CallbackInfo ci) {
        if (FlightRingPower.isFlying(player))
            ci.cancel();
    }

    @Inject(method = "getMaximumFlyingTicks", at = @At("HEAD"), cancellable = true)
    private void brightestday$allowRingFlight(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof Player targetPlayer && (FlightRingPower.canFly(targetPlayer) || RingSuspension.suspended(targetPlayer))) {
            cir.setReturnValue(Integer.MAX_VALUE);
        }
    }
}
