/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/GrillContainer.java @ c3b4d9536 (addContainerSlots; moveStack:
 *       player slots shift-click into the fuel slots, other items into the grill slots 4..8),
 * file: src/main/java/net/dries007/tfc/client/screen/GrillScreen.java @ c3b4d9536 (the thermometer and its tooltip,
 *       heatTooltipStyle.formatColored(temperature)),
 * file: src/main/java/net/dries007/tfc/common/blockentities/GrillBlockEntity.java @ c3b4d9536 (getSlotStackLimit,
 *       isItemValid)
 */
package dev.tfp.paper.grill.gui;

import dev.tfp.core.firepit.FirepitSim;
import dev.tfp.core.grill.GrillSim;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * PAR-HEAT-08: the grill screen (TFC {@code GrillScreen} + {@code GrillContainer}) on the GUI framework
 * ({@link GuiService}), provided as a module service by {@code GrillModule}. It works as the firepit screen
 * ({@code FirepitScreens}): the chest's nine slots are a copy of the grill's, committed one tick after a click or drag and
 * reloaded every {@link #RELOAD_TICKS} ticks (the thermometer, the burning fuel and the cooked items follow); one viewer
 * per grill.
 *
 * <p>Slots, as TFC's {@code isItemValid}: fuel goes only into the top fuel slot (TFC slot 3), heatable items into the five
 * grill slots; fuel slots 0 to 2 are filled by the cascade and can only be taken from. Shift-click from the player
 * inventory sends {@code #tfc:firepit_fuel} to the top fuel slot only and anything else to the grill slots in order
 * ({@code moveStack}). Every slot holds one item.
 *
 * <p>Threads: every method runs on the viewer's thread ({@code Scheduler.entity}); the busy map is thread-safe.
 */
public final class GrillScreens {
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "grill.screen";

    /** Why {@link #open} did not open a screen. */
    public enum Refusal {
        BUSY,
        GONE,
        REFUSED
    }

    public record Opened(GuiSession session, Refusal refusal) {
        public boolean ok() {
            return session != null;
        }
    }

    static final class Screen {
        final GrillLink link;
        final Object key;
        GrillSnapshot grill;
        Scheduler.TaskHandle reloader;
        boolean pending;
        /** A shift-click from the player inventory is being routed (set and cleared by {@link GrillGuiListener}). */
        boolean shiftIn;

        Screen(GrillLink link, Object key, GrillSnapshot grill) {
            this.link = link;
            this.key = key;
            this.grill = grill;
        }

        float temperature() {
            return grill == null ? 0f : grill.temperature();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final HeatDisplayStyle style;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();

    public GrillScreens(GuiService gui, Scheduler scheduler, Messages messages, HeatDisplayStyle style) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.style = Objects.requireNonNull(style, "style");
    }

    /** Opens the screen of grill {@code key} (normally the block position) for {@code viewer}, titled {@code label}. */
    public Opened open(Player viewer, Object key, GrillLink link, Function<Player, String> label) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(link, "link");
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return new Opened(null, Refusal.BUSY);
        }
        Optional<GrillSnapshot> grill = link.snapshot();
        if (grill.isEmpty()) {
            return new Opened(null, Refusal.GONE);
        }
        Screen screen = new Screen(link, key, grill.get());
        GuiSession session = gui.open(viewer, screen(screen, label));
        if (session == null) {
            return new Opened(null, Refusal.REFUSED);
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, grill.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return new Opened(session, null);
    }

    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /**
     * PAR-INFRA-66: the key of the open screen at {@code block} (the module's screen key is a record of the world id and
     * the block position; it is package-private there, so it is matched by its components).
     */
    public Optional<Object> keyAt(Block block) {
        for (Map.Entry<Object, GuiSession> e : open.entrySet()) {
            if (!e.getValue().closed() && atBlock(e.getKey(), block)) {
                return Optional.of(e.getKey());
            }
        }
        return Optional.empty();
    }

    private static boolean atBlock(Object key, Block block) {
        if (!(key instanceof Record record)) {
            return false;
        }
        try {
            Object[] v = new Object[4];
            int i = 0;
            for (java.lang.reflect.RecordComponent c : record.getClass().getRecordComponents()) {
                if (i >= 4) {
                    return false;
                }
                java.lang.reflect.Method m = c.getAccessor();
                m.setAccessible(true);
                v[i++] = m.invoke(record);
            }
            return i == 4 && block.getWorld().getUID().equals(v[0]) && Integer.valueOf(block.getX()).equals(v[1])
                && Integer.valueOf(block.getY()).equals(v[2]) && Integer.valueOf(block.getZ()).equals(v[3]);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public int openCount() {
        return (int) open.values().stream().filter(s -> !s.closed()).count();
    }

    /** Before the device changes grill {@code key}'s slots by another path: commits the open copy. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads grill {@code key} into its open screen (if any). */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    public static Optional<GrillSnapshot> shown(GuiSession session) {
        Screen s = state(session);
        return s == null ? Optional.empty() : Optional.ofNullable(s.grill);
    }

    public static boolean isGrill(GuiSession session) {
        return state(session) != null;
    }

    // ---- the screen -----------------------------------------------------------------------------------------

    private TfpGui screen(Screen screen, Function<Player, String> label) {
        GrillLink link = screen.link;
        TfpGui.Builder b = TfpGui.builder(GrillScreenLayout.CHEST, GrillScreenLayout.LAYERS).label(label);
        // shift-click order: the fuel input, then the grill slots (GrillContainer.moveStack)
        List<Integer> inputs = new ArrayList<>();
        inputs.add(FirepitSim.SLOT_FUEL_INPUT);
        for (int s = GrillSim.SLOT_EXTRA_INPUT_START; s <= GrillSim.SLOT_EXTRA_INPUT_END; s++) {
            inputs.add(s);
        }
        for (int tfc : inputs) {
            int slot = GrillScreenLayout.chestSlot(tfc);
            // GrillContainer.moveStack: a #tfc:firepit_fuel item shift-clicks into the fuel slots only, never the grill
            boolean grillSlot = GrillSim.isGrillSlot(tfc);
            b.input(slot, (s, chestSlot, stack) -> !GuiButtons.isButton(stack) && roomFor(s, chestSlot)
                && link.isItemValid(tfc, stack) && !(grillSlot && screen.shiftIn && link.isFirepitFuel(stack)));
        }
        for (int tfc = FirepitSim.SLOT_FUEL_CONSUME; tfc < FirepitSim.SLOT_FUEL_INPUT; tfc++) {
            b.output(GrillScreenLayout.chestSlot(tfc));
        }
        for (int slot : GrillScreenLayout.THERMOMETER_SLOTS) {
            b.button(slot, s -> thermometerIcon(s.viewer(), screen), (s, click) -> { });
        }
        return b.frames(s -> GrillScreenLayout.frames(screen.temperature()))
            .lockedWhen(s -> screen.grill == null)
            .onClose(this::closed)
            .build();
    }

    private static boolean roomFor(GuiSession session, int chestSlot) {
        ItemStack there = session.getInventory().getItem(chestSlot);
        int tfc = GrillScreenLayout.tfcSlot(chestSlot);
        return there == null || there.isEmpty() || there.getAmount() < GrillScreenLayout.limit(tfc);
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
        for (int tfc = 0; tfc < GrillSim.SLOTS; tfc++) {
            int slot = GrillScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            int limit = GrillScreenLayout.limit(tfc);
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

    void flush(GuiSession session) {
        Screen screen = state(session);
        if (screen == null || session.closed() || screen.grill == null) {
            return;
        }
        clamp(session);
        List<ItemStack> now = copy(session.getInventory());
        if (GrillSnapshot.sameSlots(now, screen.grill.slots())) {
            return;
        }
        Optional<GrillSnapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.grill == null) {
            return;
        }
        Optional<GrillSnapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.grill)) {
            apply(session, screen, now.get());
        }
    }

    private static void apply(GuiSession session, Screen screen, GrillSnapshot grill) {
        screen.grill = grill;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < GrillSim.SLOTS; tfc++) {
            ItemStack s = grill.slot(tfc);
            inv.setItem(GrillScreenLayout.chestSlot(tfc), s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The grill is gone: what the viewer put into the copy since the last commit goes back to the viewer. */
    private static void gone(GuiSession session, Screen screen) {
        GrillSnapshot last = screen.grill;
        screen.grill = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < GrillSim.SLOTS; tfc++) {
            int slot = GrillScreenLayout.chestSlot(tfc);
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
        if (screen.grill != null) {
            clamp(session);
            List<ItemStack> now = copy(session.getInventory());
            if (!GrillSnapshot.sameSlots(now, screen.grill.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : GrillScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null); // the grill holds them; the closed copy must not
        }
        open.remove(screen.key, session);
    }

    static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(GrillSim.SLOTS);
        for (int tfc = 0; tfc < GrillSim.SLOTS; tfc++) {
            ItemStack s = inv.getItem(GrillScreenLayout.chestSlot(tfc));
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
