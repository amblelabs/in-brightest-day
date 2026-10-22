package dev.amble.mixin.client;

import dev.amble.client.effects.LanternChargeAnimations;
import net.minecraft.world.phys.Vec3;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.flight.FlightAnimator;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow @Final private static Vector3fc FORWARDS;
    @Shadow @Final private static Vector3fc UP;
    @Shadow @Final private static Vector3fc LEFT;

    @Shadow private @Nullable Entity entity;
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private int matrixPropertiesDirty;
    @Shadow private Vec3 position;
    @Shadow private boolean detached;

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void brightestday$followRitualHead(float partialTicks, CallbackInfo ci) {
        if (this.detached || !(this.entity instanceof Player player) || LanternChargeAnimations.ritualYaw(player) == null) return;
        Vec3 offset = LanternChargeAnimations.headOffset(player);
        if (offset.lengthSqr() > 1.0E-6) this.setPosition(this.position.add(offset));
    }

    @ModifyConstant(method = "tickFov", constant = @Constant(floatValue = 1.5F))
    private float brightestday$raiseFovCapWhileFlying(float max) {
        return this.entity instanceof Player player && FlightAnimator.isAnimating(player) ? FlightAnimator.MAX_FOV_MODIFIER : max;
    }

    @ModifyReturnValue(method = "calculateFov", at = @At("RETURN"))
    private float brightestday$limitFovWhileFlying(float fov) {
        return this.entity instanceof Player player && FlightAnimator.isAnimating(player) ? Math.min(fov, FlightAnimator.maxFov()) : fov;
    }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void brightestday$bankWhileFlying(float partialTicks, CallbackInfo ci) {
        if (!(this.entity instanceof Player player)) return;

        float roll = FlightAnimator.cameraRoll(player, partialTicks);
        float shake = player == Minecraft.getInstance().player ? BlastEffects.cameraShake(partialTicks) : 0.0F;
        if (Math.abs(roll) < 0.01F && shake < 0.01F) return;

        float time = player.tickCount + partialTicks;
        this.rotation.rotateY(Mth.sin(time * 2.3F) * shake * Mth.DEG_TO_RAD);
        this.rotation.rotateX(Mth.sin(time * 3.1F + 1.7F) * shake * Mth.DEG_TO_RAD);
        this.rotation.rotateZ((-roll + Mth.sin(time * 2.7F + 0.6F) * shake * 0.5F) * Mth.DEG_TO_RAD);
        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
        this.matrixPropertiesDirty |= 3;
    }
}
