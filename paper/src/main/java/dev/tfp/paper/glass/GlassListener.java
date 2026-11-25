/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/GlassBlowpipeItem.java @ c3b4d9536 (use),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onItemStacked)
 */
package dev.tfp.paper.glass;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.Optional;

/** PAR-PROC-16: the right click with a glass blowpipe ({@link GlassWork#use}) and a batch put on a blowpipe. */
public final class GlassListener implements Listener {
    private final GlassWork work;

    public GlassListener(GlassWork work) {
        this.work = work;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || event.getHand() == null || !work.isGlassBlowpipe(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        // a block with its own use (a table, a door) keeps its use unless sneaking, as vanilla does
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && event.useInteractedBlock() != Event.Result.DENY && event.getClickedBlock().getType().isInteractable()
            && !player.isSneaking()) {
            return;
        }
        GlassWork.Result result = work.use(player, event.getHand());
        if (result != GlassWork.Result.PASS) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** TFC {@code onItemStacked}: the right button with a batch on the cursor over a single empty blowpipe. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT) {
            return;
        }
        Optional<ItemStack> loaded = work.loadBatch(event.getCurrentItem(), event.getCursor(), true);
        if (loaded.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        ItemStack cursor = event.getCursor().clone();
        cursor.setAmount(cursor.getAmount() - 1);
        event.setCurrentItem(loaded.get());
        event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
    }
}
