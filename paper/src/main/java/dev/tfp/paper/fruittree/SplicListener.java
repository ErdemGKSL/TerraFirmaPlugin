/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (useItemOn)
 */
package dev.tfp.paper.fruittree;

import dev.tfp.core.berry.FruitTree;
import dev.tfp.paper.integration.CustomItems;
import java.util.Optional;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FARM-28: knife splicing. Right-clicking a fruit tree sapling with the same tree's sapling item in the main hand and a knife
 * ({@code c:tools/knife}) in the off hand, where {@link SplicRules#maySplice} holds, adds one to its {@code saplings} and uses the item up
 * (not in creative). The handler runs before the device framework's (priority HIGH) and cancels the click only when it splices, so any
 * other click is TFC's {@code PASS_TO_DEFAULT_BLOCK_INTERACTION}. Region thread of the block.
 */
public final class SplicListener implements Listener {
    public static final String KNIVES = "c:tools/knife";

    private final FruitTreeEffects fx;
    private final CustomItems items;
    private final Predicate<ItemStack> knives;

    /** @param knives whether a stack is in {@value #KNIVES} */
    public SplicListener(FruitTreeEffects fx, CustomItems items, Predicate<ItemStack> knives) {
        this.fx = fx;
        this.items = items;
        this.knives = knives;
    }

    /** The tree whose sapling item this is. */
    Optional<FruitTree> saplingOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        Optional<String> id = items.idOf(stack);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        for (FruitTree tree : FruitTree.values()) {
            if (FruitTreeDevice.blockId(tree, FruitTreeDevice.Part.SAPLING).equals(id.get())) {
                return Optional.of(tree);
            }
        }
        return Optional.empty();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        Optional<FruitTree> tree = saplingOf(held);
        if (tree.isEmpty() || !knives.test(player.getInventory().getItemInOffHand())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (SplicAction.splice(fx, block, tree.get()) != SplicAction.Result.SPLICED) {
            return;
        }
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemStack rest = held.clone();
            rest.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(rest.getAmount() <= 0 ? ItemStack.empty() : rest);
        }
        player.swingMainHand();
    }
}
