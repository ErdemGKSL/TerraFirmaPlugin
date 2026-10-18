/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgeStep.java @ c3b4d9536 (step values, PATHS table, getOptimalStepsToTarget)
 */
package dev.tfp.core.forge;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * The eight anvil steps and their effect on the forging offset. Pure and thread-safe.
 *
 * <p>Each step moves the offset by {@link #step()}. The target offset is reached by a sequence of steps whose sum is the target,
 * and {@link #getOptimalStepsToTarget(int)} gives the fewest steps needed to reach a given offset from zero.
 *
 * <p>Button and icon coordinates of TFC's enum are UI data and are not ported.
 */
public enum ForgeStep {
    HIT_LIGHT(-3),
    HIT_MEDIUM(-6),
    HIT_HARD(-9),
    DRAW(-15),
    PUNCH(2),
    BEND(7),
    UPSET(13),
    SHRINK(16);

    /** Offsets in {@code [0, LIMIT)} are the only ones the shortest-path table covers. */
    public static final int LIMIT = 150;

    public static final ForgeStep[] VALUES = values();

    /** Fewest steps from zero to each offset, or {@code -1} when unreachable. */
    private static final int[] PATHS;

    static {
        PATHS = new int[LIMIT];
        Arrays.fill(PATHS, -1);
        PATHS[0] = 0;

        // Breadth-first search over offsets, level by level, so each offset's distance is its fewest steps.
        final ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(0);
        while (!queue.isEmpty()) {
            final int value = queue.poll();
            for (ForgeStep step : VALUES) {
                final int next = value + step.step;
                if (next >= 0 && next < LIMIT && PATHS[next] == -1) {
                    PATHS[next] = PATHS[value] + 1;
                    queue.add(next);
                }
            }
        }
    }

    private final int step;

    ForgeStep(int step) {
        this.step = step;
    }

    /**
     * @return the step by ordinal, or {@code null} for an invalid index.
     */
    public static ForgeStep valueOf(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }

    /**
     * @return the fewest number of steps that sum to {@code target} from zero, or {@link Integer#MAX_VALUE} when {@code target} is
     *         outside {@code [0, LIMIT)}.
     */
    public static int getOptimalStepsToTarget(int target) {
        return target < 0 || target >= PATHS.length ? Integer.MAX_VALUE : PATHS[target];
    }

    /**
     * @return the signed change to the forging offset this step causes.
     */
    public int step() {
        return step;
    }
}
