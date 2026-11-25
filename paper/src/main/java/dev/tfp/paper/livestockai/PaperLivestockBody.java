/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import java.util.ArrayList;
import dev.tfp.paper.prey.NearbyScan;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** {@link LivestockBody} on Paper's pathfinder, look control and entity queries. Owning thread only. */
final class PaperLivestockBody implements LivestockBody {
    @Override
    public List<LivingEntity> nearby(Mob mob, double range, long now) {
        return NearbyScan.around(mob, range, now);
    }

    @Override
    public List<LivingEntity> nearby(Mob mob, double range, long now, java.util.function.Predicate<LivingEntity> keep,
            String filter) {
        return NearbyScan.around(mob, range, now, keep, filter);
    }

    @Override
    public boolean sees(Mob mob, LivingEntity e) {
        return mob.hasLineOfSight(e);
    }

    @Override
    public List<Player> players(Mob mob, double range) {
        // Location.getNearbyPlayers(range) (the players whose box meets the cube of half size range around the animal's
        // position), over the world's player list instead of an entity-section query: a herd asks this every 10 ticks per
        // animal and the list is usually short or empty (PAR-FAUNA-33). On Folia the list holds the region's players.
        List<Player> all = mob.getWorld().getPlayers();
        if (all.isEmpty()) {
            return List.of();
        }
        Location l = mob.getLocation();
        org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(l.getX(), l.getY(), l.getZ(), l.getX(), l.getY(),
                l.getZ()).expand(range);
        List<Player> out = new ArrayList<>();
        for (Player p : all) {
            if (box.overlaps(p.getBoundingBox()) && Bukkit.isOwnedByCurrentRegion(p)) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public Location standable(Mob mob, int dx, int dy, int dz) {
        Location base = mob.getLocation();
        World world = mob.getWorld();
        int x = base.getBlockX() + dx;
        int y = base.getBlockY() + dy;
        int z = base.getBlockZ() + dz;
        Location probe = new Location(world, x + 0.5, y, z + 0.5);
        if (!Bukkit.isOwnedByCurrentRegion(probe) || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        // vanilla LandRandomPos.movePosUpOutOfSolid
        int top = world.getMaxHeight() - 1;
        while (y < top && world.getBlockAt(x, y, z).isSolid()) {
            y++;
        }
        Block below = world.getBlockAt(x, y - 1, z);
        Block at = world.getBlockAt(x, y, z);
        if (!below.isSolid() || at.isLiquid() || !at.isPassable()) {
            return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    @Override
    public boolean moveTo(Mob mob, Location to, double speed) {
        return mob.getPathfinder().moveTo(to, speed);
    }

    @Override
    public boolean moveTo(Mob mob, Entity to, double speed) {
        return to instanceof LivingEntity living && mob.getPathfinder().moveTo(living, speed);
    }

    @Override
    public void stop(Mob mob) {
        mob.getPathfinder().stopPathfinding();
    }

    @Override
    public boolean hasPath(Mob mob) {
        return mob.getPathfinder().hasPath();
    }

    @Override
    public void lookAt(Mob mob, Entity at) {
        mob.lookAt(at);
    }

    @Override
    public boolean inWater(Mob mob) {
        return mob.isInWater();
    }

    @Override
    public void swimUp(Mob mob, double speed) {
        Vector v = mob.getVelocity();
        mob.setVelocity(v.setY(v.getY() + speed));
    }
}
