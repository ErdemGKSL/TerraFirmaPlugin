/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (StationaryBush lifecycle tables),
 *       src/main/java/net/dries007/tfc/util/climate/ClimateRanges.java @ c3b4d9536 (STATIONARY_BUSHES),
 *       src/generated/resources/data/tfc/tfc/climate_range/plant/{snowberry,bunchberry,gooseberry,cloudberry,strawberry,wintergreen_berry}_bush.json @ c3b4d9536
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;
import static dev.tfp.core.berry.Lifecycle.FLOWERING;
import static dev.tfp.core.berry.Lifecycle.FRUITING;
import static dev.tfp.core.berry.Lifecycle.HEALTHY;

import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateRange;

/**
 * The stationary berry bushes in scope (TFC {@code FruitBlocks.StationaryBush}): the lifecycle of each calendar month in the northern
 * hemisphere, and the climate range the bush survives in. The cranberry is a stationary bush too (TFC's {@code WaterloggedBerryBushBlock}
 * extends {@code StationaryBerryBushBlock}); its waterlogged rule is in {@link CranberryRules}. The spreading bushes and fruit trees are
 * in {@link SpreadingSpecies} and {@link FruitTree}.
 *
 * <p>The table is indexed by the hemispheric month (see {@link BerryRules#hemisphericMonth}), as in TFC's
 * {@code getLifecycleForMonth}.
 */
public enum BushSpecies {
    CRANBERRY(new ClimateRange(25, 100, 0, -15.4f, 9.8f, 0f),
        DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    SNOWBERRY(new ClimateRange(17, 100, 0, -11.8f, 6.2f, 0f),
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT),
    BUNCHBERRY(new ClimateRange(25, 100, 0, -15.4f, 2.6f, 0f),
        DORMANT, DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    GOOSEBERRY(new ClimateRange(17, 100, 0, -8.2f, 13.4f, 0f),
        DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    CLOUDBERRY(new ClimateRange(5, 80, 0, -15.4f, 8.0f, 0f),
        DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT),
    STRAWBERRY(new ClimateRange(11, 90, 0, -2.8f, 18.8f, 0f),
        FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY),
    WINTERGREEN_BERRY(new ClimateRange(7, 85, 0, -10.0f, 8.0f, 0f),
        DORMANT, DORMANT, DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING);

    private final ClimateRange climate;
    private final Lifecycle[] lifecycle;

    BushSpecies(ClimateRange climate, Lifecycle... lifecycle) {
        if (lifecycle.length != 12) {
            throw new IllegalArgumentException("a lifecycle table has 12 months: " + name());
        }
        this.climate = climate;
        this.lifecycle = lifecycle;
    }

    /** The climate range (TFC {@code ClimateRanges.STATIONARY_BUSHES}), with no wiggle room. */
    public ClimateRange climate() {
        return climate;
    }

    /** The lifecycle of the hemispheric month (TFC {@code getLifecycleForMonth}). */
    public Lifecycle lifecycleFor(Month hemisphericMonth) {
        return lifecycle[hemisphericMonth.ordinal()];
    }
}
