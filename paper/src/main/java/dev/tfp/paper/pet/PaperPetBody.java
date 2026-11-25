/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.pet;

import dev.tfp.paper.integration.AnimationSpec;
import dev.tfp.paper.integration.EntityModels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sittable;

/** {@link PetBody} on Paper's pathfinder, entity queries and the model adapter. Owning thread only. */
final class PaperPetBody implements PetBody {
    static final String SIT = "sit";
    static final String SLEEP = "sleep";

    private final Supplier<EntityModels> models;

    PaperPetBody(Supplier<EntityModels> models) {
        this.models = models;
    }

    @Override
    public List<LivingEntity> nearby(Mob mob, double range) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mob.getNearbyEntities(range, range, range)) {
            if (e instanceof LivingEntity living && e != mob && !living.isDead() && Bukkit.isOwnedByCurrentRegion(e)) {
                out.add(living);
            }
        }
        return out;
    }

    @Override
    public boolean sees(Mob mob, LivingEntity target) {
        return target.getWorld().equals(mob.getWorld()) && Bukkit.isOwnedByCurrentRegion(target) && mob.hasLineOfSight(target);
    }

    @Override
    public Location standable(Mob mob, int dx, int dy, int dz) {
        return standableAround(mob, mob.getLocation(), dx, dy, dz);
    }

    @Override
    public Location standableAround(Mob mob, Location center, int dx, int dy, int dz) {
        World world = center.getWorld();
        int x = center.getBlockX() + dx;
        int y = center.getBlockY() + dy;
        int z = center.getBlockZ() + dz;
        Location probe = new Location(world, x + 0.5, y, z + 0.5);
        if (!Bukkit.isOwnedByCurrentRegion(probe) || !world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
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
        return Bukkit.isOwnedByCurrentRegion(to) && mob.getPathfinder().moveTo(to, speed);
    }

    @Override
    public boolean moveTo(Mob mob, LivingEntity to, double speed) {
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
    public String dimension(Mob mob) {
        return mob.getWorld().getKey().asString();
    }

    @Override
    public void pose(Mob mob, boolean sitting, boolean sleeping) {
        if (sitting || sleeping) {
            mob.getPathfinder().stopPathfinding();
        }
        EntityModels m = models.get();
        if (sitting) {
            m.animate(mob, SIT, AnimationSpec.LOOP);
        } else {
            m.stopAnimation(mob, SIT);
        }
        if (sleeping) {
            m.animate(mob, SLEEP, AnimationSpec.LOOP);
        } else {
            m.stopAnimation(mob, SLEEP);
        }
        if (mob instanceof Sittable s) {
            s.setSitting(sitting || sleeping);
        }
    }

    @Override
    public Location findBed(Mob mob, int horizontal, int vertical, Predicate<Block> sitsOn) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < PetGoal.ATTEMPTS; i++) {
            Location spot = standable(mob, r.nextInt(2 * horizontal + 1) - horizontal, r.nextInt(2 * vertical + 1) - vertical,
                    r.nextInt(2 * horizontal + 1) - horizontal);
            if (spot == null) {
                continue;
            }
            Block bed = spot.getBlock().getRelative(0, -1, 0);
            if (sitsOn.test(bed) && bed.getRelative(0, 1, 0).isEmpty()) {
                return spot;
            }
        }
        return null;
    }

    @Override
    public void attack(Mob mob, LivingEntity target, String soundKey) {
        mob.lookAt(target);
        mob.swingMainHand();
        mob.attack(target);
        if (soundKey != null) {
            mob.getWorld().playSound(mob.getLocation(), soundKey, SoundCategory.NEUTRAL, 1f, 1f);
        }
    }

    @Override
    public void sound(Mob mob, String key, float volume, float pitch) {
        mob.getWorld().playSound(mob.getLocation(), key, SoundCategory.NEUTRAL, volume, pitch);
    }

    @Override
    public void hearts(Mob mob) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Location l = mob.getLocation();
        double w = mob.getWidth();
        mob.getWorld().spawnParticle(Particle.HEART, l.getX() + (r.nextDouble() - 0.5) * w * 2, l.getY() + mob.getHeight() * r.nextDouble() + 0.5,
                l.getZ() + (r.nextDouble() - 0.5) * w * 2, 7, r.nextGaussian() * 0.02, r.nextGaussian() * 0.02, r.nextGaussian() * 0.02, 1);
    }
}
