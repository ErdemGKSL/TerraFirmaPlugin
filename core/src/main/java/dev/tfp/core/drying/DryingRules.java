/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (tick, useItemOn, canBeReplaced,
 *       setPlacedBy, isRandomlyTicking),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (getTicksSinceUpdate,
 *       resetCounter, the default lastUpdateTick),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (mudBricksTicks)
 */
package dev.tfp.core.drying;

/**
 * The drying rule of a brick or thatch pile: a pile of mud bricks or drying bricks dries once it has gone more than
 * {@code mudBricksTicks} calendar ticks without a reset. Placing it, adding a brick to it, or rain on it while it is not dry
 * resets the counter. Removing a brick does not reset it, and a dry pile cannot take bricks or reset.
 *
 * <p><b>Lazy form.</b> {@link #advance} is the closed form of TFC's {@code tick}, evaluated on demand from the last reset
 * and the rain ticks since then. It never ticks. For a pile reset at {@code L} with no rain, the dried tick is
 * {@code L + threshold + 1}: TFC's test is {@code ticksSinceUpdate > threshold}, checked at each tick. A rain tick at or before
 * the dried tick resets the counter first (TFC checks rain before the timer), so it moves the reset to that tick and the
 * dried tick moves with it. Once dry, the pile stays dry.
 *
 * <p><b>Climate.</b> The only climate condition TFC uses is {@code level.isRainingAt(pos.above())}, a value at a tick. The
 * caller passes the rain ticks as a sorted array of calendar ticks, so the rule stays pure. The glue reads the weather.
 *
 * <p><b>Ledger.</b> TFC evaluates {@code tick} on random ticks, so the real dried tick is quantized to the random tick
 * sampling. This port is the every-tick limit, as in the other lazy rules. The neighbour cascade (a dried pile schedules a
 * tick for adjacent piles) only changes when a neighbour that is already past its threshold is checked, so it has no effect
 * in this form.
 *
 * <p>Ticks are calendar ticks ({@code Calendars.get(level).getTicks()}), not player ticks.
 */
public final class DryingRules {
    /** {@code mudBricksTicks} default: 28 800 ticks, 24 in-game hours. */
    public static final int DEFAULT_THRESHOLD_TICKS = 28_800;
    /** {@code mudBricksTicks = -1}: drying is disabled, the pile never dries. */
    public static final int DISABLED = -1;
    /** Brick count limit of a drying pile ({@code COUNT_1_4}). */
    public static final int MAX_COUNT = 4;
    /** TFC's default {@code lastUpdateTick} before any reset ({@code Integer.MIN_VALUE}). */
    public static final long UNSET_TICK = Integer.MIN_VALUE;

    private DryingRules() {
    }

    /**
     * The counter of one pile.
     *
     * @param lastUpdateTick the calendar tick of the last reset (placement, brick added, or rain while not dry)
     * @param driedTick      the calendar tick the pile dried, or {@link #NOT_DRIED}
     */
    public record Pile(long lastUpdateTick, long driedTick) {
        /** Marks a pile that has not dried yet. */
        public static final long NOT_DRIED = Long.MAX_VALUE;

        public Pile {
            if (driedTick != NOT_DRIED && driedTick < lastUpdateTick) {
                throw new IllegalArgumentException("dried before its last reset: " + driedTick + " < " + lastUpdateTick);
            }
        }

        /** A pile placed (reset) at {@code tick}. */
        public static Pile placed(long tick) {
            return new Pile(tick, NOT_DRIED);
        }

        public boolean dried() {
            return driedTick != NOT_DRIED;
        }
    }

    /** Whether a brick can be added to a pile of {@code count} bricks: not dry, and below {@link #MAX_COUNT}. */
    public static boolean canAdd(Pile pile, int count) {
        return !pile.dried() && count < MAX_COUNT;
    }

    /**
     * A reset at {@code tick} (a brick placed or added, or the pile placed). A dry pile is returned unchanged, as TFC does
     * not allow adding to it. The caller advances the pile to {@code tick} first, so a dry tick at {@code tick} wins.
     */
    public static Pile reset(Pile pile, long tick) {
        if (pile.dried()) {
            return pile;
        }
        return new Pile(tick, Pile.NOT_DRIED);
    }

    /**
     * The pile at calendar tick {@code now}, given the rain ticks since its last check.
     *
     * <p>{@code rainTicks} is sorted ascending. It holds every tick it rained on the pile after the pile's last check, up to
     * {@code now}. Rain ticks at or before {@code lastUpdateTick} are ignored, so passing all rain from an earlier point is
     * fine. A rain tick resets the counter to that tick. Between resets, the first tick with
     * {@code tick - last > threshold} dries the pile, unless it rained on that tick.
     *
     * @param pile          the pile at its last check
     * @param now           the calendar tick to evaluate at
     * @param thresholdTicks {@code mudBricksTicks}, or {@link #DISABLED}
     * @param rainTicks     sorted calendar ticks on which it rained on the pile
     * @return the pile at {@code now}, with {@code driedTick} set when it has dried by {@code now}
     */
    public static Pile advance(Pile pile, long now, int thresholdTicks, long[] rainTicks) {
        if (thresholdTicks < DISABLED) {
            throw new IllegalArgumentException("thresholdTicks must be -1 or more: " + thresholdTicks);
        }
        if (pile.dried()) {
            return pile;
        }
        for (int i = 1; i < rainTicks.length; i++) {
            if (rainTicks[i] < rainTicks[i - 1]) {
                throw new IllegalArgumentException("rainTicks must be sorted ascending");
            }
        }
        long last = pile.lastUpdateTick();
        for (long rain : rainTicks) {
            if (rain > now) {
                break;
            }
            if (rain <= last) {
                continue;
            }
            if (thresholdTicks != DISABLED) {
                long dry = last + thresholdTicks + 1;
                if (dry < rain) {
                    return new Pile(last, dry);
                }
            }
            last = rain;
        }
        if (thresholdTicks != DISABLED) {
            long dry = last + thresholdTicks + 1;
            if (dry <= now) {
                return new Pile(last, dry);
            }
        }
        return new Pile(last, Pile.NOT_DRIED);
    }

    /**
     * How far the drying has got, from 0 to 1, for a pile advanced to {@code now}. It is 1 exactly when the pile has dried,
     * and 0 when drying is disabled. Before that it is the share of {@code threshold + 1} ticks since the last reset.
     */
    public static double progress(Pile pile, long now, int thresholdTicks) {
        if (pile.dried()) {
            return 1.0;
        }
        if (thresholdTicks == DISABLED) {
            return 0.0;
        }
        double elapsed = Math.max(0L, now - pile.lastUpdateTick());
        return Math.min(1.0, elapsed / (thresholdTicks + 1.0));
    }
}
