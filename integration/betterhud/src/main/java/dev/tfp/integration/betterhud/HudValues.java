/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.core.hud.HudGauge;
import dev.tfp.paper.integration.HudSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * What our BetterHud placeholders and listeners read from a {@link HudSnapshot}. Pure field reads: they
 * run on BetterHud's async timer ~20×/s per player and must never touch the world (on Folia that throws).
 * Names are the ids used in our generated BetterHud YAML ({@code [tfp_health]}, {@code class: tfp_thirst}).
 */
final class HudValues {
    private HudValues() {
    }

    /**
     * Number placeholders. Thirst and hunger are published by {@code PlayerNeedsHudService}; the placeholders read the
     * player's latest snapshot and fall back to {@link HudSnapshot#DEFAULT} before the first publish. Health and max
     * health are published by {@code PlayerNeedsHudService} on TFC's scale. {@code tfp_held_temp} has no stored item
     * heat yet (0 via the snapshot).
     */
    static final Map<String, ToDoubleFunction<HudSnapshot>> NUMBERS;

    /** Bar listeners, 0..1. */
    static final Map<String, ToDoubleFunction<HudSnapshot>> LISTENERS;

    static {
        Map<String, ToDoubleFunction<HudSnapshot>> n = new LinkedHashMap<>();
        n.put("tfp_health", HudSnapshot::health);
        n.put("tfp_max_health", HudSnapshot::maxHealth);
        n.put("tfp_thirst", HudSnapshot::thirst);
        n.put("tfp_hunger", HudSnapshot::hunger);
        n.put("tfp_held_temp", HudSnapshot::heldTemperature);
        NUMBERS = Collections.unmodifiableMap(n);

        Map<String, ToDoubleFunction<HudSnapshot>> l = new LinkedHashMap<>();
        l.put("tfp_health", s -> ratio(s.health(), s.maxHealth()));
        l.put("tfp_thirst", s -> ratio(s.thirst(), HudSnapshot.MAX_THIRST));
        l.put("tfp_hunger", s -> ratio(s.hunger(), HudSnapshot.MAX_HUNGER));
        LISTENERS = Collections.unmodifiableMap(l);
    }

    static double ratio(double value, double max) {
        return HudGauge.fraction(value, max);
    }
}
