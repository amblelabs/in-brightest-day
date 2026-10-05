package dev.amble.client.space;

import dev.amble.BrightestDay;
import dev.amble.core.space.Cosmos;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SpaceHud {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final int SHOWN = 3;
    private static final int TOP = 6;
    private static final int LINE = 11;
    private static final double VERTICAL_HINT_DEGREES = 30.0;

    private record Target(Component name, Vec3 center, double radius, int color) {}

    public static void init() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id("space_navigation"), SpaceHud::extract);
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !SpaceRenderer.inSpace()) return;

        Vec3 eye = player.getEyePosition();
        List<Target> targets = new ArrayList<>();
        for (Cosmos.Celestial body : Cosmos.BODIES) {
            if (body.enterable()) targets.add(new Target(Component.translatable(body.translationKey()), body.center(), body.radius(), body.accent()));
        }
        for (Cosmos.Wormhole wormhole : Cosmos.WORMHOLES) {
            targets.add(new Target(Component.translatable(wormhole.translationKey()), wormhole.center(), wormhole.radius(), wormhole.color()));
        }
        targets.sort(Comparator.comparingDouble(target -> target.center().distanceTo(eye) - target.radius()));

        int centerX = graphics.guiWidth() / 2;
        for (int i = 0; i < Math.min(SHOWN, targets.size()); i++) {
            Target target = targets.get(i);
            Component line = Component.literal(direction(player, eye, target.center()) + " ")
                    .append(target.name())
                    .append(Component.literal("  " + distance(target.center().distanceTo(eye) - target.radius())).withColor(0xFFB8C4BA));
            int alpha = i == 0 ? 0xFF : 0xB0;
            graphics.centeredText(client.font, line, centerX, TOP + i * LINE, ARGB.color(alpha, ARGB.srgbLerp(0.35F, ARGB.opaque(target.color()), 0xFFFFFFFF)));
        }
    }

    private static String direction(LocalPlayer player, Vec3 eye, Vec3 target) {
        Vec3 to = target.subtract(eye);
        double yaw = Math.toDegrees(Math.atan2(-to.x, to.z));
        int index = Math.floorMod((int) Math.round(Mth.wrapDegrees(yaw - player.getYRot()) / 45.0), ARROWS.length);
        double pitch = -Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)));
        double relativePitch = pitch - player.getXRot();
        String vertical = relativePitch < -VERTICAL_HINT_DEGREES ? "▲" : relativePitch > VERTICAL_HINT_DEGREES ? "▼" : "";
        return ARROWS[index] + vertical;
    }

    private static String distance(double blocks) {
        if (blocks < 1000.0) return String.format(Locale.ROOT, "%d m", Math.max(0, Math.round(blocks)));
        return String.format(Locale.ROOT, "%.1f km", blocks / 1000.0);
    }

    private SpaceHud() {}
}
