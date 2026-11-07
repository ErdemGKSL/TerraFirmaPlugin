/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/TFCChestBlockEntity.java @ c3b4d9536 (isValid),
 * src/main/java/net/dries007/tfc/common/container/RestrictedChestContainer.java @ c3b4d9536 (mayPlace),
 * src/main/java/net/dries007/tfc/common/entities/misc/TFCChestBoat.java @ c3b4d9536 (canPlaceItem),
 * src/main/java/net/dries007/tfc/common/entities/misc/TFCMinecartChest.java @ c3b4d9536,
 * src/main/java/net/dries007/tfc/common/items/TFCBundleItem.java @ c3b4d9536 (overrideOtherStackedOnMe)
 */
package dev.tfp.paper.itemsize;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

/**
 * TFC's size limits on vanilla containers (PAR-SURV-06):
 * <ul>
 *   <li>chests, double chests, chest boats and chest minecarts take only items up to {@code chestMaximumItemSize}
 *       (default large): a click, shift-click, number-key swap or drag that would put a larger item into one is
 *       cancelled, and a hopper does not move one in;</li>
 *   <li>a bundle in a slot takes the cursor item only when it is lighter than medium and smaller than large (TFC
 *       overrides only "other stacked on me", so a bundle on the cursor still picks up anything, as in TFC).</li>
 * </ul>
 * Our own device inventories call the same {@link ItemSizes} predicates. Region thread of the inventory's holder
 * (the events' own thread); the checks read only the event's stacks.
 */
public final class ContainerSizeListener implements Listener {
    private final ItemSizes sizes;
    private final Function<Inventory, InventoryHolder> holders;

    /** With Paper's snapshot-free holder lookup. */
    public ContainerSizeListener(ItemSizes sizes) {
        this(sizes, inventory -> inventory.getHolder(false));
    }

    /** @param holders the holder of an inventory (tests: {@link Inventory#getHolder()}) */
    ContainerSizeListener(ItemSizes sizes, Function<Inventory, InventoryHolder> holders) {
        this.sizes = Objects.requireNonNull(sizes, "sizes");
        this.holders = Objects.requireNonNull(holders, "holders");
    }

    /** Whether {@code inventory} is a TFC-restricted chest: a chest block, a double chest, a chest boat or minecart. */
    boolean isChest(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = holders.apply(inventory);
        return holder instanceof Chest || holder instanceof DoubleChest || holder instanceof ChestBoat
                || holder instanceof StorageMinecart;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }
        ItemStack incoming = intoChest(event, top, clicked);
        if (incoming != null && !sizes.fitsChest(incoming)) {
            event.setCancelled(true);
            return;
        }
        if (intoBundleSlot(event) && !sizes.fitsBundle(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    /**
     * The stack this click would put into a restricted chest, or null when it puts nothing into one. A bundle on the
     * cursor that places from its contents counts as its first stack that would not fit.
     */
    ItemStack intoChest(InventoryClickEvent event, Inventory top, Inventory clicked) {
        if (!isChest(top)) {
            return null;
        }
        InventoryAction action = event.getAction();
        if (clicked == top) {
            String name = action.name();
            switch (name) {
                case "PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "SWAP_WITH_CURSOR" -> {
                    return event.getCursor();
                }
                case "HOTBAR_SWAP", "HOTBAR_MOVE_AND_READD" -> {
                    return hotbarItem(event);
                }
                case "PLACE_FROM_BUNDLE" -> {
                    return firstUnfit(event.getCursor(), sizes::fitsChest);
                }
                default -> {
                    return null;
                }
            }
        }
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return event.getCurrentItem();
        }
        return null;
    }

    /** The item a number-key (or offhand-key) swap moves into the clicked slot. */
    private static ItemStack hotbarItem(InventoryClickEvent event) {
        HumanEntity who = event.getWhoClicked();
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            return who.getInventory().getItemInOffHand();
        }
        int button = event.getHotbarButton();
        return button < 0 ? null : who.getInventory().getItem(button);
    }

    /** The first stack in a bundle that fails {@code fits}, or null (none, or not a bundle). */
    private static ItemStack firstUnfit(ItemStack bundle, Predicate<ItemStack> fits) {
        if (bundle == null || !(bundle.getItemMeta() instanceof BundleMeta meta)) {
            return null;
        }
        for (ItemStack s : meta.getItems()) {
            if (!fits.test(s)) {
                return s;
            }
        }
        return null;
    }

    /** Whether the click puts the cursor stack into the bundle in the clicked slot (TFC's "other stacked on me"). */
    static boolean intoBundleSlot(InventoryClickEvent event) {
        String name = event.getAction().name();
        if (name.equals("PLACE_ALL_INTO_BUNDLE") || name.equals("PLACE_SOME_INTO_BUNDLE")) {
            return true;
        }
        // Servers whose API has no bundle actions: a right click with an item on a bundle (1.21.1 behaviour).
        ItemStack slot = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        return event.getClick() == ClickType.RIGHT && isBundle(slot) && cursor != null && !cursor.getType().isAir()
                && !isBundle(cursor);
    }

    static boolean isBundle(ItemStack stack) {
        return stack != null && stack.getType().getKey().getKey().endsWith("bundle");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!isChest(top) || sizes.fitsChest(event.getOldCursor())) {
            return;
        }
        int topSize = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** Hoppers (and droppers) use the chest's item handler in TFC, which refuses the same items. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        if (isChest(event.getDestination()) && !sizes.fitsChest(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
