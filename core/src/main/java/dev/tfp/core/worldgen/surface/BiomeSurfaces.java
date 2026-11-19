/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the surface builder, shore and sandy river
 * shore flags of each biome, in registry order), with surface/SurfaceManager.java (collectSurfaceBuilders),
 * biome/BiomeBuilder.java (the centered feature wrappers of every builder) and
 * surface/builder/ShoreAndOceanSurfaceBuilder.java (the factories)
 */
package dev.tfp.core.worldgen.surface;

import static dev.tfp.core.worldgen.surface.SurfaceRules.*;

import dev.tfp.core.worldgen.Seed;
import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.function.Function;

/**
 * Every TFC biome's own surface builder, built for one level the way TFC's {@code SurfaceManager} builds them: in the order of
 * TFC's biome registry, from the generator's shared seed stream. Each builder is wrapped as {@code BiomeBuilder.surface} wraps
 * it: the cinder cone wrapper is ported; the stratovolcano, atoll, tuff ring and tuya wrappers are not (their centered
 * features are not part of the heights either, see the ledger), but the atoll and tuya wrappers still take their seed from the
 * stream, so the seeds of the later builders are TFC's. Immutable and thread-safe once built.
 */
public final class BiomeSurfaces {
    /** The factories of {@code ShoreAndOceanSurfaceBuilder}. */
    public enum ShoreKind {
        NORMAL, SANDY, FORCE_RARE_SAND, GRAVELLY, OCEAN, SEA_CLIFFS, OLD_SHIELD_VOLCANO, ACTIVE_SHIELD_VOLCANO, MOUNTAINS,
        VOLCANIC_MOUNTAINS, ROCKY_SHORE, OCEAN_RIDGE
    }

    /** A biome of TFC's registry: its id, its builder factory (from the shared stream), and its river flags. */
    private record Entry(int biome, Function<Seed, SurfaceBuilder> factory, boolean shore, boolean sandyRiverShores) {}

    /** Marks the river biome, whose builder needs the table itself. */
    private static final Function<Seed, SurfaceBuilder> RIVER = s -> null;

    /** TFC's biome registry, in registration order: the order of the seeds the builders take. */
    private static final Entry[] REGISTRY = {
        new Entry(TfcBiomes.OCEAN, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.OCEAN_REEF, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.DEEP_OCEAN, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.DEEP_OCEAN_TRENCH, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.OCEANIC_VOLCANIC_ARC, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.OCEAN_ATOLLS, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.DEEP_OCEAN_ATOLLS, s -> shoreAndOcean(ShoreKind.OCEAN, s.seed()), false, false),
        new Entry(TfcBiomes.OCEAN_RIDGE, s -> shoreAndOcean(ShoreKind.OCEAN_RIDGE, s.seed()), false, false),
        new Entry(TfcBiomes.RIFT_VALLEY, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.RIFT_LAKE, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.COLLISIONAL_MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.PLAINS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.LOWLANDS, LandSurfaces::lowlands, false, false),
        new Entry(TfcBiomes.SALT_MARSH, LandSurfaces::lowlands, false, false),
        new Entry(TfcBiomes.LOW_CANYONS, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.RIVER_VALLEY, s -> LandSurfaces.normalAlternate(false), false, true),
        new Entry(TfcBiomes.ROLLING_HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.HIGHLANDS, s -> SurfaceBuilders.ROCKY, false, true),
        new Entry(TfcBiomes.BADLANDS, s -> new BadlandsSurface(BadlandsSurface.Kind.NORMAL, s), false, true),
        new Entry(TfcBiomes.PLATEAU, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.PLATEAU_WIDE, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.CANYONS, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.OLD_MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.OCEANIC_MOUNTAINS, s -> shoreAndOcean(ShoreKind.MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.VOLCANIC_MOUNTAINS, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.VOLCANIC_OCEANIC_MOUNTAINS, s -> shoreAndOcean(ShoreKind.VOLCANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.VOLCANIC_MOUNTAIN_ISLANDS, s -> shoreAndOcean(ShoreKind.VOLCANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.GUANO_ISLAND, s -> shoreAndOcean(ShoreKind.ROCKY_SHORE, s.seed()), false, false),
        new Entry(TfcBiomes.VOLCANIC_ISLAND, s -> SurfaceBuilders.VOLCANIC_SOIL, false, true),
        new Entry(TfcBiomes.SHORE, s -> shoreAndOcean(ShoreKind.SANDY, s.seed()), true, false),
        new Entry(TfcBiomes.TIDAL_FLATS, s -> shoreAndOcean(ShoreKind.SANDY, s.seed()), true, false),
        new Entry(TfcBiomes.SEA_STACKS, s -> shoreAndOcean(ShoreKind.SEA_CLIFFS, s.seed()), true, false),
        new Entry(TfcBiomes.TERRACE_UPPER, s -> shoreAndOcean(ShoreKind.SEA_CLIFFS, s.seed()), true, false),
        new Entry(TfcBiomes.TERRACE_LOWER, s -> shoreAndOcean(ShoreKind.SEA_CLIFFS, s.seed()), true, false),
        new Entry(TfcBiomes.SETBACK_CLIFFS, s -> shoreAndOcean(ShoreKind.SANDY, s.seed()), true, false),
        new Entry(TfcBiomes.COASTAL_DUNES, s -> shoreAndOcean(ShoreKind.SANDY, s.seed()), true, false),
        new Entry(TfcBiomes.ROCKY_SHORES, s -> shoreAndOcean(ShoreKind.ROCKY_SHORE, s.seed()), true, false),
        new Entry(TfcBiomes.EMBAYMENTS, s -> shoreAndOcean(ShoreKind.SEA_CLIFFS, s.seed()), true, false),
        new Entry(TfcBiomes.LAKE, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.RIVER, RIVER, false, true),
        new Entry(TfcBiomes.MOUNTAIN_LAKE, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.OLD_MOUNTAIN_LAKE, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.OCEANIC_MOUNTAIN_LAKE, s -> shoreAndOcean(ShoreKind.MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.VOLCANIC_MOUNTAIN_LAKE, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.VOLCANIC_OCEANIC_MOUNTAIN_LAKE, s -> shoreAndOcean(ShoreKind.VOLCANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.PLATEAU_LAKE, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.MUD_FLATS, s -> LandSurfaces.flats(DRY_MUD, DRY_MUD, MUD), false, false),
        new Entry(TfcBiomes.SALT_FLATS, s -> LandSurfaces.flats(SALTED_EARTH, DRY_MUD, MUD), false, false),
        new Entry(TfcBiomes.DUNE_SEA, s -> LandSurfaces.dune(), false, true),
        new Entry(TfcBiomes.GRASSY_DUNES, LandSurfaces::grassyDunes, false, true),
        new Entry(TfcBiomes.WHORLED_CANYONS, s -> new BadlandsSurface(BadlandsSurface.Kind.WARPED, s), false, true),
        new Entry(TfcBiomes.STAIR_STEP_CANYONS, s -> new BadlandsSurface(BadlandsSurface.Kind.MESAS, s), false, true),
        new Entry(TfcBiomes.MESAS, s -> new BadlandsSurface(BadlandsSurface.Kind.MESAS, s), false, true),
        new Entry(TfcBiomes.BUTTES, s -> new BadlandsSurface(BadlandsSurface.Kind.MESAS, s), false, true),
        new Entry(TfcBiomes.HOODOOS, s -> new BadlandsSurface(BadlandsSurface.Kind.HOODOOS, s), false, true),
        new Entry(TfcBiomes.ROCKY_PLATEAU, s -> LandSurfaces.rockyPlateau(s.seed()), false, false),
        new Entry(TfcBiomes.TOWER_KARST_PLAINS, s -> SurfaceBuilders.ROCKY, false, true),
        new Entry(TfcBiomes.TOWER_KARST_HILLS, s -> SurfaceBuilders.ROCKY, false, true),
        new Entry(TfcBiomes.TOWER_KARST_CANYONS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.TOWER_KARST_HIGHLANDS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.TOWER_KARST_LAKE, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.TOWER_KARST_BAY, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.BURREN_PLATEAU, s -> LandSurfaces.burren(s.seed()), false, false),
        new Entry(TfcBiomes.BURREN_BADLANDS, s -> LandSurfaces.burren(s.seed()), false, false),
        new Entry(TfcBiomes.BURREN_BADLANDS_TALL, s -> LandSurfaces.burren(s.seed()), false, false),
        new Entry(TfcBiomes.BURREN_PLAINS, s -> LandSurfaces.burren(s.seed()), false, false),
        new Entry(TfcBiomes.BURREN_ROCHE_MOUTONEE, s -> LandSurfaces.burren(s.seed()), false, false),
        new Entry(TfcBiomes.SHILIN_PLAINS, s -> LandSurfaces.shilin(s.seed()), false, true),
        new Entry(TfcBiomes.SHILIN_CANYONS, s -> LandSurfaces.shilin(s.seed()), false, true),
        new Entry(TfcBiomes.SHILIN_HILLS, s -> LandSurfaces.shilin(s.seed()), false, true),
        new Entry(TfcBiomes.SHILIN_HIGHLANDS, s -> LandSurfaces.shilin(s.seed()), false, true),
        new Entry(TfcBiomes.SHILIN_PLATEAU, s -> LandSurfaces.shilin(s.seed()), false, true),
        new Entry(TfcBiomes.DOLINE_PLAINS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.DOLINE_HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.DOLINE_ROLLING_HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.DOLINE_HIGHLANDS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.DOLINE_PLATEAU, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.DOLINE_CANYONS, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.CENOTE_PLAINS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.CENOTE_HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.CENOTE_ROLLING_HILLS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.CENOTE_CANYONS, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.CENOTE_HIGHLANDS, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.CENOTE_PLATEAU, s -> SurfaceBuilders.NORMAL, false, false),
        new Entry(TfcBiomes.EXTREME_DOLINE_PLATEAU, s -> SurfaceBuilders.ROCKY, false, true),
        new Entry(TfcBiomes.EXTREME_DOLINE_MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.ACTIVE_SHIELD_VOLCANO, s -> ShieldVolcanoSurface.active(s.seed()), false, false),
        new Entry(TfcBiomes.DORMANT_SHIELD_VOLCANO, s -> ShieldVolcanoSurface.dormant(), false, false),
        new Entry(TfcBiomes.EXTINCT_SHIELD_VOLCANO, s -> ShieldVolcanoSurface.dormant(), false, false),
        new Entry(TfcBiomes.ANCIENT_SHIELD_VOLCANO, s -> ShieldVolcanoSurface.dormant(), false, false),
        new Entry(TfcBiomes.SUNKEN_SHIELD_VOLCANO, s -> ShieldVolcanoSurface.dormant(), false, false),
        new Entry(TfcBiomes.SHIELD_VOLCANO_SHORE, s -> shoreAndOcean(ShoreKind.ACTIVE_SHIELD_VOLCANO, s.seed()), true, true),
        new Entry(TfcBiomes.OLD_SHIELD_VOLCANO_SHORE, s -> shoreAndOcean(ShoreKind.OLD_SHIELD_VOLCANO, s.seed()), true, true),
        new Entry(TfcBiomes.ICE_SHEET, s -> IceSheetSurface.of(IceSheetSurface.Kind.NORMAL, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_OCEANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_OCEANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_VOLCANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_VOLCANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_VOLCANIC_OCEANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_SHIELD_VOLCANO, s -> IceSheetSurface.shieldVolcano(s.seed(), false), false, false),
        new Entry(TfcBiomes.ICE_SHEET_TUYAS, s -> IceSheetSurface.of(IceSheetSurface.Kind.NORMAL, s.seed()), false, false),
        new Entry(TfcBiomes.SUBGLACIAL_LAKE, s -> IceSheetSurface.of(IceSheetSurface.Kind.HIDDEN_LAKE, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_EDGE, s -> IceSheetSurface.of(IceSheetSurface.Kind.EDGE, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_TUYAS_EDGE, s -> IceSheetSurface.of(IceSheetSurface.Kind.EDGE, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_MOUNTAINS_EDGE, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_OCEANIC_MOUNTAINS_EDGE, s -> IceSheetSurface.of(IceSheetSurface.Kind.ICE_SHEET_OCEANIC_MOUNTAINS, s.seed()), true, false),
        new Entry(TfcBiomes.MELTWATER_LAKE, s -> IceSheetSurface.of(IceSheetSurface.Kind.EDGE_LAKE, s.seed()), true, false),
        new Entry(TfcBiomes.ICE_SHEET_OCEANIC, s -> IceSheetSurface.of(IceSheetSurface.Kind.OCEANIC, s.seed()), false, false),
        new Entry(TfcBiomes.ICE_SHEET_SHORE, s -> IceSheetSurface.of(IceSheetSurface.Kind.OCEANIC, s.seed()), true, false),
        new Entry(TfcBiomes.GLACIATED_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.GLACIATED_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.GLACIATED_OCEANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.GLACIATED_OCEANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.GLACIATED_VOLCANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.GLACIATED_VOLCANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS, s -> IceSheetSurface.of(IceSheetSurface.Kind.GLACIATED_VOLCANIC_OCEANIC_MOUNTAINS, s.seed()), false, false),
        new Entry(TfcBiomes.GLACIATED_SHIELD_VOLCANO, s -> IceSheetSurface.shieldVolcano(s.seed(), true), false, false),
        new Entry(TfcBiomes.GLACIALLY_CARVED_MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.GLACIALLY_CARVED_OCEANIC_MOUNTAINS, s -> SurfaceBuilders.ROCKY, false, false),
        new Entry(TfcBiomes.GLACIALLY_CARVED_VOLCANIC_MOUNTAINS, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.GLACIALLY_CARVED_VOLCANIC_OCEANIC_MOUNTAINS, s -> SurfaceBuilders.VOLCANIC_SOIL, false, false),
        new Entry(TfcBiomes.DRUMLINS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.TUYAS, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.KNOB_AND_KETTLE, s -> SurfaceBuilders.NORMAL, false, true),
        new Entry(TfcBiomes.PATTERNED_GROUND, s -> LandSurfaces.patternedGround(s.seed()), false, true),
        new Entry(TfcBiomes.INVERTED_PATTERNED_GROUND, s -> LandSurfaces.patternedGround(s.seed()), false, true),
        new Entry(TfcBiomes.STONE_CIRCLES, s -> LandSurfaces.stoneCircles(s.seed()), false, true)
    };

    private final SurfaceBuilder[] builders = new SurfaceBuilder[TfcBiomes.COUNT];
    private final boolean[] shore = new boolean[TfcBiomes.COUNT];
    private final boolean[] sandyRiverShores = new boolean[TfcBiomes.COUNT];

    /**
     * Builds every biome's builder from the shared seed stream, as {@code new SurfaceManager(seed)} does.
     *
     * @param stream the chunk generator's seed stream, where TFC's {@code SurfaceManager} takes it: after the region generator
     *               (with the chunk data generator), the biome layers and the noise sampler's seed. Takes four seeds per
     *               badlands, three per lowlands and grassy dunes, two per other biome.
     */
    public BiomeSurfaces(Seed stream) {
        for (Entry entry : REGISTRY) {
            shore[entry.biome()] = entry.shore();
            sandyRiverShores[entry.biome()] = entry.sandyRiverShores();
        }
        for (Entry entry : REGISTRY) {
            final SurfaceBuilder base = entry.factory() == RIVER
                ? LandSurfaces.river(id -> shore[id], id -> sandyRiverShores[id], id -> builders[id])
                : entry.factory().apply(stream);
            // BiomeBuilder.surface: Cinder(Tuya(TuffRings(Atoll(Stratovolcano(base))))). The atoll and the tuya wrappers each
            // take one seed for their height noise, after the builder they wrap
            stream.next(); // AtollSurfaceBuilder.heightNoise
            stream.next(); // TuyaSurfaceBuilder.heightNoise
            builders[entry.biome()] = LandSurfaces.cinderCone(base);
        }
    }

    /** @return The builder of a TFC biome id, wrapped as TFC wraps it. */
    public SurfaceBuilder builder(int biome) {
        return builders[biome];
    }

    /** @return Whether a biome is a shore biome ({@code BiomeExtension.isShore}). */
    public boolean isShore(int biome) {
        return shore[biome];
    }

    /** @return Whether a biome has sandy river shores ({@code BiomeExtension.hasSandyRiverShores}). */
    public boolean hasSandyRiverShores(int biome) {
        return sandyRiverShores[biome];
    }

    /** A {@code ShoreAndOceanSurfaceBuilder} factory, for the level seed. */
    public static SurfaceBuilder shoreAndOcean(ShoreKind kind, long seed) {
        return switch (kind) {
            case NORMAL -> new ShoreAndOceanSurface(seed, SHORE_SURFACE, SHORE_UNDERLAYER, 6, false, false, false, SurfaceBuilders.ROCKY);
            case SANDY -> new ShoreAndOceanSurface(seed, SHORE_SAND, SHORE_SANDSTONE, 6, false, false, false, SurfaceBuilders.ROCKY);
            case FORCE_RARE_SAND -> new ShoreAndOceanSurface(seed, RARE_SHORE_SAND, RARE_SHORE_SANDSTONE, 6, false, false, false, SurfaceBuilders.ROCKY);
            case GRAVELLY -> new ShoreAndOceanSurface(seed, GRAVEL, RAW, 6, false, false, false, SurfaceBuilders.ROCKY);
            case OCEAN -> new ShoreAndOceanSurface(seed, SHORE_SURFACE, SHORE_UNDERLAYER, 6, false, false, false, SurfaceBuilders.OCEAN_FLOOR);
            case SEA_CLIFFS -> new ShoreAndOceanSurface(seed, SHORE_SURFACE, SHORE_UNDERLAYER, 2, false, false, false, SurfaceBuilders.ROCKY);
            case OLD_SHIELD_VOLCANO -> new ShoreAndOceanSurface(seed, VOLCANIC_SHORE_SAND, VOLCANIC_SHORE_SANDSTONE, 6, true, false, false, ShieldVolcanoSurface.active(seed));
            case ACTIVE_SHIELD_VOLCANO -> new ShoreAndOceanSurface(seed, VOLCANIC_SHORE_SAND, VOLCANIC_SHORE_SANDSTONE, 2, false, true, false, ShieldVolcanoSurface.dormant());
            case MOUNTAINS -> new ShoreAndOceanSurface(seed, GRAVEL, RAW, 2, false, false, false, SurfaceBuilders.ROCKY);
            case VOLCANIC_MOUNTAINS -> new ShoreAndOceanSurface(seed, GRAVEL, RAW, 2, false, false, false, SurfaceBuilders.VOLCANIC_SOIL);
            case ROCKY_SHORE -> new ShoreAndOceanSurface(seed, RAW, RAW, 6, false, false, false, SurfaceBuilders.ROCKY_SHORE);
            case OCEAN_RIDGE -> new ShoreAndOceanSurface(seed, SHORE_SURFACE, SHORE_UNDERLAYER, 6, false, false, true, SurfaceBuilders.ROCKY_SHORE);
        };
    }
}
