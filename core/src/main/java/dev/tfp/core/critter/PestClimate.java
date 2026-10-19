/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java (choosePest) @ c3b4d9536
 */
package dev.tfp.core.critter;

import java.util.Optional;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * TFC {@code Helpers.choosePest}: which pest tag an infestation draws from, given the climate at the container.
 * Pure: the caller reads the sea-level temperature and rainfall at the position and passes them in.
 */
public final class PestClimate {
    /** At or below this sea-level temperature (degrees Celsius) nothing spawns: the arctic is spared. */
    static final float ARCTIC_LIMIT = -12f;
    /** Below this temperature only the cold pests spawn. */
    static final float COLD_LIMIT = -3f;
    /** Chance (the {@code <= 0.7} draw) that the climate-specific pests are considered at all. */
    static final double CLIMATE_CHANCE = 0.7;
    /** Below this rainfall (mm) the desert pests spawn. */
    static final float DRY_RAINFALL = 160f;
    /** Above this temperature (degrees Celsius) the tropical pests spawn, when not dry. */
    static final float TROPICAL_TEMPERATURE = 12f;

    private PestClimate() {
    }

    /**
     * The pest an infestation spawns in this climate, drawing in TFC's order: a cold temperature takes a cold-tag pick with no
     * climate draw; otherwise the 0.7 draw comes first, and only when it passes can a dry or tropical pick happen; every other
     * case is a universal pick.
     *
     * @param rand                 the random source
     * @param averageSeaLevelTemp  the sea-level temperature at the container, degrees Celsius
     * @param averageRainfall      the rainfall at the container, mm
     * @return the species, or empty in the arctic
     */
    public static Optional<PestSpecies> choose(SpawnRandom rand, float averageSeaLevelTemp, float averageRainfall) {
        if (averageSeaLevelTemp <= ARCTIC_LIMIT) {
            return Optional.empty();
        }
        if (averageSeaLevelTemp < COLD_LIMIT) {
            return Optional.of(PestGroup.COLD.pick(rand));
        }
        if ((double) rand.nextFloat() <= CLIMATE_CHANCE) {
            if (averageRainfall < DRY_RAINFALL) {
                return Optional.of(PestGroup.DESERT.pick(rand));
            }
            if (averageSeaLevelTemp > TROPICAL_TEMPERATURE) {
                return Optional.of(PestGroup.TROPICAL.pick(rand));
            }
        }
        return Optional.of(PestGroup.UNIVERSAL.pick(rand));
    }
}
