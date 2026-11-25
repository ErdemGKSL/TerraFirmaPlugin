/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/NestBoxContainer.java @ c3b4d9536 (addContainerSlots, moveStack:
 *       the inventory fills slots 0 to 3 in order),
 *       src/main/java/net/dries007/tfc/client/screen/NestBoxScreen.java @ c3b4d9536 (the 2x2 inventory screen),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onContainerOpen: the infestation roll)
 */
package dev.tfp.paper.nestbox;

import dev.tfp.core.critter.PestInfestation;
import dev.tfp.core.nestbox.NestBoxRules;
import dev.tfp.core.nestbox.NestBoxScreenLayout;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.critter.CritterModule;
import dev.tfp.paper.i18n.Messages;
import dev.tfp.paper.module.ModuleServices;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PAR-FAUNA-07: the nest box screen (TFC {@code NestBoxScreen} + {@code NestBoxContainer}) on the GUI framework, in the
 * layout of {@link NestBoxScreenLayout}: four slots, eggs only, one per slot.
 *
 * <p>As the powderkeg screen: the chest's four slots are a copy of the box's; the box ({@link NestBoxLink}) stays the
 * authority. A click or drag commits the copy one tick later; every {@link #RELOAD_TICKS} ticks the screen reloads the
 * box (a hen laid, an egg hatched). One viewer per box (two copies of the same slots could duplicate items; ledger).
 * Threads: the viewer's thread; the open map is thread-safe.
 */
public final class NestBoxScreens implements Listener {
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "nestbox.screen";
    /** The item tag TFC counts for an infestation ({@code TFCTags.Items.FOODS}). */
    static final String FOOD_TAG = "c:foods";

    /** What an open rolls: the viewer, the box and the food stacks (at most five) in its slots. */
    @FunctionalInterface
    public interface PestRoll {
        void roll(Player viewer, Location box, int foodStacks);
    }

    static final class Screen {
        final NestBoxLink link;
        final Object key;
        NestBoxLink.Snapshot box;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(NestBoxLink link, Object key, NestBoxLink.Snapshot box) {
            this.link = link;
            this.key = key;
            this.box = box;
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Predicate<ItemStack> egg;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();
    private volatile PestRoll pests;
    private volatile Predicate<ItemStack> foodTest;

    public NestBoxScreens(GuiService gui, Scheduler scheduler, Messages messages, Predicate<ItemStack> egg) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.egg = Objects.requireNonNull(egg, "egg");
    }

    /** Opens the box's screen for {@code viewer}; empty when another player has it open, it is gone or refused. */
    public Optional<GuiSession> open(Player viewer, NestBoxLink link) {
        Object key = link.key();
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return Optional.empty();
        }
        Optional<NestBoxLink.Snapshot> box = link.snapshot();
        if (box.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(link, key, box.get());
        GuiSession session = gui.open(viewer, screen());
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, box.get());
        rollInfestation(viewer, key, box.get().slots());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return Optional.of(session);
    }

    /** Replaces the infestation roll and the food test (tests; the defaults are {@link CritterModule#infest} and the food tag). */
    public void pests(PestRoll roll, Predicate<ItemStack> food) {
        this.pests = roll;
        this.foodTest = food;
    }

    /**
     * TFC {@code onContainerOpen}: opening a nest box rolls an infestation at its block with the food stacks in its slots (at
     * most five; the {@code enableInfestations} option is on, as TFC's default). Viewer thread.
     */
    public void rollInfestation(Player viewer, Object key, List<ItemStack> slots) {
        if (!(key instanceof NestBoxLink.Key k)) {
            return;
        }
        World world = Bukkit.getWorld(k.world());
        if (world == null) {
            return;
        }
        PestRoll roll = pests != null ? pests : lookupRoll();
        if (roll != null) {
            roll.roll(viewer, new Location(world, k.x(), k.y(), k.z()), foodCount(slots));
        }
    }

    private int foodCount(List<ItemStack> slots) {
        Predicate<ItemStack> test = foodTest;
        if (test == null) {
            Tags tags = services().flatMap(s -> s.get(Tags.class)).orElse(null);
            test = stack -> tags != null && tags.is(stack, FOOD_TAG);
        }
        boolean[] food = new boolean[slots.size()];
        for (int i = 0; i < food.length; i++) {
            ItemStack stack = slots.get(i);
            food[i] = stack != null && !stack.isEmpty() && test.test(stack);
        }
        return PestInfestation.countFood(food);
    }

    private PestRoll lookupRoll() {
        CritterModule critter = services().flatMap(s -> s.get(CritterModule.class)).orElse(null);
        return critter == null ? null
            : (viewer, box, food) -> critter.infest(box, PestInfestation.openLevel(true, food), viewer);
    }

    private static Optional<ModuleServices> services() {
        try {
            return Optional.of(JavaPlugin.getPlugin(TfpPlugin.class).moduleServices());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    /** The open screen of box {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Before another path changes box {@code key}: commits the open screen's copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads box {@code key} into its open screen. Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    public static boolean isNestBox(GuiSession session) {
        return state(session) != null;
    }

    private TfpGui screen() {
        TfpGui.Builder b = TfpGui.builder(NestBoxScreenLayout.CHEST, NestBoxScreenLayout.LAYERS)
            .label(p -> messages.get(p, "gui.nestbox.title"));
        for (int tfc = 0; tfc < NestBoxRules.SLOTS; tfc++) {
            b.input(NestBoxScreenLayout.chestSlot(tfc), (s, slot, stack) -> !GuiButtons.isButton(stack) && egg.test(stack));
        }
        return b.onClose(this::closed).build();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isNestBox(session)) {
            touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isNestBox(session)) {
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
        if (screen == null || session.closed() || screen.box == null) {
            return;
        }
        List<ItemStack> now = copy(session.getInventory());
        if (NestBoxLink.Snapshot.sameSlots(now, screen.box.slots())) {
            return;
        }
        Optional<NestBoxLink.Committed> after = screen.link.commit(now);
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
        if (session.closed() || screen.box == null) {
            return;
        }
        Optional<NestBoxLink.Snapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.box)) {
            apply(session, screen, now.get());
        }
    }

    private static void apply(GuiSession session, Screen screen, NestBoxLink.Snapshot box) {
        screen.box = box;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < NestBoxRules.SLOTS; tfc++) {
            ItemStack s = box.slots().get(tfc);
            inv.setItem(NestBoxScreenLayout.chestSlot(tfc), s == null || s.isEmpty() ? null : s);
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

    /** The box is gone: what the viewer put into the copy since the last commit goes back to the viewer; the screen closes. */
    private static void gone(GuiSession session, Screen screen) {
        NestBoxLink.Snapshot last = screen.box;
        screen.box = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < NestBoxRules.SLOTS; tfc++) {
            int slot = NestBoxScreenLayout.chestSlot(tfc);
            ItemStack there = inv.getItem(slot);
            ItemStack known = last == null ? ItemStack.empty() : last.slots().get(tfc);
            inv.setItem(slot, null);
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
        if (screen.reloader != null) {
            screen.reloader.cancel();
        }
        if (screen.box != null) {
            List<ItemStack> now = copy(session.getInventory());
            if (!NestBoxLink.Snapshot.sameSlots(now, screen.box.slots())) {
                Optional<NestBoxLink.Committed> after = screen.link.commit(now);
                if (after.isEmpty()) {
                    gone(session, screen);
                } else {
                    giveBack(viewer, after.get().overflow());
                }
            }
        }
        for (int slot : NestBoxScreenLayout.CHEST_SLOTS) {
            session.getInventory().setItem(slot, null);
        }
        open.remove(screen.key, session);
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(NestBoxRules.SLOTS);
        for (int tfc = 0; tfc < NestBoxRules.SLOTS; tfc++) {
            ItemStack s = inv.getItem(NestBoxScreenLayout.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }
}
