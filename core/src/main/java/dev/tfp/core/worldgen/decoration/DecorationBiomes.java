/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/resources/data/tfc/tags/worldgen/placed_feature/in_biome/{large_features,surface_decoration}/*.json @ c3b4d9536
 */
package dev.tfp.core.worldgen.decoration;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PAR-WORLD-66: which TFC biomes list the decoration features of this package, from the biome tags at c3b4d9536. The land
 * biomes (99 of 125) list {@code #tfc:feature/land_plants} (with the forest floor patches) in their surface decoration and
 * the four bamboo features in their large features; the guano island lists {@code #tfc:feature/guano_islands}, the two atoll
 * biomes {@code tfc:atoll_guano} and the tidal flats {@code tfc:sea_stacks_patch} and {@code #tfc:feature/tide_pool_decorations}.
 */
public final class DecorationBiomes {
    /** The biomes that list neither the land plants nor the bamboo (the oceans, shores, flats, dunes and ice sheets). */
    static final Set<String> NOT_LAND = Set.of("coastal_dunes", "deep_ocean", "deep_ocean_atolls", "deep_ocean_trench",
        "dune_sea", "guano_island", "ice_sheet", "ice_sheet_mountains", "ice_sheet_oceanic", "ice_sheet_oceanic_mountains",
        "ice_sheet_shield_volcano", "ice_sheet_tuyas", "ice_sheet_volcanic_mountains", "ice_sheet_volcanic_oceanic_mountains",
        "mud_flats", "ocean", "ocean_atolls", "ocean_reef", "ocean_ridge", "old_shield_volcano_shore", "rocky_shores",
        "salt_flats", "shield_volcano_shore", "shore", "subglacial_lake", "tidal_flats");

    private DecorationBiomes() {
    }

    /** Whether a biome lists the forest floor (in {@code land_plants}) and the bamboo features. */
    public static boolean land(int biome) {
        return biome >= 0 && biome < TfcBiomes.COUNT && !NOT_LAND.contains(TfcBiomes.name(biome).toLowerCase(Locale.ROOT));
    }

    /** {@code #tfc:feature/guano_islands} ({@code tfc:extra_island_guano_patch}). */
    public static boolean guanoIsland(int biome) {
        return biome == TfcBiomes.GUANO_ISLAND;
    }

    /** {@code tfc:atoll_guano}, after the atoll corals. */
    public static boolean atoll(int biome) {
        return biome == TfcBiomes.OCEAN_ATOLLS || biome == TfcBiomes.DEEP_OCEAN_ATOLLS;
    }

    /** {@code tfc:sea_stacks_patch} and the tide pools. */
    public static boolean tidalFlats(int biome) {
        return biome == TfcBiomes.TIDAL_FLATS;
    }

    /**
     * The bamboo features in the large features' order ({@code rare_bamboo}, {@code bamboo}, {@code rare_bamboo_golden},
     * {@code bamboo_golden}), as indices into {@link Bamboo#FEATURES}.
     */
    public static final List<Integer> BAMBOO_ORDER = List.of(2, 0, 3, 1);
}
