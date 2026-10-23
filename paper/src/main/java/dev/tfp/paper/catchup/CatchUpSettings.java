/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.catchup;

import java.util.function.UnaryOperator;

/**
 * Settings of the catch-up service (PAR-INFRA-11).
 *
 * @param sweepPeriodTicks   server ticks between visible-area sweeps; {@code 0} turns the sweep off
 * @param sweepRadiusChunks  chunks around each player that the sweep catches up (Chebyshev radius)
 * @param feedBudgetNanos    region-thread time per tick window for capturing snapshots (and running direct kinds)
 * @param commitBudgetNanos  region-thread time per tick window for writing caught-up states
 */
public record CatchUpSettings(long sweepPeriodTicks, int sweepRadiusChunks, long feedBudgetNanos,
        long commitBudgetNanos) {
    /** Every 600 ticks (30 s), 4 chunks around each player, 1 ms + 1 ms per region per tick. */
    public static final CatchUpSettings DEFAULTS = new CatchUpSettings(600, 4, 1_000_000L, 1_000_000L);

    public CatchUpSettings {
        if (sweepPeriodTicks < 0) {
            throw new IllegalArgumentException("sweepPeriodTicks must be >= 0");
        }
        if (sweepRadiusChunks < 0 || sweepRadiusChunks > 32) {
            throw new IllegalArgumentException("sweepRadiusChunks must be in 0..32");
        }
        if (feedBudgetNanos <= 0 || commitBudgetNanos <= 0) {
            throw new IllegalArgumentException("budgets must be positive");
        }
    }

    public boolean sweepEnabled() {
        return sweepPeriodTicks > 0;
    }

    /**
     * The defaults, overridden by the JVM properties {@code tfp.catchup.sweepPeriodTicks},
     * {@code tfp.catchup.sweepRadiusChunks}, {@code tfp.catchup.feedBudgetMicros} and
     * {@code tfp.catchup.commitBudgetMicros} (the {@code config.yml} keys are PAR-INFRA-25). Bad values keep the default.
     */
    public static CatchUpSettings fromProperties(UnaryOperator<String> properties) {
        CatchUpSettings d = DEFAULTS;
        long period = parse(properties.apply("tfp.catchup.sweepPeriodTicks"), d.sweepPeriodTicks, 0, Long.MAX_VALUE);
        long radius = parse(properties.apply("tfp.catchup.sweepRadiusChunks"), d.sweepRadiusChunks, 0, 32);
        long feed = parse(properties.apply("tfp.catchup.feedBudgetMicros"), d.feedBudgetNanos / 1000, 1, 50_000);
        long commit = parse(properties.apply("tfp.catchup.commitBudgetMicros"), d.commitBudgetNanos / 1000, 1, 50_000);
        return new CatchUpSettings(period, (int) radius, feed * 1000, commit * 1000);
    }

    private static long parse(String raw, long def, long min, long max) {
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            long v = Long.parseLong(raw.trim());
            return v < min || v > max ? def : v;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
