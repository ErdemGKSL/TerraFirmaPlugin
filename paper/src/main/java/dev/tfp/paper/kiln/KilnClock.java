/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.kiln;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The lit kilns of loaded chunks and the calendar tick each one fires at, so a kiln fires while its chunk stays loaded
 * without ticking it: one global task every {@link #PERIOD_TICKS} hands the due kilns to their region threads, which
 * evaluate them lazily ({@link PitKilns#evaluate}). A kiln whose chunk was unloaded is evaluated again on chunk load.
 * Thread-safe.
 */
public final class KilnClock {
    /** How often the clock looks for due kilns (server ticks). A calendar jump is seen within this period. */
    public static final long PERIOD_TICKS = 20;

    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final Map<Pos, Long> due = new ConcurrentHashMap<>();

    /** Lists the lit kiln at {@code block}, due at calendar tick {@code tick}. */
    public void track(Block block, long tick) {
        due.put(Pos.of(block), tick);
    }

    public void untrack(Block block) {
        due.remove(Pos.of(block));
    }

    /** Drops the kilns of an unloading chunk. */
    public void untrackChunk(World world, int chunkX, int chunkZ) {
        UUID id = world.getUID();
        due.keySet().removeIf(p -> p.world().equals(id) && (p.x() >> 4) == chunkX && (p.z() >> 4) == chunkZ);
    }

    /** Whether the kiln at {@code block} is listed. */
    public boolean tracked(Block block) {
        return due.containsKey(Pos.of(block));
    }

    /** The kilns due at {@code now}, removed from the list (the evaluation lists them again if they still burn). */
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
