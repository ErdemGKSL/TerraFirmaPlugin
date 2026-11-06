/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import static dev.tfp.paper.rendered.MiningTime.BRONZE_SPEED;
import static dev.tfp.paper.rendered.MiningTime.METAL_BLOCK_HARDNESS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * PAR-INFRA-32: elapsed-tick progress. A model of CraftEngine 26.9.2's dig loop ({@code BukkitServerPlayer}:
 * {@code onSwingHand} sets the acknowledgement and the swing tick; {@code tick} advances the progress when
 * {@code ack && lastSwingTick + 2 > gameTicks} and clears the acknowledgement) is fed client swing patterns, with and
 * without {@link MiningSession} acknowledging once per server tick. With it the break takes TFC's formula ticks for
 * every client rate; without it a slow or bunching client loses ticks (the PAR-INFRA-17 measurement).
 */
class ServerTimeMiningSimulationTest {
    /** The CraftEngine loop as read from its bytecode. */
    static final class CeDig {
        final float perTick;
        boolean ack;
        int lastSwingTick;
        int gameTicks;
        float progress;

        CeDig(float perTick) {
            this.perTick = perTick;
        }

        void onSwingHand() {
            ack = true;
            lastSwingTick = gameTicks;
        }

        /** Returns true when the block breaks this tick. */
        boolean tick(int serverTick) {
            gameTicks = serverTick;
            if (ack && lastSwingTick + 2 > gameTicks) {
                progress += perTick;
                ack = false;
                return progress >= 1f;
            }
            return false; // the acknowledgement is cleared only when it counted
        }
    }

    /**
     * Server ticks from the dig start to the break. {@code swingsInTick} gives the number of client swing packets
     * handled in each server tick; with {@code driven} our task runs before ({@code driverFirst}) or after CE's tick.
     */
    static int breakTicks(float perTick, IntUnaryOperator swingsInTick, boolean driven, boolean driverFirst) {
        CeDig ce = new CeDig(perTick);
        MiningSession session = new MiningSession();
        session.start();
        ce.onSwingHand(); // the dig start swing
        for (int t = 1; t < 10_000; t++) {
            for (int i = 0; i < swingsInTick.applyAsInt(t); i++) {
                ce.onSwingHand();
                session.swing();
            }
            if (driven && driverFirst && session.tick(true, true) == MiningSession.Action.ADVANCE) {
                ce.onSwingHand();
            }
            if (ce.tick(t)) {
                return t;
            }
            if (driven && !driverFirst && session.tick(true, true) == MiningSession.Action.ADVANCE) {
                ce.onSwingHand();
            }
        }
        return -1;
    }

    static final float TOOL = MiningTime.progressPerTick(METAL_BLOCK_HARDNESS, BRONZE_SPEED, true, true);
    static final float HAND = MiningTime.progressPerTick(METAL_BLOCK_HARDNESS, 1f, true, false);

    /** A client at {@code rate} ticks/s: swing packets land on the server ticks its ticks fall into. */
    static IntUnaryOperator clientAt(double rate) {
        return t -> (int) (Math.floor(t * rate / 20.0) - Math.floor((t - 1) * rate / 20.0));
    }

    /** A full-speed client whose packets bunch: two in one tick, none in the next. */
    static final IntUnaryOperator BUNCHED = t -> t % 2 == 0 ? 2 : 0;

    @Test
    void withoutTheDriverTheBreakFollowsTheClientsSwingRate() {
        assertThat(breakTicks(TOOL, clientAt(20), false, false)).isEqualTo(21);
        assertThat(breakTicks(TOOL, clientAt(15), false, false)).isGreaterThan(21);
        assertThat(breakTicks(TOOL, BUNCHED, false, false)).isGreaterThan(21);
        assertThat(breakTicks(HAND, BUNCHED, false, false)).isGreaterThan(500);
    }

    @Test
    void withTheDriverTheBreakTakesTheFormulasServerTicks() {
        for (boolean first : new boolean[] {true, false}) {
            for (IntUnaryOperator client : new IntUnaryOperator[] {clientAt(20), clientAt(15), clientAt(8), clientAt(4), BUNCHED}) {
                assertThat(breakTicks(TOOL, client, true, first)).isEqualTo(MiningTime.ticksToBreak(5f, 7.3f, true, true));
                assertThat(breakTicks(HAND, client, true, first)).isEqualTo(MiningTime.ticksToBreak(5f, 1f, true, false));
            }
        }
    }

    @Test
    void aClientThatStopsSwingingWithoutAnAbortStopsCountingAfterTheGrace() {
        MiningSession s = new MiningSession();
        s.start();
        int advanced = 0;
        for (int t = 1; t <= 30; t++) {
            if (s.tick(true, true) == MiningSession.Action.ADVANCE) {
                advanced++;
            }
        }
        assertThat(advanced).isEqualTo(MiningSession.SWING_GRACE);
    }
}
