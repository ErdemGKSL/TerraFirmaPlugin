/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-INFRA-25: the probe's age relative to an origin (the self-test runs on a calendar far past stage 7). */
class ProbeCatchUpTest {
    @Test
    void ageFollowsTheCalendarFromTheOrigin() {
        long origin = 5_000_000;
        assertThat(ProbeCatchUp.ageAt(origin - 10, origin)).isZero();
        assertThat(ProbeCatchUp.ageAt(origin + 2_999, origin)).isEqualTo(2);
        assertThat(ProbeCatchUp.ageAt(origin + 3_000, origin)).isEqualTo(3);
        assertThat(ProbeCatchUp.ageAt(origin + 99_000, origin)).isEqualTo(7);
        assertThat(ProbeCatchUp.kind(origin).advance().advance(1, origin + 4_500)).isEqualTo(4);
        assertThat(ProbeCatchUp.kind(origin).advance().advance(5, origin + 4_500)).isEqualTo(5); // never goes back
        assertThat(ProbeCatchUp.kind().id()).isEqualTo(ProbeCatchUp.ID);
    }
}
