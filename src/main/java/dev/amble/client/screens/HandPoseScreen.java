package dev.amble.client.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.attacks.weapon.HandEffects;
import dev.amble.core.hand.HandPose;
import dev.amble.core.hand.HandPoses;
import dev.amble.core.hand.HandPreset;
import dev.amble.core.networking.payloads.c2s.HandPoseSaveC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public class HandPoseScreen extends Screen {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 216;
    private static final int MARGIN = 8;
    private static final int ROW = 20;
    private static final int GAP = 3;
    private static final int CONTROLS = 168;
    private static final int PANEL_WIDTH = 128;
    private static final int PANEL_HEIGHT = 168;
    private static final float DEPTH = 2.4F;
    private static final float HAND_CELLS = 22.0F;
    private static final float FILL = 0.85F;
    private static final int GRAB_RADIUS = 9;
    private static final float SPIN_PER_PIXEL = 0.9F;
    private static final float MAX_PITCH = 80.0F;
    private static final int COARSE_STEP = 5;
    private static final double CONTINUITY = 0.02;
    private static final int TIP_MARK = 0xFFFFFFFF;
    private static final int TIP_HELD = 0xFFFFE066;
    private static final String[] FINGERS = {"index", "middle", "ring", "pinky"};

    private static @Nullable HandPoseScreen open;

    private final @Nullable Screen parent;
    private int left;
    private int top;
    private int slot;
    private int preset;
    private HandPose pose = HandPreset.OPEN.pose();
    private boolean loaded;
    private String typed = "";
    private float yaw = 25.0F;
    private float pitch = -15.0F;
    private int grabbed = -1;
    private boolean spinning;
    private float grabX;
    private float grabY;

    public HandPoseScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.brightestday.hand.editor"));
        this.parent = parent;
    }

    public static void register() {
        LevelRenderEvents.COLLECT_SUBMITS.register(HandPoseScreen::renderWorld);
    }

    @Override
    protected void init() {
        open = this;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - HEIGHT) / 2;
        if (!this.loaded) this.load(0);
        int x = this.left + MARGIN;
        int y = this.top + 22;

        int slotWidth = (CONTROLS - GAP * (HandPoses.CUSTOM_SLOTS - 1)) / HandPoses.CUSTOM_SLOTS;
        for (int i = 0; i < HandPoses.CUSTOM_SLOTS; i++) {
            int index = i;
            Component label = Component.literal((i == this.slot ? "> " : "") + (i + 1));
            this.addRenderableWidget(Button.builder(label, button -> {
                this.load(index);
                this.rebuildWidgets();
            }).bounds(x + i * (slotWidth + GAP), y, slotWidth, ROW).build());
        }
        y += ROW + GAP;

        EditBox name = this.addRenderableWidget(new EditBox(this.font, x, y, CONTROLS, ROW, Component.translatable("gui.brightestday.hand.name")));
        name.setMaxLength(HandPoses.MAX_NAME);
        name.setHint(Component.translatable("gui.brightestday.hand.name"));
        name.setValue(this.typed);
        name.setResponder(value -> this.typed = value);
        y += ROW + GAP;

        for (int finger = 0; finger < 4; finger++) {
            int index = finger;
            this.slider(x, y, "gui.brightestday.hand.finger." + FINGERS[finger], this.pose.finger(finger), value -> this.pose = this.pose.withFinger(index, value));
            y += ROW + GAP;
        }
        this.slider(x, y, "gui.brightestday.hand.thumb_curl", this.pose.thumbCurl(), value -> this.pose = this.pose.withThumb(value, this.pose.thumbSpread()));
        y += ROW + GAP;
        this.slider(x, y, "gui.brightestday.hand.thumb_spread", this.pose.thumbSpread(), value -> this.pose = this.pose.withThumb(this.pose.thumbCurl(), value));
        y += ROW + GAP;

        int half = (CONTROLS - GAP) / 2;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.hand.from", HandPreset.values()[this.preset].label()), button -> {
            this.preset = (this.preset + 1) % HandPreset.values().length;
            this.pose = HandPreset.values()[this.preset].pose();
            this.rebuildWidgets();
        }).bounds(x, y, half, ROW).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.brightestday.hand.save"), button -> {
            ClientPlayNetworking.send(new HandPoseSaveC2SPayload(this.slot, this.typed, this.pose));
            this.minecraft.player.sendOverlayMessage(Component.translatable("message.brightestday.hand.saved", this.slot + 1));
        }).bounds(x + half + GAP, y, half, ROW).build());
    }

    private void load(int slot) {
        this.slot = slot;
        this.loaded = true;
        HandPoses.Custom custom = HandPoses.custom(this.minecraft.player, slot).orElse(null);
        this.pose = custom == null ? HandPreset.OPEN.pose() : custom.pose();
        this.typed = custom == null ? "" : custom.name();
    }

    private void slider(int x, int y, String key, int value, IntConsumer change) {
        this.addRenderableWidget(new StepSlider(x, y, CONTROLS, ROW, value, HandPose.MAX, step -> Component.translatable(key, step), change));
    }

    private int panelLeft() {
        return this.left + MARGIN + CONTROLS + 12;
    }

    private int panelTop() {
        return this.top + 22;
    }

    private boolean inPanel(double x, double y) {
        return x >= this.panelLeft() && x <= this.panelLeft() + PANEL_WIDTH && y >= this.panelTop() && y <= this.panelTop() + PANEL_HEIGHT;
    }

    private float focal() {
        float fov = Minecraft.getInstance().gameRenderer.mainCamera().getFov();
        return (this.height * 0.5F) / (float) Math.tan(Math.toRadians(Math.max(fov, 10.0F)) * 0.5);
    }

    private Vector3f center() {
        float f = this.focal();
        float panelX = this.panelLeft() + PANEL_WIDTH * 0.5F;
        float panelY = this.panelTop() + PANEL_HEIGHT * 0.5F;
        return new Vector3f((panelX - this.width * 0.5F) / f * DEPTH, -(panelY - this.height * 0.5F) / f * DEPTH, DEPTH);
    }

    private float scale() {
        return PANEL_HEIGHT / this.focal() * DEPTH / (HAND_CELLS * 0.25F) * FILL;
    }

    private Vector3f[] basis() {
        Quaternionf rotation = new Quaternionf().rotateY(this.yaw * Mth.DEG_TO_RAD).rotateX(this.pitch * Mth.DEG_TO_RAD);
        Vector3f forward = rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Vector3f up = rotation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        Vector3f side = new Vector3f(forward).cross(up);
        return new Vector3f[]{forward, side, up};
    }

    private Vector3f cameraPoint(Vec3 cell) {
        Vector3f[] basis = this.basis();
        Vec3 local = HandEffects.local(cell.x, cell.y, cell.z).scale(this.scale());
        return new Vector3f(this.center())
                .add(new Vector3f(basis[1]).mul((float) local.x))
                .add(new Vector3f(basis[2]).mul((float) local.y))
                .add(new Vector3f(basis[0]).mul((float) local.z));
    }

    private float[] project(Vector3f point) {
        float f = this.focal();
        float depth = Math.max(point.z, 0.05F);
        return new float[]{this.width * 0.5F + point.x / depth * f, this.height * 0.5F - point.y / depth * f};
    }

    private float[] tipOnScreen(HandPose pose, int finger) {
        return this.project(this.cameraPoint(HandEffects.tip(pose.sanitized(), finger)));
    }

    private int nearestTip(double x, double y) {
        int best = -1;
        double bestDistance = GRAB_RADIUS * GRAB_RADIUS;
        for (int finger = 0; finger <= 4; finger++) {
            float[] tip = this.tipOnScreen(this.pose, finger);
            double dx = tip[0] - x;
            double dy = tip[1] - y;
            double distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = finger;
            }
        }
        return best;
    }

    private double miss(HandPose pose, int finger, double x, double y) {
        float[] tip = this.tipOnScreen(pose, finger);
        double dx = tip[0] - x;
        double dy = tip[1] - y;
        return dx * dx + dy * dy;
    }

    private HandPose reach(double x, double y) {
        if (this.grabbed >= 4) {
            int bestCurl = this.pose.thumbCurl();
            int bestSpread = this.pose.thumbSpread();
            double best = Double.MAX_VALUE;
            for (int pass = 0; pass < 2; pass++) {
                int step = pass == 0 ? COARSE_STEP : 1;
                int curlFrom = pass == 0 ? 0 : Math.max(0, bestCurl - COARSE_STEP);
                int curlTo = pass == 0 ? HandPose.MAX : Math.min(HandPose.MAX, bestCurl + COARSE_STEP);
                int spreadFrom = pass == 0 ? 0 : Math.max(0, bestSpread - COARSE_STEP);
                int spreadTo = pass == 0 ? HandPose.MAX : Math.min(HandPose.MAX, bestSpread + COARSE_STEP);
                for (int curl = curlFrom; curl <= curlTo; curl += step) {
                    for (int spread = spreadFrom; spread <= spreadTo; spread += step) {
                        int dc = curl - this.pose.thumbCurl();
                        int ds = spread - this.pose.thumbSpread();
                        double cost = this.miss(this.pose.withThumb(curl, spread), 4, x, y) + CONTINUITY * (dc * dc + ds * ds);
                        if (cost < best) {
                            best = cost;
                            bestCurl = curl;
                            bestSpread = spread;
                        }
                    }
                }
            }
            return this.pose.withThumb(bestCurl, bestSpread);
        }
        int current = this.pose.finger(this.grabbed);
        int bestValue = current;
        double best = Double.MAX_VALUE;
        for (int value = 0; value <= HandPose.MAX; value++) {
            int delta = value - current;
            double cost = this.miss(this.pose.withFinger(this.grabbed, value), this.grabbed, x, y) + CONTINUITY * delta * delta;
            if (cost < best) {
                best = cost;
                bestValue = value;
            }
        }
        return this.pose.withFinger(this.grabbed, bestValue);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !this.inPanel(event.x(), event.y())) return false;
        this.grabbed = this.nearestTip(event.x(), event.y());
        if (this.grabbed >= 0) {
            float[] tip = this.tipOnScreen(this.pose, this.grabbed);
            this.grabX = (float) (tip[0] - event.x());
            this.grabY = (float) (tip[1] - event.y());
        }
        this.spinning = this.grabbed < 0;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.grabbed >= 0) {
            this.pose = this.reach(event.x() + this.grabX, event.y() + this.grabY);
            return true;
        }
        if (this.spinning) {
            this.yaw -= (float) dx * SPIN_PER_PIXEL;
            this.pitch = Mth.clamp(this.pitch - (float) dy * SPIN_PER_PIXEL, -MAX_PITCH, MAX_PITCH);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean posed = this.grabbed >= 0;
        this.grabbed = -1;
        this.spinning = false;
        if (posed) this.rebuildWidgets();
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(this.left, this.top, this.left + MARGIN * 2 + CONTROLS, this.top + HEIGHT, 0x90000000);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.left + (MARGIN * 2 + CONTROLS) / 2, this.top + 8, 0xFFFFFFFF);

        int hovered = this.grabbed >= 0 ? this.grabbed : this.inPanel(mouseX, mouseY) ? this.nearestTip(mouseX, mouseY) : -1;
        for (int finger = 0; finger <= 4; finger++) {
            float[] tip = this.tipOnScreen(this.pose, finger);
            int px = Math.round(tip[0]);
            int py = Math.round(tip[1]);
            int size = finger == hovered ? 3 : 1;
            graphics.fill(px - size, py - size, px + size + 1, py + size + 1, finger == hovered ? TIP_HELD : TIP_MARK);
        }
        graphics.centeredText(this.font, Component.translatable("gui.brightestday.hand.drag_hint"),
                this.panelLeft() + PANEL_WIDTH / 2, this.panelTop() + PANEL_HEIGHT + 4, 0xFFA0A0A0);
    }

    private static void renderWorld(LevelRenderContext context) {
        HandPoseScreen screen = open;
        Minecraft client = Minecraft.getInstance();
        if (screen == null || client.gui.screen() != screen || client.player == null) return;
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 eye = context.levelState().cameraRenderState.pos;
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        Vector3f[] basis = screen.basis();
        Vector3f center = screen.center();
        Vec3 worldCenter = eye.add(world(forward, up, left, center));
        Vec3 worldForward = world(forward, up, left, basis[0]);
        Vec3 worldSide = world(forward, up, left, basis[1]);
        Vec3 worldUp = world(forward, up, left, basis[2]);
        int color = ARGB.opaque(CorpsColors.of(client.player));
        float time = client.player.tickCount + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<ShieldEffects.Oriented> voxels = new ArrayList<>(HandEffects.voxels(worldCenter, worldForward, worldSide, worldUp,
                HandEffects.shape(screen.pose.sanitized()), screen.scale(), color, time, 0.0F));
        ShieldEffects.submitGlassOriented(context, eye, voxels, 1.0F, HandEffects.rim(screen.scale()));
    }

    private static Vec3 world(Vector3fc forward, Vector3fc up, Vector3fc left, Vector3f point) {
        return new Vec3(
                forward.x() * point.z + up.x() * point.y - left.x() * point.x,
                forward.y() * point.z + up.y() * point.y - left.y() * point.x,
                forward.z() * point.z + up.z() * point.y - left.z() * point.x);
    }

    @Override
    public void removed() {
        if (open == this) open = null;
        super.removed();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
