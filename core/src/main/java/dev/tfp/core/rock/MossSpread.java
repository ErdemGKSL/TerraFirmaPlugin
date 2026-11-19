/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/MossSpreadingBlock.java @ c3b4d9536 (spreadMoss),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (mossyRockSpreadRate)
 */
package dev.tfp.core.rock;

import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Mossy rock spreading (PAR-GATHER-06), lazily. TFC's mossy blocks random-tick; a tick spreads with chance
 * {@code 1 / mossyRockSpreadRate} to one block at a random offset in {@code [-3, 3]} per axis, which converts when it is a
 * cobble or brick block (or stair, slab, wall) next to water. A block gets a random tick with chance
 * {@code randomTickSpeed / 4096} per game tick (3 by default), so the spread attempts of a span of ticks are a Poisson
 * process, sampled here instead of ticked. Pure; the random source is passed in.
 */
public final class MossSpread {
    /** TFC's default {@code mossyRockSpreadRate}. */
    public static final int DEFAULT_RATE = 20;
    /** Vanilla's default {@code randomTickSpeed}. */
    public static final int RANDOM_TICK_SPEED = 3;
    /** Random tick chance per block and tick is {@code speed / 4096}. */
    public static final int SECTION_BLOCKS = 4096;
    /** Spread attempts replayed per source and catch-up; more would only re-roll an already mossy neighbourhood. */
    public static final int MAX_ATTEMPTS = 64;
    /** Offsets reach {@code -3..3} on each axis. */
    public static final int REACH = 3;

    private static final String ROCK = "tfp:rock/";
    private static final String COBBLE = ROCK + "cobble/";
    private static final String BRICKS = ROCK + "bricks/";
    private static final String MOSSY_COBBLE = ROCK + "mossy_cobble/";
    private static final String MOSSY_BRICKS = ROCK + "mossy_bricks/";

    private MossSpread() {
    }

    /** The expected spread attempts per game tick of one mossy block. */
    public static double attemptsPerTick(int randomTickSpeed, int rate) {
        if (rate < 1) {
            throw new IllegalArgumentException("rate must be at least 1: " + rate);
        }
        return Math.max(0, randomTickSpeed) / (double) SECTION_BLOCKS / rate;
    }

    /**
     * How many spread attempts a mossy block makes in {@code elapsedTicks}: Poisson with mean
     * {@code elapsed * attemptsPerTick}, by summing exponential gaps, at most {@link #MAX_ATTEMPTS}.
     */
    public static int attempts(long elapsedTicks, int randomTickSpeed, int rate, RandomGenerator random) {
        if (elapsedTicks <= 0) {
            return 0;
        }
        double mean = elapsedTicks * attemptsPerTick(randomTickSpeed, rate);
        if (mean <= 0) {
            return 0;
        }
        int n = 0;
        double t = 0;
        while (n < MAX_ATTEMPTS) {
            t += -Math.log(1.0 - random.nextDouble());
            if (t > mean) {
                break;
            }
            n++;
        }
        return n;
    }

    /** One offset, {@code nextInt(4) - nextInt(4)} per axis in TFC's order x, y, z: {@code {dx, dy, dz}}. */
    public static int[] offset(RandomGenerator random) {
        int dx = random.nextInt(4) - random.nextInt(4);
        int dy = random.nextInt(4) - random.nextInt(4);
        int dz = random.nextInt(4) - random.nextInt(4);
        return new int[] {dx, dy, dz};
    }

    /** True for a mossy cobble or mossy brick block id (or its stairs, slab, wall). */
    public static boolean isMossy(String blockId) {
        return blockId.startsWith(MOSSY_COBBLE) || blockId.startsWith(MOSSY_BRICKS);
    }

    /** The mossy id of a cobble or brick block id (any shape), empty for every other block. */
    public static Optional<String> mossyOf(String blockId) {
        if (blockId.startsWith(COBBLE)) {
            return Optional.of(MOSSY_COBBLE + blockId.substring(COBBLE.length()));
        }
        if (blockId.startsWith(BRICKS)) {
            return Optional.of(MOSSY_BRICKS + blockId.substring(BRICKS.length()));
        }
        return Optional.empty();
    }
}
