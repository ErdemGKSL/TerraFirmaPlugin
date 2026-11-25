/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (randomTick: age growth),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TopPlantBlock.java @ c3b4d9536 (randomTick: the head grows a block),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCVineBlock.java @ c3b4d9536 (randomTick: only above 0 C),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/GrowingBranchingCactusBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/KelpTreeFlowerBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (plantGrowthChance, plantLongGrowthChance)
 */
package dev.tfp.core.plant;

import dev.tfp.core.regrowth.PlantSweepRules;
import java.util.random.RandomGenerator;

/**
 * Plant growth by random tick, as a lazy catch-up (PAR-FARM-12, NP-SIM-01): a plant that was away for a while draws the Poisson number of
 * random ticks it would have had ({@link PlantSweepRules#lambda}) and applies TFC's per-tick rule to each, so the mean growth equals
 * TFC's. All pure; the stepped one-tick forms are the reference the tests compare against.
 */
public final class PlantGrowth {
    /** {@code ServerConfig.plantGrowthChance}. */
    public static final double GROWTH_CHANCE = 0.04;
    /** {@code ServerConfig.plantLongGrowthChance}. */
    public static final double LONG_GROWTH_CHANCE = 0.03;
    /** {@code TopPlantBlock}: a head with {@code age} 25 stops growing. */
    public static final int TOP_MAX_AGE = 25;
    /** {@code KelpTreeFlowerBlock.AGE} is {@code AGE_5}. */
    public static final int KELP_FLOWER_MAX_AGE = 5;

    private PlantGrowth() {
    }

    /** {@code PlantBlock.randomTick}, one tick: {@code nextDouble() < chance && age < max} raises the age by one. */
    public static int stepAge(int age, int maxAge, double chance, RandomGenerator random) {
        return random.nextDouble() < chance && age < maxAge ? age + 1 : age;
    }

    /** The age after {@code ticks} random ticks, one at a time (the reference). */
    public static int steppedAge(int age, int maxAge, long ticks, double chance, RandomGenerator random) {
        int a = age;
        for (long i = 0; i < ticks; i++) {
            a = stepAge(a, maxAge, chance, random);
        }
        return a;
    }

    /** The age after the Poisson number of random ticks of {@code elapsedTicks} at {@code randomTickSpeed}. */
    public static int lazyAge(int age, int maxAge, long elapsedTicks, int randomTickSpeed, double chance, RandomGenerator random) {
        if (age >= maxAge) {
            return age;
        }
        int ticks = PlantSweepRules.poisson(PlantSweepRules.lambda(elapsedTicks, randomTickSpeed), random);
        return steppedAge(age, maxAge, ticks, chance, random);
    }

    /**
     * {@code TopPlantBlock.randomTick}, one tick of a head with {@code age}: grows one block in its direction when the age is below 25, the
     * long-growth draw passes and the next block is free. The new head is {@code age + 1} ({@code state.cycle(AGE)}); the old head keeps
     * its state in TFC (its body is the {@code transform}ed block), so the column gains one block per growth.
     *
     * @return whether the column grew
     */
    public static boolean topGrows(int age, double chance, boolean nextFree, RandomGenerator random) {
        return age < TOP_MAX_AGE && random.nextDouble() < chance && nextFree;
    }

    /**
     * The blocks a {@code TopPlantBlock} column gained over the Poisson ticks of the elapsed time, stopping at age 25 and at
     * {@code freeBlocks} free cells in its direction. Each tick is {@link #topGrows} of the current head.
     *
     * @return the new head age minus the old one, that is the blocks grown
     */
    public static int lazyTopGrowth(int age, int freeBlocks, long elapsedTicks, int randomTickSpeed, double chance, RandomGenerator random) {
        int ticks = PlantSweepRules.poisson(PlantSweepRules.lambda(elapsedTicks, randomTickSpeed), random);
        return steppedTopGrowth(age, freeBlocks, ticks, chance, random);
    }

    /** {@link #lazyTopGrowth} with the ticks given, one at a time (the reference). */
    public static int steppedTopGrowth(int age, int freeBlocks, long ticks, double chance, RandomGenerator random) {
        int a = age;
        int free = freeBlocks;
        for (long i = 0; i < ticks; i++) {
            if (topGrows(a, chance, free > 0, random)) {
                a++;
                free--;
            }
        }
        return a - age;
    }

    /** {@code TFCVineBlock.randomTick}: the vine ticks (grows or spreads) only when the instant temperature at it is above 0 C. */
    public static boolean vineMayGrow(float instantTemperature) {
        return instantTemperature > 0f;
    }

    /**
     * {@code GrowingBranchingCactusBlock.randomTick}: {@code if (rand.nextFloat() > plantGrowthChance) growthTick}. The comparison is
     * TFC's (it grows on 96 % of the ticks at the default chance; ledger divergence-free port), not the inverse.
     */
    public static boolean branchingCactusGrows(double chance, RandomGenerator random) {
        return random.nextFloat() > chance;
    }

    /** {@code KelpTreeFlowerBlock.randomTick}: the water above is free and {@code plantLongGrowthChance > nextDouble()}. */
    public static boolean kelpTreeGrows(boolean emptyWaterAbove, boolean belowMaxHeight, double chance, RandomGenerator random) {
        return emptyWaterAbove && belowMaxHeight && chance > random.nextDouble();
    }
}
