package dev.amble.client.effects;

import dev.amble.client.render.Holograms;
import dev.amble.core.BrightestDayAttachments;
import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.InsigniaS2CPayload;
import dev.amble.core.ringpowers.CorpsColors;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.visuals.Insignia;
import dev.amble.core.visuals.InsigniaAnchor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

public final class InsigniaEffects {
    private static final int FADE_TICKS = 8;

    private static final class Shown {
        int age;
        int fade = -1;
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(InsigniaS2CPayload.TYPE, (payload, context) -> {
            Shown shown = SHOWN.get(payload.playerId());
            if (payload.active()) {
                if (shown == null || shown.fade >= 0) SHOWN.put(payload.playerId(), new Shown());
            } else if (shown != null && shown.fade < 0) {
                shown.fade = 0;
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SHOWN.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.isPaused()) return;
            SHOWN.values().removeIf(shown -> {
                shown.age++;
                return shown.fade >= 0 && ++shown.fade > FADE_TICKS;
            });
        });
    }

    public static boolean shown(Player player) {
        Shown shown = SHOWN.get(player.getId());
        return shown != null && shown.fade < 0;
    }

    public record Projection(Identifier texture, int color, float fade, InsigniaAnchor anchor) {}

    public static final RenderStateDataKey<Projection> PROJECTION = RenderStateDataKey.create(() -> "brightestday:insignia");

    public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
        Shown shown = SHOWN.get(entity.getId());
        Projection projection = null;
        if (shown != null && entity instanceof Player player) {
            LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
            Identifier texture = corps == null ? null : Insignia.texture(corps);
            float time = shown.age + partialTicks;
            float fade = Mth.clamp(time / FADE_TICKS, 0.0F, 1.0F);
            if (shown.fade >= 0) fade *= 1.0F - Mth.clamp((shown.fade + partialTicks) / FADE_TICKS, 0.0F, 1.0F);
            if (texture != null && fade > 0.01F) projection = new Projection(texture, ARGB.opaque(CorpsColors.of(player)), fade, BrightestDayAttachments.getInsigniaAnchor(player));
        } else if (Holograms.lit(entity) && Holograms.of(entity).settings().insignia()) {
            Identifier texture = Insignia.texture(Holograms.corps(entity).orElseThrow());
            if (texture != null) projection = new Projection(texture, ARGB.opaque(Holograms.color(entity)), 1.0F, Holograms.of(entity).settings().anchor());
        }
        ((FabricRenderState) state).setData(PROJECTION, projection);
    }

    private InsigniaEffects() {}
}
