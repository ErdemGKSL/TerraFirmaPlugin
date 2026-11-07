/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldwater;

import dev.tfp.core.fluid.RiverWaterRules;
import dev.tfp.core.worldgen.river.Flow;
import dev.tfp.core.worldwater.RiverCurrent;
import dev.tfp.paper.scheduler.Scheduler;
import dev.tfp.paper.scheduler.ThreadAsserts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * PAR-FLUID-03: the river current on dropped items, boats and mobs (NP-FLUID-01: vanilla clients have no river water fluid,
 * so the server pushes). TFC's river water pushes every entity through vanilla's fluid push; here the server adds the same
 * push ({@link RiverCurrent}) to the velocity of entities it simulates itself.
 *
 * <p>Players, and vehicles a player drives, are left alone: their motion is simulated by the client, and a server velocity
 * would overwrite it (ledger).
 *
 * <p>Threads: {@link #scan} runs on a player's thread every {@link #SCAN_PERIOD} ticks and finds nearby candidates in water;
 * each candidate gets a repeating task on its own entity scheduler that reads the blocks in its box (owned by that thread)
 * and the cached river flows ({@link WorldWaterService}, computed on the CPU pool), and stops after
 * {@link #IDLE_LIMIT} ticks out of water.
 */
public final class RiverCurrentTracker {
    /** How often a player's surroundings are searched for entities in water. */
    public static final long SCAN_PERIOD = 20;
    /** The horizontal search radius around a player (one chunk, inside the player's region on Folia). */
    static final double SCAN_RADIUS = 16;
    static final double SCAN_HEIGHT = 8;
    /** A tracked entity out of water this many ticks is dropped. */
    static final int IDLE_LIMIT = 40;
    /** At most this many entities are pushed at once (each costs one task per tick). */
    static final int MAX_TRACKED = 512;
    private static final double DEFLATE = 0.001;

    /** The fluid of one block, as the push reads it. */
    @FunctionalInterface
    interface Blocks {
        /** @return the fluid height of the water at the position, or a negative value for no water */
        float waterHeight(int x, int y, int z);
    }

    /** The river flow of a water block, empty when it is not known yet. */
    @FunctionalInterface
    interface Flows {
        Optional<Flow> flow(int x, int y, int z);
    }

    private final Scheduler scheduler;
    private final WorldWaterService water;
    private final Set<UUID> tracked = ConcurrentHashMap.newKeySet();

    public RiverCurrentTracker(Scheduler scheduler, WorldWaterService water) {
        this.scheduler = scheduler;
        this.water = water;
    }

    /** Player thread: starts the push of nearby entities in water in a TFC world. */
    public void scan(Player player) {
        ThreadAsserts.owned(player);
        World world = player.getWorld();
        if (!water.isTfcWorld(world)) {
            return;
        }
        int cx = player.getLocation().getBlockX() >> 4;
        int cz = player.getLocation().getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                water.chunk(world, cx + dx, cz + dz); // warm the cache
            }
        }
        for (Entity e : player.getNearbyEntities(SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS)) {
            if (tracked.size() >= MAX_TRACKED) {
                return;
            }
            if (pushable(e) && e.isInWater()) {
                track(e);
            }
        }
    }

    /** Starts the per-tick push of one entity unless it is already pushed. Any thread. */
    void track(Entity e) {
        UUID id = e.getUniqueId();
        if (!tracked.add(id)) {
            return;
        }
        Push push = new Push(e);
        Scheduler.TaskHandle handle = scheduler.entityRepeating(e, 1, 1, push, () -> tracked.remove(id));
        if (handle == null) {
            tracked.remove(id);
        } else {
            push.handle = handle;
        }
    }

    int trackedCount() {
        return tracked.size();
    }

    /**
     * Whether the server simulates the entity's motion and vanilla would push it: dropped items, boats nobody drives, and
     * mobs that neither ride nor are ridden by a player. Guardians are not pushed by fluids in vanilla.
     */
    static boolean pushable(Entity e) {
        if (e instanceof Player || !e.isValid() || !e.hasGravity()) {
            return false;
        }
        if (hasPlayerPassenger(e)) {
            return false;
        }
        if (e instanceof Item || e instanceof Boat) {
            return true;
        }
        return e instanceof Mob && !(e instanceof Guardian) && !e.isInsideVehicle();
    }

    private static boolean hasPlayerPassenger(Entity e) {
        for (Entity p : e.getPassengers()) {
            if (p instanceof Player || hasPlayerPassenger(p)) {
                return true;
            }
        }
        return false;
    }

    /** The repeating push of one entity, on its own thread. */
    private final class Push implements Runnable {
        private final Entity entity;
        volatile Scheduler.TaskHandle handle;
        private int idle;

        Push(Entity entity) {
            this.entity = entity;
        }

        @Override
        public void run() {
            if (!pushable(entity)) {
                stop();
                return;
            }
            if (!entity.isInWater()) {
                if (++idle > IDLE_LIMIT) {
                    stop();
                }
                return;
            }
            idle = 0;
            World world = entity.getWorld();
            Vector v = entity.getVelocity();
            RiverWaterRules.Vector change = push(entity.getBoundingBox(), v.getX(), v.getZ(), blocks(world),
                    (x, y, z) -> water.riverFlow(world, x, y, z));
            if (change.x() != 0 || change.y() != 0 || change.z() != 0) {
                entity.setVelocity(v.add(new Vector(change.x(), change.y(), change.z())));
            }
        }

        private void stop() {
            Scheduler.TaskHandle h = handle;
            if (h != null) {
                h.cancel();
            }
            tracked.remove(entity.getUniqueId());
        }
    }

    /**
     * One tick of the river current on a box: vanilla's iteration over the blocks of the box deflated by 0.001, in x, y, z
     * order. A water block whose river flow is not known yet makes the whole push wait (zero), so a half-loaded river never
     * pushes the wrong way.
     */
    static RiverWaterRules.Vector push(BoundingBox box, double vx, double vz, Blocks blocks, Flows flows) {
        double minX = box.getMinX() + DEFLATE;
        double minY = box.getMinY() + DEFLATE;
        double minZ = box.getMinZ() + DEFLATE;
        int x0 = (int) Math.floor(minX);
        int x1 = (int) Math.ceil(box.getMaxX() - DEFLATE);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.ceil(box.getMaxY() - DEFLATE);
        int z0 = (int) Math.floor(minZ);
        int z1 = (int) Math.ceil(box.getMaxZ() - DEFLATE);
        List<RiverCurrent.Cell> cells = new ArrayList<>();
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    float height = blocks.waterHeight(x, y, z);
                    if (height < 0) {
                        continue;
                    }
                    Optional<Flow> flow = flows.flow(x, y, z);
                    if (flow.isEmpty()) {
                        return new RiverWaterRules.Vector(0, 0, 0);
                    }
                    cells.add(new RiverCurrent.Cell(y, height, flow.get()));
                }
            }
        }
        return RiverCurrent.push(cells, minY, false, vx, vz);
    }

    /** The water heights of a world's blocks (owner thread of the blocks). */
    static Blocks blocks(World world) {
        return (x, y, z) -> {
            Block block = world.getBlockAt(x, y, z);
            float height = height(block.getBlockData());
            if (height < 0) {
                return height;
            }
            return isWater(world.getBlockAt(x, y + 1, z).getBlockData()) ? 1f : height;
        };
    }

    /**
     * The vanilla fluid height of a block with nothing of the same fluid above: {@code amount / 9}, with a source or a falling
     * block holding 8, a flowing block {@code 8 - level}, a waterlogged block a source; negative for no water.
     */
    static float height(BlockData data) {
        if (data.getMaterial() == Material.WATER && data instanceof Levelled levelled) {
            int level = levelled.getLevel();
            int amount = level == 0 || level >= 8 ? 8 : 8 - level;
            return amount / 9.0f;
        }
        if (data instanceof Waterlogged w && w.isWaterlogged()) {
            return RiverCurrent.OWN_HEIGHT;
        }
        if (data.getMaterial() == Material.BUBBLE_COLUMN) {
            return RiverCurrent.OWN_HEIGHT;
        }
        return -1f;
    }

    private static boolean isWater(BlockData data) {
        return height(data) >= 0;
    }
}
