/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.spawn;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ChunkClimate;

/**
 * The inputs a spawn attempt reads at one position, all values the caller has already read from the world or the chunk
 * data. Pure: nothing here touches a level.
 *
 * @param y               the block height of the position
 * @param seaLevel        the generator's sea level, for the depth rule
 * @param chunk           the chunk's climate inputs (sea-level temperature, rainfall, rain variance, base groundwater)
 * @param forestDensity   the forest density of the position's forest type, 0 to 4
 * @param northernHemisphere whether the position is in the northern hemisphere, which flips the rain variance sign
 * @param rawBrightness   the raw light level at the position
 * @param month           the hemispheral calendar month of the position
 */
public record SpawnSite(int y, int seaLevel, ChunkClimate chunk, int forestDensity, boolean northernHemisphere,
        int rawBrightness, Month month) {
}
