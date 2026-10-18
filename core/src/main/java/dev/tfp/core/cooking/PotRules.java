/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (handleCooking, isBoiling,
 *       onCalendarUpdate, PRE_BOIL_TIME),
 *       src/main/java/net/dries007/tfc/common/recipes/PotRecipe.java @ c3b4d9536 (isHotEnough)
 */
package dev.tfp.core.cooking;

/**
 * The pot's boil counter. A pot boils while it holds a recipe, has no output, and is hotter than the recipe. The caller
 * decides that predicate and passes it per window: the device temperature is a separate rule, and it is constant within
 * the window. The counter is TFC's live rule: the pre-boil ticks come first ({@link #PRE_BOIL_TIME}), then the boiling
 * ticks, and the next tick after the last one takes the output.
 *
 * <p>The pre-boil counter only resets when boiling had started ({@code boilingTicks > 0}), as TFC does.
 */
public final class PotRules {
    private PotRules() {
    }

    /** {@code PotBlockEntity.PRE_BOIL_TIME}: ticks a recipe must boil before its slots lock. */
    public static final int PRE_BOIL_TIME = 100;

    /**
     * The pot counters.
     *
     * @param preBoilingTicks ticks of pre-boil so far, up to {@link #PRE_BOIL_TIME}
     * @param boilingTicks    ticks of boil so far, up to the recipe duration
     */
    public record PotState(int preBoilingTicks, int boilingTicks) {
        public static final PotState IDLE = new PotState(0, 0);

        public PotState {
            if (preBoilingTicks < 0 || boilingTicks < 0) {
                throw new IllegalArgumentException("counters must be >= 0");
            }
        }
    }

    /**
     * The result of a window.
     *
     * @param state          the counters at the end of the window (reset when the recipe finished)
     * @param finishedAtTick the 1-based tick within the window on which the output was taken, or {@code -1} for none
     */
    public record Advance(PotState state, long finishedAtTick) {
    }

    /**
     * The lazy form of the live rule over {@code ticks} ticks with a constant boiling predicate.
     *
     * @param state    the counters at the start of the window
     * @param ticks    the window, {@code >= 0}
     * @param boiling  whether the pot boils over the whole window
     * @param duration the recipe duration in ticks, {@code >= 0}
     */
    public static Advance advance(PotState state, long ticks, boolean boiling, int duration) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        }
        if (duration < 0) {
            throw new IllegalArgumentException("duration must be >= 0: " + duration);
        }
        if (!boiling) {
            // The first tick that is not boiling resets the counters, but only if boiling had started.
            if (state.boilingTicks() > 0 && ticks > 0) {
                return new Advance(PotState.IDLE, -1);
            }
            return new Advance(state, -1);
        }
        long left = ticks;
        long used = 0;
        int pre = state.preBoilingTicks();
        int boil = state.boilingTicks();
        if (pre < PRE_BOIL_TIME) {
            long p = Math.min(left, PRE_BOIL_TIME - pre);
            pre += (int) p;
            left -= p;
            used += p;
        }
        if (left == 0) {
            return new Advance(new PotState(pre, boil), -1);
        }
        long inc = Math.max(0, Math.min(left, (long) duration - boil));
        boil += (int) inc;
        left -= inc;
        used += inc;
        if (left == 0) {
            return new Advance(new PotState(pre, boil), -1);
        }
        // The counter is full and one tick remains: that tick takes the output.
        return new Advance(PotState.IDLE, used + 1);
    }
}
