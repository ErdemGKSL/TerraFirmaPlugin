/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCSaplingBlock.java @ c3b4d9536 (randomTick, getTicksToGrow),
 *       src/main/java/net/dries007/tfc/common/blocks/wood/Wood.java @ c3b4d9536 (daysToGrow, defaultTicksToGrow),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (getTicksSinceUpdate)
 */
package dev.tfp.core.sapling;

import dev.tfp.core.worldgen.tree.Wood;
import java.util.EnumMap;
import java.util.Map;

/**
 * When a wood sapling may grow into its tree: TFC's per-wood days to grow, the sapling growth modifier of the server config, the
 * calendar ticks since it was planted, and the light it needs. Pure.
 */
public final class SaplingRules {
    /** {@code ICalendar.CALENDAR_TICKS_IN_DAY}: the sapling counter counts calendar ticks, whatever the configured day length. */
    public static final long CALENDAR_TICKS_IN_DAY = 24_000L;
    /** {@code level.getMaxLocalRawBrightness(pos.above()) >= 9}. */
    public static final int MIN_LIGHT = 9;
    /** {@code saplingGrowthModifier}'s default. */
    public static final double DEFAULT_MODIFIER = 1.0;

    /** {@code Wood.daysToGrow}, the fifth constructor argument of each TFC wood. */
    private static final Map<Wood, Integer> DAYS = new EnumMap<>(Wood.class);

    static {
        DAYS.put(Wood.ACACIA, 11);
        DAYS.put(Wood.ASH, 7);
        DAYS.put(Wood.ASPEN, 8);
        DAYS.put(Wood.BIRCH, 7);
        DAYS.put(Wood.BLACKWOOD, 8);
        DAYS.put(Wood.CHESTNUT, 7);
        DAYS.put(Wood.DOUGLAS_FIR, 7);
        DAYS.put(Wood.HICKORY, 10);
        DAYS.put(Wood.KAPOK, 7);
        DAYS.put(Wood.MANGROVE, 8);
        DAYS.put(Wood.MAPLE, 7);
        DAYS.put(Wood.OAK, 10);
        DAYS.put(Wood.PALM, 7);
        DAYS.put(Wood.PINE, 7);
        DAYS.put(Wood.ROSEWOOD, 8);
        DAYS.put(Wood.SEQUOIA, 18);
        DAYS.put(Wood.SPRUCE, 7);
        DAYS.put(Wood.SYCAMORE, 8);
        DAYS.put(Wood.WHITE_CEDAR, 7);
        DAYS.put(Wood.WILLOW, 11);
    }

    private SaplingRules() {}

    /** TFC's days to grow for a wood. */
    public static int daysToGrow(Wood wood) {
        return DAYS.get(wood);
    }

    /** {@code Wood.defaultTicksToGrow}: the days in calendar ticks. */
    public static int defaultTicksToGrow(Wood wood) {
        return (int) (daysToGrow(wood) * CALENDAR_TICKS_IN_DAY);
    }

    /** {@code TFCSaplingBlock.getTicksToGrow}: {@code (long) (ticks * saplingGrowthModifier)}. */
    public static long ticksToGrow(Wood wood, double modifier) {
        return (long) (defaultTicksToGrow(wood) * modifier);
    }

    /** {@code counter.getTicksSinceUpdate() > getTicksToGrow()}: strictly more than the required ticks. */
    public static boolean elapsed(long ticksSincePlanted, long ticksToGrow) {
        return ticksSincePlanted > ticksToGrow;
    }

    /** The light test of {@code randomTick}. */
    public static boolean lightOk(int light) {
        return light >= MIN_LIGHT;
    }

    /** The wood of a sapling block id ({@code tfp:wood/sapling/oak}), or null for another id. */
    public static Wood woodOf(String blockId) {
        final String prefix = "tfp:wood/sapling/";
        if (!blockId.startsWith(prefix)) {
            return null;
        }
        try {
            return Wood.byName(blockId.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The block id of a wood's sapling. */
    public static String blockId(Wood wood) {
        return "tfp:wood/sapling/" + wood.serializedName();
    }
}
