/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java @ c3b4d9536 (the scraping recipe input placed on the
 * top face of a scraping surface)
 */
package dev.tfp.paper.scraping;

import dev.tfp.paper.device.StackCodec;
import dev.tfp.paper.integration.BlockStateRef;
import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's {@code InteractionManager} entry for scraping recipe inputs (PAR-PROC-05): a right click with a scrapable item
 * (soaked or sheepskin hide, unrefined paper) on the top face of a scraping surface (any log) whose block above is air
 * places the scraping block there and puts one of the items on it ({@link ScrapingDevice}'s {@code placed}). TFC takes the
 * item in creative mode too ({@code stack.split(1)}).
 *
 * <p>Runs on the thread of the event, which owns the player; the placement is done inline only when that thread also owns
 * the block above (always on Paper; on Folia when the block is in the player's region, the normal case within reach),
 * else the click is left to vanilla.
 */
public final class ScrapingListener implements Listener {
    /** The placement steps the listener needs, all on the block's owner thread. */
    public interface Placer {
        /** Whether the scraping recipe of {@code item} exists (any thread). */
        boolean scrapable(ItemStack item);

        /** Whether {@code block} is a scraping surface ({@code tfc:scraping_surface}). */
        boolean surface(Block block);

        /** Whether this thread owns {@code block}. */
        boolean owns(Block block);

        /** Places the scraping block at {@code block}; false when it could not be placed. */
        boolean place(Block block, BlockStateRef state);

        /** Hands the placed item to the new device ({@code DeviceService.placed}). */
        void placed(Block block, Player player, EquipmentSlot hand, ItemStack one);

        /** Whether the device at {@code block} now holds an item. */
        boolean holds(Block block);
    }

    private final Placer placer;

    public ScrapingListener(Placer placer) {
        this.placer = Objects.requireNonNull(placer, "placer");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getBlockFace() != BlockFace.UP
                || event.getHand() == null || event.useItemInHand() == Event.Result.DENY) {
            return;
        }
        Block clicked = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (clicked == null || StackCodec.isEmpty(item) || !placer.scrapable(item)) {
            return;
        }
        if (tryPlace(event.getPlayer(), event.getHand(), clicked, item)) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /**
     * TFC: {@code clickedFace == UP && isBlock(state, SCRAPING_SURFACE) && level.getBlockState(above).isAir()}, then the block
     * and one item. Returns whether the click was handled.
     */
    boolean tryPlace(Player player, EquipmentSlot hand, Block clicked, ItemStack item) {
        Block above = clicked.getRelative(BlockFace.UP);
        if (!placer.owns(clicked) || !placer.owns(above) || !placer.surface(clicked) || !above.getType().isAir()) {
            return false;
        }
        if (!placer.place(above, BlockStateRef.of(ScrapingDevice.ID))) {
            return false;
        }
        ItemStack one = item.clone();
        one.setAmount(1);
        placer.placed(above, player, hand, one);
        if (placer.holds(above)) {
            ItemStack held = player.getInventory().getItem(hand);
            if (!StackCodec.isEmpty(held)) {
                held = held.clone();
                held.setAmount(held.getAmount() - 1);
                player.getInventory().setItem(hand, held.getAmount() <= 0 ? null : held);
            }
        }
        return true;
    }
}
