/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (useItemOn: shears on a flower),
 *       src/main/java/net/dries007/tfc/common/items/FlowerCuttingItem.java @ c3b4d9536
 */
package dev.tfp.paper.regrowth;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import dev.tfp.paper.tags.Tags;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Shears on a flower (TFC {@code PlantBlock.useItemOn}): the shears lose a point of durability, the flower is removed and a flower
 * cutting carrying the plant drops. Runs at HIGH on the block's region thread; a handled click is denied.
 */
public final class CuttingListener implements Listener {
    public static final String CUTTING = "tfp:flower_cutting";
    public static final String SHEARS = "c:tools/shear";
    public static final String FLOWERS = "minecraft:flowers";

    private final CustomBlocks blocks;
    private final CustomItems items;
    private final Tags tags;

    public CuttingListener(CustomBlocks blocks, CustomItems items, Tags tags) {
        this.blocks = blocks;
        this.items = items;
        this.tags = tags;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null
                || event.getHand() == null || !tags.is(event.getItem(), SHEARS)) {
            return;
        }
        Block block = event.getClickedBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || PlantSweeper.plantName(state.get().id()).isEmpty() || !tags.is(block, FLOWERS)) {
            return;
        }
        Optional<ItemStack> cutting = items.create(CUTTING, 1);
        if (cutting.isEmpty() || !CuttingPdc.write(cutting.get(), state.get().id())) {
            return;
        }
        Player player = event.getPlayer();
        damage(player, event.getItem(), event.getHand());
        Location at = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().playSound(at, Sound.ENTITY_SHEEP_SHEAR, SoundCategory.BLOCKS, 1f, 1f);
        blocks.remove(block);
        block.getWorld().dropItemNaturally(at, cutting.get());
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    /** {@code Helpers.damageItem}: one point, none in creative, the stack breaks at its maximum. */
    private static void damage(Player player, ItemStack stack, EquipmentSlot hand) {
        if (player.getGameMode() == GameMode.CREATIVE || !(stack.getItemMeta() instanceof Damageable d)) {
            return;
        }
        d.setDamage(d.getDamage() + 1);
        stack.setItemMeta(d);
        if (d.getDamage() >= stack.getType().getMaxDurability()) {
            player.getInventory().setItem(hand, ItemStack.empty());
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
        } else {
            player.getInventory().setItem(hand, stack);
        }
    }
}
