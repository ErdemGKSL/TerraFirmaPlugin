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
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;

/**
 * One nearby-entity scan per chunk section, shared by every animal in that section that senses within
 * {@link #SCAN_TICKS} ticks of it and by every range up to {@link #SHARED_RANGE} (PAR-FAUNA-26, PAR-FAUNA-33). Each animal
 * then keeps the entities whose box meets its own box inflated by the range, which is exactly what
 * {@code Entity#getNearbyEntities(range, range, range)} returns, on the boxes read at the scan (at most
 * {@link #SCAN_TICKS} - 1 ticks old: a sensor reading is itself kept for 20 ticks, as vanilla's). The cache is per thread
 * (a Folia region thread, or the Paper main thread); the entities an animal keeps are re-checked (alive, owned by the
 * current region) on every use.
 */
public final class NearbyScan {
    /** How far an animal's box can reach outside the section its block is in (half width and height, rounded up). */
    static final double MARGIN = 4.0;
    /** The largest range served by the shared section scan (the 16-block sensors); larger ranges scan on their own. */
    static final double SHARED_RANGE = 16.0;
    /** How many ticks a section scan is reused (its own age, so the sections' rescans stay spread over the ticks). */
    static final long SCAN_TICKS = 10;
    /** Above this many cached scans the stale ones are dropped. */
    private static final int PURGE_AT = 256;

    private record Key(UUID world, int x, int y, int z, double range) {
    }

    private record Filtered(Key key, String filter) {
    }

    /** The entities of one scan with their boxes (min x, y, z, max x, y, z per entity) at the scan. */
    private record Section(List<LivingEntity> entities, double[] boxes, long at) {
        static Section of(List<LivingEntity> entities, long at) {
            if (entities.isEmpty()) {
                return new Section(List.of(), new double[0], at);
            }
            double[] b = new double[entities.size() * 6];
            for (int i = 0; i < entities.size(); i++) {
                BoundingBox box = entities.get(i).getBoundingBox();
                b[i * 6] = box.getMinX();
                b[i * 6 + 1] = box.getMinY();
                b[i * 6 + 2] = box.getMinZ();
                b[i * 6 + 3] = box.getMaxX();
                b[i * 6 + 4] = box.getMaxY();
                b[i * 6 + 5] = box.getMaxZ();
            }
            return new Section(entities, b, at);
        }

        boolean fresh(long now) {
            return now >= at && now - at < SCAN_TICKS;
        }
    }

    private static final class State {
        final Map<Key, Section> scans = new HashMap<>();
        final Map<Filtered, Section> filtered = new HashMap<>();
    }

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private NearbyScan() {
    }

    /**
     * Living entities other than {@code mob} whose box meets the animal's box inflated by {@code range}, alive and owned by
     * the current region. Line of sight is not tested.
     *
     * @param stamp the current server tick ({@link Ticks#now}); a scan is shared for {@link #SCAN_TICKS} ticks
     */
    public static List<LivingEntity> around(Mob mob, double range, long stamp) {
        State state = state(stamp);
        return keep(mob, range, section(state, mob, key(mob, range), stamp));
    }

    private static Section section(State state, Mob mob, Key key, long stamp) {
        Section s = state.scans.get(key);
        if (s == null || !s.fresh(stamp)) {
            s = scan(mob.getWorld(), key, stamp);
            state.scans.put(key, s);
        }
        return s;
    }

    /**
     * {@link #around(Mob, double, long)} restricted to the entities {@code keep} accepts. The section scan is filtered once
     * per tick and {@code filter} name, so {@code keep} must depend on the entity only; an animal then tests its box and
     * region for the few entities left (PAR-FAUNA-33: the rabbit's predator scan, which usually finds none).
     */
    public static List<LivingEntity> around(Mob mob, double range, long stamp, Predicate<LivingEntity> keep, String filter) {
        State state = state(stamp);
        Key key = key(mob, range);
        Filtered fk = new Filtered(key, filter);
        Section section = state.filtered.get(fk);
        if (section == null || !section.fresh(stamp)) {
            Section all = section(state, mob, key, stamp);
            List<LivingEntity> out = new ArrayList<>();
            for (LivingEntity e : all.entities()) {
                if (keep.test(e)) {
                    out.add(e);
                }
            }
            section = Section.of(out, all.at());
            state.filtered.put(fk, section);
        }
        return section.entities().isEmpty() ? List.of() : keep(mob, range, section);
    }

    private static State state(long stamp) {
        State state = STATE.get();
        if (state.scans.size() + state.filtered.size() > PURGE_AT) {
            state.scans.values().removeIf(s -> !s.fresh(stamp));
            state.filtered.values().removeIf(s -> !s.fresh(stamp));
        }
        return state;
    }

    /** The section key; every range up to {@link #SHARED_RANGE} shares the scan at that range. */
    private static Key key(Mob mob, double range) {
        Location l = mob.getLocation();
        return new Key(mob.getWorld().getUID(), l.getBlockX() >> 4, l.getBlockY() >> 4, l.getBlockZ() >> 4,
                Math.max(range, SHARED_RANGE));
    }

    private static List<LivingEntity> keep(Mob mob, double range, Section section) {
        BoundingBox self = mob.getBoundingBox();
        double x0 = self.getMinX() - range;
        double y0 = self.getMinY() - range;
        double z0 = self.getMinZ() - range;
        double x1 = self.getMaxX() + range;
        double y1 = self.getMaxY() + range;
        double z1 = self.getMaxZ() + range;
        List<LivingEntity> entities = section.entities();
        double[] b = section.boxes();
        List<LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            int o = i * 6;
            // BoundingBox.overlaps on the box read at the scan
            if (b[o] < x1 && b[o + 3] > x0 && b[o + 1] < y1 && b[o + 4] > y0 && b[o + 2] < z1 && b[o + 5] > z0) {
                LivingEntity e = entities.get(i);
                if (e != mob && !e.isDead() && e.isValid() && Bukkit.isOwnedByCurrentRegion(e)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static Section scan(World world, Key k, long at) {
        double reach = k.range() + MARGIN;
        BoundingBox box = new BoundingBox(k.x() << 4, k.y() << 4, k.z() << 4, (k.x() + 1) << 4, (k.y() + 1) << 4,
                (k.z() + 1) << 4).expand(reach);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : world.getNearbyEntities(box)) {
            if (e instanceof LivingEntity living) {
                out.add(living);
            }
        }
        return Section.of(out, at);
    }
}
