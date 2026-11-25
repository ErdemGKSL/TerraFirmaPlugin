/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/BarteringItem.java, src/main/java/net/dries007/tfc/util/Metal.java
 *       (isPiglinCurrency: GOLD only) @ c3b4d9536
 */
package dev.tfp.paper.piglin;

import dev.tfp.core.piglin.PiglinCurrency;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Optional;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Piglin currency (PAR-DATA-22). Our gold ingot is a CraftEngine item on a paper base, which vanilla piglins ignore, and
 * Paper's barter API ({@code Piglin#addBarterMaterial}) is per material, so allowing paper would make every paper-based
 * item wanted. Instead, right-clicking an adult piglin with the TFC gold ingot (any other TFC ingot is refused) takes the
 * ingot (not in creative) and drops a vanilla gold ingot at the piglin: vanilla's AI picks it up, admires it and barters
 * it, with vanilla's loot and {@code PiglinBarterEvent}. The stand-in is marked: no one but a piglin can pick it up
 * (players, hoppers, merging), and when no piglin took it within {@link PiglinCurrency#OFFER_TICKS} it is removed and the
 * TFC ingot dropped back, so no ingot is lost or duplicated. Handlers run on the interacting player's or the item's thread.
 */
public final class PiglinCurrencyListener implements Listener {
    /** The stand-in ingot's marker; its value is the serialised TFC ingot given back when the piglin does not take it. */
    public static final NamespacedKey OFFER = new NamespacedKey("tfp", "piglin_offer");

    private final Function<ItemStack, Optional<String>> idOf;
    private final Scheduler scheduler;

    public PiglinCurrencyListener(Function<ItemStack, Optional<String>> idOf, Scheduler scheduler) {
        this.idOf = idOf;
        this.scheduler = scheduler;
    }

    /** Vanilla {@code Piglin#mobInteract} for the TFC ingot; a piglin that cannot admire (baby, busy) leaves it untouched. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Piglin piglin)) {
            return;
        }
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || held.isEmpty() || !idOf.apply(held).map(PiglinCurrency::isCurrency).orElse(false)) {
            return;
        }
        event.setCancelled(true); // a TFC item does nothing else on a piglin
        boolean free = piglin.getEquipment().getItemInOffHand().isEmpty();
        if (!PiglinCurrency.canAdmire(piglin.isAdult(), !piglin.isDead(), !free)) {
            return;
        }
        ItemStack one = held.asOne();
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.subtract(1);
            player.getInventory().setItem(hand, held.isEmpty() ? null : held);
        }
        scheduler.entity(piglin, () -> offer(piglin, one), () -> player.getInventory().addItem(one)
            .values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest)));
    }

    /** Piglin's thread: the stand-in ingot at its feet. */
    void offer(Piglin piglin, ItemStack tfcIngot) {
        Location at = piglin.getLocation();
        Item item = at.getWorld().dropItem(at, new ItemStack(Material.GOLD_INGOT), i -> {
            i.setPickupDelay(0);
            i.setVelocity(new Vector());
            i.getPersistentDataContainer().set(OFFER, PersistentDataType.BYTE_ARRAY, tfcIngot.serializeAsBytes());
        });
        scheduler.entityDelayed(item, PiglinCurrency.OFFER_TICKS, () -> expire(item), () -> { });
    }

    /** Item's thread: nobody took the stand-in: it goes and the TFC ingot comes back. */
    void expire(Item item) {
        if (!item.isValid()) {
            return;
        }
        byte[] bytes = item.getPersistentDataContainer().get(OFFER, PersistentDataType.BYTE_ARRAY);
        Location at = item.getLocation();
        item.remove();
        if (bytes != null) {
            at.getWorld().dropItemNaturally(at, ItemStack.deserializeBytes(bytes));
        }
    }

    static boolean isOffer(Item item) {
        return item.getPersistentDataContainer().has(OFFER, PersistentDataType.BYTE_ARRAY);
    }

    /** Only a piglin takes the stand-in. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Piglin) && isOffer(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHopper(InventoryPickupItemEvent event) {
        if (isOffer(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMerge(org.bukkit.event.entity.ItemMergeEvent event) {
        if (isOffer(event.getEntity()) || isOffer(event.getTarget())) {
            event.setCancelled(true);
        }
    }
}
