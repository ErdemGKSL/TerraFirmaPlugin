/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (randomTick, tick,
 *       growAndPropagate, placeBlockAndResetCounter, getStateForPlacement, useItemOn, the 8-month and cycle-scan rules),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/SeasonalPlantBlock.java @ c3b4d9536 (onUpdate, checkAndSetDormant,
 *       getLifecycleForMonth, stateAfterPicking),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBerryBushBlock.java @ c3b4d9536 (randomDestroyTick gate),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (berryBushGrowthTicks, fruitPickBloomDelayTicks),
 *       src/main/java/net/dries007/tfc/util/calendar/Season.java @ c3b4d9536 (season of a month)
 */
package dev.tfp.core.berry;

import static dev.tfp.core.berry.Lifecycle.DORMANT;
import static dev.tfp.core.berry.Lifecycle.FLOWERING;
import static dev.tfp.core.berry.Lifecycle.FRUITING;
import static dev.tfp.core.berry.Lifecycle.HEALTHY;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.calendar.Season;
import java.util.ArrayList;
import java.util.List;

/**
 * The berry bush rules as pure arithmetic over player ticks. A bush is never ticked. {@link #catchUp} brings the stored state to a later
 * tick in one call, by running TFC's random tick at that tick: TFC's own random tick already catches up every growth cycle through its
 * recursive {@code tick} call, so one evaluation reproduces the state the random ticks would have reached. A catch-up settles the lifecycle
 * to the month, and it counts the growth of the span in closed form (see {@link #catchUpGrowth}).
 *
 * <p>Differences from TFC, kept here so every caller sees them:
 * <ul>
 *   <li>Climate is sampled at a fixed two-day grid since the last update (and at the tick itself), not at the random ticks TFC's random
 *       draws happen to hit. A dry or cold spell shorter than the grid can be missed.</li>
 *   <li>A dead bush is removed on the first catch-up after the wither tick. TFC removes it on a 1-in-15 draw of a random tick, which
 *       cannot be replayed lazily.</li>
 *   <li>A catch-up counts its growth in closed form: one stage per owed cycle up to fully grown, then at most one propagation attempt. TFC
 *       banks the cycles through the recursion of {@code placeBlockAndResetCounter}, which spreads them over the random ticks and drops the
 *       active intervals of a span that ends dormant. The single tick ({@link #randomTick}) keeps TFC's arithmetic.</li>
 *   <li>Propagation is reported, not performed. The caller places the child (the random spread, the placement check and the child's
 *       growth budget draw all belong to the world).</li>
 *   <li>Time is player ticks, as in TFC's {@code getTicks}. Calendar ticks come from the {@link BerryClock}, which the caller builds from
 *       the server calendar.</li>
 * </ul>
 */
public final class BerryRules {
    /** TFC {@code ICalendar.PLAYER_TICKS_IN_DEFAULT_DAY}: 60 * 20 * 24. */
    public static final int PLAYER_TICKS_IN_DEFAULT_DAY = 60 * 20 * 24;
    /** TFC default {@code berryBushGrowthTicks}: 4 default days. */
    public static final long GROWTH_TICKS = 4L * PLAYER_TICKS_IN_DEFAULT_DAY;
    /** TFC default {@code fruitPickBloomDelayTicks}: 10 default days. */
    public static final long PICK_BLOOM_DELAY_TICKS = 10L * PLAYER_TICKS_IN_DEFAULT_DAY;
    /** TFC {@code BerryBushBlockEntity.growthsRemaining} starts at 24. */
    public static final int GROWTHS_INITIAL = 24;
    /** The highest growth stage ({@code STAGE_2}). */
    public static final int MAX_STAGE = 2;
    /** TFC caps the cycles of one catch-up at 8, and skips straight to 8 after eight months. */
    public static final int MAX_CYCLES = 8;
    /** Spacing of the climate samples that the wither check takes between the last update and now. */
    public static final long CLIMATE_SAMPLE_TICKS = 2L * PLAYER_TICKS_IN_DEFAULT_DAY;

    private static final long NO_WITHER = Long.MIN_VALUE;
    /** Enough steps to go from dormant to fruiting (three stages), with one spare. */
    private static final int MAX_LIFECYCLE_STEPS = 4;

    private BerryRules() {
    }

    /** The configured constants: the growth interval and the pick bloom delay, in player ticks. */
    public record Settings(long growthTicks, long pickBloomDelayTicks) {
        /** The TFC defaults. */
        public static final Settings DEFAULT = new Settings(GROWTH_TICKS, PICK_BLOOM_DELAY_TICKS);

        public Settings {
            if (growthTicks < 1) {
                throw new IllegalArgumentException("growthTicks must be >= 1: " + growthTicks);
            }
            if (pickBloomDelayTicks < 0) {
                throw new IllegalArgumentException("pickBloomDelayTicks must be >= 0: " + pickBloomDelayTicks);
            }
        }
    }

    /** The instant climate: the farmland hydration of the bush's root and the average temperature at its stem. */
    public record Sample(int hydration, float temperature) {
    }

    /** The climate at a player tick. */
    @FunctionalInterface
    public interface ClimateSource {
        Sample at(long playerTick);
    }

    /**
     * A growth step that TFC hands to the world: a propagation attempt at the fully grown stage. {@code bankedCycles} is the child's
     * growth bank (TFC passes it to the child's {@code placeBlockAndResetCounter}), and {@code growthsBeforeDraw} is the parent's growth
     * budget before the child draws its own cost: the child gets {@code growthsBeforeDraw - nextInt(1, 3)} growths.
     */
    public record Propagation(int bankedCycles, int growthsBeforeDraw) {
    }

    /** A bush that withered at {@code witherTick}, keeping its stage. */
    public record DeadBush(int stage, long witherTick) {
        /** Whether the dead bush is removed by a catch-up at {@code now}: any catch-up after the wither tick (see class notes). */
        public boolean removableAt(long now) {
            return now > witherTick;
        }
    }

    /** The result of a catch-up: still alive, or withered. */
    public sealed interface Outcome permits Alive, Withered {
    }

    /** A living bush and the propagation attempts taken to reach its state. */
    public record Alive(BerryBush bush, List<Propagation> propagations) implements Outcome {
        public Alive {
            propagations = List.copyOf(propagations);
        }
    }

    /** A bush that withered during the catch-up, with the propagation attempts taken before it withered. */
    public record Withered(DeadBush dead, List<Propagation> propagations) implements Outcome {
        public Withered {
            propagations = List.copyOf(propagations);
        }
    }

    /** One random tick: the bush after it, and the propagation attempts it took. */
    public record Tick(BerryBush bush, List<Propagation> propagations) {
        public Tick {
            propagations = List.copyOf(propagations);
        }
    }

    /** A pick: whether fruit was yielded (one item) and the bush after the pick. */
    public record Pick(boolean yielded, BerryBush bush) {
    }

    /**
     * TFC {@code SeasonalPlantBlock}'s season of a calendar month: the season of the hemispheric month ({@code Month.getSeason} of
     * {@code getHemispheralCalendarMonthOfYear}).
     */
    public static Season season(Month calendarMonth, boolean northern) {
        return hemisphericMonth(calendarMonth, northern).season();
    }

    /** TFC {@code getHemispheralCalendarMonthOfYear}: the calendar month, or its opposite in the southern hemisphere. */
    public static Month hemisphericMonth(Month calendarMonth, boolean northern) {
        return northern ? calendarMonth : calendarMonth.opposite();
    }

    /** The lifecycle of a species in a calendar month of the given hemisphere. */
    public static Lifecycle lifecycleAt(BushSpecies species, Month calendarMonth, boolean northern) {
        return species.lifecycleFor(hemisphericMonth(calendarMonth, northern));
    }

    /**
     * TFC {@code getStateForPlacement} and {@code setPlacedBy}: a new bush is healthy in its growing season and dormant otherwise, at
     * stage 0 with the full growth budget. The growth counter and the pick counter both start at {@code now}.
     */
    public static BerryBush place(BushSpecies species, long now, BerryClock clock) {
        requireAnchored(now, clock);
        Lifecycle lifecycle = species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(now))).active() ? HEALTHY : DORMANT;
        return new BerryBush(lifecycle, 0, GROWTHS_INITIAL, now, now);
    }

    /**
     * TFC {@code useItemOn}: picking yields one fruit while fruiting, sets the bush to healthy and records the pick tick. Any other stage
     * of the lifecycle yields nothing. The growth stage does not change the yield.
     */
    public static Pick pick(BerryBush bush, long now) {
        if (bush.lifecycle() != FRUITING) {
            return new Pick(false, bush);
        }
        return new Pick(true, new BerryBush(HEALTHY, bush.stage(), bush.growthsRemaining(), bush.lastUpdateTick(), now));
    }

    /**
     * Brings a living bush up to {@code now}. The climate is sampled from the last update to now on the two-day grid; the first sample out
     * of the species' range withers the bush. Growth up to that point is caught up, and the dead bush keeps its stage.
     *
     * @param clock the clock anchored at {@code now}
     */
    public static Outcome catchUp(BushSpecies species, BerryBush bush, long now, BerryClock clock, ClimateSource climate, Settings settings) {
        requireAnchored(now, clock);
        if (bush.lastUpdateTick() > now) {
            throw new IllegalArgumentException("the bush was updated after now: " + bush.lastUpdateTick() + " > " + now);
        }
        long wither = NO_WITHER;
        for (long p = bush.lastUpdateTick() + CLIMATE_SAMPLE_TICKS; p < now; p += CLIMATE_SAMPLE_TICKS) {
            if (!inClimate(species, climate.at(p))) {
                wither = p;
                break;
            }
        }
        if (wither == NO_WITHER && !inClimate(species, climate.at(now))) {
            wither = now;
        }
        if (wither == NO_WITHER) {
            Tick tick = settledTick(species, bush, now, clock, settings);
            return new Alive(tick.bush(), tick.propagations());
        }
        Tick tick = settledTick(species, bush, wither, clock.at(wither), settings);
        return new Withered(new DeadBush(tick.bush().stage(), wither), tick.propagations());
    }

    /**
     * TFC {@code randomTick} after its climate check: the placement check of the lifecycle, then the growth of the bush, including every
     * cycle a catch-up owes. The caller has already checked that the climate is in range at {@code now}.
     *
     * @param clock the clock anchored at {@code now}
     */
    public static Tick randomTick(BushSpecies species, BerryBush bush, long now, BerryClock clock, Settings settings) {
        requireAnchored(now, clock);
        Walk walk = new Walk(bush);
        tick(walk, species, now, clock, settings);
        return new Tick(walk.toBush(), walk.propagations);
    }

    /**
     * A catch-up over a real span: TFC moves the lifecycle one stage per random tick, and a span long enough to matter holds many random
     * ticks, so the lifecycle is settled to the stage the month expects before the growth is caught up. The steps stop as soon as nothing
     * changes (a flowering stage held back by a recent pick stays where it is).
     */
    private static Tick settledTick(BushSpecies species, BerryBush bush, long now, BerryClock clock, Settings settings) {
        requireAnchored(now, clock);
        Walk walk = new Walk(bush);
        for (int step = 0; step < MAX_LIFECYCLE_STEPS; step++) {
            Lifecycle before = walk.lifecycle;
            onUpdate(walk, species, now, clock, settings);
            if (walk.lifecycle == before) {
                break;
            }
        }
        catchUpGrowth(walk, species, now, clock, settings);
        return new Tick(walk.toBush(), walk.propagations);
    }

    /** TFC {@code SeasonalPlantBlock.onUpdate} with its climate check already passed. */
    public static boolean inClimate(BushSpecies species, Sample sample) {
        return species.climate().checkBoth(sample.hydration(), sample.temperature(), false);
    }

    /** Mutable working copy of a bush while one catch-up runs. */
    private static final class Walk {
        Lifecycle lifecycle;
        int stage;
        int growths;
        long lastUpdate;
        final long lastPicked;
        final List<Propagation> propagations = new ArrayList<>();

        Walk(BerryBush bush) {
            this.lifecycle = bush.lifecycle();
            this.stage = bush.stage();
            this.growths = bush.growthsRemaining();
            this.lastUpdate = bush.lastUpdateTick();
            this.lastPicked = bush.lastPickedTick();
        }

        BerryBush toBush() {
            return new BerryBush(lifecycle, stage, growths, lastUpdate, lastPicked);
        }
    }

    /** TFC {@code SeasonalPlantBlock.onUpdate}. */
    private static void onUpdate(Walk walk, BushSpecies species, long now, BerryClock clock, Settings settings) {
        Lifecycle expected = species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(now)));
        if (expected == DORMANT) {
            walk.lifecycle = DORMANT;
            return;
        }
        // The climate range is checked by the random tick at the same tick, so the range check of TFC's onUpdate always passes here.
        Lifecycle next = walk.lifecycle.advanceTowards(expected);
        if (next != walk.lifecycle && (next != FLOWERING || now - walk.lastPicked > settings.pickBloomDelayTicks())) {
            walk.lifecycle = next;
        }
    }

    /**
     * TFC {@code StationaryBerryBushBlock.tick}: the placement check, then the growth of every cycle a tick owes, with the recursion of
     * {@code placeBlockAndResetCounter}. The check that the bush is active at the tick is TFC's, so this is the single tick.
     */
    private static void tick(Walk walk, BushSpecies species, long now, BerryClock clock, Settings settings) {
        onUpdate(walk, species, now, clock, settings);
        if (!walk.lifecycle.active() || walk.growths <= 0) {
            return;
        }
        long cycles = scanCycles(walk, species, now, clock, settings);
        if (cycles < 0) {
            return;
        }
        walk.lastUpdate = now;
        if (cycles > 0) {
            growAndPropagate(walk, species, now, clock, settings, (int) cycles - 1, walk.growths - 1);
        }
    }

    /**
     * The growth of a catch-up: the cycles the span owes, in closed form. Each cycle is one stage (or, fully grown, one propagation
     * attempt), so the stage rises by the cycles up to the maximum and the budget, and a bush that reaches the maximum with cycles to
     * spare attempts one propagation. The scan is TFC's, and it is not gated on the lifecycle at {@code now}: the active intervals before
     * a dormancy still count, as they would have under the ticks. The recursion of {@link #tick} would bank those cycles just before
     * {@code now}, which is wrong when {@code now} is dormant.
     */
    private static void catchUpGrowth(Walk walk, BushSpecies species, long now, BerryClock clock, Settings settings) {
        long cycles = scanCycles(walk, species, now, clock, settings);
        if (cycles < 0) {
            return;
        }
        walk.lastUpdate = now;
        long remaining = cycles;
        int advance = (int) Math.min(Math.min(MAX_STAGE - walk.stage, remaining), walk.growths);
        walk.stage += advance;
        walk.growths -= advance;
        remaining -= advance;
        if (remaining > 0 && walk.stage == MAX_STAGE && walk.growths > 0) {
            walk.propagations.add(new Propagation((int) (remaining - 1), walk.growths - 1));
        }
    }

    /**
     * TFC's cycle count of one tick: the active intervals since the counter, at most {@link #MAX_CYCLES}, scanned forward from the counter
     * and then backward from {@code now} after a dormant interval. Returns {@code -1} when less than one interval has passed (TFC then
     * leaves the counter alone), and {@code MAX_CYCLES} after eight months.
     */
    private static long scanCycles(Walk walk, BushSpecies species, long now, BerryClock clock, Settings settings) {
        long ticksSinceUpdate = now - walk.lastUpdate;
        long maxCycles = ticksSinceUpdate / settings.growthTicks();
        if (maxCycles < 1) {
            return -1;
        }
        int limit = (int) Math.min(maxCycles, MAX_CYCLES);
        if (ticksSinceUpdate >= (long) CalendarTicks.CALENDAR_TICKS_IN_DAY * clock.daysInMonth() * 8) {
            return MAX_CYCLES;
        }
        long cycles = 0;
        long previousTick = walk.lastUpdate;
        long simulated = previousTick;
        boolean checkReverse = false;
        while (cycles < limit && simulated < now) {
            if (species.lifecycleFor(clock.hemisphericMonth(clock.calendarAt(simulated))) != DORMANT) {
                cycles++;
                simulated += settings.growthTicks();
            } else {
                checkReverse = true;
                break;
            }
        }
        if (checkReverse) {
            simulated = now;
            while (cycles < limit && simulated > previousTick) {
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

    /** TFC {@code growAndPropagate}: one stage per call while not fully grown, otherwise a propagation attempt. */
    private static void growAndPropagate(Walk walk, BushSpecies species, long now, BerryClock clock, Settings settings, int cycles, int growths) {
        if (walk.stage < MAX_STAGE) {
            walk.stage++;
            // placeBlockAndResetCounter: reset the counter, bank the remaining cycles, set the budget, then tick the bush again.
            walk.lastUpdate = now - settings.growthTicks() * cycles;
            walk.growths = growths;
            tick(walk, species, now, clock, settings);
        } else {
            walk.propagations.add(new Propagation(cycles, growths));
        }
    }

    private static void requireAnchored(long now, BerryClock clock) {
        if (clock.playerTick() != now) {
            throw new IllegalArgumentException("the clock must be anchored at now " + now + ", not " + clock.playerTick());
        }
    }
}
