/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/AxeLoggingHelper.java @ c3b4d9536 (doLogging),
 *       src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onBlockBroken, the "chop down a tree" branch)
 */
package dev.tfp.paper.logging;

import dev.tfp.core.logging.TreeLogs;
import dev.tfp.core.logging.TreeLogs.Pos;
import dev.tfp.core.worldgen.tree.BranchDirection;
import dev.tfp.paper.integration.BlockStateRef;
import dev.tfp.paper.integration.CustomBlocks;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * PAR-GATHER-03: TFC's tree felling. Breaking a natural log (one with a {@code branch_direction}, in
 * {@code #tfc:logs_that_log}) with an item in {@code #minecraft:axes} cancels the break and cuts every connected log instead
 * ({@link TreeLogs#find}), each as a {@link BlockBreakEvent} of the player (so protection plugins and the loot runtime see
 * it), with one durability per log; an axe in {@code #tfc:inefficient_logging_axes} drops each log with a 60 % chance; the
 * fall stops when the axe breaks. TFC does not check sneaking, nor do we.
 *
 * <p>Threads ("decide, then apply on the owner"): the search runs on the thread of the break event, reading only blocks that
 * thread owns ({@link #branchAt}); the cuts are applied in batches of {@value #BATCH} logs per tick, each batch on the region
 * that owns the logs it touches ({@code Scheduler.region}); a log is checked again when its batch reaches it.
 */
public final class TreeFelling implements Listener {
    /** Logs cut per region task. */
    public static final int BATCH = 16;

    /** The tag checks and the platform actions the listener needs. */
    public interface Env {
        /** Whether the stack is in {@code #minecraft:axes}. Any thread. */
        boolean isAxe(ItemStack stack);

        /** Whether the stack is in {@code #tfc:inefficient_logging_axes}. Any thread. */
        boolean isInefficient(ItemStack stack);

        /** Whether the block state is in {@code #tfc:logs_that_log}. Any thread. */
        boolean isLoggingBlock(BlockStateRef state);

        /** Runs the task on the region thread owning the chunk; the first task of a fall may run inline when owned. */
        void region(World world, int chunkX, int chunkZ, Runnable task);

        /** Runs the task on the region thread owning the chunk after the given ticks. */
        void regionDelayed(World world, int chunkX, int chunkZ, long delayTicks, Runnable task);

        /** Takes one durability from the player's main hand; false when the stack is gone (broken). */
        boolean wear(Player player);

        /** {@code level.getRandom().nextFloat()}. */
        float nextFloat();
    }

    private final CustomBlocks blocks;
    private final Env env;
    private final ThreadLocal<Boolean> cutting = ThreadLocal.withInitial(() -> false);

    public TreeFelling(CustomBlocks blocks, Env env) {
        this.blocks = Objects.requireNonNull(blocks, "blocks");
        this.env = Objects.requireNonNull(env, "env");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (cutting.get()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack axe = player.getInventory().getItemInMainHand();
        if (axe.getType().isAir() || !env.isAxe(axe)) {
            return;
        }
        Block origin = event.getBlock();
        TreeLogs.Blocks view = view(origin.getWorld());
        Pos start = new Pos(origin.getX(), origin.getY(), origin.getZ());
        if (!TreeLogs.shouldLog(view, start)) {
            return;
        }
        // TFC cancels the break whatever the outcome of the logging
        event.setCancelled(true);
        List<Pos> logs = TreeLogs.find(view, start);
        new Fall(player, origin.getWorld(), axe.clone(), env.isInefficient(axe), logs).next(true);
    }

    /** The search's view of the world: only logging blocks of chunks this thread owns. */
    TreeLogs.Blocks view(World world) {
        return (x, y, z) -> branchAt(world, x, y, z);
    }

    Optional<BranchDirection> branchAt(World world, int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)
                || !Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4)) {
            return Optional.empty();
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType().isAir()) {
            return Optional.empty();
        }
        Optional<BlockStateRef> state = blocks.stateAt(block);
        if (state.isEmpty() || !env.isLoggingBlock(state.get())) {
            return Optional.empty();
        }
        String name = state.get().properties().get("branch_direction");
        if (name == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(BranchDirection.byName(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** One tree coming down. */
    private final class Fall {
        private final Player player;
        private final World world;
        private final ItemStack axe;
        private final boolean inefficient;
        private final List<Pos> logs;
        private int index;

        Fall(Player player, World world, ItemStack axe, boolean inefficient, List<Pos> logs) {
            this.player = player;
            this.world = world;
            this.axe = axe;
            this.inefficient = inefficient;
            this.logs = logs;
        }

        void next(boolean first) {
            if (index >= logs.size()) {
                return;
            }
            Pos at = logs.get(index);
            Runnable batch = this::batch;
            if (first) {
                env.region(world, at.x() >> 4, at.z() >> 4, batch);
            } else {
                env.regionDelayed(world, at.x() >> 4, at.z() >> 4, 1, batch);
            }
        }

        private void batch() {
            int done = 0;
            while (index < logs.size() && done < BATCH) {
                Pos at = logs.get(index);
                if (!Bukkit.isOwnedByCurrentRegion(world, at.x() >> 4, at.z() >> 4)) {
                    break; // the next log is in another region: continue there
                }
                index++;
                done++;
                if (!cut(world.getBlockAt(at.x(), at.y(), at.z()))) {
                    index = logs.size();
                    return;
                }
            }
            next(false);
        }

        /** TFC {@code destroyBlock(log, drops, player)} + {@code damageItem}; false when the fall must stop. */
        private boolean cut(Block block) {
            if (!player.isOnline() || !sameAxe(player.getInventory().getItemInMainHand())) {
                return false;
            }
            if (branchAt(world, block.getX(), block.getY(), block.getZ()).filter(TreeLogs::natural).isEmpty()) {
                return true; // changed since the search: leave it
            }
            boolean drops = !inefficient || env.nextFloat() < TreeLogs.INEFFICIENT_DROP_CHANCE;
            BlockBreakEvent cut = new BlockBreakEvent(block, player);
            cut.setDropItems(drops);
            cutting.set(true);
            try {
                if (!cut.callEvent()) {
                    return true;
                }
            } finally {
                cutting.set(false);
            }
            if (blocks.stateAt(block).isPresent()) {
                blocks.remove(block);
            } else {
                block.setType(Material.AIR);
            }
            return player.getGameMode() == GameMode.CREATIVE || env.wear(player);
        }

        private boolean sameAxe(ItemStack held) {
            return held.getType() == axe.getType() && env.isAxe(held);
        }
    }
}
