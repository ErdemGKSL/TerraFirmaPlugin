/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onExplosionDetonate)
 */
package dev.tfp.paper.collapse.explosion;

import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.collapse.CollapseService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * TFC {@code onExplosionDetonate}: every explosion hands its block list and centre to the {@link CollapseService}. Thin: it
 * copies the list into positions and returns. Runs at {@code NORMAL}, below {@code PowderkegBlasts} at {@code HIGH}, so it
 * reads the list before the keg's tag filter, as TFC reads {@code getToBlow()} unfiltered.
 */
public final class ExplosionCollapseListener implements Listener {
    /** Where the decision goes; the service in the server, a recorder in tests. */
    @FunctionalInterface
    public interface Sink {
        void explosion(World world, BlockPos center, List<BlockPos> blown);
    }

    private final Sink sink;

    public ExplosionCollapseListener(CollapseService service) {
        this(service::explosion);
    }

    public ExplosionCollapseListener(Sink sink) {
        this.sink = sink;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handle(event.getLocation(), event.blockList());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handle(event.getBlock().getLocation(), event.blockList());
    }

    private void handle(Location at, List<Block> blocks) {
        World world = at.getWorld();
        if (world == null || blocks.isEmpty()) {
            return;
        }
        List<BlockPos> blown = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            blown.add(new BlockPos(b.getX(), b.getY(), b.getZ()));
        }
        sink.explosion(world, new BlockPos(at.getBlockX(), at.getBlockY(), at.getBlockZ()), blown);
    }
}
