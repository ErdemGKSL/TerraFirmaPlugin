/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (lastUpdateTick),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (tick)
 */
package dev.tfp.core.drying;

import dev.tfp.core.persist.VersionedCodec;

/**
 * What a drying pile keeps between accesses: TFC's {@code TickCounterBlockEntity} counter ({@code lastUpdateTick}), the tick the
 * pile dried, and the rain clock (the rain seen at the last settle). The brick count and the dried flag are the block state;
 * the dried tick is kept so a pile that dried while nobody looked shows when.
 *
 * <p>Rain is sampled, not replayed: nothing ticks, so the weather is read when the pile is settled (placement, a click, chunk
 * load, the clock). {@link #settle} treats rain at the previous settle and rain now as the only rain ticks of the gap (ledger
 * divergence: a shower that starts and ends between two settles is missed).
 *
 * @param count          the bricks in the pile, 1..4, mirrored from the block state so a removal can drop them
 * @param lastUpdateTick the calendar tick of the last reset, or {@link DryingRules#UNSET_TICK} before the first settle
 * @param driedTick      the calendar tick the pile dried, or {@link DryingRules.Pile#NOT_DRIED}
 * @param rainTick       the tick of the last settle (when {@code rainSeen} was read), or {@link DryingRules#UNSET_TICK}
 * @param rainSeen       whether it rained on the pile at {@code rainTick}
 */
public record DryingState(int count, long lastUpdateTick, long driedTick, long rainTick, boolean rainSeen) {
    /** A pile that was never stored (placed by something other than a player): stamped at its first settle. */
    public static final DryingState FRESH = new DryingState(1, DryingRules.UNSET_TICK, DryingRules.Pile.NOT_DRIED,
        DryingRules.UNSET_TICK, false);

    /** TFC keeps {@code lastUpdateTick} in the block entity NBT. */
    public static final VersionedCodec<DryingState> CODEC = VersionedCodec.<DryingState>builder("drying_bricks", 1)
        .build((s, out) -> out.putInt("count", s.count()).putLong("lastUpdateTick", s.lastUpdateTick()).putLong("driedTick", s.driedTick())
                .putLong("rainTick", s.rainTick()).putBoolean("rainSeen", s.rainSeen()),
            in -> new DryingState(in.getInt("count", 1), in.getLong("lastUpdateTick", DryingRules.UNSET_TICK),
                in.getLong("driedTick", DryingRules.Pile.NOT_DRIED), in.getLong("rainTick", DryingRules.UNSET_TICK),
                in.getBoolean("rainSeen", false)));

    public DryingState {
        if (count < 1 || count > DryingRules.MAX_COUNT) {
            throw new IllegalArgumentException("count must be 1.." + DryingRules.MAX_COUNT + ": " + count);
        }
    }

    /** One brick placed at {@code tick} ({@code TickCounterBlockEntity.reset}): not dry, rain clock at {@code tick}. */
    public static DryingState reset(long tick, boolean raining) {
        return new DryingState(1, tick, DryingRules.Pile.NOT_DRIED, tick, raining);
    }

    /** The pile after a brick was added at {@code tick}: one more brick, the counter reset. */
    public DryingState added(long tick, boolean raining) {
        return new DryingState(Math.min(DryingRules.MAX_COUNT, count + 1), tick, DryingRules.Pile.NOT_DRIED, tick, raining);
    }

    /** The pile with a brick taken: one less, the counter kept (TFC does not reset on removal). Count 1 cannot go lower. */
    public DryingState taken() {
        return new DryingState(Math.max(1, count - 1), lastUpdateTick, driedTick, rainTick, rainSeen);
    }

    public DryingState withCount(int n) {
        return new DryingState(n, lastUpdateTick, driedTick, rainTick, rainSeen);
    }

    public boolean dried() {
        return driedTick != DryingRules.Pile.NOT_DRIED;
    }

    public boolean unset() {
        return lastUpdateTick == DryingRules.UNSET_TICK;
    }

    /** The rule's view of this state. An unset one is read as placed at {@code now}. */
    DryingRules.Pile pile(long now) {
        return unset() ? DryingRules.Pile.placed(now) : new DryingRules.Pile(lastUpdateTick, driedTick);
    }

    /**
     * The state at {@code now}, with {@code raining} read now: {@link DryingRules#advance} over the rain ticks of the gap (the
     * previous settle if it rained then, now if it rains now). A calendar set back ({@code now} before the last reset) re-stamps
     * the counter at {@code now} instead of drying. A dry pile stays as it is (it takes no more rain).
     *
     * @param thresholdTicks {@code mudBricksTicks} or {@link DryingRules#DISABLED}
     */
    public DryingState settle(long now, int thresholdTicks, boolean raining) {
        if (dried()) {
            return this;
        }
        if (unset() || now < lastUpdateTick || (rainTick != DryingRules.UNSET_TICK && now < rainTick)) {
            return new DryingState(count, now, DryingRules.Pile.NOT_DRIED, now, raining);
        }
        long[] rain;
        if (rainSeen && raining && rainTick < now) {
            rain = new long[] {rainTick, now};
        } else if (rainSeen) {
            rain = new long[] {rainTick};
        } else if (raining) {
            rain = new long[] {now};
        } else {
            rain = new long[0];
        }
        DryingRules.Pile next = DryingRules.advance(pile(now), now, thresholdTicks, rain);
        return new DryingState(count, next.lastUpdateTick(), next.driedTick(), now, raining);
    }

    /**
     * The calendar tick the pile dries at if it does not rain until then, or {@link DryingRules.Pile#NOT_DRIED} when drying is
     * disabled or the pile is dry already. The clock evaluates the pile at that tick.
     */
    public long dueTick(int thresholdTicks) {
        if (dried() || thresholdTicks == DryingRules.DISABLED || unset()) {
            return DryingRules.Pile.NOT_DRIED;
        }
        return lastUpdateTick + thresholdTicks + 1;
    }

    /** How far the drying has got, 0..1, at {@code now}. */
    public double progress(long now, int thresholdTicks) {
        return DryingRules.progress(pile(now), now, thresholdTicks);
    }
}
