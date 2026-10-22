package dev.amble.core.hand;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

public enum HandDirection {
    FORWARD,
    UP,
    DOWN,
    LEFT,
    RIGHT,
    BEHIND;

    public static HandDirection byId(int id) {
        return values()[Mth.clamp(id, 0, values().length - 1)];
    }

    public Component label() {
        return Component.translatable("gui.brightestday.hand.direction." + this.name().toLowerCase(Locale.ROOT));
    }
}
