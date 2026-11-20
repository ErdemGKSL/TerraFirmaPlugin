/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (BlockType SHORT_GRASS, BEACH_GRASS, TALL_GRASS)
 */
package dev.tfp.core.regrowth;

import java.util.Optional;
import java.util.Set;

/** Which plants spread by {@link SpreadKind}, by TFC plant name (the golden file lists them from {@code Plant.java}). */
public final class PlantKinds {
    static final Set<String> SHORT = Set.of("beachgrass", "bluegrass", "bromegrass", "fountain_grass", "orchard_grass", "ryegrass",
        "scutch_grass", "timothy_grass", "raddia_grass", "red_oat_grass");
    static final Set<String> TALL = Set.of("pampas_grass", "rose", "bear_grass", "fan_palm", "foxglove", "king_fern", "lilac",
        "ostrich_fern", "red_sealing_wax_palm", "sapphire_tower", "sunflower", "switchgrass", "tall_fescue_grass", "toquilla_palm");

    private PlantKinds() {
    }

    /** The spread rule of the plant {@code name} ({@code ryegrass}), empty when it does not spread by the self-spread rule. */
    public static Optional<SpreadKind> of(String name) {
        if (SHORT.contains(name)) {
            return Optional.of(SpreadKind.SHORT);
        }
        return TALL.contains(name) ? Optional.of(SpreadKind.TALL) : Optional.empty();
    }
}
