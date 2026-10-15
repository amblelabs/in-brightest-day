package dev.amble.client.compat;

import dev.amble.BrightestDay;
import dev.amble.client.effects.BatteryEffects;
import dev.amble.client.effects.LightOrbEffects;
import dev.amble.client.effects.ShieldEffects;
import dev.amble.client.effects.WallEffects;
import dev.amble.client.effects.attacks.utility.LumberjackEffects;
import dev.amble.client.effects.attacks.utility.OreProbeEffects;
import dev.amble.client.effects.attacks.weapon.TurretEffects;
import dev.amble.client.effects.glide.GlideEffects;
import dev.amble.client.space.CosmosClient;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.networking.payloads.s2c.PlayerStateS2CPayload;
import dev.amble.core.ringpowers.CorpsSynergy;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ReplaySnapshot {

    private static final List<AttachmentType<?>> PLAYER_STATE = List.of(
            BrightestDayAttachments.POWERS,
            BrightestDayAttachments.RING,
            BrightestDayAttachments.COLOR_TWEAK,
            BrightestDayAttachments.EYES,
            CorpsSynergy.SYNERGY
    );

    private static final Map<Integer, Map<AttachmentType<?>, @Nullable Object>> PENDING = new HashMap<>();
    private static final @Nullable MethodHandle IN_REPLAY = findInReplay();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerStateS2CPayload.TYPE, (payload, context) -> {
            if (context.client().level == null) return;
            Entity entity = context.client().level.getEntity(payload.playerId());
            if (entity instanceof Player player) {
                player.setAttached(BrightestDayAttachments.POWERS, payload.powers());
                player.setAttached(BrightestDayAttachments.RING, payload.ring());
                player.setAttached(BrightestDayAttachments.COLOR_TWEAK, payload.colorTweak());
                player.setAttached(BrightestDayAttachments.EYES, payload.eyes());
            } else if (entity == null && inReplay()) {
                Map<AttachmentType<?>, @Nullable Object> pending = PENDING.computeIfAbsent(payload.playerId(), id -> new HashMap<>());
                pending.put(BrightestDayAttachments.POWERS, payload.powers());
                pending.put(BrightestDayAttachments.RING, payload.ring());
                pending.put(BrightestDayAttachments.COLOR_TWEAK, payload.colorTweak());
                pending.put(BrightestDayAttachments.EYES, payload.eyes());
            }
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            Map<AttachmentType<?>, @Nullable Object> pending = PENDING.remove(entity.getId());
            if (pending != null) pending.forEach((type, value) -> setAttached(entity, type, value));
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (!(entity instanceof Player) || !inReplay()) return;
            Map<AttachmentType<?>, @Nullable Object> retained = new HashMap<>();
            for (AttachmentType<?> type : PLAYER_STATE) {
                if (entity.hasAttached(type)) retained.put(type, entity.getAttached(type));
            }
            if (!retained.isEmpty()) PENDING.put(entity.getId(), retained);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> PENDING.clear());
    }

    public static boolean defer(int entityId, AttachmentType<?> type, @Nullable Object value) {
        if (!BrightestDay.MOD_ID.equals(type.identifier().getNamespace()) || !inReplay()) return false;
        PENDING.computeIfAbsent(entityId, id -> new HashMap<>()).put(type, value);
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void setAttached(Entity entity, AttachmentType<?> type, @Nullable Object value) {
        entity.setAttached((AttachmentType<Object>) type, value);
    }

    private static boolean inReplay() {
        if (IN_REPLAY == null) return false;
        try {
            return (boolean) IN_REPLAY.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    private static @Nullable MethodHandle findInReplay() {
        if (!FabricLoader.getInstance().isModLoaded("flashback")) return null;
        try {
            Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback");
            return MethodHandles.publicLookup().findStatic(flashback, "isInReplay", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException e) {
            BrightestDay.LOGGER.warn("Could not hook Flashback replay state", e);
            return null;
        }
    }

    public static void write(Consumer<CustomPacketPayload> out) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        for (AbstractClientPlayer player : client.level.players()) {
            out.accept(new PlayerStateS2CPayload(player.getId(), BrightestDayAttachments.get(player), BrightestDayAttachments.getRing(player),
                    BrightestDayAttachments.getColorTweak(player), BrightestDayAttachments.getEyes(player)));
        }
        WallEffects.snapshot(out);
        ShieldEffects.snapshot(out);
        LightOrbEffects.snapshot(out);
        TurretEffects.snapshot(out);
        GlideEffects.snapshot(out);
        LumberjackEffects.snapshot(out);
        OreProbeEffects.snapshot(out);
        BatteryEffects.snapshot(out);
        CosmosClient.snapshot(out);
        BatteryEffects.snapshot(out);
    }

    private ReplaySnapshot() {}
}
