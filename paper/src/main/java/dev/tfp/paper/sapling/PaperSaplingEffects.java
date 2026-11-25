/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.sapling;

import dev.tfp.core.sapling.SaplingTreeRefs;
import dev.tfp.core.worldgen.tree.SaplingTree;
import dev.tfp.core.worldgen.tree.Tree;
import dev.tfp.core.worldgen.tree.TreeBlock;
import dev.tfp.core.worldgen.tree.TreeGround;
import dev.tfp.core.worldgen.tree.Wood;
import dev.tfp.paper.catchup.CatchUpService;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.tags.Tags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The server side of {@link SaplingEffects}. The plan is decided on the sapling's region thread from the live blocks (a position
 * this thread does not own reads as solid, see {@link LiveTreeView}); the blocks are written chunk by chunk on each chunk's owner,
 * {@link #SLICE} blocks per tick, and a block that is no longer replaceable when its turn comes is skipped (the commit re-validates).
 */
public final class PaperSaplingEffects implements SaplingEffects {
    /** Tree blocks written per region task: a tree of a few hundred blocks spreads over a few ticks. */
    static final int SLICE = 96;

    private final Scheduler scheduler;
    private final CustomBlocks blocks;
    private final Tags tags;
    private final SaplingFeatures features;
    private final Supplier<Optional<CatchUpService>> catchUp;

    public PaperSaplingEffects(Scheduler scheduler, CustomBlocks blocks, Tags tags, SaplingFeatures features,
            Supplier<Optional<CatchUpService>> catchUp) {
        this.scheduler = scheduler;
        this.blocks = blocks;
        this.tags = tags;
        this.features = features;
        this.catchUp = catchUp;
    }

    @Override
    public Optional<BlockStateRef> blockAt(Block block) {
        return blocks.stateAt(block);
    }

    @Override
    public int lightAbove(Block sapling) {
        return sapling.getRelative(0, 1, 0).getLightLevel();
    }

    @Override
    public Optional<Tree> plan(Block sapling, Wood wood, long seed) {
        World world = sapling.getWorld();
        LiveTreeView view = new LiveTreeView(world, blocks, tags, scheduler::isOwner, sapling);
        return SaplingTree.grow(features.of(wood), view, seed, sapling.getX(), sapling.getY(), sapling.getZ());
    }

    @Override
    public void grow(Block sapling, Tree tree) {
        World world = sapling.getWorld();
        Map<Long, List<Placement>> byChunk = new LinkedHashMap<>();
        for (TreeBlock b : tree.blocks()) {
            Optional<String> ref = SaplingTreeRefs.ref(b);
            if (ref.isEmpty()) {
                continue; // roots and krummholz have no CraftEngine block yet (ledger)
            }
            int x = tree.x() + b.dx();
            int z = tree.z() + b.dz();
            long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
            byChunk.computeIfAbsent(key, k -> new ArrayList<>()).add(new Placement(x, tree.y() + b.dy(), z, BlockStateRef.parse(ref.get()), b.force()));
        }
        for (Map.Entry<Long, List<Placement>> e : byChunk.entrySet()) {
            int cx = (int) (e.getKey() >> 32);
            int cz = (int) (long) e.getKey();
            scheduler.region(world, cx, cz, () -> writeSlice(world, cx, cz, e.getValue(), 0));
        }
    }

    private record Placement(int x, int y, int z, BlockStateRef ref, boolean force) {}

    private void writeSlice(World world, int cx, int cz, List<Placement> all, int from) {
        if (!world.isChunkLoaded(cx, cz)) {
            return;
        }
        LiveTreeView view = null;
        int end = Math.min(all.size(), from + SLICE);
        for (int i = from; i < end; i++) {
            Placement p = all.get(i);
            Block block = world.getBlockAt(p.x(), p.y(), p.z());
            if (!p.force()) {
                // TreeHelpers.placeTemplate only replaces air, fluids and leaves; the world may have changed since the plan
                if (view == null) {
                    view = new LiveTreeView(world, blocks, tags, scheduler::isOwner, block);
                }
                TreeGround now = view.classify(block);
                if (!(now.isAir() || now.isWorldgenReplaceable() || now.isLeaves())) {
                    continue;
                }
            }
            blocks.place(block, p.ref(), false);
        }
        if (end < all.size()) {
            scheduler.regionDelayed(world, cx, cz, 1, () -> writeSlice(world, cx, cz, all, end));
        }
    }

    @Override
    public void track(Block block) {
        catchUp.get().ifPresent(c -> c.track(block, SaplingDevice.CATCH_UP_KIND));
    }
}
