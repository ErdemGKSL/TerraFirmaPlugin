/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * The HUD seen by feature code: publish a {@link HudSnapshot} per player. Rendering is BetterHud's job
 * ({@code integration/betterhud} registers placeholders that read these snapshots). Publishing works
 * whether or not BetterHud is installed.
 */
public interface HudBridge {
    /** True if a HUD plugin renders the snapshots. */
    boolean isActive();

    /** Publishes {@code snapshot} for {@code player}. Must run on the player's own thread. */
    void publish(Player player, HudSnapshot snapshot);

    /** The latest snapshot, or {@link HudSnapshot#DEFAULT}. Any thread, O(1). */
    HudSnapshot snapshot(UUID player);

    /** Forgets a player (quit). Any thread. */
    void remove(UUID player);
}
