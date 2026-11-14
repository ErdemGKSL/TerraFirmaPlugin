/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/PowderkegContainer.java @ c3b4d9536 (clicked: no slot edits while
 *       sealed; onButtonPress; addContainerSlots; moveStack: the inventory fills slots 0 to 11 in order),
 *       src/main/java/net/dries007/tfc/client/screen/PowderkegScreen.java @ c3b4d9536 (the seal button's tooltip
 *       tfc.tooltip.seal_barrel / unseal_barrel, drawDisabled while sealed)
 */
package dev.tfp.paper.powderkeg;

import dev.tfp.core.powderkeg.PowderkegRules;
import dev.tfp.core.powderkeg.PowderkegScreenLayout;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 * PAR-PROC-18: the powderkeg screen (TFC {@code PowderkegScreen} + {@code PowderkegContainer}) on the GUI framework, in
 * the layout of {@link PowderkegScreenLayout}.
 *
 * <p>As the barrel screen: the chest's twelve slots are a copy of the keg's; the keg ({@link PowderkegLink}) stays the
 * authority. A click or drag commits the copy one tick later; every {@link #RELOAD_TICKS} ticks the screen reloads the
 * keg. While sealed every slot click is refused and TFC's disabled highlight covers the slots; the seal button commits,
 * then toggles the seal. One viewer per keg (two copies of the same slots could duplicate items; ledger).
 *
 * <p>Threads: the viewer's thread; the open map is thread-safe.
 */
public final class PowderkegScreens implements Listener {
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "powderkeg.screen";

    static final class Screen {
        final PowderkegLink link;
        final Object key;
        PowderkegLink.Snapshot keg;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(PowderkegLink link, Object key, PowderkegLink.Snapshot keg) {
            this.link = link;
            this.key = key;
            this.keg = keg;
        }

        boolean sealed() {
            return keg != null && keg.sealed();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Predicate<ItemStack> fuel;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public PowderkegScreens(GuiService gui, Scheduler scheduler, Messages messages, Predicate<ItemStack> fuel) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.fuel = Objects.requireNonNull(fuel, "fuel");
    }

    /** Opens the keg's screen for {@code viewer}; empty when another player has it open, it is gone or refused. */
    public Optional<GuiSession> open(Player viewer, PowderkegLink link) {
        Object key = link.key();
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return Optional.empty();
        }
        Optional<PowderkegLink.Snapshot> keg = link.snapshot();
        if (keg.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(link, key, keg.get());
        GuiSession session = gui.open(viewer, screen(screen));
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, keg.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return Optional.of(session);
    }

    /** The open screen of keg {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Before another path changes keg {@code key}: commits the open screen's copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads keg {@code key} into its open screen. Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    public static boolean isPowderkeg(GuiSession session) {
        return state(session) != null;
    }

    private TfpGui screen(Screen screen) {
        TfpGui.Builder b = TfpGui.builder(PowderkegScreenLayout.CHEST, PowderkegScreenLayout.LAYERS)
            .label(p -> messages.get(p, "gui.powderkeg.title"));
        for (int tfc = 0; tfc < PowderkegRules.SLOTS; tfc++) {
            b.input(PowderkegScreenLayout.chestSlot(tfc), (s, slot, stack) -> !GuiButtons.isButton(stack) && fuel.test(stack));
        }
        b.button(PowderkegScreenLayout.SEAL_SLOT,
            s -> GuiButtons.icon(gui.glyphs(), messages.get(s.viewer(), screen.sealed() ? "gui.powderkeg.unseal" : "gui.powderkeg.seal")),
            (s, click) -> toggleSeal(s));
        return b.frames(s -> PowderkegScreenLayout.frames(screen.sealed()))
            .lockedWhen(s -> screen.keg == null || screen.keg.sealed())
            .onClose(this::closed)
            .build();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isPowderkeg(session)) {
            touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isPowderkeg(session)) {
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
        if (screen == null || session.closed() || screen.keg == null || screen.keg.sealed()) {
            return;
        }
        List<ItemStack> now = copy(session.getInventory());
        if (PowderkegLink.Snapshot.sameSlots(now, screen.keg.slots())) {
            return;
        }
        Optional<PowderkegLink.Snapshot> after = screen.link.commit(now);
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
        }
    }

    void reload(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed()) {
            return;
        }
        flush(session);
        if (session.closed() || screen.keg == null) {
            return;
        }
        Optional<PowderkegLink.Snapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.keg)) {
            apply(session, screen, now.get());
        }
    }

    private void toggleSeal(GuiSession session) {
        Screen screen = state(session);
        flush(session);
        if (screen == null || session.closed() || screen.keg == null) {
            return;
        }
        Optional<PowderkegLink.Snapshot> after = screen.link.toggleSeal(session.viewer());
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
        }
    }

    private static void apply(GuiSession session, Screen screen, PowderkegLink.Snapshot keg) {
        screen.keg = keg;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < PowderkegRules.SLOTS; tfc++) {
            ItemStack s = keg.slots().get(tfc);
            inv.setItem(PowderkegScreenLayout.chestSlot(tfc), s == null || s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The keg is gone: what the viewer put into the copy since the last commit goes back to the viewer; the screen closes. */
    private static void gone(GuiSession session, Screen screen) {
        PowderkegLink.Snapshot last = screen.keg;
        screen.keg = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < PowderkegRules.SLOTS; tfc++) {
            int slot = PowderkegScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slots().get(tfc);
            inv.setItem(slot, null);
            if (there != null && !there.isEmpty() && !there.equals(known)) {
                for (ItemStack rest : viewer.getInventory().addItem(there).values()) {
                    viewer.getWorld().dropItem(viewer.getLocation(), rest);
                }
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
        if (screen.reloader != null) {
            screen.reloader.cancel();
        }
        if (screen.keg != null && !screen.keg.sealed()) {
            List<ItemStack> now = copy(session.getInventory());
            if (!PowderkegLink.Snapshot.sameSlots(now, screen.keg.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : PowderkegScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null);
        }
        open.remove(screen.key, session);
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(PowderkegRules.SLOTS);
        for (int tfc = 0; tfc < PowderkegRules.SLOTS; tfc++) {
            ItemStack s = inv.getItem(PowderkegScreenLayout.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }
}
