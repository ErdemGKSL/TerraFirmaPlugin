/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (SpreadingBush: stages and maxHeight),
 *       src/main/java/net/dries007/tfc/util/climate/ClimateRanges.java @ c3b4d9536 (SPREADING_BUSHES),
 *       src/generated/resources/data/tfc/tfc/climate_range/plant/{blackberry,raspberry,blueberry,elderberry}_bush.json @ c3b4d9536
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;
import static dev.tfp.core.berry.Lifecycle.FLOWERING;
import static dev.tfp.core.berry.Lifecycle.FRUITING;
import static dev.tfp.core.berry.Lifecycle.HEALTHY;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateRange;

/**
 * The spreading bushes (TFC {@code FruitBlocks.SpreadingBush}): a bush block that grows up to {@link #maxHeight} blocks tall and sends
 * canes sideways, which turn into bush blocks of their own. The lifecycle of each hemispheric month and the climate range match
 * {@link BushSpecies}. See {@link SpreadRules} for the growth and the spread.
 *
 * <p>There is no catch-up for these bushes yet: a spreading bush's spread is a random draw each cycle, and a lazy catch-up would have to
 * replay those draws. The berry catch-up ({@link BerryRules#catchUp}) is for the stationary bushes only.
 */
public enum SpreadingSpecies {
    BLACKBERRY(new ClimateRange(17, 100, 0, -6.4f, 20.6f, 0f), 4,
        DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT),
    RASPBERRY(new ClimateRange(15, 95, 0, -11.8f, 15.2f, 0f), 3,
        DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    BLUEBERRY(new ClimateRange(12, 90, 0, -10.0f, 9.8f, 0f), 2,
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT),
    ELDERBERRY(new ClimateRange(9, 85, 0, -6.4f, 17.0f, 0f), 3,
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT);

    private final ClimateRange climate;
    private final int maxHeight;
    private final Lifecycle[] lifecycle;

    SpreadingSpecies(ClimateRange climate, int maxHeight, Lifecycle... lifecycle) {
        if (lifecycle.length != 12) {
            throw new IllegalArgumentException("a lifecycle table has 12 months: " + name());
        }
        this.climate = climate;
        this.maxHeight = maxHeight;
        this.lifecycle = lifecycle;
    }

    /** The climate range (TFC {@code ClimateRanges.SPREADING_BUSHES}), with no wiggle room. */
    public ClimateRange climate() {
        return climate;
    }

    /** The tallest the bush block grows (TFC {@code SpreadingBushBlock.maxHeight}). */
    public int maxHeight() {
        return maxHeight;
    }

    /** The lifecycle of the hemispheric month (TFC {@code getLifecycleForMonth}). */
    public Lifecycle lifecycleFor(Month hemisphericMonth) {
        return lifecycle[hemisphericMonth.ordinal()];
    }
}
