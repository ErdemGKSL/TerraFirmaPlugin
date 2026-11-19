/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/StationaryBerryBushBlock.java @ c3b4d9536 (randomTick, growAndPropagate,
 *       placeBlockAndResetCounter, getNewState),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBerryBushBlock.java @ c3b4d9536 (randomTick: 80 days)
 */
package dev.tfp.core.berry;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * The glue between {@link BerryRules} and a stored {@link BerryState}: one pure step that brings a stored bush up to a tick, and TFC's
 * propagation draw. No world access; the Paper side supplies the world checks as predicates.
 */
public final class BerryTicker {
    /** TFC: a propagation stops when more than this many bushes (the bush itself included) are within the box. */
    public static final int CROWDED_ABOVE = 3;
    /** TFC: tries of one propagation. */
    public static final int TRIES = 3;
    /** A cap on the child-of-a-child cascade of one catch-up (TFC's budget of growths and cycles ends it long before). */
    public static final int MAX_CASCADE = 8;

    private BerryTicker() {
    }

    /**
     * The result of {@link #settle}.
     *
     * @param state        the state to store
     * @param propagations the propagation attempts of the span, in order (empty for a dead bush)
     * @param remove       whether the block is removed (a dead bush after its wither tick)
     */
    public record Settled(BerryState state, List<BerryRules.Propagation> propagations, boolean remove) {
        public Settled {
            propagations = List.copyOf(propagations);
        }
    }

    /** A propagation child: where, and its state before TFC's immediate {@code tick}. */
    public record Child(int dx, int dy, int dz, BerryBush bush) {
    }

    /**
     * Brings the stored bush up to {@code now}: a living bush through {@link BerryRules#catchUp} with the stored sample, a dead one to its
     * removal. {@code clock} must be anchored at {@code now}.
     */
    public static Settled settle(BushSpecies species, BerryState state, long now, BerryClock clock, BerryRules.Settings settings) {
        if (state.dead()) {
            // TFC's dead bush counter starts at Integer.MIN_VALUE, so its 80 day gate always passes (ledger): any catch-up after the wither tick removes it.
            boolean old = new BerryRules.DeadBush(state.bush().stage(), state.witherTick()).removableAt(now);
            return new Settled(state, List.of(), old);
        }
        BerryRules.Outcome outcome = BerryRules.catchUp(species, state.bush(), now, clock, state.climate(), settings);
        return switch (outcome) {
            case BerryRules.Alive a -> new Settled(state.withBush(a.bush()), a.propagations(), false);
            case BerryRules.Withered w -> {
                BerryBush b = state.bush();
                BerryBush kept = new BerryBush(Lifecycle.DORMANT, w.dead().stage(), b.growthsRemaining(), b.lastUpdateTick(), b.lastPickedTick());
                yield new Settled(state.withered(w.dead().witherTick(), kept), w.propagations(), false);
            }
        };
    }

    /**
     * TFC {@code growAndPropagate}'s placement: up to {@value #TRIES} tries, each a triangle offset (x 3, y 1, z 3) from the bush; the first
     * target {@code canPlace} accepts gets a new stage 0 bush with the banked cycles and {@code growthsBeforeDraw - nextInt(1, 3)} growths.
     * The caller has checked the crowding ({@link #crowded}). Draws in TFC's order.
     */
    public static Optional<Child> propagate(RandomGenerator random, BerryRules.Propagation propagation, long now, BerryRules.Settings settings,
                                            Predicate<int[]> canPlace) {
        for (int tries = 0; tries < TRIES; tries++) {
            int dx = triangle(random, 3);
            int dy = triangle(random, 1);
            int dz = triangle(random, 3);
            if (canPlace.test(new int[] {dx, dy, dz})) {
                // TFC may store a negative count; a count <= 0 stops growth like 0 does, and BerryBush needs >= 0.
                int growths = Math.max(0, propagation.growthsBeforeDraw() - (1 + random.nextInt(2)));
                BerryBush child = new BerryBush(Lifecycle.HEALTHY, 0, growths, now - settings.growthTicks() * propagation.bankedCycles(),
                    BerryState.NEVER_PICKED);
                return Optional.of(new Child(dx, dy, dz, child));
            }
        }
        return Optional.empty();
    }

    /** TFC: more than {@value #CROWDED_ABOVE} bushes of the species in the box of radius (2, 1, 2), the bush itself included. */
    public static boolean crowded(int bushesInBox) {
        return bushesInBox > CROWDED_ABOVE;
    }

    /** {@code Helpers.triangle(random, range)}. */
    static int triangle(RandomGenerator random, int range) {
        return random.nextInt(range) - random.nextInt(range);
    }
}
