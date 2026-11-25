/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/RestrictedChestContainer.java @ c3b4d9536 (two or four rows of
 *       restricted slots: a stack above the chest's item size is refused),
 *       src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java @ c3b4d9536 (isValid)
 */
package dev.tfp.paper.chest;

import dev.tfp.core.chest.ChestRules;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiLayers;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-DECO-04: the screen of a chest or a double chest (TFC {@code RestrictedChestContainer}: nine columns, two or four rows
 * of slots that refuse items above the chest's size) on the GUI framework, without art: the title is plain text and the
 * vanilla client draws its own chest window.
 *
 * <p>As the nest box screen: the screen's slots are a copy of the devices'; the devices ({@link ChestLink}) stay the
 * authority. A click or drag commits the copy one tick later and when the screen closes. A chest has one viewer at a time
 * (two copies of the same slots could duplicate items; ledger), and so does a block of a double chest. Threads: the
 * viewer's thread; the open maps are thread-safe.
 */
public final class ChestScreens implements Listener {
    static final String STATE = "chest.screen";

    static final class Screen {
        final ChestLink link;
        final List<ChestLink.Key> keys;
        ChestLink.Snapshot chest;
        boolean pending;

        Screen(ChestLink link, ChestLink.Snapshot chest) {
            this.link = link;
            this.keys = link.keys();
            this.chest = chest;
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Predicate<ItemStack> fits;
    private final Map<ChestLink.Key, GuiSession> open = new ConcurrentHashMap<>();

    public ChestScreens(GuiService gui, Scheduler scheduler, Messages messages, Predicate<ItemStack> fits) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.fits = Objects.requireNonNull(fits, "fits");
    }

    /** Opens the chest for {@code viewer}; empty when another player has a half of it open, it is gone or refused. */
    public Optional<GuiSession> open(Player viewer, ChestLink link) {
        for (ChestLink.Key k : link.keys()) {
            GuiSession other = open.get(k);
            if (other != null && !other.closed()) {
                return Optional.empty();
            }
        }
        Optional<ChestLink.Snapshot> chest = link.snapshot();
        if (chest.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(link, chest.get());
        GuiSession session = gui.open(viewer, screen(link.size()));
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        for (ChestLink.Key k : screen.keys) {
            open.put(k, session);
        }
        apply(session, screen, chest.get());
        return Optional.of(session);
    }

    /** The open screen holding block {@code key}, if any. */
    public Optional<GuiSession> viewer(ChestLink.Key key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Before another path changes the chest at {@code key}: commits the open screen's copy. Viewer thread. */
    public void edit(ChestLink.Key key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads the chest at {@code key} into its open screen. Viewer thread. */
    public void reload(ChestLink.Key key) {
        viewer(key).ifPresent(this::reload);
    }

    public static boolean isChest(GuiSession session) {
        return state(session) != null;
    }

    private TfpGui screen(int size) {
        TfpGui.Builder b = TfpGui.builder(new ChestGeometry(size / ChestRules.COLUMNS), new GuiLayers(null, List.of()))
            .label(p -> messages.get(p, size == ChestRules.SINGLE_SLOTS ? "gui.chest.title" : "gui.chest.double.title"));
        for (int slot = 0; slot < size; slot++) {
            b.input(slot, (s, i, stack) -> !GuiButtons.isButton(stack) && fits.test(stack));
        }
        return b.onClose(this::closed).build();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isChest(session)) {
            touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isChest(session)) {
            touched(session);
        }
    }

    void touched(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.pending) {
            return;
        }
        screen.pending = true;
        boolean queued = scheduler.entityDelayed(session.viewer(), 1, () -> {
            screen.pending = false;
            flush(session);
        }, () -> screen.pending = false);
        if (!queued) {
            screen.pending = false;
        }
    }

    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.chest == null) {
            return;
        }
        List<ItemStack> now = copy(session.getInventory(), screen.link.size());
        if (ChestLink.Snapshot.sameSlots(now, screen.chest.slots())) {
            return;
        }
        Optional<ChestLink.Committed> after = screen.link.commit(now);
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            giveBack(session.viewer(), after.get().overflow());
            apply(session, screen, after.get().snapshot());
        }
    }

    void reload(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed()) {
            return;
        }
        flush(session);
        if (session.closed() || screen.chest == null) {
            return;
        }
        Optional<ChestLink.Snapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.chest)) {
            apply(session, screen, now.get());
        }
    }

    private static void apply(GuiSession session, Screen screen, ChestLink.Snapshot chest) {
        screen.chest = chest;
        Inventory inv = session.getInventory();
        for (int i = 0; i < screen.link.size(); i++) {
            ItemStack s = chest.slots().get(i);
            inv.setItem(i, s == null || s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    private static void giveBack(Player viewer, List<ItemStack> items) {
        for (ItemStack item : items) {
            for (ItemStack rest : viewer.getInventory().addItem(item).values()) {
                viewer.getWorld().dropItem(viewer.getLocation(), rest);
            }
        }
    }

    /** The chest is gone: what the viewer put into the copy since the last commit goes back to the viewer; the screen closes. */
    private static void gone(GuiSession session, Screen screen) {
        ChestLink.Snapshot last = screen.chest;
        screen.chest = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int i = 0; i < screen.link.size(); i++) {
            ItemStack there = inv.getItem(i);
            ItemStack known = last == null ? ItemStack.empty() : last.slots().get(i);
            inv.setItem(i, null);
            if (there != null && !there.isEmpty() && !there.equals(known)) {
                giveBack(viewer, List.of(there));
            }
        }
        if (!session.closed()) {
            viewer.closeInventory();
        }
    }

    private void closed(GuiSession session, Player viewer) {
        Screen screen = state(session);
        if (screen == null) {
            return;
        }
        if (screen.chest != null) {
            List<ItemStack> now = copy(session.getInventory(), screen.link.size());
            if (!ChestLink.Snapshot.sameSlots(now, screen.chest.slots())) {
                Optional<ChestLink.Committed> after = screen.link.commit(now);
                if (after.isEmpty()) {
                    gone(session, screen);
                } else {
                    giveBack(viewer, after.get().overflow());
                }
            }
        }
        for (int i = 0; i < screen.link.size(); i++) {
            session.getInventory().setItem(i, null);
        }
        Set<ChestLink.Key> keys = new HashSet<>(screen.keys);
        for (ChestLink.Key k : keys) {
            open.remove(k, session);
        }
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv, int size) {
        List<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getItem(i);
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }
}
