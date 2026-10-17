/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/biome/TFCBiomes.java @ c3b4d9536 (the biomes registered with salty()).
 */
package dev.tfp.core.worldgen.groundcover;

import dev.tfp.core.worldgen.biome.TfcBiomes;
import java.util.Locale;
import java.util.Set;

/** Which biomes hold salt water (TFC's {@code BiomeExtension.salty}): the oceans, the shores and the oceanic mountains. */
public final class SaltWater {
    static final Set<String> SALTY = Set.of("ocean", "ocean_reef", "deep_ocean", "deep_ocean_trench", "oceanic_volcanic_arc",
        "ocean_atolls", "deep_ocean_atolls", "ocean_ridge", "salt_marsh", "oceanic_mountains", "volcanic_oceanic_mountains",
        "volcanic_mountain_islands", "guano_island", "volcanic_island", "shore", "tidal_flats", "sea_stacks", "terrace_upper",
        "terrace_lower", "setback_cliffs", "coastal_dunes", "rocky_shores", "embayments", "oceanic_mountain_lake",
        "volcanic_oceanic_mountain_lake", "salt_flats", "tower_karst_bay", "sunken_shield_volcano", "shield_volcano_shore",
        "old_shield_volcano_shore", "ice_sheet_oceanic_mountains_edge", "ice_sheet_oceanic", "ice_sheet_shore",
        "glaciated_oceanic_mountains", "glaciated_volcanic_oceanic_mountains", "glacially_carved_oceanic_mountains",
        "glacially_carved_volcanic_oceanic_mountains");

    private static final boolean[] BY_ID = byId();

    private SaltWater() {}

    /** Whether the water of a biome is salt water. */
    public static boolean isSalty(int biome) {
        return biome >= 0 && biome < BY_ID.length && BY_ID[biome];
    }

    private static boolean[] byId() {
        final boolean[] out = new boolean[TfcBiomes.COUNT];
        for (int id = 0; id < out.length; id++) {
            out[id] = SALTY.contains(TfcBiomes.name(id).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
