/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java @ c3b4d9536 (randomTick, tick, grow, canSurvive,
 *       getProductItem, shapes),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaSaplingBlock.java @ c3b4d9536 (createTree, canSurvive),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBananaPlantBlock.java @ c3b4d9536 (randomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (BANANA_STAGES),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (bananaSaplingGrowthTicks, bananaPlantGrowthTicks),
 *       src/generated/resources/data/tfc/tfc/climate_range/plant/banana_tree.json @ c3b4d9536
 */
package dev.tfp.core.banana;

import static dev.tfp.core.berry.Lifecycle.DORMANT;
import static dev.tfp.core.berry.Lifecycle.FLOWERING;
import static dev.tfp.core.berry.Lifecycle.FRUITING;
import static dev.tfp.core.berry.Lifecycle.HEALTHY;

import dev.tfp.core.berry.BerryClock;
import dev.tfp.core.berry.BerryRules;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateRange;
import java.util.function.IntSupplier;

/**
 * The banana plant as decisions over the facts the world supplies. A sapling becomes a stage-1 plant after its growth time; a stage-1
 * plant grows a trunk block upwards (the old block becomes stage 0, the new top is stage 1) until the stalk is 3 to 5 blocks tall, then
 * its top becomes stage 2. Every block of the stalk keeps its own lifecycle and can be picked while fruiting, which kills the whole stalk.
 * Nothing is ticked: {@link #cycles} counts the growth the elapsed time owes.
 */
public final class BananaRules {
    /** TFC default {@code bananaSaplingGrowthTicks}: 6 default days of player ticks. */
    public static final long SAPLING_GROWTH_TICKS = 6L * BerryRules.PLAYER_TICKS_IN_DEFAULT_DAY;
    /** TFC default {@code bananaPlantGrowthTicks}: 4 default days of player ticks. */
    public static final long PLANT_GROWTH_TICKS = 4L * BerryRules.PLAYER_TICKS_IN_DEFAULT_DAY;
    /** TFC caps the cycles of one tick at 8 and skips straight to 8 after eight months. */
    public static final int MAX_CYCLES = 8;
    /** Stage of a trunk block, of the growing top, and of the finished top block. */
    public static final int STAGE_TRUNK = 0;
    public static final int STAGE_GROWING = 1;
    public static final int STAGE_TOP = 2;
    /** {@code distanceToGround(level, pos, 6)}: how far down the stalk is measured. */
    public static final int GROUND_SCAN = 6;
    /**
     * How long a dead plant stays. TFC's dead plant counter starts at {@code Integer.MIN_VALUE} (nothing resets it), so the 8-day test of
     * {@code randomDestroyTick} always passes and the plant goes on the first successful 1-in-5 random tick: about five random ticks, which
     * at the default random tick speed is about five minutes. Ours is a fixed 6000 ticks since the plant died (divergence in the ledger).
     */
    public static final long DEAD_LINGER_TICKS = 6_000L;
    /** TFC {@code BANANA_STAGES}. */
    private static final Lifecycle[] LIFECYCLE = {DORMANT, DORMANT, HEALTHY, HEALTHY, HEALTHY, HEALTHY, FLOWERING, FLOWERING, FRUITING,
        DORMANT, DORMANT, DORMANT};
    /** TFC {@code banana_tree.json}: min hydration 25, temperature 9.8 to 41.2. */
    public static final ClimateRange CLIMATE = new ClimateRange(25, 100, 0, 9.8f, 41.2f, 0f);

    private BananaRules() {
    }

    /** The lifecycle of the hemispheric month. */
    public static Lifecycle lifecycleFor(Month hemisphericMonth) {
        return LIFECYCLE[hemisphericMonth.ordinal()];
    }

    /** The outcome of a sapling's random tick. */
    public sealed interface SaplingTick permits Waiting, Withered, Grown {
    }

    /** Nothing happens: the month is dormant or the counter has not passed the growth time. */
    public record Waiting() implements SaplingTick {
    }

    /** The climate fails at the stem: the sapling becomes a dead bush. */
    public record Withered() implements SaplingTick {
    }

    /** The sapling becomes a stage-1 healthy plant; {@code carriedTicks} (the ticks past the growth time) go into the plant's counter. */
    public record Grown(long carriedTicks) implements SaplingTick {
    }

    /** TFC {@code FruitTreeSaplingBlock.randomTick} for the banana sapling. */
    public static SaplingTick saplingTick(Month hemisphericMonth, long ticksSinceUpdate, long ticksToGrow, boolean climateOk) {
        if (!lifecycleFor(hemisphericMonth).active() || ticksSinceUpdate <= ticksToGrow) {
            return new Waiting();
        }
        return climateOk ? new Grown(ticksSinceUpdate - ticksToGrow) : new Withered();
    }

    /** TFC {@code BananaPlantBlock.randomTick}: out of climate at the stem kills the stalk. */
    public static boolean killedByClimate(boolean climateOk) {
        return !climateOk;
    }

    /**
     * TFC {@code BananaPlantBlock.tick}: the growth cycles the time since {@code lastUpdate} owes, scanned forward from the counter and then
     * backward from {@code now} after a dormant month. Returns {@code -1} when less than one interval has passed (TFC then leaves the
     * counter alone), and {@link #MAX_CYCLES} after eight months.
     *
     * @param clock anchored at {@code now}
     */
    public static long cycles(long lastUpdate, long now, BerryClock clock, long growthTicks) {
        long since = now - lastUpdate;
        long max = since / growthTicks;
        if (max < 1) {
            return -1;
        }
        int limit = (int) Math.min(max, MAX_CYCLES);
        if (since >= (long) CalendarTicks.CALENDAR_TICKS_IN_DAY * clock.daysInMonth() * 8) {
            return MAX_CYCLES;
        }
        long cycles = 0;
        long simulated = lastUpdate;
        boolean reverse = false;
        while (cycles < limit && simulated < now) {
            if (lifecycleFor(clock.hemisphericMonth(clock.calendarAt(simulated))) != DORMANT) {
                cycles++;
                simulated += growthTicks;
            } else {
                reverse = true;
                break;
            }
        }
        if (reverse) {
            simulated = now;
            while (cycles < limit && simulated > lastUpdate) {
                if (lifecycleFor(clock.hemisphericMonth(clock.calendarAt(simulated))) != DORMANT) {
                    cycles++;
                    simulated -= growthTicks;
                } else {
                    break;
                }
            }
        }
        return cycles;
    }

    /** What a growth step does. */
    public enum Growth {
        /** Nothing: not stage 1, or the block above is not empty and open to the sky. */
        NONE,
        /** The old block becomes stage 0 and a stage-1 block is placed above. */
        UP,
        /** The block becomes stage 2. */
        TOP
    }

    /**
     * TFC {@code BananaPlantBlock.grow}: only stage 1 grows. When the block above is empty and sees the sky, the stalk grows while its height
     * above the ground is below a fresh draw of {@code nextInt(3, 6)}, and otherwise ends in a top block. The draw is read only then, as TFC.
     *
     * @param distanceToGround {@code min(blocks down to the first non-plant block, 6)}
     * @param draw             TFC's {@code random.nextInt(3, 6)}: 3 to 5
     */
    public static Growth grow(int stage, boolean aboveEmpty, boolean aboveSeesSky, int distanceToGround, IntSupplier draw) {
        if (stage != STAGE_GROWING || !aboveEmpty || !aboveSeesSky) {
            return Growth.NONE;
        }
        return distanceToGround < draw.getAsInt() ? Growth.UP : Growth.TOP;
    }

    /**
     * The cycles banked into the new top block. TFC passes {@code cycles - 1} to {@code grow}, which subtracts one more, so the new block is
     * credited {@code cycles - 2} growth intervals (-1 puts its counter one interval into the future).
     */
    public static int bankedCycles(long cycles) {
        return (int) cycles - 2;
    }

    /** TFC {@code BananaPlantBlock.canSurvive}: on a bush-plantable block or on a block of the same kind (plant on plant, dead on dead). */
    public static boolean survives(boolean belowPlantable, boolean belowSameKind) {
        return belowPlantable || belowSameKind;
    }

    /** TFC {@code BananaSaplingBlock.canSurvive}: only a bush-plantable block. */
    public static boolean saplingSurvives(boolean belowPlantable) {
        return belowPlantable;
    }

    /** TFC {@code getProductItem}: {@code Mth.nextInt(random, 3, 6)}, i.e. 3 to 6 bananas; {@code draw} is {@code nextInt(4)}. */
    public static int productCount(int draw) {
        return 3 + draw;
    }

    /** TFC {@code DeadBananaPlantBlock.randomTick}: a dead plant is removed once it has lingered {@link #DEAD_LINGER_TICKS}. */
    public static boolean deadExpired(long ticksSinceUpdate) {
        return ticksSinceUpdate > DEAD_LINGER_TICKS;
    }

    /**
     * TFC {@code onUpdate}/{@code checkAndSetDormant}: the lifecycle step of one block, as every seasonal plant.
     */
    public static Lifecycle update(Lifecycle stored, Month hemisphericMonth, boolean climateOk, long now, long lastPickedTick,
                                   long pickBloomDelayTicks) {
        return BerryRules.advanceLifecycle(stored, lifecycleFor(hemisphericMonth), climateOk, now, lastPickedTick, pickBloomDelayTicks);
    }
}
