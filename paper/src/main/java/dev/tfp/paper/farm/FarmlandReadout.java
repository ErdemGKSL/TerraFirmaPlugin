/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/HoeOverlays.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blockentities/FarmlandBlockEntity.java @ c3b4d9536 (addHoeOverlayInfo, addTooltipInfo)
 */
package dev.tfp.paper.farm;

import dev.tfp.core.climate.ChunkClimate;
import dev.tfp.core.climate.OverworldClimate;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.farm.FarmlandHydration;
import dev.tfp.core.farm.FarmlandState;
import dev.tfp.core.farm.Nutrients;

/**
 * The numbers and the text of the hoe overlay (PAR-FARM-01), pure given a snapshot of the water around the farmland, the clock
 * and the chunk climate. The text is TFC's {@code tfc.tooltip.farmland.hydration} and {@code .nutrients} lines.
 */
public final class FarmlandReadout {
    private FarmlandReadout() {
    }

    /** What the overlay shows. */
    public record Lines(FarmlandHydration hydration, int nitrogenPercent, int phosphorusPercent, int potassiumPercent) {
    }

    /** The hydration and the nutrient percentages ({@code (int) (value * 100)}, as TFC's tooltip). Any thread. */
    public static Lines compute(FarmlandState state, HydrationSnapshot water, ChunkClimate climate, ClockSnapshot clock, long worldSeed) {
        OverworldClimate model = OverworldClimate.forWorldSeed(worldSeed, OverworldClimate.DEFAULT_TEMPERATURE_SCALE);
        float rainfall = model.instantRainfall(climate, clock.calendarTicks(), clock.daysInMonth());
        FarmlandHydration hydration = FarmlandHydration.of(rainfall, climate.averageRainfall(), climate.rainfallVariance(),
            water.waterloggedAbove(), water.minCostWater(), water.soil());
        Nutrients n = state.nutrients();
        return new Lines(hydration, percent(n.nitrogen()), percent(n.phosphorus()), percent(n.potassium()));
    }

    private static int percent(float fraction) {
        return (int) (fraction * 100f);
    }
}
