/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PlantableInfo.java @ c3b4d9536 (the four info getters and PlantNutrients)
 */
package dev.tfp.core.plantable;

import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.climate.ClimateRange;
import java.util.List;
import java.util.Optional;

/**
 * What TFC's {@code PlantableInfo} reports for one item: the climate range, the nutrients the crop takes (negative: gives back), the
 * lifecycle of the twelve months and the growth time in ticks ({@code -1}: none). Immutable.
 */
public record PlantInfo(Optional<ClimateRange> climate, Optional<Nutrients> nutrients, Optional<List<Lifecycle>> lifecycle,
                        int growthTicks) {
    /** {@code PlantableInfo.PlantNutrients}. */
    public record Nutrients(float nitrogen, float phosphorus, float potassium) {}

    public PlantInfo {
        lifecycle = lifecycle.map(l -> {
            if (l.size() != 12) {
                throw new IllegalArgumentException("a lifecycle has 12 months: " + l.size());
            }
            return List.copyOf(l);
        });
    }
}
