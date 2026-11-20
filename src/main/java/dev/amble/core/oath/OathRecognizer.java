package dev.amble.core.oath;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Pointer;
import dev.amble.BrightestDay;
import dev.amble.config.BrightestDayConfig;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class OathRecognizer {

    private static final String VOSK_JAR_URL = "https://repo1.maven.org/maven2/com/alphacephei/vosk/0.3.45/vosk-0.3.45.jar";
    private static final Path MOD_DATA_DIR = FabricLoader.getInstance().getGameDir().resolve("data").resolve(BrightestDay.MOD_ID);

    private static final float SAMPLE_RATE = 16000.0F;
    private static final int DOWNSAMPLE = 3;

    enum State {
        IDLE,
        LOADING,
        READY,
        FAILED
    }

    private static volatile State state = State.IDLE;
    private static volatile @Nullable VoskLibrary vosk;
    private static volatile @Nullable Pointer model;

    static State state() {
        return state;
    }

    static synchronized void ensureLoading() {
        if (state != State.IDLE) return;
        state = State.LOADING;
        Thread loader = new Thread(OathRecognizer::load, "Brightest Day Oath Model");
        loader.setDaemon(true);
        loader.start();
    }

    private static void load() {
        try {
            Files.createDirectories(MOD_DATA_DIR);

            String url = VOSK_JAR_URL;
            String name = getNameFromUrl(url, true);
            Path lib = MOD_DATA_DIR.resolve(name);

            if (!Files.exists(lib)) {
                download(url, lib);
            }

            ClassLoader loader = OathRecognizer.class.getClassLoader();
            Class<?> cl = loader.getClass();
            Method addURL = cl.getMethod("addUrlFwd", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(loader, lib.toUri().toURL());

            VoskLibrary library = VoskLibrary.load();
            library.vosk_set_log_level(VoskLibrary.LOG_WARNINGS);

            url = BrightestDayConfig.get().oathModelUrl;
            name = getNameFromUrl(url, false);
            Path modelDir = MOD_DATA_DIR.resolve(name);

            if (!Files.isDirectory(modelDir)) {
                Path archive = MOD_DATA_DIR.resolve("oath-model.zip");

                download(url, archive);
                unzip(archive, MOD_DATA_DIR);

                Files.deleteIfExists(archive);

                if (!Files.isDirectory(modelDir))
                    throw new IOException("Model archive did not contain " + archive.getFileName());
            }

            Pointer loaded = library.vosk_model_new(modelDir.toString());

            if (loaded == null) throw new IOException("Failed to load oath model from " + modelDir);

            vosk = library;
            model = loaded;
            state = State.READY;
            BrightestDay.LOGGER.info("Oath recognition ready ({})", modelDir.getFileName());
        } catch (Throwable throwable) {
            state = State.FAILED;
            BrightestDay.LOGGER.warn("Oath recognition unavailable, handheld charging stays timed", throwable);
        }
    }

    private static String getNameFromUrl(String url, boolean withExtension) {
        int slashIdx = url.lastIndexOf('/') + 1;
        return withExtension ? url.substring(slashIdx) : url.substring(slashIdx, url.lastIndexOf('.'));
    }

    private static void download(String url, Path file) throws IOException, InterruptedException {
        BrightestDay.LOGGER.info("Downloading file from {} to {}...", url, file);

        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build()) {
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofFile(file));
            if (response.statusCode() != 200) throw new IOException("Download failed with HTTP " + response.statusCode());
        }
    }

    private static void unzip(Path archive, Path root) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path target = base.resolve(entry.getName()).normalize();
                if (!target.startsWith(base)) throw new IOException("Unsafe entry in model archive: " + entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static @Nullable Listener listen(List<String> words) {
        if (state != State.READY) return null;
        VoskLibrary library = vosk;
        Pointer loaded = model;
        if (library == null || loaded == null) return null;
        JsonArray grammar = new JsonArray();
        OathMatcher.grammar(words).forEach(grammar::add);
        grammar.add("[unk]");
        try {
            Pointer recognizer = library.vosk_recognizer_new_grm(loaded, SAMPLE_RATE, grammar.toString());
            if (recognizer == null) throw new IOException("Failed to create oath recognizer");
            return new Listener(library, recognizer);
        } catch (Throwable throwable) {
            BrightestDay.LOGGER.warn("Could not start oath recognizer", throwable);
            return null;
        }
    }

    interface Hearing {
        void partial(String[] heard);

        void result(String[] heard);
    }

    static final class Listener {
        private final VoskLibrary library;
        private final Pointer recognizer;
        private volatile boolean closed;

        private Listener(VoskLibrary library, Pointer recognizer) {
            this.library = library;
            this.recognizer = recognizer;
        }

        void accept(short[] samples48k, Hearing matcher) {
            int length = samples48k.length / DOWNSAMPLE;
            short[] samples = new short[length];
            for (int i = 0; i < length; i++) {
                int base = i * DOWNSAMPLE;
                samples[i] = (short) ((samples48k[base] + samples48k[base + 1] + samples48k[base + 2]) / DOWNSAMPLE);
            }
            if (this.closed) return;
            if (this.library.vosk_recognizer_accept_waveform_s(this.recognizer, samples, length)) {
                String[] heard = words(this.library.vosk_recognizer_result(this.recognizer), "text");
                if (heard.length > 0) BrightestDay.LOGGER.debug("Oath heard: {}", String.join(" ", heard));
                matcher.result(heard);
            } else {
                matcher.partial(words(this.library.vosk_recognizer_partial_result(this.recognizer), "partial"));
            }
        }

        void close() {
            if (this.closed) return;
            this.closed = true;
            this.library.vosk_recognizer_free(this.recognizer);
        }

        private static String[] words(String json, String field) {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            String text = object.has(field) ? object.get(field).getAsString().trim() : "";
            return text.isEmpty() ? new String[0] : text.split("\\s+");
        }
    }

    private OathRecognizer() {}
}
