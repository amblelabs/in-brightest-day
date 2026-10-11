package dev.amble.client.effects;

import dev.amble.core.items.PowerRingItem;
import dev.amble.core.networking.payloads.s2c.BulwarkS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import dev.amble.core.shields.BulwarkArc;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BulwarkEffects {
    private static final float CELL = VoxelRenderer.PIXEL * 3.0F;
    private static final float HALF = CELL * 0.46F;
    private static final int RAISE_TICKS = 4;
    private static final float SHIMMER_SPEED = 0.12F;

    private record Raised(int size, int color, int age) {
        Raised older() {
            return new Raised(this.size, this.color, this.age + 1);
        }
    }

    private record Cell(double fraction, double height, float glow) {}

    private record Layout(@Nullable LanternCorps corps, int columns, int rows) {}

    private static final Map<Integer, Raised> RAISED = new HashMap<>();
    private static final Map<Layout, List<Cell>> LAYOUTS = new HashMap<>();
    private static @Nullable ClientLevel level;

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(BulwarkS2CPayload.TYPE, (payload, context) -> {
            if (!payload.active()) {
                RAISED.remove(payload.playerId());
                return;
            }
            Raised previous = RAISED.get(payload.playerId());
            RAISED.put(payload.playerId(), new Raised(payload.size(), ARGB.opaque(payload.color()), previous == null ? 0 : previous.age()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RAISED.clear());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != level) {
                RAISED.clear();
                level = client.level;
            }
            if (client.isPaused()) return;
            RAISED.replaceAll((id, raised) -> raised.older());
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(BulwarkEffects::render);
    }

    public static boolean isRaised(Player player) {
        return RAISED.containsKey(player.getId());
    }

    private static void render(LevelRenderContext context) {
        if (RAISED.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        float partialTicks = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (Map.Entry<Integer, Raised> entry : RAISED.entrySet()) {
            if (!(client.level.getEntity(entry.getKey()) instanceof Player player) || player.isInvisible()) continue;
            Raised raised = entry.getValue();
            float rise = Mth.clamp((raised.age() + partialTicks) / RAISE_TICKS, 0.0F, 1.0F);
            BulwarkArc arc = arc(player, raised.size(), partialTicks);
            LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
            ShieldEffects.submit(context, camera, voxels(arc, corps, raised.color(), player.tickCount + partialTicks, rise), 1.0F);
        }
    }

    public static void preview(LevelRenderContext context, Vec3 camera, Player player, int size, int color, float partialTicks, float alpha) {
        BulwarkArc arc = arc(player, size, partialTicks);
        LanternCorps corps = PowerRingItem.getWornCorps(player).orElse(null);
        ShieldEffects.submit(context, camera, voxels(arc, corps, color, player.tickCount + partialTicks, 1.0F), alpha);
    }

    private static BulwarkArc arc(Player player, int size, float partialTicks) {
        return BulwarkArc.of(player.getPosition(partialTicks), Mth.rotLerp(partialTicks, player.yRotO, player.getYRot()), size);
    }

    private static List<ShieldEffects.Voxel> voxels(BulwarkArc arc, @Nullable LanternCorps corps, int color, float time, float rise) {
        int columns = Math.max(3, Mth.ceil(arc.arcLength() / CELL));
        int rows = Math.max(3, Mth.ceil((arc.top() - arc.bottom()) / CELL));
        List<Cell> cells = LAYOUTS.computeIfAbsent(new Layout(corps, columns, rows), BulwarkEffects::layout);

        int dim = ARGB.srgbLerp(0.55F, 0xFF000000, color);
        int bright = VoxelRenderer.toWhite(color, 0.35F);
        List<ShieldEffects.Voxel> voxels = new ArrayList<>(cells.size());
        for (Cell cell : cells) {
            if (cell.height() > rise) continue;
            float shimmer = 0.85F + 0.15F * Mth.sin(time * SHIMMER_SPEED + (float) cell.fraction() * 6.0F + (float) cell.height() * 4.0F);
            int tint = ARGB.srgbLerp(Mth.clamp(cell.glow() * shimmer, 0.0F, 1.0F), dim, bright);
            voxels.add(new ShieldEffects.Voxel(arc.point(cell.fraction(), cell.height()), HALF, tint));
        }
        return voxels;
    }

    private static List<Cell> layout(Layout layout) {
        List<Cell> cells = new ArrayList<>();
        int columns = layout.columns();
        int rows = layout.rows();
        for (int i = 0; i < columns; i++) {
            double t = columns == 1 ? 0.0 : i / (double) (columns - 1) * 2.0 - 1.0;
            for (int j = 0; j < rows; j++) {
                double v = rows == 1 ? 0.0 : j / (double) (rows - 1);
                float glow = glow(layout.corps(), t, v, i, j, columns, rows);
                if (glow >= 0.0F) cells.add(new Cell(t, v, glow));
            }
        }
        return List.copyOf(cells);
    }

    private static float glow(@Nullable LanternCorps corps, double t, double v, int i, int j, int columns, int rows) {
        double side = Math.abs(t);
        boolean bottomRim = j == 0;
        if (corps == null) return rect(side, v, bottomRim);
        return switch (corps) {
            case GREEN -> {
                double top = 1.0 - (side > 0.8 ? (side - 0.8) / 0.2 * 0.18 : 0.0);
                if (v > top) yield -1.0F;
                boolean rim = side > 0.93 || bottomRim || v > top - 0.08;
                boolean spine = Math.abs(t) < 0.04;
                yield rim ? 1.0F : spine ? 0.8F : 0.45F;
            }
            case YELLOW -> {
                double top = 0.8 + 0.2 * saw(t * 7.0);
                double edge = 1.0 - 0.1 * saw(v * 6.0);
                if (v > top || side > edge) yield -1.0F;
                yield v > top - 0.1 || side > edge - 0.08 ? 1.0F : 0.4F + 0.2F * (float) saw(t * 14.0 + v * 3.0);
            }
            case BLUE -> {
                double top = 1.0 - 0.35 * t * t;
                double bottom = 0.06 * t * t;
                if (v > top || v < bottom) yield -1.0F;
                double distance = Math.min(Math.min(top - v, v - bottom), 1.0 - side);
                yield distance < 0.07 ? 1.0F : (float) Mth.lerp(Mth.clamp(distance / 0.4, 0.0, 1.0), 0.75, 0.35);
            }
            case ORANGE -> {
                if (side > 0.9 && v > 0.9) yield -1.0F;
                boolean band = j % 5 == 0 || j == rows - 1;
                boolean stud = i % 4 == 2 && j % 5 == 2;
                yield stud ? 1.0F : band ? 0.8F : 0.5F;
            }
            case STAR_SAPPHIRE -> {
                double top = 0.72 + 0.28 * saw(t * 3.5);
                if (v > top) yield -1.0F;
                boolean facet = Math.floorMod(i + j, 6) == 0 || Math.floorMod(i - j, 6) == 0;
                yield v > top - 0.07 || facet ? 1.0F : 0.4F;
            }
            case INDIGO -> {
                boolean rib = i % 5 == 0 || i == columns - 1;
                boolean brace = j % 6 == 0 || j == rows - 1;
                double crown = 1.0 - 0.12 * Math.abs(Math.sin(t * Math.PI * 4.0));
                if (!rib && !brace || v > crown) yield -1.0F;
                yield rib && brace ? 1.0F : rib ? 0.75F : 0.55F;
            }
            case WHITE -> 0.75F + 0.25F * (float) (1.0 - side);
            default -> rect(side, v, bottomRim);
        };
    }

    private static float rect(double side, double v, boolean bottomRim) {
        return side > 0.93 || bottomRim || v > 0.94 ? 1.0F : 0.5F;
    }

    private static double saw(double x) {
        double fraction = x - Math.floor(x);
        return 1.0 - Math.abs(fraction * 2.0 - 1.0);
    }

    private BulwarkEffects() {}
}
