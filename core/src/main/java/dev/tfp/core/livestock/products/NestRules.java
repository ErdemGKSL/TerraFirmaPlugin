/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/NestBoxBlockEntity.java (serverTick, the lay branch,
 * the not-ready branch, the full-nest branch) @ c3b4d9536
 */
package dev.tfp.core.livestock.products;

import dev.tfp.core.livestock.AnimalState;
import dev.tfp.core.livestock.LivestockSpecies;

import java.util.Optional;
import java.util.function.LongUnaryOperator;

/**
 * The nest box's egg-laying step as a lazy function. A seated hen is checked on every 30-tick nest check
 * ({@code NestBoxBlockEntity.serverTick}, {@code gameTime % 30 == 0}). Between checks nothing changes, so
 * {@link #advance} walks only the checks from the seating tick to {@code now}, and stops at the first event.
 *
 * <p><b>Per check, TFC's order:</b> if the hen is not ready she is unseated ({@code Left}). If she is ready, she lays
 * with probability 1/7 ({@code nextInt(7) == 0}). A successful roll into a full nest unseats her without laying. Otherwise
 * the egg is made, and the hen is no longer fertilized and starts her cooldown at the check tick.
 *
 * <p><b>Plain eggs.</b> TFC's nest box rejects an egg without the {@code EggComponent} ({@code isItemValid}), so a plain
 * egg is not put in a slot. It is dropped at the nest, and the slot is not used. This is {@code Laid} with an empty
 * {@code nestEgg}.
 *
 * <p><b>Clock.</b> The check grid is taken over the player-tick unit, as {@link dev.tfp.core.livestock.LivestockRules}
 * does for familiarity. TFC's grid is game time, so the phase differs by at most one check period. See the ledger.
 */
public final class NestRules {
    /** TFC {@code NestBoxBlockEntity.SLOTS}. */
    public static final int SLOTS = 4;
    /** TFC nest check interval: {@code level.getGameTime() % 30 == 0}. */
    public static final long CHECK_INTERVAL = 30L;
    /** TFC {@code bird.getRandom().nextInt(7) == 0}: a ready hen lays on a 1 in 7 roll per check. */
    public static final int LAY_ROLL_BOUND = 7;

    /**
     * The random source the nest draws from: the hen's random in TFC. {@link #nextInt} is the lay roll, and
     * {@link #nextBoolean} is the chick's gender, drawn only for a fertilized egg. Both are drawn in TFC's order.
     */
    public interface NestRandom {
        int nextInt(int bound);

        boolean nextBoolean();
    }

    /** The result of {@link #advance}. */
    public sealed interface Outcome permits Laid, Left, StillSeated {
    }

    /**
     * The hen laid an egg at {@code tick}.
     *
     * @param nestEgg the fertile egg in a slot, or empty for a plain egg, which the nest drops ({@link NestRules})
     * @param hen     the hen after the lay: caught up, fertilized cleared, uses +1
     * @param product the product state with the cooldown started at {@code tick}
     */
    public record Laid(long tick, Optional<EggRules.FertileEgg> nestEgg, AnimalState hen, ProductState product) implements Outcome {
    }

    /**
     * The hen was unseated at {@code tick} without laying: she was not ready, or she rolled a lay into a full nest.
     */
    public record Left(long tick) implements Outcome {
    }

    /** The hen is still seated at {@code now}: no check through {@code now} produced an event. */
    public record StillSeated() implements Outcome {
    }

    private NestRules() {
    }

    /** The first nest check at or after {@code tick}. */
    public static long firstCheckAtOrAfter(long tick) {
        return -Math.floorDiv(-tick, CHECK_INTERVAL) * CHECK_INTERVAL;
    }

    /**
     * Walks the nest checks from {@code seatedTick} to {@code now} and returns the first event.
     *
     * <p>Only checks at or after {@code seatedTick} count. The hen's stored state is caught up on each read, so the
     * readiness at a check is the lazy readiness at that check tick.
     *
     * @param hen              the hen as stored at seating time, caught up or not
     * @param product          the hen's product state as stored
     * @param seatedTick       the player tick the hen sat down
     * @param now              the player tick of this read
     * @param slotOpen         whether a slot is free for the whole wait (the glue must check this at seating; a slot that
     *                         fills or empties during the wait is not seen)
     * @param random           the nest random, shared with the hen in TFC
     * @param calendarTicksAt  maps a player tick to the calendar tick, for the hatch day of a lay
     */
    public static Outcome advance(ProductSpec spec, LivestockSpecies species, AnimalState hen, ProductState product,
                                  long seatedTick, long now, boolean slotOpen, NestRandom random,
                                  LongUnaryOperator calendarTicksAt) {
        for (long t = firstCheckAtOrAfter(seatedTick); t <= now; t += CHECK_INTERVAL) {
            if (!ProductRules.isReady(spec, species, hen, product, t)) {
                return new Left(t);
            }
            if (random.nextInt(LAY_ROLL_BOUND) != 0) {
                continue;
            }
            if (!slotOpen) {
                return new Left(t);
            }
            final boolean chickMale = hen.fertilized() && random.nextBoolean();
            final EggRules.Laying laying = EggRules.makeEgg(species, hen, t, calendarTicksAt.applyAsLong(t), chickMale);
            final AnimalState after = laying.hen().withFertilized(false);
            final Optional<EggRules.FertileEgg> nestEgg = laying.egg();
            return new Laid(t, nestEgg, after, new ProductState(t));
        }
        return new StillSeated();
    }
}
