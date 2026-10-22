/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (tickInfestation) and src/main/java/net/dries007/tfc/ForgeEventHandler.java
 * (the container open and right-click food counts), src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java,
 * src/main/java/net/dries007/tfc/common/entities/ai/prey/PestFeastBehavior.java
 */
package dev.tfp.core.critter;

import java.util.List;
import java.util.Optional;

import dev.tfp.core.spawn.SpawnRandom;

/**
 * The infestation roll: how often a container with spoiling food gets a pest, and how many pests already nearby damp it.
 * The levels run from 0 (no food, no roll) to 5 (the worst). Pure: the caller counts the food and the nearby pests, and
 * passes the draws in.
 */
public final class PestInfestation {
    /** The highest infestation level; TFC clamps to it. */
    public static final int MAX_LEVEL = 5;
    /** The radius (blocks) of the box TFC counts nearby pests in: {@code AABB(pos).inflate(40)}. */
    public static final int NEARBY_RADIUS = 40;
    /** The pest count at which no new pest is added: the chance falls linearly to zero at this many. */
    public static final int PESTS_FOR_NO_CHANCE = 8;
    /** The infestation level of a rotten composter, rolled each tick it is rotten. */
    public static final int ROTTEN_COMPOST_LEVEL = 5;
    /** The levels a pest feast rolls after taking food from a container, one roll each. */
    public static final List<Integer> FEAST_LEVELS = List.of(1, 2, 3, 4, 5);

    private PestInfestation() {
    }

    /** TFC's clamp of the infestation level to [0, 5]. */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    /**
     * The food level of a container that a player opens: the food stacks, at most 5, and none when infestations are off in
     * the server config. TFC counts only the container's own slots (the caller passes that count).
     *
     * @param infestationsEnabled the {@code enableInfestations} server option
     * @param foodStacks          the food stacks in the container's own slots
     */
    public static int openLevel(boolean infestationsEnabled, int foodStacks) {
        return infestationsEnabled ? Math.min(MAX_LEVEL, foodStacks) : 0;
    }

    /**
     * The food level of a container that a player right-clicks: the food stacks, at most 5. TFC does not check the config
     * on this path.
     */
    public static int clickLevel(int foodStacks) {
        return Math.min(MAX_LEVEL, foodStacks);
    }

    /**
     * TFC {@code tickInfestation}, the first gate: a level of 0 does nothing and draws nothing; otherwise the chance is one in
     * {@code 120 - 20 * level} (so 1 in 100 at level 1, 1 in 20 at level 5).
     *
     * @return whether this level's roll passes
     */
    public static boolean rollsThisTick(int level, SpawnRandom rand) {
        int clamped = clampLevel(level);
        if (clamped == 0) {
            return false;
        }
        return rand.nextInt(120 - (20 * clamped)) == 0;
    }

    /**
     * The chance that a pest is added given the pests already nearby: {@code 1 - clampedMap(count, 0, 8, 0, 1)}, so 1 with no
     * pests and 0 at eight or more.
     */
    public static float spawnChance(int nearbyPests) {
        float fraction = (float) Math.max(0, nearbyPests) / PESTS_FOR_NO_CHANCE;
        return 1f - Math.min(1f, fraction);
    }

    /**
     * One infestation roll at a container: the level gate, then the nearby-pest chance (the draw passes when it is at most the
     * chance), then the climate pick. Draws happen in TFC's order. A pass with no pest is an empty result.
     *
     * <p>Not here: the spawn position search ({@code LandRandomPos}) and the entity creation, which are glue.
     *
     * @param level       the infestation level, clamped to [0, 5]
     * @param nearbyPests the pests within {@link #NEARBY_RADIUS}
     * @param temperature the sea-level temperature at the container, degrees Celsius
     * @param rainfall    the rainfall at the container, mm
     * @param rand        the random source
     * @return the species to spawn, or empty
     */
    public static Optional<PestSpecies> roll(int level, int nearbyPests, float temperature, float rainfall, SpawnRandom rand) {
        if (!rollsThisTick(level, rand)) {
            return Optional.empty();
        }
        if (rand.nextFloat() > spawnChance(nearbyPests)) {
            return Optional.empty();
        }
        return PestClimate.choose(rand, temperature, rainfall);
    }
}
