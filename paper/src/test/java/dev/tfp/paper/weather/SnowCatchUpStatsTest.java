/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The catch-up counters of the PAR-CLIM-03 first-load cost check. */
class SnowCatchUpStatsTest {
    @Test
    void aQuietPeriodLogsNothing() {
        SnowCatchUpStats stats = new SnowCatchUpStats();
        assertThat(stats.drain()).isNull();
    }

    @Test
    void theLineSumsLoadsPlansAndHops() {
        SnowCatchUpStats stats = new SnowCatchUpStats();
        stats.load(20_000);
        stats.load(40_000);
        stats.due.increment();
        stats.plan(300_000, 64, 0);
        stats.hop(50_000);
        stats.hop(150_000);
        assertThat(stats.drain()).isEqualTo("loads=2 due=1 plansWithSteps=1 accumulateSteps=64 meltSteps=0"
                + " loadAvgUs=30 loadMaxUs=40 planAvgUs=300 planMaxUs=300 hops=2 hopAvgUs=100 hopMaxUs=150");
        assertThat(stats.drain()).as("nothing new since the last line").isNull();
        stats.load(10_000);
        assertThat(stats.drain()).startsWith("loads=3 ");
    }

    @Test
    void anEmptyPlanIsNotAPlanWithSteps() {
        SnowCatchUpStats stats = new SnowCatchUpStats();
        stats.load(1_000);
        stats.due.increment();
        stats.plan(1_000, 0, 0);
        assertThat(stats.drain()).contains("plansWithSteps=0").contains("hopAvgUs=0");
    }

    @Test
    void theLogIsOffUnlessAsked() {
        assertThat(System.getProperty(SnowCatchUpStats.PROPERTY)).isNull();
        assertThat(SnowCatchUpStats.enabled()).isFalse();
    }
}
