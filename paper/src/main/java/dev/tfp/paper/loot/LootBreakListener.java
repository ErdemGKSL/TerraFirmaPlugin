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
import org.bukkit.ExplosionResult;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Drops TFC loot for CraftEngine blocks whose table ships in the data root ({@link LootService#handlesBlock}). Those
 * blocks carry no CraftEngine {@code loot:} of their own, so this is their only drop source. A player break uses the
 * main-hand tool and drops nothing unless that tool is correct for the block ({@link CorrectTool}, as TFC's
 * {@code hasCorrectToolForDrops} gate); an explosion uses an empty tool and the explosion radius (decay). Creative breaks,
 * breaks another plugin set to drop nothing, and explosions that do not destroy blocks (wind charges, breezes:
 * {@link ExplosionResult#TRIGGER_BLOCK}; {@link ExplosionResult#KEEP}) drop nothing, as in vanilla. Region thread:
 * snapshot only, the roll runs on the CPU pool.
 */
public final class LootBreakListener implements Listener {
    /**
     * Whether {@code tool} may harvest the custom block at {@code block} (CraftEngine's {@code correct_tools} /
     * {@code required_break_power}). Region thread.
     */
    @FunctionalInterface
    public interface CorrectTool {
        /** Accepts every tool: used until the CraftEngine adapter provides the real check (PAR-INFRA-22). */
        CorrectTool ANY = (block, tool) -> true;

        boolean test(Block block, ItemStack tool);
    }

    private final LootService service;
    private final CustomBlocks blocks;
    private final CorrectTool correctTool;

    public LootBreakListener(LootService service, CustomBlocks blocks) {
        this(service, blocks, CorrectTool.ANY);
    }

    public LootBreakListener(LootService service, CustomBlocks blocks, CorrectTool correctTool) {
        this.service = service;
        this.blocks = blocks;
        this.correctTool = correctTool;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE || !event.isDropItems()) {
            return;
        }
        Optional<Target> target = target(event.getBlock());
        if (target.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (!correctTool.test(event.getBlock(), hand)) {
            return;
        }
        LootContext context = LootContext.builder()
                .block(target.get().state.id(), target.get().state.properties())
                .tool(service.tool(hand))
                .build();
        service.dropBlock(event.getBlock(), target.get().table, context);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explode(event.blockList(), event.getYield(), event.getExplosionResult());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explode(event.blockList(), event.getYield(), event.getExplosionResult());
    }

    /**
     * Only {@code DESTROY} and {@code DESTROY_WITH_DECAY} remove the listed blocks; {@code TRIGGER_BLOCK} (wind charge,
     * breeze) only lists them to toggle doors and buttons and keeps them, so rolling there would duplicate loot. The
     * explosion radius is a loot parameter only with decay, as in vanilla's explosion.
     */
    private void explode(List<Block> destroyed, float yield, ExplosionResult result) {
        if (yield <= 0f || (result != ExplosionResult.DESTROY && result != ExplosionResult.DESTROY_WITH_DECAY)) {
            return;
        }
        Float radius = result == ExplosionResult.DESTROY_WITH_DECAY ? LootService.radiusFromYield(yield) : null;
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
