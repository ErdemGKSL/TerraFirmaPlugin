/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CrucibleContainer.java @ c3b4d9536 (addContainerSlots; moveStack:
 *       a mold to the output slot, anything else to the inputs),
 *       src/main/java/net/dries007/tfc/client/screen/CrucibleScreen.java @ c3b4d9536 (renderBg: the thermometer, the alloy
 *       fill and its text lines; the thermometer tooltip heatTooltipStyle.formatColored(temperature)),
 *       src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (getSlotStackLimit,
 *       isItemValid)
 */
package dev.tfp.paper.crucible.gui;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tooltip.HeatDisplayStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-METAL-12: the crucible screen (TFC {@code CrucibleScreen} + {@code CrucibleContainer}) on the GUI framework
 * ({@link GuiService}), provided as a module service by {@code CrucibleModule}.
 *
 * <p>Ten slots as TFC: nine inputs (any heatable item, one each) and the output (one container; the output rules wait for
 * the molds, see the ledger). A shift-click from the player inventory tries the inputs. The thermometer column (three
 * buttons) shows the temperature as TFC's tooltip, the fluid area (four buttons) the amount, and the text panel (27
 * buttons) the alloy and its metals as TFC's text lines (TFC's scroll bar is not needed: the tooltip lists every metal).
 * The art layers draw the thermometer and the alloy fill. The chest's slots are a copy of the crucible's; the crucible
 * ({@link CrucibleLink}) stays the authority, committed one tick after a click and reloaded every {@link #RELOAD_TICKS}
 * ticks, which also lets the viewer watch items melt. One viewer per crucible.
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class CrucibleScreens {
    /** How often an open screen reloads the crucible. */
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "crucible.screen";

    /** Why {@link #open} did not open a screen. */
    public enum Refusal {
        BUSY,
        GONE,
        REFUSED
    }

    /** The result of {@link #open}. */
    public record Opened(GuiSession session, Refusal refusal) {
        public boolean ok() {
            return session != null;
        }
    }

    static final class Screen {
        final CrucibleLink link;
        final Object key;
        CrucibleSnapshot crucible;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(CrucibleLink link, Object key, CrucibleSnapshot crucible) {
            this.link = link;
            this.key = key;
            this.crucible = crucible;
        }

        float temperature() {
            return crucible == null ? 0f : crucible.temperature();
        }

        int amount() {
            return crucible == null ? 0 : crucible.alloy().amount();
        }

        int capacity() {
            return crucible == null ? 1 : crucible.capacity();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final BiFunction<String, FluidAlloy, List<Component>> alloyLines;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    /**
     * @param alloyLines the alloy as TFC's screen text: the locale and the alloy to the lines (the metal the alloy makes
     *                   with its amount, then one line per metal)
     */
    public CrucibleScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style,
            BiFunction<String, FluidAlloy, List<Component>> alloyLines) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
        this.alloyLines = Objects.requireNonNull(alloyLines, "alloyLines");
    }

    /** Opens the screen of crucible {@code key} (normally the block position) for {@code viewer}, titled {@code label}. */
    public Opened open(Player viewer, Object key, CrucibleLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<CrucibleSnapshot> crucible = link.snapshot();
        if (crucible.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, crucible.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, crucible.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    /** The open screen of crucible {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before the device changes crucible {@code key}'s slots by another path: commits the open copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /**
     * TFC's shift over a mold (PourFasterPacket): the molten mold in input {@code tfcSlot} of {@code session} pours fast for
     * the next units. Commits the copy first. Returns whether the slot held a molten mold. Viewer thread.
     */
    boolean pourFaster(GuiSession session, int tfcSlot) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.crucible == null || tfcSlot < 0 || tfcSlot >= CrucibleScreenLayout.SLOT_OUTPUT) {
            return false;
        }
        flush(session);
        if (session.closed() || screen.crucible == null) {
            return false;
        }
        Optional<CrucibleSnapshot> after = screen.link.pourFaster(tfcSlot);
        after.ifPresent(a -> apply(session, screen, a));
        return after.isPresent();
    }

    /** Re-reads crucible {@code key} into its open screen (if any). Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    /** The crucible last shown in {@code session}. */
    public static Optional<CrucibleSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.crucible);
    }

    public static boolean isCrucible(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        CrucibleLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(CrucibleScreenLayout.CHEST, CrucibleScreenLayout.LAYERS).label(label);
        // shift-click order (CrucibleContainer.moveStack): the inputs; a container goes to the output
        for (int tfc = 0; tfc <= CrucibleScreenLayout.SLOT_OUTPUT; tfc++) {
            int t = tfc;
            b.input(CrucibleScreenLayout.chestSlot(tfc), (s, chestSlot, stack) -> !GuiButtons.isButton(stack)
                && roomFor(s, chestSlot) && link.isItemValid(t, stack));
        }
        for (int slot : CrucibleScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        for (int slot : CrucibleScreenLayout.FLUID_SLOTS) {
            b.button(slot, s -> fluidIcon(s.viewer(), screen), (s, click) -> { });
        }
        for (int slot : CrucibleScreenLayout.INFO_SLOTS) {
            b.button(slot, s -> infoIcon(s.viewer(), screen), (s, click) -> { });
        }
        return b.frames(s -> CrucibleScreenLayout.frames(screen.temperature(), screen.amount(), screen.capacity()))
            .lockedWhen(s -> screen.crucible == null)
            .onClose(this::closed)
            .build();
    }

    /** A one-item slot already holding its item takes nothing more. */
    private static boolean roomFor(GuiSession session, int chestSlot) {
        ItemStack there = session.getInventory().getItem(chestSlot);
        int tfc = CrucibleScreenLayout.tfcSlot(chestSlot);
        return there == null || there.isEmpty() || there.getAmount() < CrucibleScreenLayout.limit(tfc);
    }

    /** TFC's thermometer tooltip: the temperature in the heat tooltip style, nothing when cold. */
    ItemStack thermometerIcon(Player viewer, Screen screen) {
        Optional<TextComponent> text = style.formatColored(screen.temperature(), Messages.code(viewer), messages::get);
        return icon(text.map(t -> (Component) t), List.of());
    }

    /** The alloy's amount over the capacity; nothing for an empty crucible. */
    ItemStack fluidIcon(Player viewer, Screen screen) {
        if (screen.crucible == null || screen.amount() <= 0) {
            return icon(Optional.empty(), List.of());
        }
        String text = messages.get(Messages.code(viewer), "gui.crucible.fluid", String.valueOf(screen.amount()),
            String.valueOf(screen.capacity()));
        return icon(Optional.of(Component.text(text)), List.of());
    }

    /** TFC's text lines: what the alloy is with its amount, then each metal with its share. */
    ItemStack infoIcon(Player viewer, Screen screen) {
        if (screen.crucible == null || screen.amount() <= 0) {
            return icon(Optional.empty(), List.of());
        }
        List<Component> lines = alloyLines.apply(Messages.code(viewer), screen.crucible.alloy());
        if (lines.isEmpty()) {
            return icon(Optional.empty(), List.of());
        }
        return icon(Optional.of(lines.get(0)), lines.subList(1, lines.size()));
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

    // ---- sync -----------------------------------------------------------------------------------------------

    /** A click or drag happened in {@code session}'s window: commit the copy next tick (once per tick). */
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

    /** Gives back what a shift-click, drag or swap put above a slot's limit. Returns whether anything moved. */
    static boolean clamp(GuiSession session) {
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        boolean moved = false;
        for (int tfc = 0; tfc < CrucibleScreenLayout.SLOTS; tfc++) {
            int slot = CrucibleScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            int limit = CrucibleScreenLayout.limit(tfc);
            if (there == null || there.getAmount() <= limit) {
                continue;
            }
            ItemStack excess = there.clone();
            excess.setAmount(there.getAmount() - limit);
            there.setAmount(limit);
            inv.setItem(slot, there);
            give(viewer, excess);
            moved = true;
        }
        return moved;
    }

    /** Commits the chest's copy when it differs from the crucible's last known slots. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.crucible == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (CrucibleSnapshot.sameSlots(now, screen.crucible.slots())) {
            return;
        }
        Optional<CrucibleSnapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.crucible == null) {
            return;
        }
        Optional<CrucibleSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.crucible)) {
            apply(session, screen, now.get());
        }
    }

    /** Shows {@code crucible}: the ten slots, then the title frames and the button tooltips. */
    private static void apply(GuiSession session, Screen screen, CrucibleSnapshot crucible) {
        screen.crucible = crucible;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < CrucibleScreenLayout.SLOTS; tfc++) {
            ItemStack s = crucible.slot(tfc);
            inv.setItem(CrucibleScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The crucible is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private static void gone(GuiSession session, Screen screen) {
        CrucibleSnapshot last = screen.crucible;
        screen.crucible = null; // locks every slot until the screen is closed
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < CrucibleScreenLayout.SLOTS; tfc++) {
            int slot = CrucibleScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slot(tfc);
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
        if (screen.crucible != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!CrucibleSnapshot.sameSlots(now, screen.crucible.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : CrucibleScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the crucible holds them; the closed copy must not
        }
        open.remove(screen.key, session);
        screen.link.closed(viewer);
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(CrucibleScreenLayout.SLOTS);
        for (int tfc = 0; tfc < CrucibleScreenLayout.SLOTS; tfc++) {
            ItemStack s = inv.getItem(CrucibleScreenLayout.chestSlot(tfc));
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
