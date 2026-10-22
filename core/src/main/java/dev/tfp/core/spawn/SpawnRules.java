/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/Faunas.java @ c3b4d9536 (the spawn placement predicate),
 * src/main/java/net/dries007/tfc/world/placement/ClimatePlacement.java (isValid),
 * src/main/java/net/dries007/tfc/util/EnvironmentHelpers.java (adjustAvgTempForElev).
 * The weighted species pick and the group size are Minecraft's spawner rules, which TFC's biome JSON feeds; they are
 * mirrored here from Minecraft's WeightedRandom and NaturalSpawner, not from the TFC source.
 */
package dev.tfp.core.spawn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.tfp.core.worldgen.chunk.ChunkData;

/**
 * The pure spawn rules: which species a biome attempt picks, and whether a TFC species' climate gate lets it spawn at a
 * position. Decided on any thread; the caller applies the result on the owning region.
 *
 * <p>An attempt is: pick an entry by weight from one category list, draw its group size, and for a TFC species run the
 * gate. Draws happen in TFC's order, so the same {@link SpawnRandom} sequence gives the same species.
 */
public final class SpawnRules {
    /** TFC {@code EnvironmentHelpers.SEA_LEVEL} / {@code TFCChunkGenerator.SEA_LEVEL_Y}. */
    static final float TEMPERATURE_SEA_LEVEL = 63f;
    static final int MIN_ELEVATION = -64;
    static final int MAX_ELEVATION = 320;
    static final float MIN_RAIN_VARIANCE = -1f;
    static final float MAX_RAIN_VARIANCE = 1f;

    private SpawnRules() {
    }

    /**
     * The weighted pick from a category list, as Minecraft's {@code WeightedRandom}: one draw in [0, total weight), then the
     * first entry whose weight covers it. Empty if the list has no weight.
     */
    public static Optional<SpawnEntry> pick(List<SpawnEntry> candidates, SpawnRandom rand) {
        int total = 0;
        for (SpawnEntry entry : candidates) {
            total += entry.weight();
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int i = rand.nextInt(total);
        for (SpawnEntry entry : candidates) {
            i -= entry.weight();
            if (i < 0) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * One spawn attempt at {@code site}: the weighted pick, the group size, then the TFC gate for a TFC species.
     *
     * @param candidates the category list the attempt draws from, in the order of the biome JSON
     * @param fauna      the gate of each TFC species by its namespaced type; every TFC candidate needs one
     * @return the pick, or empty if nothing was picked or the TFC gate refused the position
     * @throws IllegalArgumentException if a TFC candidate has no gate in {@code fauna}
     */
    public static Optional<SpawnPick> attempt(List<SpawnEntry> candidates, Map<String, FaunaRule> fauna, SpawnSite site, SpawnRandom rand) {
        Optional<SpawnEntry> chosen = pick(candidates, rand);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        SpawnEntry entry = chosen.get();
        int groupSize = entry.minCount() + rand.nextInt(entry.maxCount() - entry.minCount() + 1);
        if (entry.isTfc()) {
            FaunaRule rule = fauna.get(entry.type());
            if (rule == null) {
                throw new IllegalArgumentException("no fauna rule for " + entry.type());
            }
            if (!accepts(rule, site, rand)) {
                return Optional.empty();
            }
        }
        return Optional.of(new SpawnPick(entry, groupSize));
    }

    /**
     * TFC's spawn placement predicate for a TFC species, in TFC's order: the chance draw, the depth rule, the climate window
     * ({@link #climateValid}), the months and the brightness cap. Aquatic fluid checks and solid ground are not here.
     */
    public static boolean accepts(FaunaRule rule, SpawnSite site, SpawnRandom rand) {
        if (rand.nextInt(rule.chance()) != 0) {
            return false;
        }
        if (rule.distanceBelowSeaLevel() != -1 && site.y() > site.seaLevel() - rule.distanceBelowSeaLevel()) {
            return false;
        }
        if (!climateValid(rule, site, rand)) {
            return false;
        }
        if (!rule.months().isEmpty() && !rule.months().contains(site.month())) {
            return false;
        }
        return rule.maxBrightness() == -1 || site.rawBrightness() <= rule.maxBrightness();
    }

    /**
     * TFC {@code ClimatePlacement.isValid}: the elevation, temperature, groundwater, rain variance and forest window, then
     * the fuzzy draw if the rule is fuzzy. Bounds are inclusive. Rain variance is flipped in the southern hemisphere.
     */
    static boolean climateValid(FaunaRule rule, SpawnSite site, SpawnRandom rand) {
        final int y = site.y();
        final float temperature = adjustAverageTemperatureForElevation(y, site.chunk().averageSeaLevelTemperature());
        final float groundwater = rule.ignoreRivers()
                ? site.chunk().averageRainfall()
                : Math.min(site.chunk().baseGroundwater() + site.chunk().averageRainfall(), ChunkData.MAX_GROUNDWATER);
        final float rainVariance = site.northernHemisphere() ? site.chunk().rainfallVariance() : -site.chunk().rainfallVariance();
        final int density = site.forestDensity();

        if (y < MIN_ELEVATION || y > MAX_ELEVATION
                || temperature < rule.minTemperature() || temperature > rule.maxTemperature()
                || groundwater < rule.minGroundwater() || groundwater > rule.maxGroundwater()
                || rainVariance < MIN_RAIN_VARIANCE || rainVariance > MAX_RAIN_VARIANCE
                || density < rule.minForest() || density > rule.maxForest()) {
            return false;
        }
        if (!rule.fuzzy()) {
            return true;
        }
        final float targetTemperature = (rule.minTemperature() + rule.maxTemperature()) / 2f;
        final float normTemperature = Math.abs(temperature - targetTemperature) / (rule.maxTemperature() - rule.minTemperature());
        final float targetGroundwater = (rule.minGroundwater() + rule.maxGroundwater()) / 2f;
        final float normGroundwater = Math.abs(groundwater - targetGroundwater) / (rule.maxGroundwater() - rule.minGroundwater());
        final float targetRainVariance = (MIN_RAIN_VARIANCE + MAX_RAIN_VARIANCE) / 2f;
        final float normRainVariance = Math.abs(rainVariance - targetRainVariance) / (MAX_RAIN_VARIANCE - MIN_RAIN_VARIANCE);
        final float first = rand.nextFloat();
        final float second = rand.nextFloat();
        return first * second > Math.max(normTemperature, Math.max(normGroundwater, normRainVariance));
    }

    /**
     * TFC {@code EnvironmentHelpers.adjustAvgTempForElev}: above sea level the temperature drops by 0.16225 per block, capped
     * at 17.822 degrees. Unlike the overworld climate's own elevation adjustment, nothing happens at or below sea level.
     */
    static float adjustAverageTemperatureForElevation(int y, float averageTemperature) {
        if (y > TEMPERATURE_SEA_LEVEL) {
            float elevation = (y - TEMPERATURE_SEA_LEVEL) * 0.16225f;
            elevation = Math.min(Math.max(elevation, 0f), 17.822f);
            return averageTemperature - elevation;
        }
        return averageTemperature;
    }
}
