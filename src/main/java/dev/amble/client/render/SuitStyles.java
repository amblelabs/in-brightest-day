package dev.amble.client.render;

import dev.amble.BrightestDay;
import dev.amble.core.progression.Emotion;
import dev.amble.core.ringpowers.ColorTweak;
import dev.amble.core.ringpowers.LanternCorps;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public final class SuitStyles {
    private static final String FOLDER = "textures/lanterns/suits/comic";
    private static final String VARIANT = ColorTweak.COMIC + "_";
    private static final Map<LanternCorps, List<String>> STYLES = new EnumMap<>(LanternCorps.class);

    public static void clear() {
        STYLES.clear();
    }

    public static List<String> styles(LanternCorps corps) {
        return STYLES.computeIfAbsent(corps, SuitStyles::discover);
    }

    public static String resolve(LanternCorps corps, String style) {
        List<String> styles = styles(corps);
        if (styles.contains(style)) return style;
        if (style.startsWith(ColorTweak.COMIC) && styles.contains(ColorTweak.COMIC)) return ColorTweak.COMIC;
        return ColorTweak.MOVIE;
    }

    public static boolean comic(String style) {
        return !ColorTweak.MOVIE.equals(style);
    }

    public static Identifier comicTexture(LanternCorps corps, String style) {
        String suffix = style.startsWith(VARIANT) ? "_" + style.substring(VARIANT.length()) : "";
        return BrightestDay.id(FOLDER + "/" + emotion(corps) + suffix + ".png");
    }

    public static Component name(String style) {
        if (ColorTweak.MOVIE.equals(style)) return Component.translatable("gui.brightestday.suit_style.movie");
        if (ColorTweak.COMIC.equals(style)) return Component.translatable("gui.brightestday.suit_style.comic");
        String[] words = style.substring(VARIANT.length()).split("_");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!title.isEmpty()) title.append(' ');
            title.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return Component.translatable("gui.brightestday.suit_style.variant", title.toString());
    }

    private static List<String> discover(LanternCorps corps) {
        List<String> styles = new ArrayList<>();
        styles.add(ColorTweak.MOVIE);
        String emotion = emotion(corps);
        TreeSet<String> variants = new TreeSet<>();
        boolean base = false;
        for (Identifier path : Minecraft.getInstance().getResourceManager().listResources(FOLDER, id -> id.getPath().endsWith(".png")).keySet()) {
            if (!BrightestDay.MOD_ID.equals(path.getNamespace())) continue;
            String file = path.getPath().substring(FOLDER.length() + 1, path.getPath().length() - ".png".length());
            if (file.equals(emotion)) base = true;
            else if (file.startsWith(emotion + "_") && file.length() > emotion.length() + 1) variants.add(VARIANT + file.substring(emotion.length() + 1));
        }
        if (base) styles.add(ColorTweak.COMIC);
        styles.addAll(variants);
        return List.copyOf(styles);
    }

    private static String emotion(LanternCorps corps) {
        return switch (corps) {
            case WHITE -> "life";
            case BLACK -> "death";
            default -> Emotion.of(corps).map(StringRepresentable::getSerializedName).orElse(corps.getSerializedName());
        };
    }

    private SuitStyles() {}
}
