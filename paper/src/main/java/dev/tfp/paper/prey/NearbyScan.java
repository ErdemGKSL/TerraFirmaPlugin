/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;

/**
 * One nearby-entity scan per chunk section, range and tick, shared by every animal in that section that senses in the
 * same tick (PAR-FAUNA-26). Each animal then keeps the entities whose box meets its own box inflated by the range, which
 * is exactly what {@code Entity#getNearbyEntities(range, range, range)} returns. The cache is per thread (a Folia region
 * thread, or the Paper main thread) and is dropped when the stamp changes; entities are re-checked (alive, owned by the
 * current region) on every use.
 */
public final class NearbyScan {
    /** How far an animal's box can reach outside the section its block is in (half width and height, rounded up). */
    static final double MARGIN = 4.0;

    private record Key(UUID world, int x, int y, int z, double range) {
    }

    private static final class State {
        long stamp = Long.MIN_VALUE;
        final Map<Key, List<LivingEntity>> scans = new HashMap<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private NearbyScan() {
    }

    /**
     * Living entities other than {@code mob} whose box meets the animal's box inflated by {@code range}, alive and owned by
     * the current region. Line of sight is not tested.
     *
     * @param stamp the current tick (any value that changes every tick); scans are shared within one stamp
     */
    public static List<LivingEntity> around(Mob mob, double range, long stamp) {
        Location l = mob.getLocation();
        World world = mob.getWorld();
        State state = STATE.get();
        if (state.stamp != stamp) {
            state.scans.clear();
            state.stamp = stamp;
        }
        Key key = new Key(world.getUID(), l.getBlockX() >> 4, l.getBlockY() >> 4, l.getBlockZ() >> 4, range);
        List<LivingEntity> section = state.scans.computeIfAbsent(key, k -> scan(world, k));
        BoundingBox box = mob.getBoundingBox().expand(range);
        List<LivingEntity> out = new ArrayList<>(section.size());
        for (LivingEntity e : section) {
            if (e != mob && !e.isDead() && e.isValid() && box.overlaps(e.getBoundingBox()) && Bukkit.isOwnedByCurrentRegion(e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<LivingEntity> scan(World world, Key k) {
        double reach = k.range() + MARGIN;
        BoundingBox box = new BoundingBox(k.x() << 4, k.y() << 4, k.z() << 4, (k.x() + 1) << 4, (k.y() + 1) << 4,
                (k.z() + 1) << 4).expand(reach);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(box)) {
            if (e instanceof LivingEntity living) {
                out.add(living);
            }
        }
        return out;
    }
}
