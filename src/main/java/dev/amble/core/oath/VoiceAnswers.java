package dev.amble.core.oath;

import dev.amble.config.BrightestDayConfig;
import dev.amble.core.comms.Comms;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class VoiceAnswers {
    public enum Answer { YES, NO }

    private static final Set<String> YES = Set.of("yes", "yeah", "yep", "yup", "aye");
    private static final Set<String> NO = Set.of("no", "nope", "nah", "never");
    private static final List<String> WORDS = List.of("yes", "yeah", "yep", "yup", "aye", "no", "nope", "nah", "never");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Brightest Day Answer Listener");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session implements OathRecognizer.Hearing {
        final OathRecognizer.Listener listener;
        volatile @Nullable Answer answer;
        boolean closed;

        Session(OathRecognizer.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void partial(String[] heard) {
            this.result(heard);
        }

        @Override
        public void result(String[] heard) {
            for (String word : heard) {
                Answer parsed = parse(word);
                if (parsed != null) {
                    this.answer = parsed;
                    return;
                }
            }
        }
    }

    public static @Nullable Answer parse(String text) {
        String word = OathMatcher.normalize(text.trim());
        if (YES.contains(word)) return Answer.YES;
        if (NO.contains(word)) return Answer.NO;
        return null;
    }

    public static boolean listen(ServerPlayer player) {
        if (!BrightestDayConfig.get().oathRecognition || !Comms.available() || !Comms.voiceReady(player.getUUID())) return false;
        OathRecognizer.ensureLoading();
        if (OathRecognizer.state() != OathRecognizer.State.READY) return false;
        OathRecognizer.Listener listener = OathRecognizer.listen(WORDS);
        if (listener == null) return false;
        end(player.getUUID());
        SESSIONS.put(player.getUUID(), new Session(listener));
        return true;
    }

    public static boolean listening(UUID player) {
        return SESSIONS.containsKey(player);
    }

    public static void hear(UUID player, short[] samples) {
        Session session = SESSIONS.get(player);
        if (session == null) return;
        WORKER.execute(() -> {
            synchronized (session) {
                if (session.closed) return;
                session.listener.accept(samples, session);
            }
        });
    }

    public static @Nullable Answer answer(UUID player) {
        Session session = SESSIONS.get(player);
        return session == null ? null : session.answer;
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

    private VoiceAnswers() {}
}
