/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.itest.cases;

import dev.tfp.itest.SelfTestCase;
import dev.tfp.itest.SelfTestContext;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.integration.HudPlaceholders;
import dev.tfp.paper.integration.HudSnapshot;
import dev.tfp.paper.integration.HudSnapshotCache;
import java.time.Duration;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code bh.placeholder} (M1-INT-04): {@code tfp_health}, looked up in BetterHud's own number-placeholder
 * registry, evaluates from our per-player {@link HudSnapshot} cache. Evaluated off the tick threads (the
 * self-test thread, like BetterHud's async update timer), so a placeholder that touched the world would
 * trip Folia's thread checks (the run also fails on any such log line). A synthetic player id: no client needed.
 */
public final class BetterHudPlaceholderCase implements SelfTestCase {
    static final String PLACEHOLDER = "tfp_health";

    @Override
    public String name() {
        return "bh.placeholder: tfp_health evaluates from the HUD snapshot off the tick threads (M1-INT-04)";
    }

    @Override
    public Duration timeout() {
        return Duration.ofSeconds(10);
    }

    @Override
    public CompletableFuture<String> run(SelfTestContext ctx) {
        TfpPlugin tfp = ctx.tfp();
        if (!tfp.integrations().activePlugins().contains("BetterHud")) {
            throw new Skip("BetterHud is not installed or its adapter is disabled");
        }
        return CompletableFuture.completedFuture(check(tfp.integrations().hudPlaceholders(), tfp.hudCache()));
    }

    /** The case body over the two interfaces (unit-tested with fakes). */
    static String check(HudPlaceholders placeholders, HudSnapshotCache cache) {
        Set<String> registered = placeholders.registered();
        if (!registered.contains(PLACEHOLDER)) {
            throw new AssertionError("BetterHud has no '" + PLACEHOLDER + "' number placeholder of ours (registered: "
                    + registered + ")");
        }
        UUID player = UUID.randomUUID();
        try {
            cache.put(player, HudSnapshot.DEFAULT.withHealth(37.5, 50));
            expect(placeholders.evaluate(PLACEHOLDER, player), 37.5, "after publishing health 37.5");
            expect(placeholders.evaluate("tfp_max_health", player), 50, "max health");
            cache.put(player, HudSnapshot.DEFAULT.withHealth(12, 50));
            expect(placeholders.evaluate(PLACEHOLDER, player), 12, "after publishing health 12");
        } finally {
            cache.remove(player);
        }
        expect(placeholders.evaluate(PLACEHOLDER, player), HudSnapshot.DEFAULT.health(), "after the player was removed");
        return PLACEHOLDER + " = 37.5 then 12 from the snapshot, default " + HudSnapshot.DEFAULT.health()
                + " without one, on " + Thread.currentThread().getName() + "; registered " + registered;
    }

    private static void expect(OptionalDouble actual, double expected, String when) {
        if (actual.isEmpty() || Double.compare(actual.getAsDouble(), expected) != 0) {
            throw new AssertionError(PLACEHOLDER + " " + when + ": expected " + expected + ", got "
                    + (actual.isEmpty() ? "no placeholder" : actual.getAsDouble()));
        }
    }
}
