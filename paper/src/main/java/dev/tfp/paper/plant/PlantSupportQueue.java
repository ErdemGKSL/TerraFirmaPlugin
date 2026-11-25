/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/TFCBushBlock.java @ c3b4d9536 (canSurvive)
 */
package dev.tfp.paper.plant;

import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * PAR-FARM-12: a block next to a change is checked once per tick: the owner thread snapshots it ({@link PlantSupport#snapshot}), the CPU
 * pool decides, the owner thread re-validates (same state, still unsupported) and removes it with its drops through the custom block
 * (both halves of a tall plant). Requests for one block within a tick coalesce.
 */
public final class PlantSupportQueue {
    private final PlantSupport support;
    private final CustomBlocks blocks;
    private final Scheduler scheduler;
    private final Map<Pos, Boolean> pending = new ConcurrentHashMap<>();

    public PlantSupportQueue(PlantSupport support, CustomBlocks blocks, Scheduler scheduler) {
        this.support = support;
        this.blocks = blocks;
        this.scheduler = scheduler;
    }

    /** Owner thread of {@code block}: asks for the block and the plant above it (the usual victim of a broken support) to be checked. */
    public void request(Block block) {
        check(block);
        check(block.getRelative(BlockFace.UP));
    }

    /** The number of checks waiting (tests). */
    int pending() {
        return pending.size();
    }

    /** Owner thread: just {@code block}. */
    public void requestSelf(Block block) {
        check(block);
    }

    private void check(Block block) {
        Pos key = new Pos(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        if (pending.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        World world = block.getWorld();
        Chunk chunk = block.getChunk();
        // after the current tick's changes are done
        scheduler.regionDelayed(world, chunk.getX(), chunk.getZ(), 1, () -> {
            Optional<PlantSupport.Snapshot> snapshot;
            try {
                snapshot = support.snapshot(block);
            } finally {
                pending.remove(key);
            }
            if (snapshot.isEmpty()) {
                return;
            }
            PlantSupport.Snapshot s = snapshot.get();
            scheduler.cpu(() -> {
                if (PlantSupport.decide(s)) {
                    return;
                }
                scheduler.region(world, chunk.getX(), chunk.getZ(), () -> commit(block, s));
            });
        });
    }

    /** Owner thread: removes the plant if it still is what was decided about. */
    private void commit(Block block, PlantSupport.Snapshot s) {
        Optional<BlockStateRef> now = blocks.stateAt(block);
        if (now.isEmpty() || !now.get().equals(s.state())) {
            return;
        }
        blocks.remove(block);
        if (s.type().isTall()) {
            // the other half: removing one half removes the pair (TFCTallGrassBlock.updateShape)
            Block other = block.getRelative(s.upper() ? BlockFace.DOWN : BlockFace.UP);
            blocks.stateAt(other).filter(o -> o.id().equals(s.state().id())).ifPresent(o -> blocks.remove(other));
        }
    }

    private record Pos(java.util.UUID world, int x, int y, int z) {
    }
}
