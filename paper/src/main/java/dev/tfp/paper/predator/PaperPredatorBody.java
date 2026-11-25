/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.predator;

import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.paper.integration.AnimationSpec;
import dev.tfp.paper.integration.EntityModels;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Sittable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** {@link PredatorBody} on Paper's pathfinder, entity queries and the model adapter. Owning thread only. */
final class PaperPredatorBody implements PredatorBody {
    static final String SLEEP = "sleep";
    static final String ATTACK = "attack";
    private static final AnimationSpec ATTACK_ONCE = AnimationSpec.once(10);

    private final Supplier<EntityModels> models;

    PaperPredatorBody(Supplier<EntityModels> models) {
        this.models = models;
    }

    @Override
    public List<LivingEntity> visible(Mob mob, double range) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : nearby(mob, range)) {
            if (mob.hasLineOfSight(e)) {
                out.add(e);
            }
        }
        return out;
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
    public List<org.bukkit.entity.Player> players(Mob mob, double range) {
        List<org.bukkit.entity.Player> out = new ArrayList<>();
        for (LivingEntity e : nearby(mob, range)) {
            if (e instanceof org.bukkit.entity.Player p && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                out.add(p);
            }
        }
        return out;
    }

    @Override
    public List<LivingEntity> touching(Mob mob) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mob.getWorld().getNearbyEntities(mob.getBoundingBox())) {
            if (e instanceof LivingEntity living && e != mob && !living.isDead() && Bukkit.isOwnedByCurrentRegion(e)) {
                out.add(living);
            }
        }
        return out;
    }

    @Override
    public boolean sees(Mob mob, LivingEntity target, double range) {
        return target.getWorld().equals(mob.getWorld())
                && target.getLocation().distanceSquared(mob.getLocation()) <= range * range
                && Bukkit.isOwnedByCurrentRegion(target) && mob.hasLineOfSight(target);
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
        return Bukkit.isOwnedByCurrentRegion(to) && mob.getPathfinder().moveTo(to, speed);
    }

    @Override
    public boolean moveTo(Mob mob, LivingEntity to, double speed) {
        // Pathfinder.moveTo(LivingEntity, double): the overload every supported Paper and Folia has (PAR-FAUNA-26)
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
    public void jump(Mob mob, double vx, double vy, double vz) {
        mob.setJumping(true);
        if (vx != 0 || vy != 0 || vz != 0) {
            mob.setVelocity(mob.getVelocity().add(new Vector(vx, vy, vz)));
        }
    }

    @Override
    public PredatorMelee.Box box(Entity e) {
        BoundingBox b = e.getBoundingBox();
        return new PredatorMelee.Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ());
    }

    @Override
    public long dayTime(Mob mob) {
        return mob.getWorld().getFullTime();
    }

    @Override
    public boolean peaceful(Mob mob) {
        return mob.getWorld().getDifficulty() == Difficulty.PEACEFUL;
    }

    @Override
    public void attack(Mob mob, LivingEntity target, String sound) {
        mob.lookAt(target);
        mob.swingMainHand();
        mob.attack(target);
        // Predator.doHurtTarget: entity event 4 (the attack animation) and the attack sound, hit or not
        models.get().animate(mob, ATTACK, ATTACK_ONCE);
        mob.getWorld().playSound(mob.getLocation(), "tfp_animals:entity." + sound + ".attack", SoundCategory.NEUTRAL, 1f,
                voicePitch(mob));
    }

    @Override
    public void sleepPose(Mob mob, boolean asleep) {
        if (asleep) {
            mob.getPathfinder().stopPathfinding();
            models.get().animate(mob, SLEEP, AnimationSpec.LOOP);
        } else {
            models.get().stopAnimation(mob, SLEEP);
        }
        if (mob instanceof Sittable s) {
            s.setSitting(asleep);
        }
    }

    @Override
    public void vanillaSound(Mob mob, String key, float volume, float pitch) {
        mob.getWorld().playSound(mob.getLocation(), key, SoundCategory.NEUTRAL, volume, pitch);
    }

    @Override
    public void aggressive(Mob mob, boolean aggressive) {
        if (mob.isAggressive() != aggressive) {
            mob.setAggressive(aggressive);
        }
    }

    /** Vanilla {@code LivingEntity.getVoicePitch}. */
    static float voicePitch(Mob mob) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        float base = mob instanceof org.bukkit.entity.Ageable a && !a.isAdult() ? 1.5f : 1.0f;
        return (r.nextFloat() - r.nextFloat()) * 0.2f + base;
    }
}
