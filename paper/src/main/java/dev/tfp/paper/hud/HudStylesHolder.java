/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.hud;

import java.util.Objects;

/**
 * The server's current {@link HudStyles} (PAR-HUD-17), published by {@code ConfigService} on every load of config.yml
 * (startup and {@code /tfp reload}) and read by the HUD text placeholders on BetterHud's async timer. One volatile
 * reference to an immutable record: a reader never sees a half-swapped value and never reads the config file.
 * {@link HudStyles#DEFAULTS} until the first load.
 */
public final class HudStylesHolder {
    private static volatile HudStyles current = HudStyles.DEFAULTS;

    private HudStylesHolder() {
    }

    /** The styles the HUD shows now. Any thread. */
    public static HudStyles current() {
        return current;
    }

    /** Swaps the styles; the next placeholder evaluation uses them. Any thread. */
    public static void publish(HudStyles styles) {
        current = Objects.requireNonNull(styles, "styles");
    }
}
