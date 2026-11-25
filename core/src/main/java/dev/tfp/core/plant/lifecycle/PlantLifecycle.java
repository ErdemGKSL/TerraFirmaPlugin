/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/PlantBlockModel.java @ c3b4d9536 (getModelFromBlockState,
 *       getModelFromCalendar, getModelByDayTime),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/Plant.java @ c3b4d9536 (the bloom fields and their defaults),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (hash(long, int, int, int))
 */
package dev.tfp.core.plant.lifecycle;

import dev.tfp.core.worldgen.Mth;
import java.util.Random;

/**
 * TFC's seasonal plant stage ({@code tfc:plant} model loader). TFC picks it on the client per block, from the calendar's
 * fraction of the year, the block's climate (rainfall variance for wet-season bloomers, else average temperature), the
 * viewer's hemisphere and the time of day, with a per-column random offset. Pure and thread-safe.
 */
public final class PlantLifecycle {
    /** The six models of a {@code tfc:plant} model, in TFC's field order. */
    public enum Stage {
        DORMANT, SPROUTING, BUDDING, BLOOMING, SEEDING, DYING;

        /** The key of the stage in a {@code tfc:plant} model JSON. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** {@code Helpers.PRIME_X}, {@code Helpers.PRIME_Y}. */
    private static final int PRIME_X = 501125321;
    private static final int PRIME_Y = 1136930381;
    /** The salt of {@code getModelFromBlockState}. */
    private static final long SALT = 836494186029734123L;
    /** {@code ICalendar.CALENDAR_TICKS_IN_DAY}. */
    public static final long TICKS_IN_DAY = 24000;

    /**
     * The bloom fields of one {@code Plant} enum entry ({@code RegistryPlant}).
     *
     * @param startHour {@code startHour}/{@code endHour}: day-time window of the blooming look (0, 0 = always)
     */
    public record Params(boolean wetSeasonBlooming, float bloomOffset, float bloomingEnd, float seedingEnd, float dyingEnd,
                         float dormantEnd, float sproutingEnd, int startHour, int endHour) {
        /** The defaults of {@code Plant(BlockType, float, boolean)}. */
        public static Params defaults(boolean wetSeasonBlooming) {
            return new Params(wetSeasonBlooming, -0.124f, 0.25f, 0.42f, 0.5f, 0.75f, 0.88f, 0, 0);
        }
    }

    private PlantLifecycle() {
    }

    /** {@code Helpers.hash(long salt, int x, int y, int z)}. */
    static int hash(long salt, int x, int y, int z) {
        long hash = salt ^ ((long) x * PRIME_X) ^ ((long) y * PRIME_Y) ^ z;
        hash *= 0x27d4eb2d;
        return (int) hash;
    }

    /**
     * {@code getModelFromBlockState} for a plant block.
     *
     * @param x                  block x
     * @param z                  block z (TFC hashes the column at y 0)
     * @param rainfallVariance   {@code Climate.getRainfallVariance} at the block (wet-season bloomers)
     * @param averageTemperature {@code Climate.getAverageTemperature} at the block (the others)
     * @param northernHemisphere {@code ClientHelpers.inNorthernHemisphere}: the viewer's hemisphere
     * @param fractionOfYear     {@code getCalendarFractionOfYear}
     * @param dayTime            the level's day time ({@code ClientSolarCalculatorBridge.getDayTime}), used only by plants
     *                           with a blooming window
     */
    public static Stage stage(Params p, int x, int z, float rainfallVariance, float averageTemperature, boolean northernHemisphere,
                              float fractionOfYear, long dayTime) {
        float start = p.bloomOffset();
        final Random random = new Random();
        final float randomScale;
        random.setSeed(hash(SALT, x, 0, z));
        if (p.wetSeasonBlooming()) {
            randomScale = Mth.clampedMap(Math.abs(rainfallVariance), 0.0f, 0.3f, 0.5f, 0.03f);
            start = start + (rainfallVariance < 0f ? 1f : 1.5f);
        } else {
            randomScale = Mth.clampedMap(averageTemperature, 16f, 26f, 0.03f, 0.5f);
            start = start + (northernHemisphere ? 1.5f : 1f);
        }
        start = (start + random.nextFloat(-randomScale, randomScale)) % 1;
        return fromCalendar(start, start + p.bloomingEnd(), start + p.seedingEnd(), start + p.dyingEnd(), start + p.dormantEnd(),
            start + p.sproutingEnd(), p.startHour(), p.endHour(), randomScale > 0.25f, fractionOfYear, dayTime);
    }

    /** {@code getModelFromCalendar()}: the stage of a block that is not a plant block (no climate, no offset). */
    public static Stage stageWithoutPlant(float fractionOfYear) {
        return fromCalendar(0.4f, 0.6f, 0.75f, 0.9f, 1.1f, 1.25f, 0, 0, false, fractionOfYear, 0);
    }

    static Stage fromCalendar(float bloomingStart, float bloomingEnd, float seedingEnd, float dyingEnd, float dormantEnd,
                              float sproutingEnd, int startTime, int endTime, boolean nonDormant, float timeOfYear, long dayTime) {
        final float adjustedTimeOfYear = timeOfYear < bloomingStart ? timeOfYear + 1f : timeOfYear;
        if (adjustedTimeOfYear < bloomingEnd) {
            return startTime != endTime ? byDayTime(startTime, endTime, dayTime) : Stage.BLOOMING;
        } else if (adjustedTimeOfYear < seedingEnd) {
            return Stage.SEEDING;
        } else if (adjustedTimeOfYear < dyingEnd) {
            return nonDormant ? Stage.BUDDING : Stage.DYING;
        }
        if (adjustedTimeOfYear < dormantEnd) {
            if (nonDormant) {
                return startTime != endTime ? byDayTime(startTime, endTime, dayTime) : Stage.BLOOMING;
            }
            return Stage.DORMANT;
        } else if (adjustedTimeOfYear < sproutingEnd) {
            return nonDormant ? Stage.SEEDING : Stage.SPROUTING;
        }
        return Stage.BUDDING;
    }

    /** {@code getModelByDayTime}: outside the blooming window the plant shows its budding look. */
    static Stage byDayTime(int startTime, int endTime, long levelDayTime) {
        final long dayTime = levelDayTime % TICKS_IN_DAY;
        if ((endTime < dayTime && dayTime < startTime) || (startTime < endTime && (dayTime < startTime || endTime < dayTime))) {
            return Stage.BUDDING;
        }
        return Stage.BLOOMING;
    }
}
