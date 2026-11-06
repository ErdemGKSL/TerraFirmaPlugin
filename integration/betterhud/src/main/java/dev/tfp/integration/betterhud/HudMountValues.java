/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.integration.betterhud;

import dev.tfp.paper.player.MountHudService;
import dev.tfp.paper.player.MountHudService.MountReading;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * The mount health placeholders (PAR-HUD-09, TFC {@code IngameOverlays.MOUNT_HEALTH}). They read only the rider's
 * published {@link MountReading} ({@link MountHudService#reading}), never the world, so they are safe on BetterHud's
 * async timer on Folia.
 * <ul>
 *   <li>{@code tfp_mount_health}: the ridden living entity's health on TFC's text scale (x 50), 0 while not riding one;
 *       also a listener, 0..1.</li>
 *   <li>{@code tfp_mount_max_health}: its max health (x 50), 0 while not riding one: the mount bar shows only while it is
 *       above 0.</li>
 * </ul>
 */
final class HudMountValues {
    private HudMountValues() {
    }

    /** Number placeholders by name, as used in our generated BetterHud YAML. */
    static final Map<String, ToDoubleFunction<MountReading>> NUMBERS;

    /** Bar listeners, 0..1. */
    static final Map<String, ToDoubleFunction<MountReading>> LISTENERS;

    /** The reading of a player (the published one in production). */
    static final Function<UUID, MountReading> READINGS = MountHudService::reading;

    static {
        Map<String, ToDoubleFunction<MountReading>> n = new LinkedHashMap<>();
        n.put("tfp_mount_health", MountReading::health);
        n.put("tfp_mount_max_health", MountReading::maxHealth);
        NUMBERS = Collections.unmodifiableMap(n);

        Map<String, ToDoubleFunction<MountReading>> l = new LinkedHashMap<>();
        l.put("tfp_mount_health", r -> HudValues.ratio(r.health(), r.maxHealth()));
        LISTENERS = Collections.unmodifiableMap(l);
    }
}
