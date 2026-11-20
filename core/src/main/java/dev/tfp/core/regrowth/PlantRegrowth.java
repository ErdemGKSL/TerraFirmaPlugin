/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantRegrowth.java @ c3b4d9536 (canSpread, spreadSelf, DEFAULT_PLACEMENT_TEST),
 *       src/main/java/net/dries007/tfc/util/Helpers.java @ c3b4d9536 (Mth.nextInt use)
 */
package dev.tfp.core.regrowth;

import java.util.random.RandomGenerator;

/** TFC's plant spread arithmetic, pure: the chance, the draw of the new position and the density rule. */
public final class PlantRegrowth {
    /** TFC's {@code plantSpreadChance} config default. */
    public static final float SPREAD_CHANCE = 0.001f;

    private PlantRegrowth() {
    }

    /**
     * {@code canSpread}: one float draw below the chance, and the season of the plant's hemisphere is not winter. The draw is taken first,
     * as in TFC, so a seeded caller gets TFC's sequence.
     */
    public static boolean canSpread(RandomGenerator random, float chance, boolean winter) {
        return random.nextFloat() < chance && !winter;
    }

    /** {@code Mth.nextInt(random, min, max)}: {@code min} when {@code min >= max}, else uniform in {@code [min, max]}. */
    static int nextInt(RandomGenerator random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    /**
     * {@code spreadSelf}, the offset: both axes in {@code [0, range]}; when that is the plant's own cell, again in {@code [1, range]}.
     *
     * @return {@code {dx, dz}}
     */
    public static int[] spreadOffset(RandomGenerator random, int range) {
        int dx = nextInt(random, 0, range);
        int dz = nextInt(random, 0, range);
        if (dx == 0 && dz == 0) {
            dx = nextInt(random, 1, range);
            dz = nextInt(random, 1, range);
        }
        return new int[] {dx, dz};
    }

    /**
     * {@code spreadSelf}, the density test, as a counter: the caller counts the plants of the natural-regrowing set in the square around the
     * target one at a time; spreading is denied when the count before adding one exceeds {@code maxPlants} ({@code plants++ > maxPlants}).
     *
     * @param countBefore the plants counted so far
     */
    public static boolean tooDense(int countBefore, int maxPlants) {
        return countBefore > maxPlants;
    }
}
