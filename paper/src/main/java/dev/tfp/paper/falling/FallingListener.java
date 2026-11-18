/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.falling;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

/**
 * PAR-PHYS-01: vanilla's placement of one of our falling blocks is cancelled and decided by {@link FallingBlocks}
 * (TFC's rules, the custom state placed through the adapter); falling blocks loaded from a chunk are tracked again.
 * Both events fire on the owner thread of the entity.
 */
public final class FallingListener implements Listener {
    private final FallingBlocks falling;

    public FallingListener(FallingBlocks falling) {
        this.falling = falling;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock entity && falling.changeBlock(entity, event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            falling.resume(entity);
        }
    }
}
