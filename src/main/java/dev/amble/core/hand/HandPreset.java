package dev.amble.core.hand;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum HandPreset {
    FIST(new HandPose(100, 100, 100, 100, 75, 15)),
    OPEN(new HandPose(0, 0, 0, 0, 5, 70)),
    POINT(new HandPose(0, 100, 100, 100, 75, 15)),
    PEACE(new HandPose(0, 0, 100, 100, 75, 15)),
    THUMBS_UP(new HandPose(100, 100, 100, 100, 0, 100)),
    CLAW(new HandPose(55, 55, 55, 55, 45, 55)),
    FLAT(new HandPose(0, 0, 0, 0, 0, 0)),
    PINCH(new HandPose(60, 10, 5, 0, 60, 45));

    private final HandPose pose;

    HandPreset(HandPose pose) {
        this.pose = pose;
    }

    public HandPose pose() {
        return this.pose;
    }

    public Component label() {
        return Component.translatable("gui.brightestday.hand.pose." + this.name().toLowerCase(Locale.ROOT));
    }
}
