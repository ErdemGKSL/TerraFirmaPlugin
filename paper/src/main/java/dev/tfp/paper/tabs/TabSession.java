/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * PAR-SURV-07: one open tab page (the holder of its 4-row inventory). Only the viewer's thread touches it.
 */
public final class TabSession implements InventoryHolder {
    private final Player viewer;
    private final TabPage page;
    private Inventory inventory;
    private boolean closed;

    TabSession(Player viewer, TabPage page) {
        this.viewer = viewer;
        this.page = page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Player viewer() {
        return viewer;
    }

    public TabPage page() {
        return page;
    }

    public boolean closed() {
        return closed;
    }

    void close() {
        closed = true;
        inventory.clear(); // the buttons never leave the screen
    }

    /** The session whose screen {@code top} is, or null. */
    public static TabSession of(Inventory top) {
        return top != null && top.getHolder() instanceof TabSession s ? s : null;
    }
}
