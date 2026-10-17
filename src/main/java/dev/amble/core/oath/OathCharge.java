package dev.amble.core.oath;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import dev.amble.core.comms.Comms;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import dev.amble.core.items.LanternBlockItem;
import dev.amble.core.networking.payloads.s2c.OathS2CPayload;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OathCharge {
    public static final int SILENCE_TIMEOUT_TICKS = 20 * 25;
    public static final float MUTED_SLOWDOWN = 2.0F;
    private static final int SWEEP_INTERVAL = 20;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Brightest Day Oath Listener");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static @Nullable JsonObject ENGLISH;

    private static final class Session {
        final OathMatcher matcher;
        final OathRecognizer.Listener listener;
        final String oath;
        final boolean ritual;
        volatile int sentReached = -1;
        volatile int quietTicks;
        boolean closed;

        Session(OathMatcher matcher, OathRecognizer.Listener listener, String oath, boolean ritual) {
            this.ritual = ritual;
            this.matcher = matcher;
            this.listener = listener;
            this.oath = oath;
        }
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (BrightestDayConfig.get().oathRecognition && Comms.available()) OathRecognizer.ensureLoading();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SESSIONS.isEmpty() || server.getTickCount() % SWEEP_INTERVAL != 0) return;
            for (UUID id : List.copyOf(SESSIONS.keySet())) {
                Session session = SESSIONS.get(id);
                if (session == null || session.ritual) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof LanternBlockItem)) {
                    if (player != null) end(player);
                    else end(id);
                }
            }
        });
    }

    public static int timed(LivingEntity entity, int ticks) {
        return entity instanceof Player player && Comms.muted(player) ? Math.round(ticks * MUTED_SLOWDOWN) : ticks;
    }

    public static boolean begin(ServerPlayer player, LanternCorps corps, boolean ritual) {
        if (!BrightestDayConfig.get().oathRecognition || !Comms.available()) return false;
        if (!Comms.voiceReady(player.getUUID())) {
            BrightestDay.LOGGER.info("Timed charge for {}: voice chat not connected", player.getScoreboardName());
            return false;
        }
        OathRecognizer.ensureLoading();
        if (OathRecognizer.state() != OathRecognizer.State.READY) {
            BrightestDay.LOGGER.info("Timed charge for {}: oath model {}", player.getScoreboardName(), OathRecognizer.state());
            return false;
        }

        String oath = english(corps.oathKey());
        List<String> words = OathMatcher.tokenize(oath);
        OathRecognizer.Listener listener = OathRecognizer.listen(words);
        if (listener == null) {
            BrightestDay.LOGGER.info("Timed charge for {}: recognizer unavailable", player.getScoreboardName());
            return false;
        }

        end(player.getUUID());
        SESSIONS.put(player.getUUID(), new Session(new OathMatcher(words), listener, oath, ritual));
        BrightestDay.LOGGER.info("Oath charge started for {}", player.getScoreboardName());
        ServerPlayNetworking.send(player, new OathS2CPayload(oath, corps.color(), 0, 0.0F, true));
        return true;
    }

    private static String english(String key) {
        if (ENGLISH == null) {
            try (InputStream input = OathCharge.class.getResourceAsStream("/assets/" + BrightestDay.MOD_ID + "/lang/en_us.json")) {
                ENGLISH = input == null ? new JsonObject() : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException exception) {
                ENGLISH = new JsonObject();
            }
        }
        return ENGLISH.has(key) ? ENGLISH.get(key).getAsString() : Language.getInstance().getOrDefault(key);
    }

    public static boolean listening(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static boolean active(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void hear(UUID player, short[] samples) {
        Session session = SESSIONS.get(player);
        if (session == null) return;
        WORKER.execute(() -> {
            synchronized (session) {
                if (session.closed) return;
                session.listener.accept(samples, session.matcher);
            }
        });
    }

    public static float progress(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? 0.0F : session.matcher.progress();
    }

    public static boolean complete(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.matcher.complete();
    }

    public static boolean tick(ServerPlayer player, int color) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) return true;
        int reached = session.matcher.reached();
        if (reached != session.sentReached) {
            session.sentReached = reached;
            session.quietTicks = 0;
            ServerPlayNetworking.send(player, new OathS2CPayload(session.oath, color, reached, session.matcher.progress(), true));
        } else {
            session.quietTicks++;
        }
        return session.quietTicks < SILENCE_TIMEOUT_TICKS;
    }

    public static void end(UUID player) {
        Session session = SESSIONS.remove(player);
        if (session == null) return;
        WORKER.execute(() -> {
            synchronized (session) {
                session.closed = true;
                session.listener.close();
            }
        });
    }

    public static void end(ServerPlayer player) {
        boolean had = SESSIONS.containsKey(player.getUUID());
        end(player.getUUID());
        if (had) ServerPlayNetworking.send(player, OathS2CPayload.NONE);
    }

    private OathCharge() {}
}
