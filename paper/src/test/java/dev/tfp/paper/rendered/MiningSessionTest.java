/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import static dev.tfp.paper.rendered.MiningSession.Action.ADVANCE;
import static dev.tfp.paper.rendered.MiningSession.Action.STOP;
import static dev.tfp.paper.rendered.MiningSession.Action.WAIT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-INFRA-32: when one server tick of a dig counts. */
class MiningSessionTest {
    @Test
    void countsEveryTickWhileTheClientDigs() {
        MiningSession s = new MiningSession();
        s.start();
        for (int i = 0; i < 100; i++) {
            if (i % 3 == 0) {
                s.swing();
            }
            assertThat(s.tick(true, true)).isEqualTo(ADVANCE);
        }
    }

    @Test
    void abortAndGameModeStopAtOnce() {
        MiningSession s = new MiningSession();
        s.start();
        s.abort();
        assertThat(s.tick(true, true)).isEqualTo(STOP);
        s.start();
        assertThat(s.tick(false, true)).as("creative or spectator").isEqualTo(STOP);
    }

    @Test
    void waitsForThePluginsDigToStartThenGivesUp() {
        MiningSession s = new MiningSession();
        s.start();
        for (int i = 0; i < MiningSession.NOT_MINING_TIMEOUT; i++) {
            s.swing();
            assertThat(s.tick(true, false)).isEqualTo(WAIT);
        }
        assertThat(s.tick(true, false)).as("a vanilla block: the client breaks it").isEqualTo(STOP);
    }

    @Test
    void aBreakFollowedByTheNextDigKeepsTheTask() {
        MiningSession s = new MiningSession();
        s.start();
        assertThat(s.tick(true, true)).isEqualTo(ADVANCE);
        assertThat(s.tick(true, false)).as("the block broke").isEqualTo(WAIT);
        s.start();
        assertThat(s.tick(true, true)).isEqualTo(ADVANCE);
    }

    @Test
    void noSwingWaitsThenStops() {
        MiningSession s = new MiningSession();
        s.start();
        for (int i = 1; i <= MiningSession.SWING_GRACE; i++) {
            assertThat(s.tick(true, true)).isEqualTo(ADVANCE);
        }
        for (int i = MiningSession.SWING_GRACE + 1; i <= MiningSession.IDLE_TIMEOUT; i++) {
            assertThat(s.tick(true, true)).isEqualTo(WAIT);
        }
        s.swing();
        assertThat(s.tick(true, true)).as("a swing after a hitch resumes").isEqualTo(ADVANCE);
        for (int i = 0; i < MiningSession.IDLE_TIMEOUT; i++) {
            s.tick(true, true);
        }
        assertThat(s.tick(true, true)).isEqualTo(STOP);
    }
}
