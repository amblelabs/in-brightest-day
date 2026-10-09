package dev.amble.client.effects;

import dev.amble.client.flight.FlightControls;
import dev.amble.core.blocks.LanternBlock;
import dev.amble.core.forge.BatteryCoreBlock;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.c2s.CycleConstructC2SPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.constructs.AreaShieldConstruct;
import dev.amble.core.ringpowers.constructs.ConstructRingPower;
import dev.amble.core.ringpowers.constructs.WallConstruct;
import dev.amble.core.ringpowers.impl.ArmedRingPower;
import dev.amble.core.walls.WallGeometry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConstructClient {
    private static final float PREVIEW_ALPHA = 0.3F;
    private static final float WALL_PREVIEW_HALF = 0.48F;

    private static final Map<Identifier, Integer> SIZES = new HashMap<>();

    public static void init() {
        LevelRenderEvents.COLLECT_SUBMITS.register(ConstructClient::renderPreview);
    }

    public static int size(ConstructRingPower construct) {
        int size = SIZES.getOrDefault(construct.id(), construct.defaultSize());
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? construct.clampSize(size) : construct.clampSize(player, size);
    }

    public static int selectedSize(LocalPlayer player) {
        return ArmedRingPower.selectedConstruct(player).map(ConstructClient::size).orElse(1);
    }

    public static void cycle() {
        ClientPlayNetworking.send(CycleConstructC2SPayload.INSTANCE);
    }

    public static boolean isLookingAtLantern() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null
                && client.hitResult instanceof BlockHitResult blockHit
                && blockHit.getType() == HitResult.Type.BLOCK
                && (client.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof LanternBlock
                || client.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof BatteryCoreBlock
                || BatteryEffects.isBattery(blockHit.getBlockPos()));
    }

    public static boolean onScroll(int wheel) {
        if (FlightControls.onScroll(wheel)) return true;
        if (CommsClient.onScroll(wheel)) return true;
        if (TractorEffects.onScroll(wheel)) return true;
        if (SculptClient.onScroll(wheel)) return true;

        LocalPlayer player = Minecraft.getInstance().player;
        Optional<ConstructRingPower> construct = player == null ? Optional.empty() : sizingConstruct(player);
        if (construct.isEmpty()) return false;

        int current = size(construct.get());
        int resized = construct.get().clampSize(player, current + Integer.signum(wheel));
        if (resized != current) {
            SIZES.put(construct.get().id(), resized);
            Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable("message.brightestday.construct_size",
                    Component.translatable(construct.get().getTranslationKey()), construct.get().describeSize(resized)), false);
        }
        return true;
    }

    private static Optional<ConstructRingPower> sizingConstruct(LocalPlayer player) {
        if (!ArmedRingPower.isArmed(player) || ArmedRingPower.isAbilityMode(player) || !PowerRingItem.hasCharge(player)) return Optional.empty();
        return ArmedRingPower.selectedConstruct(player).filter(ConstructRingPower::usesSize);
    }

    private static void renderPreview(LevelRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || isLookingAtLantern()) return;
        Optional<ConstructRingPower> construct = sizingConstruct(player);
        if (construct.isEmpty()) return;

        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;
        int color = ARGB.opaque(CorpsColors.of(player));
        float time = player.tickCount + partialTicks;
        int size = size(construct.get());

        if (construct.get() instanceof WallConstruct) {
            Vec3 base = player.pick(WallConstruct.RANGE, partialTicks, false).getLocation();
            List<ShieldEffects.Voxel> cells = new ArrayList<>();
            for (BlockPos pos : WallGeometry.cells(base, player.getViewYRot(partialTicks), size)) {
                cells.add(new ShieldEffects.Voxel(Vec3.atCenterOf(pos), WALL_PREVIEW_HALF, color));
            }
            ShieldEffects.submit(context, camera, cells, PREVIEW_ALPHA);
        } else if (construct.get() instanceof AreaShieldConstruct) {
            Vec3 target = player.pick(AreaShieldConstruct.RANGE, partialTicks, false).getLocation();
            ShieldEffects.submit(context, camera, ShieldEffects.sphere(target, size, size * 2.0F, time, color), PREVIEW_ALPHA);
        }
    }

    private ConstructClient() {}
}
