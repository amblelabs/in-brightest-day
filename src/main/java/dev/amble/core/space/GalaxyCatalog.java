package dev.amble.core.space;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class GalaxyCatalog {
    private static final long CATALOG_SEED = 0x2814_0000_0666_0001L;
    private static final int MIN_PLANETS = 3;
    private static final int EXTRA_PLANETS = 3;
    private static final int STARS = 400;
    private static final double MIN_DISTANCE = 20000.0 / Cosmos.COMPRESSION;
    private static final double MAX_DISTANCE = 60000.0 / Cosmos.COMPRESSION;
    private static final double MIN_SCALE = 0.35;
    private static final double MAX_SCALE = 0.8;
    private static final double SEPARATION = 2000.0 / Cosmos.COMPRESSION;
    private static final double WORMHOLE_CLEARANCE = 1500.0 / Cosmos.COMPRESSION;
    private static final int ATTEMPTS = 60;
    private static final String[] SYLLABLES = {"ka", "tor", "xen", "bal", "ris", "ul", "mor", "dra", "vey", "sol", "quen", "ith", "zar", "lo", "pha", "gor", "nev", "ash", "tel", "ry"};
    private static final int[] STAR_COLORS = {0x9DB4FF, 0xCAD8FF, 0xF8F7FF, 0xFFF4EA, 0xFFE8A0, 0xFFB46B, 0xFF7A4A};

    public enum Kind {
        ROCKY(Cosmos.Style.ROCK, Cosmos.GENERIC_ROCKY, 0x7C8894, 0x9AA4AE, 0xC8CED4, 35),
        ICE(Cosmos.Style.ICE, Cosmos.GENERIC_ICE, 0xA8D8F0, 0xD0ECF8, 0xF4FAFF, 25),
        DESERT(Cosmos.Style.DESERT, Cosmos.GENERIC_DESERT, 0xD8B070, 0xE8C890, 0xF4E8D0, 25),
        LAVA(Cosmos.Style.LAVA, Cosmos.GENERIC_LAVA, 0x3A140C, 0x5A2010, 0x3A2A28, 15);

        public final Cosmos.Style style;
        public final ResourceKey<Level> surface;
        public final int sky;
        public final int fog;
        public final int clouds;
        final int weight;

        Kind(Cosmos.Style style, ResourceKey<Level> surface, int sky, int fog, int clouds, int weight) {
            this.style = style;
            this.surface = surface;
            this.sky = sky;
            this.fog = fog;
            this.clouds = clouds;
            this.weight = weight;
        }
    }

    public record Star(ResourceKey<Level> sector, float yaw, float pitch, float size, int color) {}

    public record Catalog(List<Cosmos.Planet> planets, List<Star> stars) {}

    public static Catalog generate(List<Cosmos.Sector> sectors, List<Cosmos.Planet> fixed, List<Cosmos.Wormhole> wormholes) {
        List<Cosmos.Planet> planets = new ArrayList<>();
        List<Star> stars = new ArrayList<>();
        int region = 0;
        for (int s = 0; s < sectors.size(); s++) {
            Cosmos.Sector sector = sectors.get(s);
            Random random = new Random(CATALOG_SEED + s * 0x9E3779B97F4A7C15L);
            List<Cosmos.Planet> occupied = new ArrayList<>();
            for (Cosmos.Planet planet : fixed) if (planet.sector() == sector.key() || planet.sector() == null) occupied.add(planet);
            int count = sector.key() == Cosmos.SECTOR_2814 ? 0 : MIN_PLANETS + random.nextInt(EXTRA_PLANETS);
            for (int p = 0; p < count; p++) {
                Kind kind = kind(random);
                double scale = MIN_SCALE + random.nextDouble() * (MAX_SCALE - MIN_SCALE);
                long seed = random.nextLong();
                String name = name(random);
                for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
                    double x = Math.round(Math.cos(angle) * distance);
                    double z = Math.round(Math.sin(angle) * distance);
                    if (!clear(x, z, scale, occupied, sector, wormholes)) continue;
                    Cosmos.Planet planet = new Cosmos.Planet(sector.id() + "_" + p, sector.key(), kind.surface, kind.style, seed, x, z, scale, 0,
                            kind.sky, kind.fog, kind.clouds, name, region++);
                    planets.add(planet);
                    occupied.add(planet);
                    break;
                }
            }
            for (int i = 0; i < STARS; i++) {
                float yaw = random.nextFloat() * 360.0F;
                float pitch = (float) Math.toDegrees(Math.asin(random.nextFloat() * 1.9F - 0.95F));
                float size = 0.12F + random.nextFloat() * random.nextFloat() * 0.45F;
                stars.add(new Star(sector.key(), yaw, pitch, size, STAR_COLORS[random.nextInt(STAR_COLORS.length)]));
            }
        }
        return new Catalog(List.copyOf(planets), List.copyOf(stars));
    }

    private static boolean clear(double x, double z, double scale, List<Cosmos.Planet> occupied, Cosmos.Sector sector, List<Cosmos.Wormhole> wormholes) {
        double footprint = Cosmos.FOOTPRINT * scale;
        for (Cosmos.Planet other : occupied) {
            double reach = footprint + Cosmos.FOOTPRINT * other.scale() + SEPARATION;
            if (Math.abs(x - other.x()) < reach && Math.abs(z - other.z()) < reach) return false;
        }
        for (Cosmos.Wormhole wormhole : wormholes) {
            if (wormhole.sector() != sector.key()) continue;
            double reach = footprint + WORMHOLE_CLEARANCE;
            if (Math.abs(x - wormhole.x()) < reach && Math.abs(z - wormhole.z()) < reach) return false;
        }
        return true;
    }

    private static Kind kind(Random random) {
        int total = 0;
        for (Kind kind : Kind.values()) total += kind.weight;
        int roll = random.nextInt(total);
        for (Kind kind : Kind.values()) {
            roll -= kind.weight;
            if (roll < 0) return kind;
        }
        return Kind.ROCKY;
    }

    private static String name(Random random) {
        StringBuilder name = new StringBuilder();
        int syllables = 2 + random.nextInt(2);
        for (int i = 0; i < syllables; i++) name.append(SYLLABLES[random.nextInt(SYLLABLES.length)]);
        String text = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
        return random.nextInt(3) == 0 ? text + " " + (char) ('A' + random.nextInt(6)) : text;
    }

    private GalaxyCatalog() {}
}
