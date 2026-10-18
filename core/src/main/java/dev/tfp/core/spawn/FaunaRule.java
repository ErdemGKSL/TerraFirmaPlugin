/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Fauna.java and src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java
 * and src/generated/resources/data/tfc/tfc/fauna/*.json @ c3b4d9536
 */
package dev.tfp.core.spawn;

import java.util.List;

import dev.tfp.core.calendar.Month;

/**
 * The spawn gate of one TFC species, from its {@code tfc:fauna} JSON: the chance, the depth below sea level, the climate
 * window (temperature, groundwater, forest density), fuzzy placement, the brightness cap and the months.
 *
 * <p>Defaults are TFC's: chance 1, no depth limit, unbounded temperature and groundwater, forest 0 to 4, not fuzzy, rivers
 * counted, no brightness cap, every month. Elevation, rain variance, forest types and solid ground are not in TFC's
 * fauna data, so they are not here.
 *
 * @param chance                  the spawn chance: the attempt passes with probability 1 / chance, at least 1
 * @param distanceBelowSeaLevel   the spawn must be at most this far below sea level, or -1 for no limit
 * @param minTemperature          the lowest temperature at the position, degrees Celsius
 * @param maxTemperature          the highest temperature at the position, degrees Celsius
 * @param minGroundwater          the lowest groundwater, mm
 * @param maxGroundwater          the highest groundwater, mm
 * @param minForest               the lowest forest density, 0 to 4
 * @param maxForest               the highest forest density, 0 to 4
 * @param fuzzy                   whether the climate match is probabilistic, nearer the middle of the window is likelier
 * @param ignoreRivers            whether groundwater is just the average rainfall, without the river term
 * @param maxBrightness           the highest raw light level, or -1 for no cap
 * @param months                  the hemispheral months it may spawn in, empty for all
 */
public record FaunaRule(
        int chance,
        int distanceBelowSeaLevel,
        float minTemperature,
        float maxTemperature,
        float minGroundwater,
        float maxGroundwater,
        int minForest,
        int maxForest,
        boolean fuzzy,
        boolean ignoreRivers,
        int maxBrightness,
        List<Month> months) {

    public FaunaRule {
        if (chance < 1) {
            throw new IllegalArgumentException("chance must be at least 1, was " + chance);
        }
        months = List.copyOf(months);
    }
}
