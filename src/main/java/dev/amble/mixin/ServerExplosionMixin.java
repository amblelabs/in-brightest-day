package dev.amble.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amble.core.shields.ShieldManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;
    @Shadow @Final private Vec3 center;

    @ModifyReturnValue(method = "calculateExplodedPositions", at = @At("RETURN"))
    private List<BlockPos> brightestday$containBlocks(List<BlockPos> original) {
        if (!ShieldManager.shielded(this.level)) return original;
        List<BlockPos> kept = new ArrayList<>(original.size());
        for (BlockPos pos : original) {
            if (!ShieldManager.separates(this.level, this.center, Vec3.atCenterOf(pos))) kept.add(pos);
        }
        return kept;
    }

    @WrapOperation(method = "hurtEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean brightestday$containEntities(Entity entity, Explosion explosion, Operation<Boolean> original) {
        if (ShieldManager.shielded(this.level) && ShieldManager.separates(this.level, this.center, entity.getBoundingBox().getCenter())) return true;
        return original.call(entity, explosion);
    }
}
