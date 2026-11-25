/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/DryingBricksBlock.java @ c3b4d9536 (useItemOn, getStateForPlacement,
 *       canBeReplaced, setPlacedBy, tick),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536
 */
package dev.tfp.paper.drying;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The wet piles of loaded chunks and the player tick each one is looked at next, so a pile dries while its chunk stays loaded
 * without ticking it: one global task hands the due piles to their region threads, which settle them lazily
 * ({@link DryingBricks#settle}). The due tick is the earlier of the dry tick and a re-check ({@link DryingBricks#RECHECK_TICKS},
 * about TFC's random tick cadence), which is when the rain is sampled. Thread-safe.
 */
public final class DryingClock {
    /** How often the clock looks for due piles (server ticks). */
    public static final long PERIOD_TICKS = 20;

    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Map<Pos, Long> due = new ConcurrentHashMap<>();

    public void track(Block block, long tick) {
        due.put(Pos.of(block), tick);
    }

    public void untrack(Block block) {
        due.remove(Pos.of(block));
    }

    /** Drops the piles of an unloading chunk. */
    public void untrackChunk(World world, int chunkX, int chunkZ) {
        UUID id = world.getUID();
        due.keySet().removeIf(p -> p.world().equals(id) && (p.x() >> 4) == chunkX && (p.z() >> 4) == chunkZ);
    }

    public boolean tracked(Block block) {
        return due.containsKey(Pos.of(block));
    }

    /** The piles due at {@code now}, removed from the list (settling lists them again if they are still wet). */
    public List<Block> due(long now) {
        List<Block> out = new ArrayList<>();
        for (Map.Entry<Pos, Long> e : due.entrySet()) {
            if (e.getValue() <= now && due.remove(e.getKey(), e.getValue())) {
                World world = Bukkit.getWorld(e.getKey().world());
                if (world != null) {
                    out.add(world.getBlockAt(e.getKey().x(), e.getKey().y(), e.getKey().z()));
                }
            }
        }
        return out;
    }

    public int size() {
        return due.size();
    }
}
