package dev.amble.verification;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.mojang.blaze3d.Blaze3D;
import com.sun.net.httpserver.HttpServer;
import dev.amble.BrightestDay;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetaVerification {

    public static final Logger LOGGER = LoggerFactory.getLogger(BetaVerification.class);

    private static final long TIMEOUT = 60_000L; // 60 seconds
    private static final int PORT = 54321;

    public static final int UNVERIFIED_PER_VERIFIED = 3;

    private static final String SUCCESS_HTML = "<html><body><h1>Verification successful! You can close this window.</h1></body></html>";

    public static Component PLAY = Component.translatable("gui.brightestday.beta.play");
    public static Component TOOLTIP = Component.translatable("gui.brightestday.beta.play.tooltip");
    public static Component BROWSER = Component.translatable("gui.brightestday.beta.browser");

    private static final BetaTokenPrefs PREFS = new BetaTokenPrefs(BrightestDay.class, BrightestDay.MOD_ID);

    public static ServerData SERVER_DATA;
    private static String RECEIVED_TOKEN;

    private static HttpServer SERVER;
    private static HttpClient CLIENT;

    public record ServerData(String verifier) { }

    public static final String BRANCH;

    static {
        // 1.x.xx-[BRANCH-]dev+mc.1.20.1
        String version = FabricLoader.getInstance().getModContainer(BrightestDay.MOD_ID).get().getMetadata().getVersion().getFriendlyString();
        // get the part of the version string between the - and +
        BRANCH = version.substring(version.indexOf("-") + 1, version.indexOf("+"));
    }

    private static final Supplier<Boolean> IS_UNSTABLE = Suppliers.memoize(() -> !BRANCH.contains("release"));
    private static final Supplier<Boolean> IS_OFFICIAL_BETA = Suppliers.memoize(() -> BRANCH.contains("dev"));
    private static final Supplier<Boolean> IS_LOCAL = Suppliers.memoize(() -> BRANCH.contains("local"));

    public static boolean isBuildUnstable() {
        return IS_UNSTABLE.get();
    }

    public static boolean isBuildOfficialBeta() {
        return IS_OFFICIAL_BETA.get();
    }

    public static boolean isBuildLocal() {
        return IS_LOCAL.get();
    }

    public static boolean isBetaLocked() {
        return !FabricLoader.getInstance().isDevelopmentEnvironment() && isBuildUnstable() && !BetaVerification.PREFS.isTokenValid();
    }

    public static void init() {
        if (!isBetaLocked()) return;

        BetaPlayerLimitTracker.init();

        BetaVerification.fetchServerData().thenAcceptAsync(_ -> PREFS.isTokenValid());
    }

    public static boolean isServerRunning() {
        return SERVER != null;
    }

    public static CompletableFuture<String> downloadAsString(String url) {
        return downloadAsString(url, UnaryOperator.identity());
    }

    public static CompletableFuture<String> downloadAsString(String url, UnaryOperator<HttpRequest.Builder> builder) {
        HttpRequest request = builder.apply(HttpRequest.newBuilder())
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();

        if (CLIENT == null)
            CLIENT = HttpClient.newHttpClient();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    // Fail the future for non-successful HTTP status codes
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    } else {
                        if (response.body() != null)
                            return response.body();

                        throw new RuntimeException("HTTP error code: " + response.statusCode());
                    }
                });
    }

    public static CompletableFuture<Void> fetchServerData() {
        return downloadAsString("https://amblelabs.dev/data.json").exceptionally(throwable -> {
            LOGGER.error("Failed to access the server data, falling back", throwable);
            return new Gson().toJson(new ServerData("https://amble-verifier.amblelabs.workers.dev"));
        }).thenAccept(s -> {
            SERVER_DATA = new Gson().fromJson(s, ServerData.class);
            LOGGER.info("Successfully fetched server data: {}", SERVER_DATA);
        });
    }

    public static void startAndWaitForToken(BooleanConsumer consumer) {
        new Thread(() -> {
            try {
                startAndWaitForToken0();
            } catch (Exception e) {
                LOGGER.error("Failed to wait for token", e);
            }

            consumer.accept(PREFS.isTokenValid());
        }).start();
    }

    private static HttpServer createServer(int port) throws IOException {
        return HttpServer.create(new InetSocketAddress(port), 0);
    }

    private static void startAndWaitForToken0() throws Exception {
        if (SERVER == null) {
            for (int port = PORT; SERVER == null && port < PORT + 5; port++) {
                try {
                    SERVER = createServer(port);
                } catch (BindException e) {
                    LOGGER.error("Failed to bind to port {}", port, e);
                    break;
                }
            }

            Objects.requireNonNull(SERVER, "Failed to start the server");

            SERVER.createContext("/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("token=")) {
                    RECEIVED_TOKEN = URLDecoder.decode(query.substring(6), StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, SUCCESS_HTML.length());
                    exchange.getResponseBody().write(SUCCESS_HTML.getBytes());
                    exchange.getResponseBody().close();
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ignored) { }
                        SERVER.stop(0);
                        SERVER = null;
                    }).start();
                } else {
                    exchange.sendResponseHeaders(400, 0);
                    exchange.getResponseBody().close();
                }
            });
            SERVER.start();
        }

        Blaze3D.openUri(new URI(getAuthUrl()));

        long waitingFor = 0L;
        while (RECEIVED_TOKEN == null) {
            Thread.sleep(500);
            waitingFor += 500L;

            if (waitingFor > TIMEOUT) {
                SERVER.stop(0);
                SERVER = null;
                return;
            }
        }

        PREFS.saveToken(RECEIVED_TOKEN);
        PREFS.verify();

        RECEIVED_TOKEN = null;
    }

    public static String getAuthUrl() {
        return SERVER_DATA.verifier + "/auth?port=" + PORT;
    }
}