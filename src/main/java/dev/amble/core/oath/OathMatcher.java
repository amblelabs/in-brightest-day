package dev.amble.core.oath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OathMatcher implements OathRecognizer.Hearing {
    private static final int SKIP = 2;
    private static final int FUZZY_MIN_LENGTH = 4;
    private static final Map<String, Set<String>> ALIASES = Map.ofEntries(
            Map.entry("tor", Set.of("tore", "torr")),
            Map.entry("lorek", Set.of("lore")),
            Map.entry("san", Set.of("sun", "son", "sans")),
            Map.entry("bor", Set.of("bore", "boar", "bar")),
            Map.entry("nakka", Set.of("naka", "knack", "nacho")),
            Map.entry("mur", Set.of("more", "murr", "myrrh", "mer")),
            Map.entry("natromo", Set.of("nitro", "metro", "maestro", "astro", "petro", "promo", "nostrum", "tro")),
            Map.entry("faan", Set.of("fan", "fawn", "fun")),
            Map.entry("tornek", Set.of("torn", "tornado")),
            Map.entry("wot", Set.of("what", "watt", "wat")),
            Map.entry("ur", Set.of("or", "er", "her")),
            Map.entry("ter", Set.of("tear", "tur")),
            Map.entry("ker", Set.of("care", "cur", "core")),
            Map.entry("lo", Set.of("low", "lowe")),
            Map.entry("abin", Set.of("bin", "robin")),
            Map.entry("sur", Set.of("sir", "sure", "sewer")),
            Map.entry("taan", Set.of("tan", "ton")),
            Map.entry("lek", Set.of("lick", "lake")),
            Map.entry("nok", Set.of("knock", "nock")),
            Map.entry("formorrow", Set.of("for", "morrow", "tomorrow", "marrow", "more")),
            Map.entry("evil's", Set.of("evil", "evils")),
            Map.entry("sinestro's", Set.of("sinister")));

    private final List<String> words;
    private volatile int committed;
    private volatile int shown;

    public OathMatcher(List<String> words) {
        this.words = words;
    }

    public static List<String> tokenize(String oath) {
        List<String> words = new ArrayList<>();
        for (String token : oath.trim().split("\\s+")) words.add(normalize(token));
        return words;
    }

    public static Set<String> grammar(List<String> words) {
        Set<String> grammar = new LinkedHashSet<>();
        for (String word : words) {
            if (word.isEmpty()) continue;
            grammar.add(word);
            grammar.addAll(ALIASES.getOrDefault(word, Set.of()));
        }
        return grammar;
    }

    public static String normalize(String token) {
        return token.toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
    }

    @Override
    public void partial(String[] heard) {
        this.shown = Math.max(this.shown, this.advance(this.committed, heard));
    }

    @Override
    public void result(String[] heard) {
        this.committed = Math.max(this.committed, this.advance(this.committed, heard));
        this.shown = Math.max(this.shown, this.committed);
    }

    public int reached() {
        return this.shown;
    }

    public float progress() {
        return this.words.isEmpty() ? 1.0F : Math.min(1.0F, this.shown / (float) (this.words.size() - 1));
    }

    public boolean complete() {
        return this.shown >= this.words.size() - 1;
    }

    private int advance(int from, String[] heard) {
        int position = this.skipSilent(from);
        for (String word : heard) {
            if (word.isEmpty() || word.equals("[unk]")) continue;
            int limit = Math.min(this.words.size(), position + SKIP + 1);
            for (int j = position; j < limit; j++) {
                if (!this.words.get(j).isEmpty() && matches(this.words.get(j), word)) {
                    position = this.skipSilent(j + 1);
                    break;
                }
            }
        }
        return position;
    }

    private int skipSilent(int position) {
        while (position < this.words.size() && this.words.get(position).isEmpty()) position++;
        return position;
    }

    private static boolean matches(String expected, String heard) {
        if (expected.equals(heard) || ALIASES.getOrDefault(expected, Set.of()).contains(heard)) return true;
        return expected.length() >= FUZZY_MIN_LENGTH && distance(expected, heard) <= 1;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
