/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/FirepitBlock.java @ c3b4d9536 (useItemOn: a wrought iron
 *       grill used on a firepit converts it to a grill, the grill is used up outside creative)
 */
package dev.tfp.paper.grill;

import dev.tfp.paper.advancement.criteria.AdvancementTriggerEvent;
import dev.tfp.paper.advancement.criteria.CriteriaMap;
import dev.tfp.paper.firepit.FirepitDevice;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-HEAT-08: TFC's {@code FirepitBlock.useItemOn} first branch, a {@code tfp:wrought_iron_grill} used on a firepit. The
 * click is cancelled before CraftEngine (priority LOW, as the barrel rack) so the firepit's own handler never sees it, and
 * the firepit becomes a grill ({@link GrillConversion#toGrill}); the grill item is used up outside creative. A sneaking
 * click is the item's own use in TFC (secondary use skips the block), so it is left alone. The other interactions of the
 * same player in that tick (Paper's off-hand event after the cancelled main-hand one, which would reach the new grill)
 * are cancelled, as vanilla ends the click with the conversion.
 *
 * <p>Threads: the event's thread, which owns the block (Paper, Folia); a block of a region the thread does not own is left
 * alone.
 */
public final class GrillListener implements Listener {
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Scheduler scheduler;
    private final GrillConversion conversion;
    private final Map<UUID, Integer> lastConverted = new ConcurrentHashMap<>();

    public GrillListener(CustomBlocks blocks, CustomItems items, Scheduler scheduler, GrillConversion conversion) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.items = Objects.requireNonNull(items, "items");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
    }

    boolean isGrillItem(ItemStack stack) {
        return stack != null && !stack.isEmpty() && items.idOf(stack).filter(GrillDevice.GRILL_ITEM::equals).isPresent();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        Integer converted = lastConverted.get(player.getUniqueId());
        if (converted != null && converted == player.getTicksLived()) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == null || event.getClickedBlock() == null
                || event.useItemInHand() == Event.Result.DENY || player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (!isGrillItem(held) || !scheduler.isOwner(block.getWorld(), block.getX() >> 4, block.getZ() >> 4)) {
            return;
        }
        if (!blocks.stateAt(block).map(s -> s.id().equals(FirepitDevice.ID)).orElse(false)) {
            return;
        }
        event.setCancelled(true);
        if (!conversion.toGrill(block)) {
            return;
        }
        lastConverted.put(player.getUniqueId(), player.getTicksLived());
        AdvancementTriggerEvent.publish(player, CriteriaMap.GRILL); // TFC FIREPIT_CREATED(grill)
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(event.getHand(), rest.getAmount() <= 0 ? ItemStack.empty() : rest);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastConverted.remove(event.getPlayer().getUniqueId());
    }
}
