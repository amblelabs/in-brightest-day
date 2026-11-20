package dev.amble.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KineticWeapon.class)
public class KineticWeaponMixin {

    @WrapOperation(method = "damageEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;stabAttack(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"))
    public boolean stabAttack(LivingEntity instance, EquipmentSlot weaponSlot, Entity target, float baseDamage, boolean dealsDamage, boolean dealsKnockback, boolean dismounts, Operation<Boolean> original) {
        if (instance instanceof Player player && FlightRingPower.isFlying(player))
            baseDamage = Math.min(baseDamage, FlightRingPower.isBoosting(player) ? 13 : 10);

        return original.call(instance, weaponSlot, target, baseDamage, dealsDamage, dealsKnockback, dismounts);
    }
}
