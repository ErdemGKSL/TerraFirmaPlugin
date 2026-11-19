/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingBushBlock.java @ c3b4d9536 (randomTick, growAndPropagate,
 *       placeNewBushAndResetCounters),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SpreadingCaneBlock.java @ c3b4d9536 (randomTick, growAndPropagate,
 *       placeCompanionBlockAndResetCounter),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (tick, the cycle scan)
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * The lazy catch-up of the spreading bushes and their canes (PAR-FARM-07), the counterpart of {@link BerryTicker} for
 * {@link SpreadingSpecies}. {@link #settle} brings the stored state to {@code now} (climate, lifecycle, the cycles the span owes) and
 * reports how many growth cycles are owed; the caller then takes one decision per cycle from the real surroundings
 * ({@link SpreadRules#spread}, {@link SpreadRules#caneStep}) and applies it with {@link #grow} or {@link #growCane}.
 *
 * <p>Differences from TFC, kept here so every caller sees them:
 * <ul>
 *   <li>The climate is checked at {@code now} only (the stored sample), not on the grid of {@link BerryRules#catchUp}. A bush that
 *       withers gets no growth from the span.</li>
 *   <li>The cycles of a span are counted in one scan ({@link BerryRules#MAX_CYCLES} at most), and each becomes one decision with the
 *       world as it is at the catch-up, not as it was at the cycle's moment.</li>
 * </ul>
 */
public final class SpreadTicker {
    private static final int MAX_LIFECYCLE_STEPS = 4;

    private SpreadTicker() {
    }

    /**
     * The result of {@link #settle}.
     *
     * @param state  the state to store (lifecycle settled; its growth counter at {@code now} when a cycle was owed)
     * @param cycles the growth cycles owed, {@code 0..}{@value BerryRules#MAX_CYCLES}; the budget (growths remaining) is not yet charged
     * @param remove whether the block is removed (a dead bush after its wither tick)
     */
    public record Settled(BerryState state, int cycles, boolean remove) {
    }

    /** A growth: the source after the decision and the new block, if the decision places one. */
    public record Growth(BerryBush source, BerryBush child, SpreadRules.Kind kind) {
    }

    /** TFC's catch-up of one spreading bush or cane at {@code now}; {@code clock} must be anchored at {@code now}. */
    public static Settled settle(SpreadingSpecies species, BerryState state, long now, BerryClock clock, BerryRules.Settings settings) {
        if (clock.playerTick() != now) {
            throw new IllegalArgumentException("the clock must be anchored at now " + now + ", not " + clock.playerTick());
        }
        BerryBush bush = state.bush();
        if (state.dead()) {
            return new Settled(state, 0, new BerryRules.DeadBush(bush.stage(), state.witherTick()).removableAt(now));
        }
        if (!species.climate().checkBoth(state.hydration(), state.temperature(), false)) {
            BerryBush kept = new BerryBush(DORMANT, bush.stage(), bush.growthsRemaining(), bush.lastUpdateTick(), bush.lastPickedTick());
            return new Settled(state.withered(now, kept), 0, false);
        }
        Lifecycle lifecycle = bush.lifecycle();
        Lifecycle expected = species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(now)));
        for (int step = 0; step < MAX_LIFECYCLE_STEPS; step++) {
            Lifecycle next = BerryRules.advanceLifecycle(lifecycle, expected, true, now, bush.lastPickedTick(), settings.pickBloomDelayTicks());
            if (next == lifecycle) {
                break;
            }
            lifecycle = next;
        }
        int cycles = scanCycles(species, bush.lastUpdateTick(), now, clock, settings);
        if (cycles < 0) { // less than one interval: TFC leaves the counter alone
            return new Settled(state.withBush(new BerryBush(lifecycle, bush.stage(), bush.growthsRemaining(), bush.lastUpdateTick(),
                bush.lastPickedTick())), 0, false);
        }
        if (bush.growthsRemaining() <= 0) {
            cycles = 0;
        }
        return new Settled(state.withBush(new BerryBush(lifecycle, bush.stage(), bush.growthsRemaining(), now, bush.lastPickedTick())),
            cycles, false);
    }

    /**
     * TFC's cycle count (the scan of {@link BerryRules}) for a species table: the active months since the counter, at most
     * {@link BerryRules#MAX_CYCLES}, scanned forward from the counter and backward from {@code now} after a dormant month. {@code -1}
     * when less than one interval has passed.
     */
    static int scanCycles(SpreadingSpecies species, long lastUpdate, long now, BerryClock clock, BerryRules.Settings settings) {
        long since = now - lastUpdate;
        long max = since / settings.growthTicks();
        if (max < 1) {
            return -1;
        }
        int limit = (int) Math.min(max, BerryRules.MAX_CYCLES);
        if (since >= (long) CalendarTicks.CALENDAR_TICKS_IN_DAY * clock.daysInMonth() * 8) {
            return BerryRules.MAX_CYCLES;
        }
        int cycles = 0;
        long simulated = lastUpdate;
        boolean reverse = false;
        while (cycles < limit && simulated < now) {
            if (species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(simulated))) != DORMANT) {
                cycles++;
                simulated += settings.growthTicks();
            } else {
                reverse = true;
                break;
            }
        }
        if (reverse) {
            simulated = now;
            while (cycles < limit && simulated > lastUpdate) {
                if (species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(simulated))) != DORMANT) {
                    cycles++;
                    simulated -= settings.growthTicks();
                } else {
                    break;
                }
            }
        }
        return cycles;
    }

    /**
     * TFC {@code placeBlockAndResetCounter} / {@code placeNewBushAndResetCounters}: the bush after one decision of a bush block, and the
     * child it places. Each of them banks the {@code remainingCycles} that are still owed and has {@code growths - 1} left. A bush block
     * becomes stage 1 after {@code STAGE_UP}; after {@code CANE} it becomes stage 2 when the step says so, otherwise it stays; after
     * {@code BUSH_UP} it is stage 2. The new cane is stage 0; the bush block above is stage 1 (TFC's upward growth keeps stage 1).
     */
    public static Growth grow(BerryBush bush, SpreadRules.SpreadStep step, int remainingCycles, long now, BerryRules.Settings settings) {
        if (step.kind() == SpreadRules.Kind.NONE) {
            return new Growth(bush, null, step.kind());
        }
        long banked = now - settings.growthTicks() * remainingCycles;
        int growths = Math.max(0, bush.growthsRemaining() - 1);
        int stage = switch (step.kind()) {
            case STAGE_UP -> 1;
            case CANE -> step.stopAtStageTwo() ? 2 : bush.stage();
            case BUSH_UP -> 2;
            case NONE -> bush.stage();
        };
        BerryBush source = new BerryBush(bush.lifecycle(), stage, growths, banked, bush.lastPickedTick());
        BerryBush child = switch (step.kind()) {
            case CANE -> new BerryBush(bush.lifecycle(), 0, growths, banked, BerryState.NEVER_PICKED);
            case BUSH_UP -> new BerryBush(bush.lifecycle(), 1, growths, banked, BerryState.NEVER_PICKED);
            default -> null;
        };
        return new Growth(source, child, step.kind());
    }

    /**
     * TFC {@code SpreadingCaneBlock.growAndPropagate}: the cane after one cycle ({@code STAGE_UP}: stage plus one) or the bush block that
     * replaces it ({@code CONVERT_TO_BUSH}: stage 1, same lifecycle). Both bank the remaining cycles and have {@code growths - 1} left.
     * Returns the same cane for {@code NONE}.
     */
    public static BerryBush growCane(BerryBush cane, SpreadRules.CaneKind kind, int remainingCycles, long now, BerryRules.Settings settings) {
        if (kind == SpreadRules.CaneKind.NONE) {
            return cane;
        }
        long banked = now - settings.growthTicks() * remainingCycles;
        int growths = Math.max(0, cane.growthsRemaining() - 1);
        int stage = kind == SpreadRules.CaneKind.STAGE_UP ? cane.stage() + 1 : 1;
        return new BerryBush(cane.lifecycle(), stage, growths, banked, cane.lastPickedTick());
    }
}
