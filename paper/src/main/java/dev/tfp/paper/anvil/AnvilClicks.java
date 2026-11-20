/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.anvil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/**
 * The clicks the anvil device consumed (PAR-METAL-03). Vanilla sends the other hand's use only when the first hand's use
 * passed; Bukkit fires {@code PlayerInteractEvent} for both hands whatever the first did. A consumed click marks the player
 * here, and the {@link AnvilListener} cancels the same player's other event of that tick. Any thread (a player is on one
 * region at a time).
 */
public final class AnvilClicks {
    private final Map<UUID, Integer> consumed = new ConcurrentHashMap<>();

    /** The device consumed a click of {@code player} now. */
    public void mark(Player player) {
        consumed.put(player.getUniqueId(), player.getTicksLived());
    }

    /** Whether the device consumed a click of {@code player} in this tick. */
    public boolean consumedNow(Player player) {
        Integer tick = consumed.get(player.getUniqueId());
        return tick != null && tick == player.getTicksLived();
    }
}
