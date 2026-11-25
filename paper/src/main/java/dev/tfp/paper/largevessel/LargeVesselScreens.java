/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/LargeVesselContainer.java @ c3b4d9536 (clicked: container slots
 *       refuse clicks while sealed; moveStack: no shift-click while sealed; onButtonPress),
 *       src/main/java/net/dries007/tfc/client/screen/LargeVesselScreen.java @ c3b4d9536 (the seal button),
 *       src/main/java/net/dries007/tfc/client/screen/button/VesselSealButton.java @ c3b4d9536 (the sprites),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onContainerOpen: the infestation roll, PAR-FAUNA-38)
 */
package dev.tfp.paper.largevessel;

import dev.tfp.core.critter.PestInfestation;
import dev.tfp.core.vessel.LargeVesselRules;
import dev.tfp.core.gui.SampleScreen;
import dev.tfp.paper.TfpPlugin;
import dev.tfp.paper.critter.CritterModule;
import dev.tfp.paper.gui.GuiButtons;
import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.gui.TfpGui;
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
 * PAR-POT-06: the large vessel screen (TFC {@code LargeVesselScreen} + {@code LargeVesselContainer}) on the GUI framework, in
 * the layout of {@link SampleScreen} (TFC's art in a 3-row chest, nine slots, the seal button on chest slot 16).
 *
 * <p>As the powderkeg and barrel screens: the chest's nine slots are a copy of the vessel's; the vessel
 * ({@link LargeVesselLink}) stays the authority. A click or drag commits the copy one tick later; every
 * {@link #RELOAD_TICKS} ticks the screen reloads the vessel. While sealed every slot click is refused; the seal button
 * commits, then toggles the seal. One viewer per vessel (two copies of the same slots could duplicate items; ledger).
 *
 * <p>Threads: the viewer's thread; the open map is thread-safe.
 */
public final class LargeVesselScreens implements Listener {
    public static final long RELOAD_TICKS = 10;
    static final String STATE = "largevessel.screen";
    /** PAR-FAUNA-38: TFC's {@code TFCTags.Items.FOODS}. */
    static final String FOOD_TAG = "c:foods";

    /** PAR-FAUNA-38: the infestation roll at a vessel's block (default {@link CritterModule#infest}). */
    public interface PestRoll {
        void roll(Player viewer, Location vessel, int foodStacks);
    }

    static final class Screen {
        final LargeVesselLink link;
        final Object key;
        LargeVesselLink.Snapshot vessel;
        Scheduler.TaskHandle reloader;
        boolean pending;

        Screen(LargeVesselLink link, Object key, LargeVesselLink.Snapshot vessel) {
            this.link = link;
            this.key = key;
            this.vessel = vessel;
        }

        boolean sealed() {
            return vessel != null && vessel.sealed();
        }
    }

    private final GuiService gui;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Predicate<ItemStack> fits;
    private final Map<Object, GuiSession> open = new ConcurrentHashMap<>();
    private volatile PestRoll pests;
    private volatile Predicate<ItemStack> foodTest;

    public LargeVesselScreens(GuiService gui, Scheduler scheduler, Messages messages, Predicate<ItemStack> fits) {
        this.gui = Objects.requireNonNull(gui, "gui");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.fits = Objects.requireNonNull(fits, "fits");
    }

    /** Opens the vessel's screen for {@code viewer}; empty when another player has it open, it is gone or refused. */
    public Optional<GuiSession> open(Player viewer, LargeVesselLink link) {
        Object key = link.key();
        GuiSession other = open.get(key);
        if (other != null && !other.closed()) {
            return Optional.empty();
        }
        Optional<LargeVesselLink.Snapshot> vessel = link.snapshot();
        if (vessel.isEmpty()) {
            return Optional.empty();
        }
        Screen screen = new Screen(link, key, vessel.get());
        GuiSession session = gui.open(viewer, screen(screen));
        if (session == null) {
            return Optional.empty();
        }
        session.put(STATE, screen);
        open.put(key, session);
        apply(session, screen, vessel.get());
        rollInfestation(viewer, key, vessel.get());
        screen.reloader = scheduler.entityRepeating(viewer, RELOAD_TICKS, RELOAD_TICKS, () -> reload(session), () -> { });
        return Optional.of(session);
    }

    /** Replaces the infestation roll and the food test (tests; the defaults are {@link CritterModule#infest} and the food tag). */
    public void pests(PestRoll roll, Predicate<ItemStack> food) {
        this.pests = roll;
        this.foodTest = food;
    }

    /**
     * PAR-FAUNA-38, TFC {@code onContainerOpen}: opening an unsealed large vessel ({@code LargeVesselContainer.canBeInfested})
     * rolls an infestation at its block with the food stacks of its nine slots (at most five; the {@code enableInfestations}
     * option is on, as TFC's default). A sealed vessel rolls nothing. Viewer thread.
     */
    public void rollInfestation(Player viewer, Object key, LargeVesselLink.Snapshot vessel) {
        if (vessel.sealed() || !(key instanceof LargeVesselLink.Key k)) {
            return;
        }
        World world = Bukkit.getWorld(k.world());
        if (world == null) {
            return;
        }
        PestRoll roll = pests != null ? pests : lookupRoll();
        if (roll != null) {
            roll.roll(viewer, new Location(world, k.x(), k.y(), k.z()), foodCount(vessel.slots()));
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
            : (viewer, at, food) -> critter.infest(at, PestInfestation.openLevel(true, food), viewer);
    }

    private static Optional<ModuleServices> services() {
        try {
            return Optional.of(JavaPlugin.getPlugin(TfpPlugin.class).moduleServices());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    /** The open screen of vessel {@code key}, if any. */
    public Optional<GuiSession> viewer(Object key) {
        GuiSession s = open.get(key);
        return s == null || s.closed() ? Optional.empty() : Optional.of(s);
    }

    /** Before another path changes vessel {@code key}: commits the open screen's copy. Viewer thread. */
    public void edit(Object key) {
        viewer(key).ifPresent(this::flush);
    }

    /** Re-reads vessel {@code key} into its open screen. Viewer thread. */
    public void reload(Object key) {
        viewer(key).ifPresent(this::reload);
    }

    public static boolean isLargeVessel(GuiSession session) {
        return state(session) != null;
    }

    private TfpGui screen(Screen screen) {
        TfpGui.Builder b = TfpGui.builder(SampleScreen.CHEST, SampleScreen.LAYERS)
            .label(p -> messages.get(p, "gui.large_vessel.title"));
        for (int tfc = 0; tfc < LargeVesselRules.SLOTS; tfc++) {
            b.input(SampleScreen.SLOTS.chestSlot(tfc), (s, slot, stack) -> !GuiButtons.isButton(stack) && fits.test(stack));
        }
        b.button(SampleScreen.BUTTON_SLOT,
            s -> GuiButtons.icon(gui.glyphs(), messages.get(s.viewer(), screen.sealed() ? "gui.large_vessel.unseal" : "gui.large_vessel.seal")),
            (s, click) -> toggleSeal(s));
        return b.frames(s -> Map.of(SampleScreen.SEAL_LAYER, screen.sealed() ? SampleScreen.FRAME_SEALED : SampleScreen.FRAME_UNSEALED))
            .lockedWhen(s -> screen.vessel == null || screen.vessel.sealed())
            .onClose(this::closed)
            .build();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isLargeVessel(session)) {
            touched(session);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (session != null && isLargeVessel(session)) {
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
        if (screen == null || session.closed() || screen.vessel == null || screen.vessel.sealed()) {
            return;
        }
        List<ItemStack> now = copy(session.getInventory());
        if (LargeVesselLink.Snapshot.sameSlots(now, screen.vessel.slots())) {
            return;
        }
        Optional<LargeVesselLink.Snapshot> after = screen.link.commit(now);
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
        if (session.closed() || screen.vessel == null) {
            return;
        }
        Optional<LargeVesselLink.Snapshot> now = screen.link.snapshot();
        if (now.isEmpty()) {
            gone(session, screen);
        } else if (!now.get().equals(screen.vessel)) {
            apply(session, screen, now.get());
        }
    }

    private void toggleSeal(GuiSession session) {
        Screen screen = state(session);
        flush(session);
        if (screen == null || session.closed() || screen.vessel == null) {
            return;
        }
        Optional<LargeVesselLink.Snapshot> after = screen.link.toggleSeal(session.viewer());
        if (after.isEmpty()) {
            gone(session, screen);
        } else {
            apply(session, screen, after.get());
        }
    }

    private static void apply(GuiSession session, Screen screen, LargeVesselLink.Snapshot vessel) {
        screen.vessel = vessel;
        Inventory inv = session.getInventory();
        for (int tfc = 0; tfc < LargeVesselRules.SLOTS; tfc++) {
            ItemStack s = vessel.slots().get(tfc);
            inv.setItem(SampleScreen.SLOTS.chestSlot(tfc), s == null || s.isEmpty() ? null : s);
        }
        session.refresh();
    }

    /** The vessel is gone: what the viewer put into the copy since the last commit goes back to the viewer; the screen closes. */
    private static void gone(GuiSession session, Screen screen) {
        LargeVesselLink.Snapshot last = screen.vessel;
        screen.vessel = null;
        Inventory inv = session.getInventory();
        Player viewer = session.viewer();
        for (int tfc = 0; tfc < LargeVesselRules.SLOTS; tfc++) {
            int slot = SampleScreen.SLOTS.chestSlot(tfc);
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
        if (screen.vessel != null && !screen.vessel.sealed()) {
            List<ItemStack> now = copy(session.getInventory());
            if (!LargeVesselLink.Snapshot.sameSlots(now, screen.vessel.slots()) && screen.link.commit(now).isEmpty()) {
                gone(session, screen);
            }
        }
        for (int slot : SampleScreen.SLOTS.chestSlots()) {
            session.getInventory().setItem(slot, null);
        }
        open.remove(screen.key, session);
    }

    private static Screen state(GuiSession session) {
        Object s = session.get(STATE, null);
        return s instanceof Screen screen ? screen : null;
    }

    private static List<ItemStack> copy(Inventory inv) {
        List<ItemStack> out = new ArrayList<>(LargeVesselRules.SLOTS);
        for (int tfc = 0; tfc < LargeVesselRules.SLOTS; tfc++) {
            ItemStack s = inv.getItem(SampleScreen.SLOTS.chestSlot(tfc));
            out.add(s == null || s.isEmpty() ? ItemStack.empty() : s.clone());
        }
        return out;
    }
}
