package dev.amble.core.space;

import dev.amble.BrightestDay;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Cosmos {
    public static final double COMPRESSION = 10.0;
    public static final double PLANET_HALF_SIZE = 3000.0;
    public static final double VIRTUAL_PER_BLOCK = 4.0 * COMPRESSION;
    public static final double FOOTPRINT = PLANET_HALF_SIZE / VIRTUAL_PER_BLOCK;
    public static final double SURFACE_SCALE = 8.0 * COMPRESSION;
    public static final double MOGO_X = 24000.0 / COMPRESSION;
    public static final double MOGO_Z = -24000.0 / COMPRESSION;

    public static final double REGION_SPACING = 100000.0;
    public static final double PLANET_CENTER_Y = 400.0;
    public static final double WORMHOLE_SIZE = 30.0;
    public static volatile ResourceKey<Level> knownMogo = key("sector_0");
    private static final double HUB = 15000.0 / COMPRESSION;
    private static final double RIFT = 7000.0 / COMPRESSION;
    private static final double WORMHOLE_Y = 520.0;
    private static final double ARRIVAL_OFFSET = 90.0;

    public enum Style { EARTH, OA, MOGO, ODYM, RYUT, ZAMARON, QWARD, ROCK, GAS, RINGED_GAS, ICE, MARS, STAR, DESERT, LAVA }

    public record Sector(ResourceKey<Level> key, String id, int starColor, boolean antimatter) {}

    public record Planet(String id, @Nullable ResourceKey<Level> sector, @Nullable ResourceKey<Level> surface, Style style, long seed, double x, double z, double scale,
                         int ringColor, int sky, int fog, int clouds, String name, int region) {
        public double footprint() {
            return FOOTPRINT * this.scale;
        }

        public double surfaceHalf() {
            return this.footprint() * SURFACE_SCALE;
        }

        public double regionX() {
            return this.region < 0 ? 0.0 : this.region * REGION_SPACING;
        }
    }

    public record Wormhole(String id, ResourceKey<Level> sector, double x, double y, double z, ResourceKey<Level> destination, double toX, double toY, double toZ, int color, boolean antimatter) {}

    public static final ResourceKey<Level> SECTOR_2814 = SpaceTravel.SPACE;
    public static final ResourceKey<Level> SECTOR_0 = key("sector_0");
    public static final ResourceKey<Level> SECTOR_666 = key("sector_666");
    public static final ResourceKey<Level> SECTOR_1416 = key("sector_1416");
    public static final ResourceKey<Level> ODYM_GALAXY = key("odym_galaxy");
    public static final ResourceKey<Level> ANTIMATTER = key("antimatter");

    public static final ResourceKey<Level> OA = key("oa");
    public static final ResourceKey<Level> MOGO = key("mogo");
    public static final ResourceKey<Level> ODYM = key("odym");
    public static final ResourceKey<Level> RYUT = key("ryut");
    public static final ResourceKey<Level> ZAMARON = key("zamaron");
    public static final ResourceKey<Level> QWARD = key("qward");
    public static final ResourceKey<Level> GENERIC_ROCKY = key("rocky_world");
    public static final ResourceKey<Level> GENERIC_ICE = key("ice_world");
    public static final ResourceKey<Level> GENERIC_DESERT = key("desert_world");
    public static final ResourceKey<Level> GENERIC_LAVA = key("lava_world");
    private static final List<ResourceKey<Level>> GENERIC = List.of(GENERIC_ROCKY, GENERIC_ICE, GENERIC_DESERT, GENERIC_LAVA);

    public static final List<Sector> SECTORS = List.of(
            new Sector(SECTOR_2814, "sector_2814", 0xFFF2C0, false),
            new Sector(SECTOR_0, "sector_0", 0xD8F4FF, false),
            new Sector(SECTOR_666, "sector_666", 0xFF3A1E, false),
            new Sector(SECTOR_1416, "sector_1416", 0xFF8AE0, false),
            new Sector(ODYM_GALAXY, "odym_galaxy", 0x7AB8FF, false),
            new Sector(ANTIMATTER, "antimatter", 0x6A0010, true));

    public static final Planet EARTH = new Planet("earth", SECTOR_2814, Level.OVERWORLD, Style.EARTH, 2814L, 0.0, 0.0, 1.0, 0, 0, 0, 0, "", -1);
    public static final Planet OA_PLANET = new Planet("oa", SECTOR_0, OA, Style.OA, 1L, 0.0, 0.0, 1.0, 0, 0x141A22, 0x1B222B, 0x303A44, "", -1);
    public static final Planet MOGO_PLANET = new Planet("mogo", null, MOGO, Style.MOGO, 2L, MOGO_X, MOGO_Z, 0.8, 0x00E03C, 0, 0, 0, "", -1);
    public static final Planet ODYM_PLANET = new Planet("odym", ODYM_GALAXY, ODYM, Style.ODYM, 3L, 0.0, 0.0, 1.0, 0, 0x6FB8C8, 0x9ED8C0, 0xE8FFF4, "", -1);
    public static final Planet RYUT_PLANET = new Planet("ryut", SECTOR_666, RYUT, Style.RYUT, 666L, 0.0, 0.0, 1.0, 0, 0x3A1A12, 0x5A2A18, 0x4A3028, "", -1);
    public static final Planet ZAMARON_PLANET = new Planet("zamaron", SECTOR_1416, ZAMARON, Style.ZAMARON, 1416L, 0.0, 0.0, 1.0, 0, 0xC88A9A, 0xD8A0A8, 0xF0D0D8, "", -1);
    public static final Planet QWARD_PLANET = new Planet("qward", ANTIMATTER, QWARD, Style.QWARD, 9L, 0.0, 0.0, 1.0, 0, 0x2A0408, 0x3A0A0C, 0x501418, "", -1);

    private static final List<Planet> HOMES = List.of(OA_PLANET, ODYM_PLANET, RYUT_PLANET, ZAMARON_PLANET, QWARD_PLANET);

    public static final List<Wormhole> WORMHOLES = List.of(
            hub("to_2814", HUB, 0.0, SECTOR_2814),
            hub("to_666", 0.0, HUB, SECTOR_666),
            hub("to_1416", -HUB, 0.0, SECTOR_1416),
            hub("to_odym", 0.0, -HUB, ODYM_GALAXY),
            home("home_2814", SECTOR_2814, HUB, 0.0),
            home("home_666", SECTOR_666, 0.0, HUB),
            home("home_1416", SECTOR_1416, -HUB, 0.0),
            home("home_odym", ODYM_GALAXY, 0.0, -HUB),
            new Wormhole("antimatter", SECTOR_0, RIFT, 560.0, -RIFT, ANTIMATTER, 0.0, 560.0, HUB - ARRIVAL_OFFSET, 0xC0102A, true),
            new Wormhole("antimatter_return", ANTIMATTER, 0.0, 560.0, HUB, SECTOR_0, RIFT - ARRIVAL_OFFSET, 560.0, -(RIFT - ARRIVAL_OFFSET), 0xC0102A, false));


    public static final GalaxyCatalog.Catalog CATALOG = GalaxyCatalog.generate(SECTORS,
            List.of(EARTH, OA_PLANET, MOGO_PLANET, ODYM_PLANET, RYUT_PLANET, ZAMARON_PLANET, QWARD_PLANET), WORMHOLES);

    private static Wormhole hub(String id, double x, double z, ResourceKey<Level> destination) {
        double[] arrival = arrival(x, z);
        return new Wormhole(id, SECTOR_0, x, WORMHOLE_Y, z, destination, arrival[0], WORMHOLE_Y, arrival[1], 0x7A4CFF, false);
    }

    private static Wormhole home(String id, ResourceKey<Level> sector, double x, double z) {
        double[] arrival = arrival(x, z);
        return new Wormhole(id, sector, 0.0, WORMHOLE_Y, HUB, SECTOR_0, arrival[0], WORMHOLE_Y, arrival[1], 0x7A4CFF, false);
    }

    private static double[] arrival(double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        double scale = (length - ARRIVAL_OFFSET) / length;
        return new double[]{x * scale, z * scale};
    }

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, BrightestDay.id(path));
    }

    public static @Nullable Sector sector(ResourceKey<Level> key) {
        for (Sector sector : SECTORS) if (sector.key() == key) return sector;
        return null;
    }

    public static boolean isSector(ResourceKey<Level> key) {
        return sector(key) != null;
    }

    public static List<Planet> planetsIn(ResourceKey<Level> sector, @Nullable ResourceKey<Level> mogoSector) {
        List<Planet> planets = new ArrayList<>();
        if (sector == SECTOR_2814) planets.add(EARTH);
        if (sector == SECTOR_0) planets.add(OA_PLANET);
        if (sector == SECTOR_666) planets.add(RYUT_PLANET);
        if (sector == SECTOR_1416) planets.add(ZAMARON_PLANET);
        if (sector == ODYM_GALAXY) planets.add(ODYM_PLANET);
        if (sector == ANTIMATTER) planets.add(QWARD_PLANET);
        if (mogoSector != null && sector == mogoSector && sector != ANTIMATTER) planets.add(MOGO_PLANET);
        for (Planet planet : CATALOG.planets()) if (planet.sector() == sector) planets.add(planet);
        return planets;
    }

    public static boolean isGeneric(ResourceKey<Level> surface) {
        return GENERIC.contains(surface);
    }

    public static boolean isSurface(ResourceKey<Level> surface) {
        return isGeneric(surface) || planetBySurface(surface) != null;
    }

    public static @Nullable Planet planetAt(ResourceKey<Level> surface, double x) {
        if (!isGeneric(surface)) return planetBySurface(surface);
        int region = (int) Math.round(x / REGION_SPACING);
        for (Planet planet : CATALOG.planets()) if (planet.region() == region && planet.surface() == surface) return planet;
        return null;
    }

    public static @Nullable Planet planetBySurface(ResourceKey<Level> surface) {
        if (surface == Level.OVERWORLD) return EARTH;
        if (surface == MOGO) return MOGO_PLANET;
        for (Planet planet : HOMES) if (planet.surface() == surface) return planet;
        return null;
    }

    public static @Nullable ResourceKey<Level> homeSector(Planet planet, ResourceKey<Level> mogoSector) {
        return planet == MOGO_PLANET ? mogoSector : planet.sector();
    }

    private Cosmos() {}
}
