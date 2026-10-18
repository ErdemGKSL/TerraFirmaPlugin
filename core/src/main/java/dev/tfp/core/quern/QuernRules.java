/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (MANUAL_TICKS, MANUAL_RECIPE_PER_TICK,
 * NETWORK_RECIPE_PER_SPEED, clientTick, startGrinding, finishGrinding, isGrinding),
 * file: src/main/java/net/dries007/tfc/common/recipes/QuernRecipe.java @ c3b4d9536 (getRecipe)
 */
package dev.tfp.core.quern;

import dev.tfp.core.tag.TagLookup;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The quern's grinding rules, as pure functions of the turns the axle has made. Nothing is ticked: a grind is a number of
 * progress units, and progress is a function of the total rotation since it started.
 *
 * <p><b>Progress.</b> A grind needs {@link #TICKS_PER_GRIND} (90) progress units. Manual grinding adds one unit per tick, so a
 * manual grind takes 90 ticks. A powered quern adds {@code speed * 90 / 2pi} per tick, which makes one full turn (2pi radians of
 * rotation) worth exactly 90 units whatever the speed. The lazy form is therefore {@code progress = 90 * turns}, clamped to
 * {@code [0, 90]}, where {@code turns} is the rotation in radians divided by 2pi.
 *
 * <p><b>Start.</b> TFC starts a grind when the input matches a recipe and a handstone is in place. TFC does not require a free
 * output slot: the result is merged into the output slot, and any excess is spawned into the world when the grind finishes.
 */
public final class QuernRules {
    /** Progress units for one grind, which is MANUAL_TICKS in TFC. */
    public static final int TICKS_PER_GRIND = 90;

    /** Progress units per full turn of the axle. */
    public static final double PROGRESS_PER_TURN = TICKS_PER_GRIND;

    private static final double TAU = 2 * Math.PI;

    private QuernRules() {}

    /**
     * The progress of a grind after the given number of turns, in units from {@code 0} to {@link #TICKS_PER_GRIND}.
     *
     * @throws IllegalArgumentException if {@code turns} is negative or not finite
     */
    public static double progress(double turns) {
        requireTurns(turns);
        return Math.min(PROGRESS_PER_TURN * turns, TICKS_PER_GRIND);
    }

    /** Whether a grind has finished after the given number of turns. */
    public static boolean isFinished(double turns) {
        return progress(turns) >= TICKS_PER_GRIND;
    }

    /** The turns made by rotating {@code radians} of axle rotation, one turn being 2pi radians. */
    public static double turnsFromRadians(double radians) {
        if (!(radians >= 0) || Double.isInfinite(radians)) throw new IllegalArgumentException("radians must be finite and >= 0: " + radians);
        return radians / TAU;
    }

    /** The progress of a manual grind after {@code ticks} ticks of grinding, one unit per tick. */
    public static double manualProgress(long ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0: " + ticks);
        return Math.min(ticks, TICKS_PER_GRIND);
    }

    /**
     * The recipe a grinding input matches: the first by id among the recipes whose ingredient the input satisfies. Empty when
     * none does, and then no grind can start.
     */
    public static Optional<QuernRecipe> find(Collection<QuernRecipe> recipes, String itemId, boolean rotten, TagLookup tags) {
        return recipes.stream()
            .sorted(Comparator.comparing(QuernRecipe::id))
            .filter(r -> r.test(itemId, rotten, tags))
            .findFirst();
    }

    /** Whether a grind may start on the input: a recipe matched the input, and a handstone is in place. */
    public static boolean canStart(Optional<QuernRecipe> recipe, boolean hasHandstone) {
        return recipe.isPresent() && hasHandstone;
    }

    private static void requireTurns(double turns) {
        if (!(turns >= 0) || Double.isInfinite(turns)) throw new IllegalArgumentException("turns must be finite and >= 0: " + turns);
    }
}
