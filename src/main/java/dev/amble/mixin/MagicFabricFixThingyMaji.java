package dev.amble.mixin;

import net.fabricmc.fabric.impl.attachment.AttachmentTargetImpl;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@SuppressWarnings("UnstableApiUsage")
@Mixin(targets = "net.fabricmc.fabric.impl.attachment.AttachmentSavedData$1")
public class MagicFabricFixThingyMaji {

    @Shadow @Final AttachmentTargetImpl val$target;

    @Redirect(method = "encode(Lnet/fabricmc/fabric/impl/attachment/AttachmentSavedData;Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/TagValueOutput;createWithoutContext(Lnet/minecraft/util/ProblemReporter;)Lnet/minecraft/world/level/storage/TagValueOutput;"))
    public TagValueOutput codec(ProblemReporter problemReporter) {
        return TagValueOutput.createWithContext(problemReporter, val$target.fabric_getRegistryAccess());
    }
}
