/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/feature/IfThenFeature.java @ c3b4d9536 (the disc of the clay feature), with the
 * data of src/main/resources/data/tfc/worldgen/configured_feature/ and placed_feature/ for
 * {native_copper,native_gold,native_silver,cassiterite}_{deposit,deep_deposit}, clay_disc, clay_disc_with_indicator and
 * peat_disc, the tags data/tfc/tags/worldgen/placed_feature/feature/{ore_deposits,soil_discs}.json and in_biome/soil_discs/*.json
 * (order and biomes), and the vanilla 1.21 steps they name (rarity_filter, in_square, heightmap, height_range uniform).
 */
package dev.tfp.core.worldgen.deposit;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import dev.tfp.core.worldgen.groundcover.ClimateFilter;
import dev.tfp.core.worldgen.groundcover.FeatureRandom;
import dev.tfp.core.worldgen.groundcover.GroundView;
import dev.tfp.core.worldgen.random.XoroshiroRandomSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * TFC's deposits: the ore deposits of rivers and lakes (gravel with native copper, gold, silver or cassiterite, in the floor
 * gravel or, deeper, in the raw rock) and the soil discs of the land (clay and peat). Each is a {@code tfc:soil_disc} behind a
 * rarity filter, an {@code in_square} origin, a height (a heightmap or a uniform range) and, for the soil discs, a climate
 * filter. Pure and thread-safe; {@link #chunk} decides the replaced blocks of one chunk.
 *
 * <p>Not ported (ledger): the water clay disc (it needs {@code tfc:near_fluid}), the rooted dirt and mollisol discs (they need
 * the forest), powder snow, and the clay indicator plants of the clay disc (M2-GEN-13).
 */
public final class Deposits {
    /** How a feature's origin gets its y. */
    public enum Anchor {
        /** {@code heightmap OCEAN_FLOOR_WG}. */
        OCEAN_FLOOR,
        /** {@code heightmap WORLD_SURFACE_WG}. */
        WORLD_SURFACE,
        /** {@code height_range uniform [minY, maxY]}. */
        UNIFORM
    }

    /**
     * One placed disc feature.
     *
     * @param feature the placed feature id
     * @param disc    the disc
     * @param rarity  {@code rarity_filter} chance
     * @param anchor  how the origin's y is chosen
     * @param minY    the uniform range's lowest y ({@link Anchor#UNIFORM})
     * @param maxY    the uniform range's highest y, inclusive
     * @param climate the climate filter after the height, or null
     * @param biomes  the biome names whose tag lists the feature
     */
    public record Feature(String feature, SoilDisc disc, int rarity, Anchor anchor, int minY, int maxY, ClimateFilter climate,
                          Set<String> biomes) {}

    /** The biomes of {@code #tfc:feature/ore_deposits}: the river and lake biomes. */
    static final Set<String> ORE_DEPOSIT_BIOMES = Set.of("lake", "meltwater_lake", "oceanic_mountain_lake", "rift_lake", "river",
        "tower_karst_lake", "volcanic_oceanic_mountain_lake");

    /** The biomes whose soil disc tag is empty; every other biome lists {@code #tfc:feature/soil_discs}. */
    static final Set<String> NO_SOIL_DISC_BIOMES = Set.of("coastal_dunes", "deep_ocean", "deep_ocean_atolls", "deep_ocean_trench",
        "dune_sea", "guano_island", "ice_sheet", "ice_sheet_mountains", "ice_sheet_oceanic", "ice_sheet_oceanic_mountains",
        "ice_sheet_shield_volcano", "ice_sheet_tuyas", "ice_sheet_volcanic_mountains", "ice_sheet_volcanic_oceanic_mountains",
        "mud_flats", "ocean", "ocean_atolls", "ocean_reef", "ocean_ridge", "old_shield_volcano_shore", "rocky_shores", "salt_flats",
        "shield_volcano_shore", "shore", "subglacial_lake", "tidal_flats");

    private static final Set<String> SOIL_DISC_BIOMES = soilDiscBiomes();

    /** The features, in TFC's order: the ore deposits tag, then the soil discs tag (the order of the river tag). */
    public static final List<Feature> FEATURES = features();

    /** The material at a position of the generating chunk, as the terrain wrote it, or null for anything else. */
    @FunctionalInterface
    public interface MaterialAt {
        DepositMaterial material(int x, int y, int z);
    }

    private final FeatureRandom[] randoms;

    public Deposits(long levelSeed) {
        this.randoms = new FeatureRandom[FEATURES.size()];
        for (int i = 0; i < randoms.length; i++) {
            randoms[i] = new FeatureRandom(levelSeed, FEATURES.get(i).feature());
        }
    }

    /**
     * The blocks the discs replace inside chunk {@code (chunkX, chunkZ)}: the discs of the 3 x 3 chunks around it (the largest
     * radius is 10), in feature order then chunk order (x outer). A chunk places a feature when the biome at its centre lists
     * it (ledger). A position replaced by an earlier disc is no longer replaceable.
     *
     * @param materials the terrain's material at a position of the chunk; asked only inside it
     */
    public List<DepositBlock> chunk(int chunkX, int chunkZ, GroundView view, MaterialAt materials) {
        final List<DepositBlock> out = new ArrayList<>();
        final Set<Long> replaced = new HashSet<>();
        for (int f = 0; f < FEATURES.size(); f++) {
            final Feature feature = FEATURES.get(f);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final int cx = chunkX + dx;
                    final int cz = chunkZ + dz;
                    if (!placesIn(feature, view.biome((cx << 4) + 8, (cz << 4) + 8))) {
                        continue;
                    }
                    place(randoms[f].chunk(cx, cz), feature, cx << 4, cz << 4, view, (x, y, z) -> {
                        if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                            return;
                        }
                        final long key = ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
                        if (replaced.contains(key)) {
                            return;
                        }
                        final DepositMaterial material = materials.material(x, y, z);
                        final DepositKind kind = feature.disc().replacement(material);
                        if (kind != null) {
                            replaced.add(key);
                            out.add(new DepositBlock(x, y, z, kind, material, feature.disc().mineral(), feature.feature()));
                        }
                    });
                }
            }
        }
        return out;
    }

    /** Whether a feature is in the feature list of a biome. */
    public static boolean placesIn(Feature feature, int biome) {
        return biome >= 0 && biome < TfcBiomes.COUNT && feature.biomes().contains(TfcBiomes.name(biome).toLowerCase(Locale.ROOT));
    }

    /**
     * One chunk's feature with TFC's draws in TFC's order: the rarity float, {@code in_square} (x, then z), the height (no draw
     * for a heightmap, one {@code nextInt} for a uniform range), the climate filter at the origin, then the disc.
     *
     * @param visitor gets every position of the disc whose integrity float passed, inside the chunk or not
     * @return whether the placement passed and the disc was drawn
     */
    public static boolean place(XoroshiroRandomSource random, Feature feature, int blockX, int blockZ, GroundView view,
                                SoilDisc.Visitor visitor) {
        if (!(random.nextFloat() < 1f / feature.rarity())) {
            return false;
        }
        final int x = blockX + random.nextInt(16);
        final int z = blockZ + random.nextInt(16);
        final int y;
        switch (feature.anchor()) {
            case OCEAN_FLOOR -> y = view.oceanFloor(x, z);
            case WORLD_SURFACE -> y = view.worldSurface(x, z);
            default -> y = random.nextInt(feature.maxY() - feature.minY() + 1) + feature.minY();
        }
        if (feature.anchor() != Anchor.UNIFORM && y <= view.minY()) {
            return false;
        }
        if (feature.climate() != null && !feature.climate().isValid(view, x, y, z, random)) {
            return false;
        }
        feature.disc().shape(random, x, y, z, visitor);
        return true;
    }

    private static List<Feature> features() {
        final List<Feature> out = new ArrayList<>();
        // The ore_deposits tag: <mineral>_deposit, then <mineral>_deep_deposit, for copper, cassiterite, silver, gold
        for (String mineral : List.of("native_copper", "cassiterite", "native_silver", "native_gold")) {
            out.add(new Feature("tfc:" + mineral + "_deposit",
                new SoilDisc(1, 3, 2, 0.9f, Map.of(DepositMaterial.GRAVEL, DepositKind.ORE_DEPOSIT), mineral),
                12, Anchor.OCEAN_FLOOR, 0, 0, null, ORE_DEPOSIT_BIOMES));
            out.add(new Feature("tfc:" + mineral + "_deep_deposit",
                new SoilDisc(3, 10, 3, 0.9f, Map.of(DepositMaterial.RAW_ROCK, DepositKind.ORE_DEPOSIT), mineral),
                24, Anchor.UNIFORM, 40, 63, null, ORE_DEPOSIT_BIOMES));
        }
        out.add(new Feature("tfc:clay_disc_with_indicator",
            new SoilDisc(3, 5, 3, 1f, Map.of(DepositMaterial.DIRT, DepositKind.CLAY, DepositMaterial.GRASS, DepositKind.CLAY_GRASS), null),
            20, Anchor.WORLD_SURFACE, 0, 0,
            new ClimateFilter(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, 175f, Float.POSITIVE_INFINITY, -1f, 1f, false, -64, 320, false, false),
            SOIL_DISC_BIOMES));
        out.add(new Feature("tfc:peat_disc",
            new SoilDisc(5, 9, 7, 1f, Map.of(DepositMaterial.DIRT, DepositKind.PEAT, DepositMaterial.GRASS, DepositKind.PEAT_GRASS,
                DepositMaterial.MUD, DepositKind.PEAT), null),
            40, Anchor.WORLD_SURFACE, 0, 0,
            new ClimateFilter(12f, Float.POSITIVE_INFINITY, 350f, Float.POSITIVE_INFINITY, -1f, 1f, false, -64, 320, false, false),
            SOIL_DISC_BIOMES));
        return List.copyOf(out);
    }

    private static Set<String> soilDiscBiomes() {
        final Set<String> out = new HashSet<>();
        for (int id = 0; id < TfcBiomes.COUNT; id++) {
            final String name = TfcBiomes.name(id).toLowerCase(Locale.ROOT);
            if (!NO_SOIL_DISC_BIOMES.contains(name)) {
                out.add(name);
            }
        }
        return Set.copyOf(out);
    }
}
