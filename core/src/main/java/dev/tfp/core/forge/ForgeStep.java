/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgeStep.java @ c3b4d9536 (step values, PATHS table, getOptimalStepsToTarget, the button and icon coordinates)
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
 * <p>The button and icon coordinates are TFC's (PAR-METAL-05): the button position in TFC's 176 x 207 anvil screen and the icon
 * position in its texture; the plugin's screen re-lays them (see {@code AnvilScreenLayout}).
 */
public enum ForgeStep {
    HIT_LIGHT(-3, 53, 56, 64, 240),
    HIT_MEDIUM(-6, 71, 56, 80, 240),
    HIT_HARD(-9, 53, 74, 96, 240),
    DRAW(-15, 71, 74, 112, 240),
    PUNCH(2, 89, 56, 0, 240),
    BEND(7, 107, 56, 16, 240),
    UPSET(13, 89, 74, 32, 240),
    SHRINK(16, 107, 74, 48, 240);

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
    private final int buttonX;
    private final int buttonY;
    private final int iconX;
    private final int iconY;

    ForgeStep(int step, int buttonX, int buttonY, int iconX, int iconY) {
        this.step = step;
        this.buttonX = buttonX;
        this.buttonY = buttonY;
        this.iconX = iconX;
        this.iconY = iconY;
    }

    /** x of the step's button in TFC's anvil screen ({@code AnvilStepButton}). */
    public int buttonX() {
        return buttonX;
    }

    /** y of the step's button in TFC's anvil screen. */
    public int buttonY() {
        return buttonY;
    }

    /** u of the step's icon in TFC's {@code anvil.png}. */
    public int iconX() {
        return iconX;
    }

    /** v of the step's icon in TFC's {@code anvil.png} (the rule icons use {@code iconY - 16}). */
    public int iconY() {
        return iconY;
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
