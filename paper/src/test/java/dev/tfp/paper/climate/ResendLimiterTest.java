/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.climate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ResendLimiterTest {
    @Test
    void handsOutAtMostTheBudgetPerTickOldestFirst() {
        ResendLimiter<String> limiter = new ResendLimiter<>(2, 10);
        for (String k : new String[] {"a", "b", "c", "d", "e"}) {
            assertThat(limiter.offer(k, 0)).isTrue();
        }
        assertThat(limiter.drain(1)).containsExactly("a", "b");
        assertThat(limiter.drain(2)).containsExactly("c", "d");
        assertThat(limiter.drain(3)).containsExactly("e");
        assertThat(limiter.drain(4)).isEmpty();
        assertThat(limiter.pending()).isZero();
    }

    @Test
    void aQueuedKeyIsQueuedOnce() {
        ResendLimiter<String> limiter = new ResendLimiter<>(8, 10);
        assertThat(limiter.offer("a", 0)).isTrue();
        assertThat(limiter.offer("a", 0)).isFalse();
        assertThat(limiter.pending()).isEqualTo(1);
        assertThat(limiter.drain(0)).containsExactly("a");
    }

    @Test
    void aResentKeyWaitsForItsCooldown() {
        ResendLimiter<String> limiter = new ResendLimiter<>(8, 100);
        limiter.offer("a", 0);
        assertThat(limiter.drain(5)).containsExactly("a");
        assertThat(limiter.offer("a", 6)).isFalse();
        assertThat(limiter.offer("a", 104)).isFalse();
        assertThat(limiter.offer("a", 105)).isTrue();
        assertThat(limiter.drain(105)).containsExactly("a");
    }

    @Test
    void aSeasonChangeLiftsTheCooldowns() {
        ResendLimiter<String> limiter = new ResendLimiter<>(8, 100);
        limiter.offer("a", 0);
        limiter.drain(0);
        limiter.clearCooldowns();
        assertThat(limiter.offer("a", 1)).isTrue();
    }

    @Test
    void sustainedLoadIsSpreadOverTicks() {
        ResendLimiter<Integer> limiter = new ResendLimiter<>(8, 100);
        for (int i = 0; i < 441; i++) { // a 21 x 21 view
            limiter.offer(i, 0);
        }
        int ticks = 0;
        int sent = 0;
        while (limiter.pending() > 0) {
            int n = limiter.drain(++ticks).size();
            assertThat(n).isLessThanOrEqualTo(8);
            sent += n;
        }
        assertThat(sent).isEqualTo(441);
        assertThat(ticks).isEqualTo(56);
    }

    @Test
    void rejectsABadBudget() {
        assertThatThrownBy(() -> new ResendLimiter<String>(0, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
