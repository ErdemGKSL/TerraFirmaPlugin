/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/SluiceBlockEntity.java @ c3b4d9536 (serverTick, getRepresentativeState,
 * isItemValid, saveAdditional)
 */
package dev.tfp.core.pan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The sluice's soil slots and production cycle. Pure and thread-safe.
 *
 * <p>The glue drives it once per game tick, in TFC's order: on a tick where the game time is a multiple of
 * {@link #GATHER_INTERVAL}, the glue hands the gathered soil to {@link #accept} first, then calls {@link #step}. Soil is a
 * deposit identified by its string id; the glue resolves the deposit's loot table from it when {@link Step#produced()} is set.
 *
 * <p>TFC's rules, all checked against the pinned commit:
 * <ul>
 *   <li>The sluice works only when fully structured, that is {@link State#BOTH}: water flows in from the input and the output
 *       holds the same fluid. Otherwise the cycle does not run and the countdown does not move.</li>
 *   <li>The countdown ({@code ticksRemaining}) counts down only while working. When it reaches zero it resets to the configured
 *       cycle length, whether or not there was soil. The first filled slot is produced and emptied, if there was one.</li>
 *   <li>Soil is gathered once per second while working. A slot holds one piece of soil, and the first empty slot takes it.</li>
 * </ul>
 */
public final class SluiceRules {
    private SluiceRules() {}

    /** TFC {@code SluiceBlockEntity.MAX_SOIL}: the number of soil slots. */
    public static final int MAX_SOIL = 32;
    /** TFC {@code ServerConfig.sluiceTicks} default: ticks per production cycle. */
    public static final int DEFAULT_CYCLE_TICKS = 100;
    /** TFC: soil is gathered when the game time is a multiple of this. */
    public static final int GATHER_INTERVAL = 20;

    /** The fluid state of the sluice, from its input and output (TFC {@code SluiceBlockEntity.State}). */
    public enum State {
        NONE, INPUT_ONLY, OUTPUT_ONLY, BOTH
    }

    /**
     * TFC {@code SluiceBlockEntity.getRepresentativeState}.
     *
     * @param inputValid        whether the input holds a full usable source fluid
     * @param outputValid       whether the output holds a usable fluid
     * @param outputSameAsInput whether the output holds the same fluid as the input
     */
    public static State representative(boolean inputValid, boolean outputValid, boolean outputSameAsInput) {
        if (inputValid) {
            return outputValid && outputSameAsInput ? State.BOTH : State.INPUT_ONLY;
        }
        return outputValid ? State.OUTPUT_ONLY : State.NONE;
    }

    /**
     * The sluice's stored state.
     *
     * @param ticksRemaining the countdown to the next production
     * @param slots          the soil slots, {@link #MAX_SOIL} long, where {@code null} is an empty slot
     */
    public record Run(int ticksRemaining, List<String> slots) {
        public Run {
            slots = Collections.unmodifiableList(new ArrayList<>(slots));
        }

        /** A sluice with no soil and a full countdown. */
        public static Run empty(int cycleTicks) {
            List<String> none = new ArrayList<>(MAX_SOIL);
            for (int i = 0; i < MAX_SOIL; i++) {
                none.add(null);
            }
            return new Run(cycleTicks, none);
        }
    }

    /**
     * The result of one tick.
     *
     * @param run      the sluice after the tick
     * @param produced the deposit id whose loot table to roll, or {@code null} when nothing was produced
     */
    public record Step(Run run, String produced) {}

    /**
     * TFC gathering: put soil into the first empty slot. A full sluice takes no soil.
     *
     * @param run     the sluice
     * @param deposit the deposit id of the soil
     */
    public static Run accept(Run run, String deposit) {
        List<String> slots = new ArrayList<>(run.slots());
        int empty = slots.indexOf(null);
        if (empty < 0) {
            return run;
        }
        slots.set(empty, deposit);
        return new Run(run.ticksRemaining(), slots);
    }

    /**
     * One game tick of the production cycle (TFC {@code serverTick}, after gathering).
     *
     * @param run         the sluice before the tick
     * @param state       the sluice's representative state this tick
     * @param cycleTicks  the configured cycle length
     */
    public static Step step(Run run, State state, int cycleTicks) {
        if (state != State.BOTH) {
            return new Step(run, null);
        }
        int remaining = run.ticksRemaining() - 1;
        if (remaining > 0) {
            return new Step(new Run(remaining, run.slots()), null);
        }
        List<String> slots = new ArrayList<>(run.slots());
        String produced = null;
        int first = indexOfFirstSoil(slots);
        if (first >= 0) {
            produced = slots.get(first);
            slots.set(first, null);
        }
        return new Step(new Run(cycleTicks, slots), produced);
    }

    private static int indexOfFirstSoil(List<String> slots) {
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) != null) {
                return i;
            }
        }
        return -1;
    }
}
