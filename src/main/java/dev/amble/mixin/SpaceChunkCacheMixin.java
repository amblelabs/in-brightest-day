package dev.amble.mixin;

import dev.amble.core.space.Cosmos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class SpaceChunkCacheMixin {

    @Unique
    private static final int SECTOR_DISTANCE = 3;

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    public abstract void setViewDistance(int newDistance);

    @Shadow
    public abstract void setSimulationDistance(int simulationDistance);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void brightestday$sectorDistance(CallbackInfo ci) {
        if (!Cosmos.isSector(this.level.dimension())) return;
        this.setViewDistance(SECTOR_DISTANCE);
        this.setSimulationDistance(SECTOR_DISTANCE);
    }

    @ModifyVariable(method = "setViewDistance", at = @At("HEAD"), argsOnly = true)
    private int brightestday$capView(int distance) {
        return Cosmos.isSector(this.level.dimension()) ? Math.min(distance, SECTOR_DISTANCE) : distance;
    }

    @ModifyVariable(method = "setSimulationDistance", at = @At("HEAD"), argsOnly = true)
    private int brightestday$capSimulation(int distance) {
        return Cosmos.isSector(this.level.dimension()) ? Math.min(distance, SECTOR_DISTANCE) : distance;
    }
}
