/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.gui;

import dev.tfp.core.gui.TitleGlyphs;
import dev.tfp.core.gui.TitleRefreshThrottle;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

/**
 * PAR-INFRA-03: one viewer's open {@link TfpGui}: the chest inventory (this session is its holder), per-viewer state, and
 * the title refresh, rate-limited to {@link TitleRefreshThrottle#DEFAULT_INTERVAL_MILLIS} (4 per second).
 *
 * <p>Thread: the viewer's entity thread (Folia) or the main thread (Paper). Inventory click events already run there;
 * anything else that changes the session hops to it with the scheduler's {@code entity} first.
 */
public final class GuiSession implements InventoryHolder {
    private final GuiService service;
    private final TfpGui gui;
    private final Player viewer;
    private final Map<String, Object> state = new HashMap<>();
    private final TitleRefreshThrottle<String> throttle = new TitleRefreshThrottle<>(TitleRefreshThrottle.DEFAULT_INTERVAL_MILLIS);
    private Inventory inventory;
    private boolean closed;
    private int titleSends;

    GuiSession(GuiService service, TfpGui gui, Player viewer) {
        this.service = service;
        this.gui = gui;
        this.viewer = viewer;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public TfpGui gui() {
        return gui;
    }

    public Player viewer() {
        return viewer;
    }

    /** Per-viewer state of the screen (a seal flag, a selected page). */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T fallback) {
        Object v = state.get(key);
        return v == null ? fallback : (T) v;
    }

    public void put(String key, Object value) {
        state.put(key, Objects.requireNonNull(value));
    }

    public boolean closed() {
        return closed;
    }

    /** Title refreshes sent since the screen opened (not counting the open). */
    public int titleSends() {
        return titleSends;
    }

    /** The current title glyphs (background and overlays for the current state). */
    public TitleGlyphs glyphs() {
        return gui.layers().compose(gui.frames(this));
    }

    /** The title as a legacy string, the form {@code InventoryView#setTitle} takes. */
    String legacyTitle() {
        return GuiTitles.legacy(service.glyphs(), glyphs(), gui.label(viewer));
    }

    /**
     * Re-applies the state: button icons now, the title now or at the end of the refresh interval (the latest state
     * wins). Call after changing anything the frames or icons read.
     */
    public void refresh() {
        if (closed) {
            return;
        }
        placeButtons();
        switch (throttle.submit(legacyTitle(), service.now())) {
            case TitleRefreshThrottle.SendNow<String> s -> sendTitle(s.value());
            case TitleRefreshThrottle.Schedule<String> s -> service.later(this, s.delayMillis(), this::flush);
            case TitleRefreshThrottle.Idle<String> i -> {
            }
        }
    }

    void opened() {
        placeButtons();
        throttle.opened(legacyTitle(), service.now());
    }

    private void flush() {
        if (closed) {
            return;
        }
        String title = throttle.flush(service.now());
        if (title != null) {
            sendTitle(title);
        }
    }

    private void sendTitle(String title) {
        InventoryView view = viewer.getOpenInventory();
        if (view.getTopInventory().getHolder() == this) {
            view.setTitle(title); // one OpenScreen packet; CraftEngine rewrites the tags in it
            titleSends++;
            placeButtons(); // the client re-opens the screen; send the buttons with it
        }
    }

    void placeButtons() {
        for (TfpGui.Button b : gui.buttons().values()) {
            inventory.setItem(b.slot(), b.icon().apply(this));
        }
    }

    void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (TfpGui.Button b : gui.buttons().values()) {
            inventory.setItem(b.slot(), null); // buttons never leave the screen
        }
        gui.closed(this, viewer);
    }
}
