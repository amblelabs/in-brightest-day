package dev.amble.client.lore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amble.BrightestDay;
import dev.amble.core.ringpowers.LanternCorps;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LoreLibrary implements ResourceManagerReloadListener {
    private static final Identifier BOOK = BrightestDay.id("lore/book.json");

    public record Chapter(String title, @Nullable LanternCorps corps, List<String> pages) {}

    public record Book(String title, List<Chapter> chapters) {}

    private static Book book = new Book("", List.of());

    public static void init() {
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(BrightestDay.id("lore"), new LoreLibrary());
    }

    public static Book book() {
        return book;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        Optional<Resource> resource = manager.getResource(BOOK);
        if (resource.isEmpty()) {
            book = new Book("", List.of());
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Chapter> chapters = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("chapters")) {
                JsonObject chapter = element.getAsJsonObject();
                List<String> pages = new ArrayList<>();
                JsonArray array = chapter.getAsJsonArray("pages");
                if (array != null) for (JsonElement page : array) pages.add(page.getAsString());
                chapters.add(new Chapter(chapter.has("title") ? chapter.get("title").getAsString() : "", corps(chapter), List.copyOf(pages)));
            }
            book = new Book(root.has("title") ? root.get("title").getAsString() : "", List.copyOf(chapters));
        } catch (Exception exception) {
            BrightestDay.LOGGER.warn("Failed to load lore book {}", BOOK, exception);
            book = new Book("", List.of());
        }
    }

    private static @Nullable LanternCorps corps(JsonObject chapter) {
        if (!chapter.has("color")) return null;
        String name = chapter.get("color").getAsString();
        for (LanternCorps corps : LanternCorps.values()) {
            if (corps.getSerializedName().equalsIgnoreCase(name)) return corps;
        }
        return null;
    }
}
