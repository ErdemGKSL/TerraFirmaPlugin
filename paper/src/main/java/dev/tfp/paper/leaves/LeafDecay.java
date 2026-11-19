/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/wood/TFCLeavesBlock.java @ c3b4d9536 (tick, randomTick,
 *       createDestructionEffects)
 */
package dev.tfp.paper.leaves;

import dev.tfp.core.leaves.LeafRules;
import dev.tfp.core.leaves.LeafRules.Cell;
import dev.tfp.core.leaves.LeafRules.Pos;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * PAR-GATHER-04: TFC's slow leaf decay. When a log is broken (a player, a felled tree), the leaves within
 * {@value LeafRules#MAX_DECAY_DISTANCE} steps of it through leaves of one block are checked
 * ({@link LeafRules#orphans}); each one that has no log in reach and is not persistent is scheduled to decay after a random
 * delay (TFC: the first random tick that finds the distance too large, a mean of about 1,365 ticks at the default random
 * tick speed). When its turn comes the leaf is checked again and, if still orphaned, removed, with a one percent chance of a
 * fallen twig or leaves on the ground below.
 *
 * <p>Only leaves with a {@code persistent=false} property take part: the property marks player-placed leaves as persistent
 * and is added with {@code branch_direction} by PAR-GATHER-09; without it nothing decays (player builds are safe).
 *
 * <p>Threads: the check runs on the region of the broken log, reading only chunks that region owns, with a cache so each
 * block is read once; the decay runs on the region of each leaf.
 */
public final class LeafDecay implements Listener {
    /** The platform pieces the decay needs. */
    public interface Env {
        /** Whether the block state is a log ({@code #minecraft:logs}). Any thread. */
        boolean isLog(BlockStateRef state);

        /** Runs the task on the region thread owning the chunk, after the ticks. */
        void regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task);

        /** A uniform float in {@code [0, 1)}. */
        float nextFloat();
    }

    /** TFC's mean wait of a random tick for one block: {@code 4096 / 3} ticks at the default {@code randomTickSpeed}. */
    static final double MEAN_DELAY_TICKS = 4096.0 / 3.0;

    private static final long MAX_DELAY_TICKS = 24_000L;

    private final CustomBlocks blocks;
    private final Env env;

    public LeafDecay(CustomBlocks blocks, Env env) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.env = Objects.requireNonNull(env, "env");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !env.isLog(state.get())) {
            return;
        }
        check(block);
    }

    /** Checks the leaves around a log that is about to disappear and schedules the decay of the orphans. Region thread. */
    void check(Block removed) {
        World world = removed.getWorld();
        Pos origin = new Pos(removed.getX(), removed.getY(), removed.getZ());
        Map<Pos, Cell> cache = new HashMap<>();
        Map<Pos, String> leafIds = new HashMap<>();
        // the leaves around the log are found first, then each is judged against leaves of its own block
        LeafRules.Probe any = (x, y, z) -> {
            Pos p = new Pos(x, y, z);
            if (p.equals(origin)) {
                return Cell.OTHER;
            }
            return cache.computeIfAbsent(p, k -> classify(world, k, leafIds));
        };
        // the neighbours of the log decide which block's leaves are checked: each distinct leaf block on its own
        Map<String, List<Pos>> byBlock = new HashMap<>();
        for (int[] f : new int[][] {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}}) {
            Pos n = new Pos(origin.x() + f[0], origin.y() + f[1], origin.z() + f[2]);
            if (any.at(n) == Cell.LEAF) {
                byBlock.computeIfAbsent(leafIds.get(n), k -> new java.util.ArrayList<>()).add(n);
            }
        }
        for (Map.Entry<String, List<Pos>> entry : byBlock.entrySet()) {
            String id = entry.getKey();
            LeafRules.Probe own = (x, y, z) -> {
                Pos p = new Pos(x, y, z);
                if (p.equals(origin)) {
                    return Cell.OTHER;
                }
                Cell c = cache.computeIfAbsent(p, k -> classify(world, k, leafIds));
                return c == Cell.LEAF && !id.equals(leafIds.get(p)) ? Cell.OTHER : c;
            };
            for (Pos leaf : LeafRules.orphans(own, origin)) {
                if (isNonPersistentLeaf(world, leaf, id)) {
                    long delay = delay();
                    env.regionDelayed(world, leaf.x() >> 4, leaf.z() >> 4, delay, () -> decay(world, leaf, id));
                }
            }
        }
    }

    private Cell classify(World world, Pos p, Map<Pos, String> leafIds) {
        if (p.y() < world.getMinHeight() || p.y() >= world.getMaxHeight() || !world.isChunkLoaded(p.x() >> 4, p.z() >> 4)
                || !Bukkit.isOwnedByCurrentRegion(world, p.x() >> 4, p.z() >> 4)) {
            return Cell.OTHER;
        }
        Block block = world.getBlockAt(p.x(), p.y(), p.z());
        if (block.getType().isAir()) {
            return Cell.OTHER;
        }
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty()) {
            return Cell.OTHER;
        }
        if (env.isLog(state.get())) {
            return Cell.LOG;
        }
        if (state.get().id().startsWith("tfp:wood/leaves/")) {
            leafIds.put(p, state.get().id());
            return Cell.LEAF;
        }
        return Cell.OTHER;
    }

    private boolean isNonPersistentLeaf(World world, Pos p, String id) {
        Optional<BlockStateRef> state = blocks.stateAt(world.getBlockAt(p.x(), p.y(), p.z()));
        return state.isPresent() && state.get().id().equals(id) && "false".equals(state.get().properties().get("persistent"));
    }

    /** An exponential wait with TFC's mean, at least one tick. */
    long delay() {
        double u = Math.max(env.nextFloat(), 1.0E-6);
        return Math.min(MAX_DELAY_TICKS, Math.max(1L, (long) Math.ceil(-Math.log(u) * MEAN_DELAY_TICKS)));
    }

    /** TFC {@code randomTick}/{@code tick}: remove the leaf if it still has no log in reach. Region thread of the leaf. */
    void decay(World world, Pos leaf, String id) {
        if (!world.isChunkLoaded(leaf.x() >> 4, leaf.z() >> 4) || !isNonPersistentLeaf(world, leaf, id)) {
            return;
        }
        Map<Pos, String> leafIds = new HashMap<>();
        Map<Pos, Cell> cache = new HashMap<>();
        LeafRules.Probe probe = (x, y, z) -> {
            Pos p = new Pos(x, y, z);
            Cell c = cache.computeIfAbsent(p, k -> classify(world, k, leafIds));
            return c == Cell.LEAF && !id.equals(leafIds.get(p)) ? Cell.OTHER : c;
        };
        if (!LeafRules.decays(probe, leaf)) {
            return;
        }
        Block block = world.getBlockAt(leaf.x(), leaf.y(), leaf.z());
        blocks.remove(block);
        if (block.getType() != Material.AIR && blocks.stateAt(block).isEmpty()) {
            block.setType(Material.AIR);
        }
        if (env.nextFloat() < LeafRules.DECAY_FALLEN_CHANCE) {
            fallenLeaves(world, leaf, id);
        }
    }

    /** TFC {@code createDestructionEffects(false)}: a fallen twig, or half the time fallen leaves, on the ground below. */
    void fallenLeaves(World world, Pos leaf, String leavesId) {
        String wood = leavesId.substring("tfp:wood/leaves/".length());
        LeafRules.Column column = new LeafRules.Column() {
            private Optional<BlockStateRef> state(int x, int y, int z) {
                return world.isChunkLoaded(x >> 4, z >> 4) && Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)
                        ? blocks.stateAt(world.getBlockAt(x, y, z)) : Optional.of(BlockStateRef.of("minecraft:bedrock"));
            }

            @Override
            public boolean leavesOrReplaceable(int x, int y, int z) {
                Optional<BlockStateRef> s = state(x, y, z);
                return (s.isPresent() && s.get().id().startsWith("tfp:wood/leaves/")) || replaceable(x, y, z);
            }

            @Override
            public boolean replaceable(int x, int y, int z) {
                return world.getBlockAt(x, y, z).isReplaceable() && state(x, y, z).isEmpty();
            }

            @Override
            public boolean air(int x, int y, int z) {
                return world.getBlockAt(x, y, z).getType().isAir();
            }

            @Override
            public boolean canSurvive(int x, int y, int z, boolean leaf) {
                return world.getBlockAt(x, y - 1, z).getType().isSolid();
            }
        };
        LeafRules.fallenLeavesLanding(column, leaf, false, env::nextFloat).ifPresent(landing -> {
            String id = landing.leaf() ? "tfp:wood/fallen_leaves/" + wood : "tfp:wood/twig/" + wood;
            blocks.place(world.getBlockAt(landing.pos().x(), landing.pos().y(), landing.pos().z()), BlockStateRef.of(id), true);
        });
    }
}
