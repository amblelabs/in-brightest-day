package dev.amble.client.flight;

import dev.amble.BrightestDay;
import dev.amble.client.BrightestDayKeybinds;
import dev.amble.client.effects.VisualQuality;
import dev.amble.core.flight.FlightBoost;
import dev.amble.core.flight.GroundLaunch;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.GroundChargeC2SPayload;
import dev.amble.core.networking.payloads.c2s.GroundLaunchC2SPayload;
import dev.amble.core.networking.payloads.c2s.SetFlightC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.impl.FlightRingPower;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class GroundLaunchClient {
    private static final int SOUND_INTERVAL = 4;
    private static final Identifier BAR_BACKGROUND = Identifier.withDefaultNamespace("hud/jump_bar_background");
    private static final Identifier BAR_PROGRESS = Identifier.withDefaultNamespace("hud/jump_bar_progress");
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;
    private static final int BAR_MARGIN = 24;
    private static final int SPIRAL_ARMS = 3;
    private static final int LAUNCH_GRACE = 30;

    private static int charge;
    private static int grace;
    private static int readyAt;

    public static void init() {
        FlightRingPower.launching = GroundLaunchClient::launching;
        ClientTickEvents.END_CLIENT_TICK.register(GroundLaunchClient::tick);
        HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, BrightestDay.id("launch_bar"), GroundLaunchClient::extractBar);
    }

    public static boolean charging() {
        return charge > 0;
    }

    public static boolean launching() {
        return grace > 0;
    }

    public static float progress(float partialTicks) {
        return charge <= 0 ? 0.0F : Mth.clamp((charge + partialTicks) / GroundLaunch.FULL_CHARGE, 0.0F, 1.0F);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            charge = 0;
            return;
        }
        if (client.isPaused()) return;
        if (grace > 0) grace--;
        boolean holding = client.gui.screen() == null && BrightestDayKeybinds.FLIGHT.isDown() && player.isShiftKeyDown()
                && player.onGround() && !player.isPassenger() && FlightRingPower.hasFlight(player) && PowerRingItem.hasCharge(player);
        if (holding) {
            if (charge == 0) {
                LaunchAnimations.apply(player, GroundLaunch.CHARGING);
                if (ClientPlayNetworking.canSend(GroundChargeC2SPayload.TYPE)) ClientPlayNetworking.send(new GroundChargeC2SPayload(true));
            }
            charge = Math.min(charge + 1, GroundLaunch.FULL_CHARGE);
            charging(client, player);
            return;
        }
        if (charge >= GroundLaunch.FULL_CHARGE && player.onGround() && (player.tickCount >= readyAt || readyAt - player.tickCount > GroundLaunch.COOLDOWN_TICKS) && ClientPlayNetworking.canSend(GroundLaunchC2SPayload.TYPE)) {
            ClientPlayNetworking.send(new GroundLaunchC2SPayload(charge));
            takeOff(player, GroundLaunch.power(charge));
            LaunchAnimations.apply(player, GroundLaunch.LAUNCHED);
        } else if (charge > 0) {
            LaunchAnimations.apply(player, GroundLaunch.IDLE);
            if (ClientPlayNetworking.canSend(GroundChargeC2SPayload.TYPE)) ClientPlayNetworking.send(new GroundChargeC2SPayload(false));
        }
        charge = 0;
    }

    private static void extractBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (charge <= 0) return;
        int left = (graphics.guiWidth() - BAR_WIDTH) / 2;
        int top = graphics.guiHeight() - BAR_MARGIN - BAR_HEIGHT;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND, left, top, BAR_WIDTH, BAR_HEIGHT);
        int filled = Mth.lerpDiscrete(progress(deltaTracker.getGameTimeDeltaPartialTick(false)), 0, BAR_WIDTH);
        if (filled > 0) graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_PROGRESS, BAR_WIDTH, BAR_HEIGHT, 0, 0, left, top, filled, BAR_HEIGHT);
    }

    private static void takeOff(LocalPlayer player, float power) {
        grace = LAUNCH_GRACE;
        readyAt = player.tickCount + GroundLaunch.COOLDOWN_TICKS;
        FlightRingPower.setEnabled(player, true);
        if (ClientPlayNetworking.canSend(SetFlightC2SPayload.TYPE)) ClientPlayNetworking.send(new SetFlightC2SPayload(true));
        player.setOnGround(false);
        player.setDeltaMovement(GroundLaunch.velocity(player.getLookAngle(), power));
        FlightBoost.startClient(player, FlightBoost.BURST_TICKS);
    }

    private static void charging(Minecraft client, LocalPlayer player) {
        float progress = charge / (float) GroundLaunch.FULL_CHARGE;
        int color = ARGB.opaque(CorpsColors.of(player));
        RandomSource random = player.getRandom();
        Vec3 feet = player.position();

        DustParticleOptions dust = new DustParticleOptions(color, 0.8F + 0.8F * progress);
        float spin = charge * 0.35F;
        double radius = 1.1 - 0.6 * progress;
        int perArm = VisualQuality.count(1 + Math.round(2 * progress));
        for (int arm = 0; arm < SPIRAL_ARMS; arm++) {
            for (int i = 0; i < perArm; i++) {
                float angle = spin + arm * Mth.TWO_PI / SPIRAL_ARMS + i * 0.25F;
                double height = random.nextDouble() * (0.4 + 1.4 * progress);
                client.level.addParticle(dust, feet.x + Mth.cos(angle) * radius, feet.y + height, feet.z + Mth.sin(angle) * radius,
                        0.0, 0.04 + 0.08 * progress, 0.0);
            }
        }

        BlockState ground = client.level.getBlockState(player.getOnPos());
        if (ground.getRenderShape() != RenderShape.INVISIBLE && random.nextFloat() < 0.3F + 0.6F * progress) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            float angle = random.nextFloat() * Mth.TWO_PI;
            double reach = 0.3 + random.nextDouble() * 0.6;
            client.level.addParticle(debris, feet.x + Mth.cos(angle) * reach, feet.y + 0.05, feet.z + Mth.sin(angle) * reach, 0.0, 0.05 + 0.1 * progress, 0.0);
        }

        if (charge % SOUND_INTERVAL == 0) {
            client.level.playLocalSound(feet.x, feet.y, feet.z, SoundEvents.BREEZE_CHARGE, SoundSource.PLAYERS, 0.3F + 0.4F * progress, 0.6F + 1.0F * progress, false);
        }
        if (charge == GroundLaunch.FULL_CHARGE - 1) {
            client.level.playLocalSound(feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F, false);
        }
    }

    private GroundLaunchClient() {}
}
