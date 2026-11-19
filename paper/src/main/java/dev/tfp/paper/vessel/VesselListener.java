/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/VesselItem.java @ c3b4d9536 (use: not while sneaking)
 */
package dev.tfp.paper.vessel;

import dev.tfp.paper.gui.GuiService;
import dev.tfp.paper.gui.GuiSession;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * PAR-POT-04: the vessel's events. Right-click opens the screen; clicks keep an open screen committed and keep the
 * vessel itself in its slot; a click on a vessel and a cheap per-player sweep evaluate the lazy melt of its contents.
 *
 * <p>The sweep is one entity-scheduler task per online player every {@link #SWEEP_TICKS} ticks that looks at that player's
 * inventory only (41 slots, a custom id read each, the melt only for a vessel with a heat record and contents). There is
 * no world scan and no per-tick work. Threads: every handler runs on the player's thread.
 */
public final class VesselListener implements Listener {
    /** The sweep period: two seconds. */
    public static final long SWEEP_TICKS = 40;

    private final VesselItems items;
    private final VesselScreens screens;
    private final Scheduler scheduler;
    private final Map<UUID, Scheduler.TaskHandle> sweeps = new ConcurrentHashMap<>();

    public VesselListener(VesselItems items, VesselScreens screens, Scheduler scheduler) {
        this.items = Objects.requireNonNull(items, "items");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Starts the sweep of {@code player} (replaces a running one). */
    public void startSweep(Player player) {
        Scheduler.TaskHandle handle = scheduler.entityRepeating(player, SWEEP_TICKS, SWEEP_TICKS, () -> sweep(player),
            () -> sweeps.remove(player.getUniqueId()));
        if (handle == null) {
            return;
        }
        Scheduler.TaskHandle old = sweeps.put(player.getUniqueId(), handle);
        if (old != null) {
            old.cancel();
        }
    }

    /** Stops every sweep (module disable). */
    public void stopSweeps() {
        sweeps.values().forEach(Scheduler.TaskHandle::cancel);
        sweeps.clear();
    }

    /** Evaluates the melt of every vessel in {@code player}'s inventory. */
    public void sweep(Player player) {
        PlayerInventory inv = player.getInventory();
        int size = inv.getSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && !stack.isEmpty() && items.isVessel(stack) && items.settle(stack)) {
                inv.setItem(i, stack);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        startSweep(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Scheduler.TaskHandle h = sweeps.remove(e.getPlayer().getUniqueId());
        if (h != null) {
            h.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
            || e.getHand() == null || e.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Player player = e.getPlayer();
        ItemStack held = e.getItem();
        if (player.isSneaking() || held == null || !items.isVessel(held)) {
            return; // sneak + mold is the pour (PAR-POT-05)
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
            && e.getClickedBlock().getType().isInteractable()) {
            return; // a block that takes the click (a door, a chest) wins, as in vanilla
        }
        e.setCancelled(true);
        screens.use(player, e.getHand());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (screens.isVesselScreen(session)) {
            if (touchesOpenVessel(e, player, screens.handleIndex(session))) {
                e.setCancelled(true);
            } else {
                screens.commitSoon(session);
            }
            return;
        }
        evaluate(e.getCurrentItem(), e::setCurrentItem);
        evaluate(e.getCursor(), e::setCursor);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        GuiSession session = GuiService.session(e.getView().getTopInventory());
        if (screens.isVesselScreen(session)) {
            screens.commitSoon(session);
        }
    }

    private void evaluate(ItemStack stack, java.util.function.Consumer<ItemStack> write) {
        if (stack != null && !stack.isEmpty() && items.isVessel(stack) && items.settle(stack)) {
            write.accept(stack);
        }
    }

    /** Whether the click would take the open vessel out of its slot or put something on it. */
    static boolean touchesOpenVessel(InventoryClickEvent e, Player player, int index) {
        if (index < 0) {
            return false;
        }
        if (e.getClickedInventory() != null && e.getClickedInventory().equals(player.getInventory())
            && e.getSlot() == index) {
            return true;
        }
        if (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() == index) {
            return true;
        }
        return e.getClick() == ClickType.SWAP_OFFHAND && index == VesselScreens.OFF_HAND_INDEX;
    }
}
