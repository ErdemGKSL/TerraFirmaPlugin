/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/PlantBlock.java @ c3b4d9536 (createFlowerbed: stacking),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/TFCCactusBedBlock.java @ c3b4d9536 (stacking)
 */
package dev.tfp.paper.plant;

import dev.tfp.core.plant.PlantStacking;
import dev.tfp.core.worldgen.plant.PlantType;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.integration.CustomItems;
import java.util.Optional;
import java.util.OptionalInt;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-FARM-12: block updates around plants feed the {@link PlantSupportQueue}, and a plant item used on a flowerbed or cactus bed of
 * its own kind (not sneaking) raises its {@code age} (TFC {@code canBeReplaced} + {@code getStateForPlacement}). Shearing a flower is
 * PAR-FARM-11's {@code CuttingListener}.
 */
public final class PlantListener implements Listener {
    private final CustomBlocks blocks;
    private final CustomItems items;
    private final PlantSupportQueue queue;

    public PlantListener(CustomBlocks blocks, CustomItems items, PlantSupportQueue queue) {
        this.blocks = blocks;
        this.items = items;
        this.queue = queue;
    }

    /** A neighbour changed: the block itself is checked (it is the one being updated). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isPresent() && PlantSupport.typeOf(state.get().id()).isPresent()) {
            queue.requestSelf(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStack(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack held = event.getItem();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null || held == null || event.getHand() == null) {
            return;
        }
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return;
        }
        PlantType type = PlantType.of(state.get().id());
        if (type == null || !PlantStacking.stacks(type)) {
            return;
        }
        Player player = event.getPlayer();
        boolean same = items.idOf(held).filter(id -> id.equals(state.get().id())).isPresent();
        OptionalInt age = PlantStacking.stackedAge(type, parseAge(state.get()), player.isSneaking(), same);
        if (age.isEmpty()) {
            return;
        }
        if (!blocks.place(block, state.get().with("age", Integer.toString(age.getAsInt())), true)) {
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(event.getHand(), held.getAmount() <= 0 ? ItemStack.empty() : held);
        }
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5),
            type == PlantType.CACTUSBED ? Sound.BLOCK_WOOL_PLACE : Sound.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1f, 1f);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    static int parseAge(BlockStateRef state) {
        try {
            return Integer.parseInt(state.properties().getOrDefault("age", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
