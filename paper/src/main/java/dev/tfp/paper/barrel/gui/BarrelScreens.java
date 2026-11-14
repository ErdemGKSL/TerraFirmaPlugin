/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/BarrelContainer.java @ c3b4d9536 (clicked: no slot edits while
 * sealed; onButtonPress; addContainerSlots; moveStack: fluid containers to the fluid-in slot, the rest to the item slot),
 * file: src/main/java/net/dries007/tfc/client/screen/BarrelScreen.java @ c3b4d9536 (the seal button's tooltip, the tank,
 * drawDisabled while sealed)
 */
package dev.tfp.paper.barrel.gui;

import dev.tfp.core.vessel.FluidAmount;
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
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-PROC-09: the barrel screen (TFC {@code BarrelScreen} + {@code BarrelContainer}) on the GUI framework
 * ({@link GuiService}), provided as a module service by {@link BarrelGuiModule}.
 *
 * <p>The chest's three slots are a copy of the barrel's; the barrel ({@link BarrelLink}) stays the authority. After every
 * click or drag in the screen the copy is committed one tick later (the click has been applied by then) and the barrel's
 * answer (instant recipes, the excess moving in) is written back; every {@link #RELOAD_TICKS} ticks the screen reloads the
 * barrel to show changes from outside (sealed recipes finishing, another player's bucket). While sealed every slot click is
 * refused ({@code BarrelContainer.clicked}) and TFC's disabled highlight covers the slots. The seal button commits the
 * copy, then toggles the seal.
 *
 * <p>One viewer per barrel: TFC lets several players share one menu over one block entity, but here each viewer has a
 * copy, and two copies of the same slots could duplicate items, so a second player is refused while one has it open
 * (ledger divergence).
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class BarrelScreens {
    /** How often an open screen reloads the barrel. */
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "barrel.screen";

    /** Why {@link #open} did not open a screen. */
    public enum Refusal {
        /** Another player has this barrel open. */
        BUSY,
        /** The barrel is gone. */
        GONE,
        /** The server refused the inventory (a plugin cancelled the open event). */
        REFUSED
    }

    /** The result of {@link #open}: the session or why there is none. */
    public record Opened(GuiSession session, Refusal refusal) {
        public boolean ok() {
            return session != null;
        }
    }

    /** One open screen's barrel: the link, the last known barrel (null once gone) and the scheduled work. */
    static final class Screen {
        final BarrelLink link;
        final Object key;
        BarrelSnapshot barrel;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(BarrelLink link, Object key, BarrelSnapshot barrel) {
            this.link = link;
            this.key = key;
            this.barrel = barrel;
        }

        boolean sealed() {
            return barrel != null && barrel.sealed();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public BarrelScreens(GuiService gui, Scheduler scheduler, Messages messages) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Opens the screen of the barrel {@code key} (any value with equals/hashCode, normally the block position) for
     * {@code viewer}, titled {@code label} (TFC: the barrel's block name).
     */
    public Opened open(Player viewer, Object key, BarrelLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<BarrelSnapshot> barrel = link.snapshot();
        if (barrel.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, barrel.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, barrel.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    /** The open screen of barrel {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Number of open barrel screens. */
    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /**
     * Before the device changes barrel {@code key}'s slots by another path: commits the open screen's copy (if any) so the
     * change starts from it; call {@link #reload(Object)} after the change. Viewer thread.
     */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads barrel {@code key} into its open screen (if any). Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    /** The barrel last shown in {@code session}, empty for another screen or once the barrel is gone. */
    public static Optional<BarrelSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.barrel);
    }

    /** True when {@code session} is a barrel screen. */
    public static boolean isBarrel(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        BarrelLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(BarrelScreenLayout.CHEST, BarrelScreenLayout.LAYERS).label(label);
        // shift-clicks: fluid containers to the fluid-in slot, the rest to the item slot (BarrelContainer.moveStack)
        b.input(BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_FLUID_IN),
            (s, slot, stack) -> !GuiButtons.isButton(stack) && link.acceptsContainer(stack));
        b.input(BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_ITEM),
            (s, slot, stack) -> !GuiButtons.isButton(stack) && link.acceptsItem(stack));
        b.output(BarrelScreenLayout.chestSlot(BarrelScreenLayout.TFC_FLUID_OUT));
        for (int slot : BarrelScreenLayout.TANK_SLOTS) {
            boolean drawn = slot == BarrelScreenLayout.TANK_SLOT;
            b.button(slot, s -> tankIcon(s.viewer(), screen, drawn), (s, click) -> { });
        }
        b.button(BarrelScreenLayout.SEAL_SLOT, s -> sealIcon(s.viewer(), screen), (s, click) -> toggleSeal(s));
        return b.frames(s -> BarrelScreenLayout.frames(screen.sealed()))
            .lockedWhen(s -> screen.barrel == null || screen.barrel.sealed())
            .onClose(this::closed)
            .build();
    }

    private ItemStack tankIcon(Player viewer, Screen screen, boolean drawn) {
        BarrelSnapshot barrel = screen.barrel;
        FluidAmount fluid = barrel == null ? FluidAmount.EMPTY : barrel.fluid();
        int capacity = barrel == null ? 1 : barrel.capacity();
        Component tooltip = BarrelTank.tooltip(fluid, messages.get(viewer, "gui.barrel.fluid")).orElse(null);
        return BarrelTank.icon(gui.glyphs(), drawn, fluid, capacity, tooltip);
    }

    private ItemStack sealIcon(Player viewer, Screen screen) {
        boolean sealed = screen.sealed();
        ItemStack icon = GuiButtons.icon(gui.glyphs(), messages.get(viewer, sealed ? "gui.barrel.unseal" : "gui.barrel.seal"));
        if (sealed) {
            List<String> lines = screen.link.sealedLines(viewer, screen.barrel);
            if (!lines.isEmpty()) {
                ItemMeta meta = icon.getItemMeta();
                List<Component> lore = new ArrayList<>();
                for (String line : lines) {
                    lore.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lore);
                icon.setItemMeta(meta);
            }
        }
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

    /** Commits the chest's copy when it differs from the barrel's last known slots. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.barrel == null || screen.barrel.sealed()) {
            return;
        }
        List<ItemStack> now = copy(session.getInventory());
        if (BarrelSnapshot.sameSlots(now, screen.barrel.slots())) {
            return;
        }
        Optional<BarrelSnapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.barrel == null) {
            return;
        }
        Optional<BarrelSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.barrel)) {
            apply(session, screen, now.get());
        }
    }

    private void toggleSeal(GuiSession session) {
        Screen screen = state(session);
        flush(session);
        if (screen == null || session.closed() || screen.barrel == null) {
            return;
        }
        Optional<BarrelSnapshot> after = screen.link.toggleSeal(session.viewer());
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
        }
    }

    /** Shows {@code barrel}: the three slots, then the title (frames) and the buttons. */
    private static void apply(GuiSession session, Screen screen, BarrelSnapshot barrel) {
        screen.barrel = barrel;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < BarrelScreenLayout.TFC_SLOTS; tfc++) {
            ItemStack s = barrel.slot(tfc);
            inv.setItem(BarrelScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /**
     * The barrel is gone (TFC: {@code stillValid} fails and the menu closes). The barrel's own slots were dropped with the
     * block; what the viewer put into the copy since the last commit goes back to the viewer.
     */
    private static void gone(GuiSession session, Screen screen) {
        BarrelSnapshot last = screen.barrel;
        screen.barrel = null; // locks every slot until the screen is closed
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < BarrelScreenLayout.TFC_SLOTS; tfc++) {
            int slot = BarrelScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slot(tfc);
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
        if (screen.barrel != null && !screen.barrel.sealed()) {
            List<ItemStack> now = copy(session.getInventory());
            if (!BarrelSnapshot.sameSlots(now, screen.barrel.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : BarrelScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the barrel holds them; the closed copy must not
        }
        open.remove(screen.key, session);
        screen.link.closed(viewer);
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(BarrelScreenLayout.TFC_SLOTS);
        for (int tfc = 0; tfc < BarrelScreenLayout.TFC_SLOTS; tfc++) {
            ItemStack s = inv.getItem(BarrelScreenLayout.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }
}
