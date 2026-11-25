/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.paper.hud.InkOverlayService;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

/**
 * The effect overlay placeholders (PAR-HUD-20, TFC {@code IngameOverlays.INK}). They read only the player's published
 * {@link InkOverlayService#reading}, never the world, so they are safe on BetterHud's async timer on Folia.
 * <ul>
 *   <li>{@code tfp_ink}: 0 nothing, 1 ink splatter, 2 glow ink splatter; 0 in spectator.</li>
 * </ul>
 */
final class HudEffectValues {
    private HudEffectValues() {
    }

    /** Number placeholders by name, as used in our generated BetterHud YAML. */
    static final Map<String, ToIntFunction<UUID>> NUMBERS;

    static {
        Map<String, ToIntFunction<UUID>> n = new LinkedHashMap<>();
        n.put("tfp_ink", InkOverlayService::reading);
        NUMBERS = Collections.unmodifiableMap(n);
    }
}
