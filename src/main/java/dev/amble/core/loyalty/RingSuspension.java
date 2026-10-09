package dev.amble.core.loyalty;

import dev.amble.BrightestDay;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class RingSuspension {
    private static final double LIFT = 2.0;
    private static final double STEP = 0.25;
    private static final double EASE = 0.2;
    private static final double MAX_SPEED = 0.25;

    public static final AttachmentType<Double> HEIGHT = AttachmentRegistry.<Double>builder()
            .syncWith(ByteBufCodecs.DOUBLE, AttachmentSyncPredicate.all())
            .buildAndRegister(BrightestDay.id("ring_suspension"));

    public static void init() {}

    public static void lift(Player player) {
        double rise = 0.0;
        while (rise + STEP <= LIFT && player.level().noCollision(player, player.getBoundingBox().move(0.0, rise + STEP, 0.0))) rise += STEP;
        if (rise < STEP) return;
        player.setAttached(HEIGHT, player.getY() + rise);
    }

    public static void release(Player player) {
        if (player.removeAttached(HEIGHT) != null) player.resetFallDistance();
    }

    public static boolean suspended(Player player) {
        return player.hasAttached(HEIGHT);
    }

    public static void hold(Player player) {
        Double height = player.getAttached(HEIGHT);
        if (height == null) return;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, Mth.clamp((height - player.getY()) * EASE, -MAX_SPEED, MAX_SPEED), motion.z);
        player.resetFallDistance();
    }

    private RingSuspension() {}
}
