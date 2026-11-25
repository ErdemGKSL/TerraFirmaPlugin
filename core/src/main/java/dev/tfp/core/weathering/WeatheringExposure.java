/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/IWeatheringBlock.java @ c3b4d9536 (onRandomTick: neighbour modifier,
 *       drip path, climate path, climateModifier)
 */
package dev.tfp.core.weathering;

import dev.tfp.core.weathering.WeatheringRules.Age;

/**
 * The environment terms of metal weathering ({@code IWeatheringBlock.onRandomTick}), pure and in TFC's {@code float}
 * arithmetic. The result of {@link #extraChance} is the {@code extraChance} that {@link WeatheringRules#catchUp} takes: the
 * larger of the drip chance and the climate chance (TFC tests both against the same draw).
 */
public final class WeatheringExposure {
    /** The neighbour modifier when more than {@link #CROWDED_FRACTION} of the weathering blocks nearby are further along. */
    public static final float CROWDED_MODIFIER = 0.00025f;
    /** TFC's {@code count / overall > 0.4} threshold. */
    public static final float CROWDED_FRACTION = 0.4f;
    /** The drip path needs at most this many weathering blocks among the four horizontal neighbours. */
    public static final int MAX_ADJACENT = 3;
    /** How many blocks above the drip and the sky checks read ({@code y = 1..4}). */
    public static final int ABOVE = 4;
    /** The drip chance each block above that is further along adds. */
    public static final float DRIP_STEP = 0.2f;

    private WeatheringExposure() {
    }

    /**
     * What a block's surroundings look like, read on its owning region.
     *
     * @param moreWeathered   weathering blocks within Manhattan distance 4 that are further along than this one
     * @param overall         all weathering blocks within Manhattan distance 4, this one included
     * @param adjacent        weathering blocks among the four horizontal neighbours
     * @param dripBlocks      consecutive blocks straight above (at most {@link #ABOVE}) that are weathering and further along
     * @param topExposed      the {@link #ABOVE} blocks above all have an empty collision shape
     */
    public record Site(int moreWeathered, int overall, int adjacent, int dripBlocks, boolean topExposed) {
        public Site {
            if (overall < 1 || moreWeathered < 0 || moreWeathered > overall || adjacent < 0 || adjacent > 4
                    || dripBlocks < 0 || dripBlocks > ABOVE) {
                throw new IllegalArgumentException("bad site: " + moreWeathered + "/" + overall + " adjacent " + adjacent
                        + " drip " + dripBlocks);
            }
        }
    }

    /** TFC's neighbour modifier: {@link #CROWDED_MODIFIER} when {@code count / overall > 0.4}, else 1. */
    public static float neighborModifier(Site site) {
        return (float) site.moreWeathered() / site.overall() > CROWDED_FRACTION ? CROWDED_MODIFIER : 1f;
    }

    /** The drip path's chance: 0 with more than three adjacent weathering blocks. */
    public static float dripChance(Age age, Site site) {
        if (site.adjacent() > MAX_ADJACENT) {
            return 0f;
        }
        float chance = (float) WeatheringRules.ageModifier(age) * (DRIP_STEP * site.dripBlocks());
        return age != Age.NONE ? chance * neighborModifier(site) : chance;
    }

    /** The climate path's chance: 0 unless the four blocks above are open. */
    public static float climatePathChance(Age age, Site site, float climateModifier) {
        if (!site.topExposed()) {
            return 0f;
        }
        float chance = (float) WeatheringRules.ageModifier(age) * climateModifier;
        return age != Age.NONE ? chance * neighborModifier(site) : chance;
    }

    /**
     * TFC {@code climateModifier}.
     *
     * @param instantTemperature the instant temperature in degrees
     * @param averageRainfall    the average rainfall in mm
     * @param windLength         the wind vector's length
     * @param raining            whether it rains at the block
     */
    public static float climateModifier(float instantTemperature, float averageRainfall, float windLength, boolean raining) {
        float temp = Math.abs(-0.5f + (clamp(instantTemperature, -40f, 40f) + 40f) / 80f) * 2f;
        float rainfall = clamp(averageRainfall, 0f, 500f) / 500f;
        float wind = clamp(windLength, 0f, 1f);
        float rain = raining ? 1f : 0.5f;
        float climate = temp * rainfall;
        float weather = rain * wind;
        return (clamp(climate, 0f, 1f) + 0.2f) * 0.6f + (clamp(weather, 0f, 1f) + 0.2f) * 0.4f;
    }

    /**
     * The {@code extraChance} of {@link WeatheringRules#catchUp}: the larger of the drip and the climate chance.
     *
     * @param climateModifier see {@link #climateModifier}
     */
    public static double extraChance(Age age, Site site, float climateModifier) {
        return Math.max(dripChance(age, site), climatePathChance(age, site, climateModifier));
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }
}
