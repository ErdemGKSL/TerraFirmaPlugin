/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/FireboxContainer.java @ c3b4d9536 (addContainerSlots, moveStack:
 *       the inventory fills slots 0 to 15 in order),
 *       src/main/java/net/dries007/tfc/client/screen/FireboxScreen.java @ c3b4d9536 (the thermometer and its tooltip
 *       heatTooltipStyle.formatColored(temperature); the status line tfc.tooltip.firebox.no_heat / time_to_heat / heated)
 */
package dev.tfp.paper.firebox;

import dev.tfp.core.firebox.FireboxScreenLayout;
import dev.tfp.core.firebox.FireboxSim;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import dev.tfp.paper.tooltip.TimeDeltaStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-HEAT-12: the firebox screen (TFC {@code FireboxScreen} + {@code FireboxContainer}) on the GUI framework, in the
 * layout of {@link FireboxScreenLayout}.
 *
 * <p>As the powderkeg screen: the chest's sixteen slots are a copy of the firebox's; the firebox ({@link FireboxLink})
 * stays the authority. A click or drag commits the copy one tick later (TFC's cascade then moves the fuel down); every
 * {@link #RELOAD_TICKS} ticks the screen reloads the firebox. The thermometer is a layer of the title; its column's
 * buttons show the temperature as TFC's tooltip does, the row under the slots shows TFC's status line as a tooltip (no
 * free text can be drawn in a vanilla chest window: ledger). One viewer per firebox.
 *
 * <p>Threads: the viewer's thread; the open map is thread-safe.
 */
public final class FireboxScreens implements Listener {
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "firebox.screen";

    static final class Screen {
        final FireboxLink link;
        final Object key;
        FireboxLink.Snapshot box;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(FireboxLink link, Object key, FireboxLink.Snapshot box) {
            this.link = link;
            this.key = key;
            this.box = box;
        }

        float temperature() {
            return box == null ? 0f : box.data().temperature();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final IntSupplier daysInMonth;
    private final Predicate<ItemStack> fuel;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public FireboxScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style,
            IntSupplier daysInMonth, Predicate<ItemStack> fuel) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
        this.daysInMonth = Objects.requireNonNull(daysInMonth, "daysInMonth");
        this.fuel = Objects.requireNonNull(fuel, "fuel");
    }

    /** Opens the firebox's screen for {@code viewer}; empty when another player has it open, it is gone or refused. */
    public Optional<GuiSession> open(Player viewer, FireboxLink link) {
        Object key = link.key();
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return Optional.empty();
        }
        Optional<FireboxLink.Snapshot> box = link.snapshot();
        if (box.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(link, key, box.get());
        GuiSession session = gui.open(viewer, screen(screen));
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, box.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return Optional.of(session);
    }

    /** The open screen of firebox {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Before another path changes firebox {@code key}: commits the open screen's copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads firebox {@code key} into its open screen. Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    public static boolean isFirebox(GuiSession session) {
        return state(session) != null;
    }

    /** The firebox last shown in {@code session}. */
    public static Optional<FireboxLink.Snapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.box);
    }

    private TfpGui screen(Screen screen) {
        TfpGui.Builder b = TfpGui.builder(FireboxScreenLayout.CHEST, FireboxScreenLayout.LAYERS)
            .label(p -> messages.get(p, "gui.firebox.title"));
        for (int tfc = 0; tfc < FireboxSim.SLOTS; tfc++) {
            b.input(FireboxScreenLayout.chestSlot(tfc), (s, slot, stack) -> !GuiButtons.isButton(stack) && fuel.test(stack)
                && empty(s.getInventory().getItem(slot)));
        }
        for (int slot : FireboxScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        for (int slot : FireboxScreenLayout.STATUS_SLOTS) {
            b.button(slot, s -> statusIcon(s.viewer(), screen), (s, click) -> { });
        }
        return b.frames(s -> FireboxScreenLayout.frames(screen.temperature()))
            .lockedWhen(s -> screen.box == null)
            .onClose(this::closed)
            .build();
    }

    private static boolean empty(ItemStack s) {
        return s == null || s.isEmpty();
    }

    /** TFC's thermometer tooltip: the temperature in the heat tooltip style, nothing when cold. */
    ItemStack thermometerIcon(Player viewer, Screen screen) {
        ItemStack icon = GuiButtons.icon(gui.glyphs(), "");
        ItemMeta meta = icon.getItemMeta();
        Optional<TextComponent> text = style.formatColored(screen.temperature(), Messages.code(viewer), messages::get);
        if (text.isPresent()) {
            meta.customName(text.get().decoration(TextDecoration.ITALIC, false));
        } else {
            meta.setHideTooltip(true);
        }
        icon.setItemMeta(meta);
        return icon;
    }

    /** TFC's status line ({@code FireboxScreen.renderBg}) as the tooltip of the row under the slots. */
    ItemStack statusIcon(Player viewer, Screen screen) {
        ItemStack icon = GuiButtons.icon(gui.glyphs(), "");
        ItemMeta meta = icon.getItemMeta();
        Optional<String> text = statusText(viewer, screen.box);
        if (text.isPresent()) {
            meta.customName(Component.text(text.get(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else {
            meta.setHideTooltip(true);
        }
        icon.setItemMeta(meta);
        return icon;
    }

    /** The status line of {@code box} in the viewer's language, empty when TFC draws none (cold). */
    Optional<String> statusText(Player viewer, FireboxLink.Snapshot box) {
        if (box == null) {
            return Optional.empty();
        }
        String locale = Messages.code(viewer);
        int count = box.data().heatingCount();
        return switch (box.status()) {
            case NONE -> Optional.empty();
            case NO_HEAT -> Optional.of(messages.get(locale, "gui.firebox.no_heat"));
            case TIME_TO_HEAT -> Optional.of(messages.get(locale, "gui.firebox.time_to_heat", count,
                TimeDeltaStyle.DAYS_MONTHS_YEARS.timeDelta(FireboxSim.timeLeft(box.data(), box.now()), daysInMonth.getAsInt(),
                    locale, messages::get)));
            case HEATED -> Optional.of(messages.get(locale, "gui.firebox.heated", count));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isFirebox(session)) {
            touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isFirebox(session)) {
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

    /** Gives back what a shift-click, drag or swap put above a slot's one item. */
    static boolean clamp(GuiSession session) {
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        boolean moved = false;
        for (int tfc = 0; tfc < FireboxSim.SLOTS; tfc++) {
            int slot = FireboxScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            if (there == null || there.getAmount() <= FireboxSim.SLOT_LIMIT) {
                continue;
            }
            ItemStack excess = there.clone();
            excess.setAmount(there.getAmount() - FireboxSim.SLOT_LIMIT);
            there.setAmount(FireboxSim.SLOT_LIMIT);
            inv.setItem(slot, there);
            give(viewer, excess);
            moved = true;
        }
        return moved;
    }

    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.box == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (FireboxLink.Snapshot.sameSlots(now, screen.box.slots())) {
            return;
        }
        Optional<FireboxLink.Snapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.box == null) {
            return;
        }
        Optional<FireboxLink.Snapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().sameView(screen.box)) {
            apply(session, screen, now.get());
        } else {
            screen.box = now.get();
        }
    }

    private static void apply(GuiSession session, Screen screen, FireboxLink.Snapshot box) {
        screen.box = box;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < FireboxSim.SLOTS; tfc++) {
            ItemStack s = box.slots().get(tfc);
            inv.setItem(FireboxScreenLayout.chestSlot(tfc), s == null || s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The firebox is gone: what the viewer put into the copy since the last commit goes back; the screen closes. */
    private static void gone(GuiSession session, Screen screen) {
        FireboxLink.Snapshot last = screen.box;
        screen.box = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < FireboxSim.SLOTS; tfc++) {
            int slot = FireboxScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slots().get(tfc);
            inv.setItem(slot, null);
            if (there != null && !there.isEmpty() && !there.equals(known)) {
                give(viewer, there);
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
        if (screen.box != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!FireboxLink.Snapshot.sameSlots(now, screen.box.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : FireboxScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the firebox holds them; the closed copy must not
        }
        open.remove(screen.key, session);
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(FireboxSim.SLOTS);
        for (int tfc = 0; tfc < FireboxSim.SLOTS; tfc++) {
            ItemStack s = inv.getItem(FireboxScreenLayout.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }

    static void give(Player viewer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : viewer.getInventory().addItem(stack.clone()).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), rest);
        }
    }
}
