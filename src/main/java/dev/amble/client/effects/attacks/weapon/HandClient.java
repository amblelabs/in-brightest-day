package dev.amble.client.effects.attacks.weapon;

import dev.amble.BrightestDay;
import dev.amble.client.effects.BlastEffects;
import dev.amble.client.effects.VoxelRenderer;
import dev.amble.core.hand.HandPose;
import dev.amble.core.hand.HandDirection;
import dev.amble.core.hand.HandPoses;
import dev.amble.core.hand.HandPreset;
import dev.amble.core.networking.payloads.c2s.HandSelectC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.RingPowerRegistry;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class HandClient {
    private static final double SWIPE_THRESHOLD = 60.0;
    private static final double SWIPE_SCALE = 0.5;
    private static final int HOLD_OPEN = 5;
    private static final double CAMERA_SCALE = 0.25;
    private static final int WHEEL_SIZE = 40;
    private static final int WHEEL_ROW = 44;
    private static final int WHEEL_MARGIN = 10;
    private static final int WHEEL_PADDING = 4;
    private static final int WHEEL_REACH = 2;
    private static final int WHEEL_LABEL_GAP = 6;
    private static final float WHEEL_SHRINK = 0.18F;
    private static final float WHEEL_DIM = 0.38F;
    private static final float WHEEL_EASE = 0.45F;
    private static final float WHEEL_FADE = 0.25F;

    private static boolean active;
    private static double swipeX;
    private static double swipeY;
    private static HandDirection shown = HandDirection.FORWARD;
    private static int shownPose = -1;
    private static float wheel;
    private static float oWheel;
    private static float wheelTarget;
    private static float visible;
    private static float oVisible;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(HandClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, BrightestDay.id("hand_wheel"), HandClient::extractWheel);
    }

    private static boolean charging(LocalPlayer player) {
        return BlastEffects.chargeTicks() >= HOLD_OPEN && ArmedRingPower.selectedConstruct(player).orElse(null) == RingPowerRegistry.HAND;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        boolean now = player != null && client.gui.screen() == null && charging(player);
        if (now && !active) {
            swipeX = 0.0;
            swipeY = 0.0;
            shownPose = -1;
            int index = Math.max(0, available(player).indexOf(HandPoses.client().pose()));
            wheel = oWheel = wheelTarget = index;
        }
        oWheel = wheel;
        oVisible = visible;
        wheel += (wheelTarget - wheel) * WHEEL_EASE;
        visible = Mth.approach(visible, now ? 1.0F : 0.0F, WHEEL_FADE);
        active = now;
        if (!active) return;
        HandDirection direction = direction(player);
        int pose = HandPoses.client().pose();
        if (direction != shown || pose != shownPose || player.tickCount % 10 == 0) {
            shown = direction;
            shownPose = pose;
            client.gui.hud.setOverlayMessage(Component.translatable("message.brightestday.hand.aiming", HandPoses.label(player, pose), direction.label())
                    .withColor(ARGB.opaque(CorpsColors.of(player))), false);
        }
    }

    public static double onMouse(double dx, double dy) {
        if (!active) return 1.0;
        swipeX += dx * SWIPE_SCALE;
        swipeY += dy * SWIPE_SCALE;
        return CAMERA_SCALE;
    }

    public static boolean onScroll(int wheel) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!active || player == null || wheel == 0) return false;
        List<Integer> poses = available(player);
        int index = poses.indexOf(HandPoses.client().pose());
        int next = poses.get(Math.floorMod(index + (wheel > 0 ? -1 : 1), poses.size()));
        wheelTarget += wheel > 0 ? -1 : 1;
        HandPoses.setClient(new HandPoses.Selection(HandPoses.client().direction(), next));
        return true;
    }

    public static void beforeFire(LocalPlayer player) {
        if (ArmedRingPower.selectedConstruct(player).orElse(null) != RingPowerRegistry.HAND) return;
        HandPoses.Selection selection = new HandPoses.Selection(direction(player), HandPoses.client().pose());
        HandPoses.setClient(selection);
        if (ClientPlayNetworking.canSend(HandSelectC2SPayload.TYPE)) {
            ClientPlayNetworking.send(new HandSelectC2SPayload(selection.direction().ordinal(), selection.pose()));
        }
        active = false;
    }

    private static void extractWheel(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        float fade = Mth.lerp(partial, oVisible, visible);
        if (player == null || fade <= 0.01F) return;

        List<Integer> poses = available(player);
        if (poses.isEmpty()) return;
        float position = Mth.lerp(partial, oWheel, wheel);
        int color = ARGB.opaque(CorpsColors.of(player));
        int right = graphics.guiWidth() - WHEEL_MARGIN;
        int center = graphics.guiHeight() / 2;
        int slide = Math.round((1.0F - fade) * (WHEEL_SIZE + WHEEL_MARGIN));
        int nearest = Math.round(position);
        int selected = Math.floorMod(Math.round(wheelTarget), poses.size());

        for (int k = -WHEEL_REACH; k <= WHEEL_REACH; k++) {
            int slot = nearest + k;
            float offset = slot - position;
            float distance = Math.abs(offset);
            if (distance > WHEEL_REACH) continue;
            float shrink = 1.0F - WHEEL_SHRINK * distance;
            float alpha = fade * Mth.clamp(1.0F - WHEEL_DIM * distance, 0.0F, 1.0F);
            int size = Math.round(WHEEL_SIZE * shrink);
            int x1 = right + slide;
            int x0 = x1 - size;
            int y0 = center + Math.round(offset * WHEEL_ROW) - size / 2;
            int index = Math.floorMod(slot, poses.size());
            boolean current = index == selected;
            graphics.fill(x0, y0, x1, y0 + size, ARGB.color(Math.round(0x90 * alpha), 0x000000));
            if (current) outline(graphics, x0, y0, x1, y0 + size, ARGB.color(Math.round(0xFF * alpha), color));
            drawHand(graphics, HandPoses.resolve(player, poses.get(index)), x0, y0, size, color, alpha);
            if (distance < 0.5F) {
                Component label = HandPoses.label(player, poses.get(index));
                graphics.text(client.font, label, x0 - WHEEL_LABEL_GAP - client.font.width(label), y0 + (size - client.font.lineHeight) / 2 + 1,
                        ARGB.color(Math.round(0xFF * alpha), 0xFFFFFF), true);
            }
        }
    }

    private static void outline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void drawHand(GuiGraphicsExtractor graphics, HandPose pose, int x0, int y0, int size, int color, float alpha) {
        List<HandEffects.Cell> cells = new ArrayList<>();
        for (HandEffects.Cell cell : HandEffects.shape(pose)) if (cell.kind() != HandEffects.ARM) cells.add(cell);
        if (cells.isEmpty()) return;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (HandEffects.Cell cell : cells) {
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y()); maxY = Math.max(maxY, cell.y());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        int inner = size - WHEEL_PADDING * 2;
        int pixel = Math.max(1, (int) Math.floor(inner / (Math.max(maxX - minX, maxZ - minZ) + 1.0F)));
        int width = Math.round((maxX - minX + 1.0F) * pixel);
        int height = Math.round((maxZ - minZ + 1.0F) * pixel);
        int left = x0 + (size - width) / 2;
        int bottom = y0 + (size + height) / 2;
        cells.sort(Comparator.comparingDouble(HandEffects.Cell::y));
        float depth = Math.max(maxY - minY, 1.0F);
        for (HandEffects.Cell cell : cells) {
            float light = 0.45F + 0.55F * (cell.y() - minY) / depth;
            int tint = ARGB.color(Math.round(0xFF * alpha), ARGB.scaleRGB(VoxelRenderer.toWhite(color, 0.25F * light), light));
            int px = left + Math.round((maxX - cell.x()) * pixel);
            int py = bottom - Math.round((cell.z() - minZ + 1.0F) * pixel);
            graphics.fill(px, py, px + pixel, py + pixel, tint);
        }
    }

    private static List<Integer> available(LocalPlayer player) {
        List<Integer> poses = new ArrayList<>();
        int presets = HandPreset.values().length;
        for (int pose = 0; pose < HandPoses.poseCount(); pose++) {
            if (pose < presets || HandPoses.custom(player, pose - presets).isPresent()) poses.add(pose);
        }
        return poses;
    }

    private static HandDirection direction(LocalPlayer player) {
        if (player.isShiftKeyDown()) return HandDirection.BEHIND;
        if (!active || Math.hypot(swipeX, swipeY) < SWIPE_THRESHOLD) return HandDirection.FORWARD;
        if (Math.abs(swipeX) >= Math.abs(swipeY)) return swipeX < 0.0 ? HandDirection.RIGHT : HandDirection.LEFT;
        return swipeY < 0.0 ? HandDirection.UP : HandDirection.DOWN;
    }

    private HandClient() {}
}
