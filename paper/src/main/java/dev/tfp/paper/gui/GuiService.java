/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.paper.scheduler.Scheduler;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * PAR-INFRA-03: opens {@link TfpGui}s and runs their sessions; provided by {@link GuiModule} as a module service
 * ({@code services().require(GuiService.class)}). Clicks are routed by {@link GuiListener}.
 */
public final class GuiService {
    /** Server ticks are 50 ms. */
    static final long TICK_MILLIS = 50;

    private final Server server;
    private final Scheduler scheduler;
    private final LongSupplier millis;
    private final boolean glyphs;

    /**
     * @param glyphs true when CraftEngine runs (its pack draws the art and its interception turns the title tags into
     *               glyphs); false gives plain titles and visible button items
     * @param millis wall clock in milliseconds, for the title throttle
     */
    public GuiService(Server server, Scheduler scheduler, LongSupplier millis, boolean glyphs) {
        this.server = Objects.requireNonNull(server);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.millis = Objects.requireNonNull(millis);
        this.glyphs = glyphs;
    }

    public boolean glyphs() {
        return glyphs;
    }

    long now() {
        return millis.getAsLong();
    }

    Scheduler scheduler() {
        return scheduler;
    }

    /**
     * Opens {@code gui} for {@code viewer}. Call on the viewer's thread ({@code scheduler.entity}).
     *
     * @return the session, or null when the server refused to open the screen (a plugin cancelled the open event)
     */
    public GuiSession open(Player viewer, TfpGui gui) {
        GuiSession session = new GuiSession(this, gui, viewer);
        Inventory inv = server.createInventory(session, gui.chest().size(),
            GuiTitles.title(glyphs, session.glyphs(), gui.label(viewer)));
        session.inventory(inv);
        session.opened();
        if (viewer.openInventory(inv) == null) {
            session.close();
            return null;
        }
        return session;
    }

    /** The session whose screen {@code top} is, or null. */
    public static GuiSession session(Inventory top) {
        if (top == null) {
            return null;
        }
        InventoryHolder h = top.getHolder();
        return h instanceof GuiSession s ? s : null;
    }

    /** Runs {@code task} on {@code session}'s viewer thread after {@code delayMillis} (rounded up to ticks). */
    void later(GuiSession session, long delayMillis, Runnable task) {
        long ticks = Math.max(1, (delayMillis + TICK_MILLIS - 1) / TICK_MILLIS);
        scheduler.entityDelayed(session.viewer(), ticks, task, () -> { });
    }
}
