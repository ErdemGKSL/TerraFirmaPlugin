/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (useItemOn: TFCItems.POT turns
 *       the firepit into a pot, the item used up unless creative),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/PotBlock.java @ c3b4d9536 (useItemOn: the fluid transfer
 *       consumes the click)
 */
package dev.tfp.paper.pot;

import dev.tfp.paper.barrel.BarrelItemTanks;
import dev.tfp.paper.device.DeviceService;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The two pot clicks that must not reach CraftEngine or the vanilla item use (PAR-HEAT-09), routed from the Bukkit event
 * before both (LOW), the event cancelled:
 * <ul>
 *   <li>A ceramic pot used on a firepit turns it into a pot ({@link PotConversion#toPot}), as TFC's
 *       {@code FirepitBlock.useItemOn}. The firepit device's own click handler belongs to the firepit; this keeps the
 *       conversion in the pot's files.</li>
 *   <li>A fluid container (a vanilla bucket, TFC's buckets, jugs and bottles) used on a pot goes to the pot device (its
 *       fluid transfer), as the barrel's bucket guard: otherwise vanilla {@code BucketItem.use} would also empty or fill
 *       the bucket at the world next to the pot. Every other interaction of the same player in that tick is cancelled too
 *       (Paper's item use packet after the hand changed, the off hand), and a bucket event at a pot is refused.</li>
 * </ul>
 * Sneaking with an item in hand skips the block, as vanilla's {@code isSecondaryUseActive}. Threads: the event thread (the
 * player's region); a block on another region is left alone.
 */
public final class PotInteractListener implements Listener {
    private final DeviceService devices;
    private final PotConversion conversion;
    private final BarrelItemTanks tanks;
    private final Function<ItemStack, Optional<String>> itemIds;
    private final Scheduler scheduler;
    private final Logger logger;
    private final Map<UUID, Integer> lastUse = new ConcurrentHashMap<>();

    public PotInteractListener(DeviceService devices, PotConversion conversion, BarrelItemTanks tanks,
            Function<ItemStack, Optional<String>> itemIds, Scheduler scheduler, Logger logger) {
        this.devices = Objects.requireNonNull(devices, "devices");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        this.tanks = Objects.requireNonNull(tanks, "tanks");
        this.itemIds = Objects.requireNonNull(itemIds, "itemIds");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (event.getHand() == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR)) {
            return;
        }
        Player player = event.getPlayer();
        if (sameTick(player)) {
            event.setCancelled(true);
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (action != Action.RIGHT_CLICK_BLOCK || clicked == null || held == null || held.isEmpty() || player.isSneaking()
                || !owned(clicked)) {
            return;
        }
        try {
            if (isPotItem(held) && conversion.isFirepit(clicked)) {
                lastUse.put(player.getUniqueId(), player.getTicksLived());
                event.setCancelled(true);
                if (conversion.toPot(clicked)) {
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        PotDevice.shrink(player, event.getHand());
                    }
                    devices.neighbourChanged(clicked); // the pot's state and its burn-out callback
                }
            } else if (tanks.tank(held).isPresent() && conversion.isPot(clicked)) {
                lastUse.put(player.getUniqueId(), player.getTicksLived());
                event.setCancelled(true);
                devices.use(clicked, player, event.getHand(), held);
            }
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Pot at " + clicked.getWorld().getName() + " " + clicked.getX() + "," + clicked.getY()
                + "," + clicked.getZ() + ": the click failed", e);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (refuses(event.getPlayer(), event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (refuses(event.getPlayer(), event.getBlockClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    boolean isPotItem(ItemStack stack) {
        return itemIds.apply(stack).map(PotDevice.POT_ITEM::equals).orElse(false);
    }

    private boolean refuses(Player player, Block clicked) {
        return sameTick(player) || (clicked != null && !player.isSneaking() && owned(clicked) && conversion.isPot(clicked));
    }

    private boolean sameTick(Player player) {
        Integer last = lastUse.get(player.getUniqueId());
        return last != null && last == player.getTicksLived();
    }

    private boolean owned(Block block) {
        return scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
    }
}
