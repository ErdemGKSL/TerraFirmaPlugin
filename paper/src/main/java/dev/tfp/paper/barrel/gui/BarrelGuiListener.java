/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * PAR-PROC-09: after any click or drag in a barrel screen (also a cancelled one: the GUI framework moves shift-clicked
 * stacks itself and cancels the event), {@link BarrelScreens#touched} queues the commit of the screen's copy for the next
 * tick, when the click has been applied. Runs on the viewer's thread.
 */
public final class BarrelGuiListener implements Listener {
    private final BarrelScreens screens;

    public BarrelGuiListener(BarrelScreens screens) {
        this.screens = Objects.requireNonNull(screens, "screens");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && BarrelScreens.isBarrel(session)) {
            screens.touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && BarrelScreens.isBarrel(session)) {
            screens.touched(session);
        }
    }
}
