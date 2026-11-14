/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/FirepitContainer.java @ c3b4d9536 (addContainerSlots; moveStack:
 *       player slots shift-click into SLOT_FUEL_INPUT..SLOT_ITEM_INPUT),
 * file: src/main/java/net/dries007/tfc/client/screen/FirepitScreen.java @ c3b4d9536 (the thermometer and its tooltip,
 *       heatTooltipStyle.formatColored(temperature)),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AbstractFirepitBlockEntity.java @ c3b4d9536
 *       (getSlotStackLimit, isItemValid)
 */
package dev.tfp.paper.firepit.gui;

import dev.tfp.core.firepit.FirepitSim;
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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-HEAT-07: the firepit screen (TFC {@code FirepitScreen} + {@code FirepitContainer}) on the GUI framework
 * ({@link GuiService}), provided as a module service by {@link FirepitGuiModule}.
 *
 * <p>Slots, as TFC's {@code isItemValid}: fuel goes only into the top fuel slot (TFC slot 3), heatable items only into the
 * input; fuel slots 0 to 2 are filled by the firepit's cascade and can only be taken from, as can the outputs. Shift-click
 * from the player inventory tries the top fuel slot, then the input ({@code moveStack}). Every slot but the outputs holds
 * one item ({@code getSlotStackLimit}): a click places one and leaves the rest on the cursor; a shift-click, drag or swap
 * that put more in gives the excess back to the player.
 *
 * <p>The chest's seven slots are a copy of the firepit's; the firepit ({@link FirepitLink}) stays the authority. After a
 * click or drag the copy is committed one tick later and the firepit's answer (the cascade) is written back; every
 * {@link #RELOAD_TICKS} ticks the screen reloads the firepit so the thermometer frame, the burning fuel and the cooked
 * outputs follow. One viewer per firepit, as the barrel screen (ledger divergence).
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class FirepitScreens {
    /** How often an open screen reloads the firepit. */
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "firepit.screen";

    /** Why {@link #open} did not open a screen. */
    public enum Refusal {
        /** Another player has this firepit open. */
        BUSY,
        /** The firepit is gone. */
        GONE,
        /** The server refused the inventory. */
        REFUSED
    }

    /** The result of {@link #open}. */
    public record Opened(GuiSession session, Refusal refusal) {
        public boolean ok() {
            return session != null;
        }
    }

    static final class Screen {
        final FirepitLink link;
        final Object key;
        FirepitSnapshot firepit;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(FirepitLink link, Object key, FirepitSnapshot firepit) {
            this.link = link;
            this.key = key;
            this.firepit = firepit;
        }

        float temperature() {
            return firepit == null ? 0f : firepit.temperature();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public FirepitScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Opens the screen of firepit {@code key} (normally the block position) for {@code viewer}, titled {@code label}. */
    public Opened open(Player viewer, Object key, FirepitLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<FirepitSnapshot> firepit = link.snapshot();
        if (firepit.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, firepit.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, firepit.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    /** The open screen of firepit {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before the device changes firepit {@code key}'s slots by another path: commits the open copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads firepit {@code key} into its open screen (if any). Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    /** The firepit last shown in {@code session}. */
    public static Optional<FirepitSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.firepit);
    }

    public static boolean isFirepit(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        FirepitLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(FirepitScreenLayout.CHEST, FirepitScreenLayout.LAYERS).label(label);
        // shift-click order: SLOT_FUEL_INPUT, then SLOT_ITEM_INPUT (FirepitContainer.moveStack)
        for (int tfc : new int[] {FirepitSim.SLOT_FUEL_INPUT, FirepitSim.SLOT_ITEM_INPUT}) {
            int slot = FirepitScreenLayout.chestSlot(tfc);
            b.input(slot, (s, chestSlot, stack) -> !GuiButtons.isButton(stack) && roomFor(s, chestSlot)
                && link.isItemValid(tfc, stack));
        }
        for (int tfc = FirepitSim.SLOT_FUEL_CONSUME; tfc < FirepitSim.SLOT_FUEL_INPUT; tfc++) {
            b.output(FirepitScreenLayout.chestSlot(tfc));
        }
        b.output(FirepitScreenLayout.chestSlot(FirepitSim.SLOT_OUTPUT_1));
        b.output(FirepitScreenLayout.chestSlot(FirepitSim.SLOT_OUTPUT_2));
        for (int slot : FirepitScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        return b.frames(s -> FirepitScreenLayout.frames(screen.temperature()))
            .lockedWhen(s -> screen.firepit == null)
            .onClose(this::closed)
            .build();
    }

    /** A one-item slot already holding its item takes nothing more. */
    private static boolean roomFor(GuiSession session, int chestSlot) {
        ItemStack there = session.getInventory().getItem(chestSlot);
        int tfc = FirepitScreenLayout.tfcSlot(chestSlot);
        return there == null || there.isEmpty() || there.getAmount() < FirepitScreenLayout.limit(tfc);
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

    /**
     * Gives back what a shift-click, drag or swap put above a slot's limit (TFC's slots never take more). Returns whether
     * anything moved.
     */
    static boolean clamp(GuiSession session) {
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        boolean moved = false;
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            int slot = FirepitScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            int limit = FirepitScreenLayout.limit(tfc);
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

    /** Commits the chest's copy when it differs from the firepit's last known slots. */
    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.firepit == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (FirepitSnapshot.sameSlots(now, screen.firepit.slots())) {
            return;
        }
        Optional<FirepitSnapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.firepit == null) {
            return;
        }
        Optional<FirepitSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.firepit)) {
            apply(session, screen, now.get());
        }
    }

    /** Shows {@code firepit}: the seven slots, then the title (thermometer frame) and the thermometer tooltips. */
    private static void apply(GuiSession session, Screen screen, FirepitSnapshot firepit) {
        screen.firepit = firepit;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            ItemStack s = firepit.slot(tfc);
            inv.setItem(FirepitScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The firepit is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private static void gone(GuiSession session, Screen screen) {
        FirepitSnapshot last = screen.firepit;
        screen.firepit = null; // locks every slot until the screen is closed
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            int slot = FirepitScreenLayout.chestSlot(tfc);
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
        if (screen.firepit != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!FirepitSnapshot.sameSlots(now, screen.firepit.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : FirepitScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the firepit holds them; the closed copy must not
        }
        open.remove(screen.key, session);
        screen.link.closed(viewer);
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(FirepitSim.SLOTS);
        for (int tfc = 0; tfc < FirepitSim.SLOTS; tfc++) {
            ItemStack s = inv.getItem(FirepitScreenLayout.chestSlot(tfc));
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
