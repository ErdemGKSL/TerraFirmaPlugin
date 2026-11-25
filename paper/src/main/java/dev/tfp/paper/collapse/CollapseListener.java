/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onBreakBlock, the collapse branch)
 */
package dev.tfp.paper.collapse;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Feeds TFC's collapse source to the {@link CollapseService}: a player breaking a {@code #can_trigger_collapse} block, in
 * any game mode and with any tool, as TFC's {@code BlockEvent.BreakEvent} handler (which exempts none). Runs before the
 * block is removed, as TFC does; thin: a tag filter and a snapshot.
 */
public final class CollapseListener implements Listener {
    private final CollapseService service;

    public CollapseListener(CollapseService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        service.trigger(event.getBlock());
    }
}
