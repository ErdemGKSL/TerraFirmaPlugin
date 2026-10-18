/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Puts a first-joining player on the TFC spawn of the overworld. Paper's first-join placement can land 5 to 10 blocks away
 * from the world spawn, and the gamerule that would stop it is not available on this server, so the spawn location is set here.
 * Only new players ({@link AsyncPlayerSpawnLocationEvent#isNewPlayer()}) in worlds that use {@link TfcChunkGenerator} are
 * changed; respawns (beds, anchors) are left to Paper.
 */
public final class TfcFirstJoinSpawn implements Listener {

    /**
     * Async event (fired during the configuration phase, before the player enters the world). This handler must never search
     * the terrain or change the world: it runs on an async thread, and Bukkit world changes (such as
     * {@code World.setSpawnLocation}) belong to the main thread. So it only reads the spawn the generator already cached. The
     * spawn is normally cached on the main thread when the world loads ({@code getFixedSpawnLocation}). If it is not cached,
     * nothing is set here and Paper's own first-join spawn applies to that player. This is acceptable.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (!event.isNewPlayer()) {
            return;
        }
        World world = event.getSpawnLocation().getWorld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL
            || !(world.getGenerator() instanceof TfcChunkGenerator generator)) {
            return;
        }
        generator.tfcSpawnLocationIfCached(world).ifPresent(event::setSpawnLocation);
    }
}
