/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.core.climate.ClimateFormat;
import dev.tfp.paper.integration.HudSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The climate text placeholders (M2-CLIM-03). They read only the player's {@link HudSnapshot} (the value the climate
 * service publishes from the player's thread), never the world, so they are safe on BetterHud's async timer on Folia.
 * Shown as a dash until the first reading.
 */
final class HudClimateValues {
    private HudClimateValues() {
    }

    /** Text placeholders by name, as used in our generated BetterHud YAML ({@code [tfp_ambient_temp]}). */
    static final Map<String, Function<HudSnapshot, String>> TEXTS;

    static {
        Map<String, Function<HudSnapshot, String>> t = new LinkedHashMap<>();
        t.put("tfp_ambient_temp", s -> ClimateFormat.temperature(s.ambientTemperature()));
        TEXTS = Collections.unmodifiableMap(t);
    }
}
