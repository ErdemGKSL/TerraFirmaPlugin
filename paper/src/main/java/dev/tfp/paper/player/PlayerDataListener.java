/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.player;

import dev.tfp.paper.i18n.Messages;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Wires {@link PlayerDataStore} to the player lifecycle:
 * <ul>
 *   <li>{@link AsyncPlayerPreLoginEvent} (HIGHEST, an async thread, so blocking file I/O is allowed): load.
 *       Unreadable data refuses the login instead of starting the player from scratch, so nothing is
 *       overwritten. If a later plugin refuses the login, {@link PlayerDataStore#unloadAbsent} cleans up.</li>
 *   <li>{@link PlayerQuitEvent} (MONITOR): unload and save asynchronously.</li>
 * </ul>
 */
public final class PlayerDataListener implements Listener {
    private final PlayerDataStore store;
    private final Messages messages;
    private final Logger log;

    public PlayerDataListener(PlayerDataStore store, Messages messages, Logger log) {
        this.store = store;
        this.messages = messages;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            store.loadBlocking(event.getUniqueId());
        } catch (RuntimeException e) {
            log.log(Level.SEVERE, "Refusing login of " + event.getName() + " (" + event.getUniqueId()
                    + "): player data unreadable", e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Component.text(messages.get(Messages.FALLBACK, "login.player_data_unreadable")));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        store.unload(event.getPlayer().getUniqueId());
    }
}
