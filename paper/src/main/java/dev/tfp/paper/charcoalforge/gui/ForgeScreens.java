/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/CharcoalForgeContainer.java @ c3b4d9536 (addContainerSlots, moveStack),
 *       src/main/java/net/dries007/tfc/client/screen/CharcoalForgeScreen.java @ c3b4d9536 (renderBg: the temperature bar;
 *       the tooltip heatTooltipStyle.formatColored(temperature))
 */
package dev.tfp.paper.charcoalforge.gui;

import dev.tfp.core.charcoalforge.CharcoalForgeSim;
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
 * PAR-METAL-09: the charcoal forge screen (TFC {@code CharcoalForgeScreen} + {@code CharcoalForgeContainer}) on the GUI
 * framework ({@link GuiService}), provided as a module service by {@code CharcoalForgeModule}.
 *
 * <p>Fourteen slots as TFC: five fuel, five inputs and four extra (molds and vessels), one item each. A shift-click from the
 * player inventory tries the extras first, then the fuel and input slots (TFC {@code moveStack}). Four buttons over the
 * temperature bar give TFC's tooltip (the temperature in the heat tooltip style); the art layer draws the bar. The chest's
 * slots are a copy of the forge's; the forge ({@link ForgeLink}) stays the authority, committed one tick after a click and
 * reloaded every {@link #RELOAD_TICKS} ticks, which also lets the viewer watch the temperature and the inputs heat. One viewer
 * per forge.
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class ForgeScreens {
    /** How often an open screen reloads the forge. */
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "charcoalforge.screen";

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
        final ForgeLink link;
        final Object key;
        ForgeSnapshot forge;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(ForgeLink link, Object key, ForgeSnapshot forge) {
            this.link = link;
            this.key = key;
            this.forge = forge;
        }

        float temperature() {
            return forge == null ? 0f : forge.temperature();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public ForgeScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Opens the screen of forge {@code key} (normally the block position) for {@code viewer}, titled {@code label}. */
    public Opened open(Player viewer, Object key, ForgeLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<ForgeSnapshot> forge = link.snapshot();
        if (forge.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, forge.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, forge.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    /** The open screen of forge {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before the device changes forge {@code key}'s slots by another path: commits the open copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads forge {@code key} into its open screen (if any). Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    /** The forge last shown in {@code session}. */
    public static Optional<ForgeSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.forge);
    }

    public static boolean isForge(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        ForgeLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(ForgeScreenLayout.CHEST, ForgeScreenLayout.LAYERS).label(label);
        // shift-click order (CharcoalForgeContainer.moveStack): the extras, then the fuel and input slots
        for (int tfc = CharcoalForgeSim.SLOT_EXTRA_MIN; tfc <= CharcoalForgeSim.SLOT_EXTRA_MAX; tfc++) {
            input(b, link, tfc);
        }
        for (int tfc = CharcoalForgeSim.SLOT_FUEL_MIN; tfc <= CharcoalForgeSim.SLOT_INPUT_MAX; tfc++) {
            input(b, link, tfc);
        }
        for (int slot : ForgeScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        return b.frames(s -> ForgeScreenLayout.frames(screen.temperature()))
            .lockedWhen(s -> screen.forge == null)
            .onClose(this::closed)
            .build();
    }

    private static void input(TfpGui.Builder b, ForgeLink link, int tfc) {
        b.input(ForgeScreenLayout.chestSlot(tfc), (s, chestSlot, stack) -> !GuiButtons.isButton(stack)
            && roomFor(s, chestSlot) && link.isItemValid(tfc, stack));
    }

    /** A one-item slot already holding its item takes nothing more. */
    private static boolean roomFor(GuiSession session, int chestSlot) {
        ItemStack there = session.getInventory().getItem(chestSlot);
        int tfc = ForgeScreenLayout.tfcSlot(chestSlot);
        return there == null || there.isEmpty() || there.getAmount() < ForgeScreenLayout.limit(tfc);
    }

    /** TFC's thermometer tooltip: the temperature in the heat tooltip style, nothing when cold. */
    ItemStack thermometerIcon(Player viewer, Screen screen) {
        Optional<TextComponent> text = style.formatColored(screen.temperature(), Messages.code(viewer), messages::get);
        return icon(text.map(t -> (Component) t), List.of());
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
        for (int tfc = 0; tfc < ForgeScreenLayout.SLOTS; tfc++) {
            int slot = ForgeScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            int limit = ForgeScreenLayout.limit(tfc);
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

    /** Commits the chest's copy when it differs from the forge's last known slots. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.forge == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (ForgeSnapshot.sameSlots(now, screen.forge.slots())) {
            return;
        }
        Optional<ForgeSnapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.forge == null) {
            return;
        }
        Optional<ForgeSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.forge)) {
            apply(session, screen, now.get());
        }
    }

    /** Shows {@code forge}: the ten slots, then the title frames and the button tooltips. */
    private static void apply(GuiSession session, Screen screen, ForgeSnapshot forge) {
        screen.forge = forge;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < ForgeScreenLayout.SLOTS; tfc++) {
            ItemStack s = forge.slot(tfc);
            inv.setItem(ForgeScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The forge is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private static void gone(GuiSession session, Screen screen) {
        ForgeSnapshot last = screen.forge;
        screen.forge = null; // locks every slot until the screen is closed
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < ForgeScreenLayout.SLOTS; tfc++) {
            int slot = ForgeScreenLayout.chestSlot(tfc);
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
        if (screen.forge != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!ForgeSnapshot.sameSlots(now, screen.forge.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : ForgeScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the forge holds them; the closed copy must not
        }
        open.remove(screen.key, session);
        screen.link.closed(viewer);
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(ForgeScreenLayout.SLOTS);
        for (int tfc = 0; tfc < ForgeScreenLayout.SLOTS; tfc++) {
            ItemStack s = inv.getItem(ForgeScreenLayout.chestSlot(tfc));
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
