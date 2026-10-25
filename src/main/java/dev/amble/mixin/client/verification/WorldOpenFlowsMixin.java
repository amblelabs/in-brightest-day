package dev.amble.mixin.client.verification;

import dev.amble.verification.BetaVerification;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {

    @Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
    public void openWorld(String levelId, Runnable onCancel, CallbackInfo ci) {
        if (BetaVerification.isBetaLocked())
            ci.cancel();
    }

    @Inject(method = "openWorldLoadLevelData", at = @At("HEAD"))
    public void openWorldLoadLevelData(LevelStorageSource.LevelStorageAccess worldAccess, Runnable onCancel, CallbackInfo ci) {
        if (BetaVerification.isBetaLocked()) {
            worldAccess.safeClose();
            onCancel.run();
        }
    }
}
