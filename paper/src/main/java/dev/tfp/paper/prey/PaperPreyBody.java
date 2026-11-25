/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** {@link PreyBody} on Paper's pathfinder, look control and entity queries. Owning thread only. */
final class PaperPreyBody implements PreyBody {
    @Override
    public List<LivingEntity> visible(Mob mob, double range) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mob.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity living && e != mob && !living.isDead() && Bukkit.isOwnedByCurrentRegion(e)
                    && mob.hasLineOfSight(e)) {
                out.add(living);
            }
        }
        return out;
    }

    @Override
    public List<LivingEntity> touching(Mob mob, double reach) {
        BoundingBox box = mob.getBoundingBox().clone().expand(reach);
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mob.getWorld().getNearbyEntities(box)) {
            if (e instanceof LivingEntity living && e != mob && !living.isDead() && Bukkit.isOwnedByCurrentRegion(e)) {
                out.add(living);
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
        // vanilla LandRandomPos.movePosUpOutOfSolid: step up while the block is solid, at most to the world top
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
        return mob.getPathfinder().moveTo(to, speed);
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

    @Override
    public void ram(Mob mob, LivingEntity target, double damage, double strength, double x, double z) {
        target.damage(damage, DamageSource.builder(DamageType.MOB_ATTACK_NO_AGGRO).withCausingEntity(mob)
                .withDirectEntity(mob).build());
        target.knockback(strength, x, z);
    }

    @Override
    public void sound(Mob mob, String event) {
        mob.getWorld().playSound(mob.getLocation(), "tfp_animals:" + event, SoundCategory.NEUTRAL, 1f, 1f);
    }
}
