/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.loot;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.List;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Drops TFC loot for CraftEngine blocks whose table ships in the data root ({@link LootService#handlesBlock}). Those
 * blocks carry no CraftEngine {@code loot:} of their own, so this is their only drop source. A player break uses the
 * main-hand tool; an explosion uses an empty tool and the explosion radius (decay). Creative breaks drop nothing, as in
 * vanilla. Region thread: snapshot only, the roll runs on the CPU pool.
 */
public final class LootBreakListener implements Listener {
    private final LootService service;
    private final CustomBlocks blocks;

    public LootBreakListener(LootService service, CustomBlocks blocks) {
        this.service = service;
        this.blocks = blocks;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Optional<Target> target = target(event.getBlock());
        if (target.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        LootContext context = LootContext.builder()
                .block(target.get().state.id(), target.get().state.properties())
                .tool(service.tool(event.getPlayer().getInventory().getItemInMainHand()))
                .build();
        service.dropBlock(event.getBlock(), target.get().table, context);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield());
    }

    private void explode(List<Block> destroyed, float yield) {
        if (yield <= 0f) {
            return;
        }
        Float radius = LootService.radiusFromYield(yield);
        for (Block block : destroyed) {
            Optional<Target> target = target(block);
            if (target.isEmpty()) {
                continue;
            }
            LootContext context = LootContext.builder()
                    .block(target.get().state.id(), target.get().state.properties())
                    .tool(LootContext.Tool.EMPTY)
                    .explosionRadius(radius)
                    .build();
            service.dropBlock(block, target.get().table, context);
        }
    }

    private record Target(BlockStateRef state, LootTable table) {}

    private Optional<Target> target(Block block) {
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return Optional.empty();
        }
        return service.tables().forBlock(state.get().id()).map(t -> new Target(state.get(), t));
    }
}
