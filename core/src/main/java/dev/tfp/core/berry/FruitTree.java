/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (Tree: daysToGrow, stages),
 *       src/main/java/net/dries007/tfc/util/climate/ClimateRanges.java @ c3b4d9536 (FRUIT_TREES),
 *       src/generated/resources/data/tfc/tfc/climate_range/plant/{cherry,green_apple,lemon,olive,orange,peach,plum,red_apple}_tree.json
 *       @ c3b4d9536
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;
import static dev.tfp.core.berry.Lifecycle.FLOWERING;
import static dev.tfp.core.berry.Lifecycle.FRUITING;
import static dev.tfp.core.berry.Lifecycle.HEALTHY;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateRange;

/**
 * The fruit trees (TFC {@code FruitBlocks.Tree}): the days a sapling needs to grow into a tree, the lifecycle of each hemispheric month
 * (shared by the leaves) and the climate range the tree survives in. See {@link FruitTreeRules}.
 */
public enum FruitTree {
    CHERRY(8, new ClimateRange(7, 80, 0, -6.4f, 15.2f, 0f),
        HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT, DORMANT, HEALTHY),
    GREEN_APPLE(10, new ClimateRange(10, 75, 0, -11.8f, 11.6f, 0f),
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    LEMON(8, new ClimateRange(19, 95, 0, 6.2f, 26.0f, 0f),
        DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT),
    OLIVE(12, new ClimateRange(22, 95, 0, 0.8f, 24.2f, 0f),
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT),
    ORANGE(7, new ClimateRange(27, 100, 0, 8.0f, 41.2f, 0f),
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT),
    PEACH(11, new ClimateRange(15, 95, 0, -4.6f, 17.0f, 0f),
        HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT, DORMANT, HEALTHY),
    PLUM(8, new ClimateRange(9, 75, 0, -8.2f, 13.4f, 0f),
        HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT, DORMANT, DORMANT, DORMANT),
    RED_APPLE(10, new ClimateRange(16, 75, 0, -11.8f, 11.6f, 0f),
        DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING, DORMANT, DORMANT);

    private final int daysToGrow;
    private final ClimateRange climate;
    private final Lifecycle[] lifecycle;

    FruitTree(int daysToGrow, ClimateRange climate, Lifecycle... lifecycle) {
        if (lifecycle.length != 12) {
            throw new IllegalArgumentException("a lifecycle table has 12 months: " + name());
        }
        this.daysToGrow = daysToGrow;
        this.climate = climate;
        this.lifecycle = lifecycle;
    }

    /** The climate range (TFC {@code ClimateRanges.FRUIT_TREES}), with no wiggle room. */
    public ClimateRange climate() {
        return climate;
    }

    /** TFC {@code Tree.defaultTicksToGrow}: the days to grow, in calendar ticks (the unit of the sapling's counter in TFC). */
    public int defaultTicksToGrow() {
        return daysToGrow * CalendarTicks.CALENDAR_TICKS_IN_DAY;
    }

    /** The lifecycle of the hemispheric month (TFC {@code getLifecycleForMonth}). */
    public Lifecycle lifecycleFor(Month hemisphericMonth) {
        return lifecycle[hemisphericMonth.ordinal()];
    }
}
