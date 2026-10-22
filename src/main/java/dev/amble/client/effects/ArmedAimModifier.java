package dev.amble.client.effects;

import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import dev.amble.client.flight.FlightAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class ArmedAimModifier extends AbstractModifier {
    private final Avatar avatar;
    private float headX;
    private float headY;

    public ArmedAimModifier(Avatar avatar) {
        this.avatar = avatar;
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone bone) {
        super.get3DTransform(bone);
        if (!(this.avatar instanceof Player player)) return;
        if (FlightAnimator.landWeight(player, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)) > 0.0F) return;

        String name = bone.getName();
        if ("head".equals(name)) {
            this.headX = bone.rotation.x;
            this.headY = bone.rotation.y;
            return;
        }

        this.aim(player, name, bone);
        this.swing(player, name, bone);
    }

    private void aim(Player player, String name, PlayerAnimBone bone) {
        float amount = ArmedPose.amount(player);
        if (amount <= 0.001F) return;

        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        if (name.equals(right ? "right_arm" : "left_arm")) {
            bone.rotation.set(
                    Mth.lerp(amount, bone.rotation.x, this.headX - Mth.HALF_PI),
                    Mth.lerp(amount, bone.rotation.y, this.headY + (right ? -ArmedPose.AIM_INWARD : ArmedPose.AIM_INWARD)),
                    Mth.lerp(amount, bone.rotation.z, 0.0F));
        }
    }

    private void swing(Player player, String name, PlayerAnimBone bone) {
        LivingEntity.SwingDescription description = player.getCurrentSwing();
        float swing = player.getSwingAnimation(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (description == null || swing <= 0.0F) return;

        HumanoidArm arm = description.hand().asArm(player.getMainArm());
        if (!name.equals(arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm")) return;

        float twist = Mth.sin(Mth.sqrt(swing) * Mth.TWO_PI) * 0.2F * (arm == HumanoidArm.LEFT ? -1.0F : 1.0F);
        float lift = Mth.sin(Ease.outQuart(swing) * Mth.PI);
        float follow = Mth.sin(swing * Mth.PI) * -(this.headX - 0.7F) * 0.75F;
        bone.rotation.set(
                bone.rotation.x - (lift * 1.2F + follow),
                bone.rotation.y + twist * 2.0F,
                bone.rotation.z + Mth.sin(swing * Mth.PI) * -0.4F);
    }
}
