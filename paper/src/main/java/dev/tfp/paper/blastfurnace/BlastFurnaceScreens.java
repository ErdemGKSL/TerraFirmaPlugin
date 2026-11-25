/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BlastFurnaceContainer.java @ c3b4d9536 (one slot, the tuyere),
 *       src/main/java/net/dries007/tfc/client/screen/BlastFurnaceScreen.java @ c3b4d9536 (the temperature indicator, the ore
 *       and fuel meters, the output tank and their tooltips)
 */
package dev.tfp.paper.blastfurnace;

import dev.tfp.core.blastfurnace.BlastFurnaceData;
import dev.tfp.core.blastfurnace.BlastFurnaceRules;
import dev.tfp.core.blastfurnace.BlastFurnaceRules.Tank;
import dev.tfp.core.blastfurnace.BlastFurnaceScreenLayout;
import dev.tfp.core.device.DeviceRecord;
import dev.tfp.core.device.ItemBlob;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import dev.tfp.paper.tooltip.TooltipStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-METAL-27: the blast furnace screen (TFC {@code BlastFurnaceScreen} + {@code BlastFurnaceContainer}) on the GUI
 * framework: one slot for the tuyere, TFC's background, the two capacity-limited meters (ore and fuel), the temperature
 * indicator and the output tank as art layers, and buttons over them that carry TFC's tooltips ({@link
 * BlastFurnaceScreenLayout}). The chest's tuyere slot is a copy; the furnace stays the authority: a click is committed by a
 * region task of the furnace block one tick later, and an open screen reloads every {@link #RELOAD_TICKS} ticks so the
 * viewer watches the heat. Any number of viewers.
 *
 * <p>Threads: inventory events run on the clicking player's thread, device edits on the furnace's region; the two hand over
 * through {@link Scheduler#entity} and {@link Scheduler#region}.
 */
public final class BlastFurnaceScreens implements Listener {
    public static final String TITLE_KEY = "gui.blast_furnace.title";
    /** How often an open screen reloads the furnace. */
    public static final long RELOAD_TICKS = 10;
    /** The tuyere slot of the chest. */
    public static final int TUYERE_SLOT = BlastFurnaceScreenLayout.TUYERE_SLOT;
    static final String STATE = "blastfurnace.screen";

    /** What the screen shows, read on the furnace's region. */
    record Snapshot(boolean present, ItemStack tuyere, BlastFurnaceData data, int fuel, int inputs, int capacity, int maximum,
            int fluidCapacity) {
        static final Snapshot GONE = new Snapshot(false, ItemStack.empty(), BlastFurnaceData.FRESH, 0, 0, 0, 0, 1);

        BlastFurnaceScreenLayout.View view() {
            return new BlastFurnaceScreenLayout.View(data.state().temperature(), data.lit(), capacity, maximum, inputs, fuel,
                data.output().amount(), fluidCapacity);
        }
    }

    /** One open screen. */
    static final class Screen {
        final Block furnace;
        volatile Snapshot snap;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(Block furnace, Snapshot snap) {
            this.furnace = furnace;
            this.snap = snap;
        }
    }

    private final BlastFurnaceDevice device;
    private final DeviceService devices;
    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style = TooltipStyles.TFC_DEFAULTS.heat();

    public BlastFurnaceScreens(BlastFurnaceDevice device, DeviceService devices, GuiService gui, Scheduler scheduler,
            Messages messages) {
        this.device = Objects.requireNonNull(device, "device");
        this.devices = Objects.requireNonNull(devices, "devices");
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    // ---- opening ---------------------------------------------------------------------------------------------

    /** Opens the screen for {@code player}: reads the furnace on its region, then opens the chest on the player's thread. */
    public void open(Player player, Block furnace) {
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            Snapshot s = snapshot(furnace);
            if (!s.present()) {
                return;
            }
            scheduler.entity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                Screen screen = new Screen(furnace, s);
                GuiSession session = gui.open(player, screen(screen));
                if (session == null) {
                    return;
                }
                session.put(STATE, screen);
                apply(session, screen, s);
                screen.reloader = scheduler.entityRepeating(player, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
            }, () -> { });
        });
    }

    /** The furnace caught up to now. Region thread of {@code furnace}. */
    Snapshot snapshot(Block furnace) {
        Optional<DeviceRecord<BlastFurnaceData>> rec = devices.edit(furnace, device.kind(), (record, now) -> record);
        return rec.map(r -> snapshotOf(furnace, r)).orElse(Snapshot.GONE);
    }

    private Snapshot snapshotOf(Block furnace, DeviceRecord<BlastFurnaceData> r) {
        BlastFurnaceRules.Config cfg = device.services().config().get();
        int cap = BlastFurnaceRules.capacity(device.levels(furnace), cfg.capacityPerLevel());
        int max = BlastFurnaceRules.capacity(cfg.maxChimneyHeight(), cfg.capacityPerLevel());
        // TFC's inputStacks hold the ore and the flux alike
        int inputs = device.group(r, BlastFurnaceDevice.INPUT).size() + device.group(r, BlastFurnaceDevice.CATALYST).size();
        return new Snapshot(true, device.tuyere(r), r.state(), device.group(r, BlastFurnaceDevice.FUEL).size(), inputs, cap,
            max, cfg.fluidCapacity());
    }

    // ---- the screen ------------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen) {
        TfpGui.Builder b = TfpGui.builder(BlastFurnaceScreenLayout.CHEST, BlastFurnaceScreenLayout.LAYERS)
            .label(p -> messages.get(p, TITLE_KEY));
        b.input(TUYERE_SLOT, (s, slot, stack) -> !GuiButtons.isButton(stack) && roomFor(s)
            && device.services().tuyereTag().test(stack));
        for (int slot : BlastFurnaceScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen.snap), (s, click) -> { });
        }
        for (int slot : BlastFurnaceScreenLayout.ORE_SLOTS) {
            b.button(slot, s -> icon(Optional.of(legacy(messages.get(s.viewer(), "gui.blast_furnace.input",
                screen.snap.inputs(), screen.snap.capacity()))), List.of()), (s, click) -> { });
        }
        for (int slot : BlastFurnaceScreenLayout.FUEL_SLOTS) {
            b.button(slot, s -> icon(Optional.of(legacy(messages.get(s.viewer(), "gui.blast_furnace.fuel",
                screen.snap.fuel(), screen.snap.capacity()))), List.of()), (s, click) -> { });
        }
        for (int slot : BlastFurnaceScreenLayout.TANK_SLOTS) {
            b.button(slot, s -> tankIcon(s.viewer(), screen.snap), (s, click) -> { });
        }
        return b.frames(s -> BlastFurnaceScreenLayout.frames(screen.snap.view()))
            .lockedWhen(s -> !screen.snap.present())
            .onClose(this::closed)
            .build();
    }

    /** The one-item slot takes a tuyere only when empty. */
    private static boolean roomFor(GuiSession session) {
        return StackCodec.isEmpty(session.getInventory().getItem(TUYERE_SLOT));
    }

    /** TFC's temperature tooltip: the heat level in the heat tooltip style, nothing when cold; the air as a lore line. */
    private ItemStack thermometerIcon(Player viewer, Snapshot snap) {
        Optional<TextComponent> text = style.formatColored(snap.data().state().temperature(), Messages.code(viewer), messages::get);
        List<Component> lore = new ArrayList<>();
        if (text.isPresent() && snap.data().state().airTicks() > 0) {
            lore.add(legacy(messages.get(viewer, "gui.blast_furnace.air", snap.data().state().airTicks())));
        }
        return icon(text.map(t -> (Component) t), lore);
    }

    /** TFC's fluid tooltip: the output fluid and its amount (nothing when empty); the molten input as a lore line. */
    private ItemStack tankIcon(Player viewer, Snapshot snap) {
        Tank out = snap.data().output();
        Tank in = snap.data().input();
        if (out.isEmpty()) {
            return icon(Optional.empty(), List.of());
        }
        List<Component> lore = new ArrayList<>();
        if (!in.isEmpty()) {
            lore.add(legacy(messages.get(viewer, "gui.blast_furnace.molten", in.fluid(), in.amount())));
        }
        return icon(Optional.of(legacy(messages.get(viewer, "gui.blast_furnace.output", out.fluid(), out.amount(),
            snap.fluidCapacity()))), lore);
    }

    private ItemStack icon(Optional<? extends Component> text, List<Component> lore) {
        ItemStack icon = GuiButtons.icon(gui.glyphs(), "");
        ItemMeta meta = icon.getItemMeta();
        if (text.isPresent()) {
            meta.customName(text.get().colorIfAbsent(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                List<Component> out = new ArrayList<>(lore.size());
                for (Component c : lore) {
                    out.add(c.colorIfAbsent(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(out);
            }
        } else {
            meta.setHideTooltip(true);
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }

    // ---- sync ------------------------------------------------------------------------------------------------

    /** Shows {@code snap}: the tuyere slot, then the title frames and the button tooltips. */
    private static void apply(GuiSession session, Screen screen, Snapshot snap) {
        screen.snap = snap;
        Inventory inv = session.getInventory();
        inv.setItem(TUYERE_SLOT, StackCodec.isEmpty(snap.tuyere()) ? null : snap.tuyere());
        session.refresh();
    }

    private void reload(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed()) {
            return;
        }
        Block furnace = screen.furnace;
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            Snapshot s = snapshot(furnace);
            scheduler.entity(session.viewer(), () -> {
                if (session.closed()) {
                    return;
                }
                if (!s.present()) {
                    session.viewer().closeInventory();
                    return;
                }
                flushNow(session, screen); // a pending change of the copy goes first
                if (!s.equals(screen.snap)) {
                    apply(session, screen, s);
                }
            }, () -> { });
        });
    }

    /** A click or drag happened in {@code session}'s window: commit the copy next tick (once per tick). */
    void touched(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.pending) {
            return;
        }
        screen.pending = true;
        boolean queued = scheduler.entityDelayed(session.viewer(), 1, () -> {
            screen.pending = false;
            flushNow(session, screen);
        }, () -> screen.pending = false);
        if (!queued) {
            screen.pending = false;
        }
    }

    /** Commits the tuyere slot when it differs from the furnace's last known. Viewer thread. */
    private void flushNow(GuiSession session, Screen screen) {
        if (session.closed() || !screen.snap.present()) {
            return;
        }
        clamp(session);
        ItemStack now = copyOf(session.getInventory().getItem(TUYERE_SLOT));
        ItemStack known = screen.snap.tuyere();
        if (same(now, known)) {
            return;
        }
        commit(session, screen, now, known);
    }

    /** Gives back what a shift-click, drag or swap put above the slot's limit of one. */
    static boolean clamp(GuiSession session) {
        Inventory inv = session.getInventory();
        ItemStack there = inv.getItem(TUYERE_SLOT);
        if (there == null || there.getAmount() <= 1) {
            return false;
        }
        ItemStack excess = there.clone();
        excess.setAmount(there.getAmount() - 1);
        there.setAmount(1);
        inv.setItem(TUYERE_SLOT, there);
        give(session.viewer(), excess);
        return true;
    }

    /**
     * Puts {@code now} into the furnace's tuyere slot (empty takes it out). The furnace's region does it; a tuyere that could
     * not go in (another one is there since) goes back to the viewer.
     */
    private void commit(GuiSession session, Screen screen, ItemStack now, ItemStack known) {
        Block furnace = screen.furnace;
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            boolean[] rejected = {put(furnace, now, known)};
            Snapshot s = snapshot(furnace);
            scheduler.entity(session.viewer(), () -> {
                if (rejected[0]) {
                    give(session.viewer(), now);
                }
                if (!session.closed() && s.present()) {
                    apply(session, screen, s);
                }
            }, () -> { });
        });
    }

    /** Stores {@code now} as the furnace's tuyere. True when refused: another tuyere is there since. Region thread. */
    private boolean put(Block furnace, ItemStack now, ItemStack known) {
        boolean[] rejected = {false};
        devices.edit(furnace, device.kind(), (record, t) -> {
            ItemStack cur = device.tuyere(record);
            if (StackCodec.isEmpty(known) && !StackCodec.isEmpty(cur) && !StackCodec.isEmpty(now)) {
                rejected[0] = true;
                return record;
            }
            return record.withSlot(BlastFurnaceDevice.TUYERE,
                StackCodec.isEmpty(now) ? ItemBlob.EMPTY : devices.stacks().encode(now));
        });
        return rejected[0];
    }

    private void closed(GuiSession session, Player viewer) {
        Screen screen = state(session);
        if (screen == null) {
            return;
        }
        if (screen.reloader != null) {
            screen.reloader.cancel();
        }
        if (screen.snap.present()) { // the copy's last change goes to the furnace, the closed copy must hold nothing
            clamp(session);
            ItemStack now = copyOf(session.getInventory().getItem(TUYERE_SLOT));
            if (!same(now, screen.snap.tuyere())) {
                commitClosed(screen, now, screen.snap.tuyere(), viewer);
            }
        }
        session.getInventory().setItem(TUYERE_SLOT, null);
    }

    private void commitClosed(Screen screen, ItemStack now, ItemStack known, Player viewer) {
        Block furnace = screen.furnace;
        scheduler.region(furnace.getWorld(), furnace.getX() >> 4, furnace.getZ() >> 4, () -> {
            boolean[] rejected = {put(furnace, now, known)};
            if (rejected[0]) {
                scheduler.entity(viewer, () -> give(viewer, now), () -> { });
            }
        });
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static ItemStack copyOf(ItemStack s) {
        return s == null || s.isEmpty() ? ItemStack.empty() : s.clone();
    }

    private static boolean same(ItemStack a, ItemStack b) {
        boolean ea = StackCodec.isEmpty(a);
        boolean eb = StackCodec.isEmpty(b);
        return ea || eb ? ea && eb : a.equals(b);
    }

    static void give(Player viewer, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (ItemStack rest : viewer.getInventory().addItem(stack.clone()).values()) {
            viewer.getWorld().dropItem(viewer.getLocation(), rest);
        }
    }

    // ---- events ----------------------------------------------------------------------------------------------

    /** A click that places a stack onto the one-item slot puts one in and leaves the rest on the cursor. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || state(session) == null || e.getRawSlot() != TUYERE_SLOT) {
            return;
        }
        ItemStack cursor = e.getCursor();
        if (cursor == null || cursor.isEmpty() || cursor.getAmount() <= 1) {
            return;
        }
        InventoryAction action = e.getAction();
        if (action == InventoryAction.SWAP_WITH_CURSOR) {
            e.setCancelled(true);
        } else if (action == InventoryAction.PLACE_ALL || action == InventoryAction.PLACE_SOME) {
            e.setCancelled(true);
            if (!StackCodec.isEmpty(session.getInventory().getItem(TUYERE_SLOT))) {
                return;
            }
            ItemStack placed = cursor.clone();
            placed.setAmount(1);
            session.getInventory().setItem(TUYERE_SLOT, placed);
            ItemStack rest = cursor.clone();
            rest.setAmount(cursor.getAmount() - 1);
            e.getView().setCursor(rest);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session == null || state(session) == null) {
            return;
        }
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && e.getRawSlot() >= e.getView().getTopInventory().getSize()) {
            clamp(session);
        }
        touched(session);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && state(session) != null) {
            touched(session);
        }
    }
}
