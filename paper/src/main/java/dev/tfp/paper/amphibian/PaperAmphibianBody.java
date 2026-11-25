/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.amphibian;

import dev.tfp.core.amphibian.AmphibianIdle;
import dev.tfp.core.amphibian.AmphibiousEscape;
import dev.tfp.core.aquatic.PlayDeadTimer;
import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.paper.integration.AnimationSpec;
import dev.tfp.paper.integration.EntityModels;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** {@link AmphibianBody} on Paper's pathfinder, entity queries and the model adapter. Owning thread only. */
final class PaperAmphibianBody implements AmphibianBody {
    static final String PLAY_DEAD = "play_dead";
    /** The random-position searches try this many spots, as vanilla's {@code RandomPos.generateRandomPos}. */
    static final int TRIES = 10;

    private final Supplier<EntityModels> models;

    PaperAmphibianBody(Supplier<EntityModels> models) {
        this.models = models;
    }

    @Override
    public boolean inWater(Mob mob) {
        return mob.isInWater(); // a bubble column holds water too
    }

    @Override
    public boolean waterAt(Location at) {
        return Bukkit.isOwnedByCurrentRegion(at) && water(at.getBlock());
    }

    static boolean water(Block b) {
        return b.getType() == Material.WATER || b.getType() == Material.BUBBLE_COLUMN
                || (b.getBlockData() instanceof Waterlogged w && w.isWaterlogged());
    }

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
    public Optional<Location> randomSwim(Mob mob) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < TRIES; i++) {
            Location at = offset(mob, r.nextInt(-AmphibianIdle.STROLL_HORIZONTAL, AmphibianIdle.STROLL_HORIZONTAL + 1),
                    r.nextInt(-AmphibianIdle.STROLL_VERTICAL, AmphibianIdle.STROLL_VERTICAL + 1),
                    r.nextInt(-AmphibianIdle.STROLL_HORIZONTAL, AmphibianIdle.STROLL_HORIZONTAL + 1));
            if (at != null && water(at.getBlock())) {
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Location> randomStroll(Mob mob) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < TRIES; i++) {
            Location at = offset(mob, r.nextInt(-AmphibianIdle.STROLL_HORIZONTAL, AmphibianIdle.STROLL_HORIZONTAL + 1),
                    r.nextInt(-AmphibianIdle.STROLL_VERTICAL, AmphibianIdle.STROLL_VERTICAL + 1),
                    r.nextInt(-AmphibianIdle.STROLL_HORIZONTAL, AmphibianIdle.STROLL_HORIZONTAL + 1));
            Location stand = at == null ? null : standUp(at);
            if (stand != null) {
                return Optional.of(stand);
            }
        }
        return Optional.empty();
    }

    @Override
    public AmphibiousEscape.Candidate awayFrom(Mob mob, Location from) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector away = mob.getLocation().toVector().subtract(from.toVector()).setY(0);
        double base = away.lengthSquared() < 1.0E-7 ? r.nextDouble(Math.PI * 2) : Math.atan2(away.getZ(), away.getX());
        // DefaultRandomPos.getPosAway: a spot within 10 x 7 in a half-circle cone away from the threat
        double angle = base + (r.nextDouble() - 0.5) * Math.PI;
        double dist = Math.sqrt(r.nextDouble()) * AmphibiousEscape.AWAY_HORIZONTAL;
        int dy = r.nextInt(-AmphibiousEscape.AWAY_VERTICAL, AmphibiousEscape.AWAY_VERTICAL + 1);
        Location l = mob.getLocation();
        double x = l.getX() + Math.cos(angle) * dist;
        double y = l.getY() + dy;
        double z = l.getZ() + Math.sin(angle) * dist;
        World w = mob.getWorld();
        // new BlockPos((int) x, (int) y, (int) z): truncation as in TFC
        Location at = new Location(w, (int) x, (int) y, (int) z);
        if (!Bukkit.isOwnedByCurrentRegion(at) || !w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return null;
        }
        Block block = at.getBlock();
        // AmphibianNavigation.isStableDestination: the block below is not air
        if (block.getRelative(0, -1, 0).getType().isAir()) {
            return null;
        }
        boolean waterAbove = water(block.getRelative(0, 1, 0));
        boolean land = !block.getType().isAir() && !water(block);
        return new AmphibiousEscape.Candidate(new AmphibiousEscape.Vec(x, y, z), waterAbove, land);
    }

    @Override
    public AmphibianIdle.WaterProbe waterProbe(Mob mob) {
        Location base = mob.getLocation();
        World w = mob.getWorld();
        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();
        return new AmphibianIdle.WaterProbe() {
            private Block at(int dx, int dy, int dz) {
                int x = bx + dx;
                int z = bz + dz;
                if (!w.isChunkLoaded(x >> 4, z >> 4) || !Bukkit.isOwnedByCurrentRegion(w, x >> 4, z >> 4)) {
                    return null;
                }
                return w.getBlockAt(x, by + dy, z);
            }

            @Override
            public boolean water(int dx, int dy, int dz) {
                Block b = at(dx, dy, dz);
                return b != null && b.getType() == Material.WATER;
            }

            @Override
            public boolean airAbove(int dx, int dy, int dz) {
                Block b = at(dx, dy + 1, dz);
                return b != null && b.getType().isAir();
            }

            @Override
            public boolean close(int dx, int dy, int dz) {
                double x = bx + dx + 0.5 - base.getX();
                double y = by + dy + 0.5 - base.getY();
                double z = bz + dz + 0.5 - base.getZ();
                return x * x + y * y + z * z < 1.5 * 1.5;
            }
        };
    }

    @Override
    public PredatorMelee.Box box(Entity e) {
        BoundingBox b = e.getBoundingBox();
        return new PredatorMelee.Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ());
    }

    @Override
    public void attack(Mob mob, LivingEntity target) {
        mob.lookAt(target);
        mob.swingMainHand();
        mob.attack(target);
    }

    @Override
    public void effects(Mob mob, List<PlayDeadTimer.StatusEffect> effects) {
        for (PlayDeadTimer.StatusEffect e : effects) {
            PotionEffectType type = effectType(e.id());
            if (type != null) {
                // new MobEffectInstance(effect, 200, amplifier, false, false): not ambient, no particles
                mob.addPotionEffect(new PotionEffect(type, e.durationTicks(), e.amplifier(), false, false));
            }
        }
    }

    /** TFC's {@code MobEffects} field names to registry keys ({@code DAMAGE_RESISTANCE} is {@code minecraft:resistance}). */
    static PotionEffectType effectType(String id) {
        String key = id.substring(id.indexOf(':') + 1);
        if (key.equals("damage_resistance")) {
            key = "resistance";
        }
        return Registry.EFFECT.get(NamespacedKey.minecraft(key));
    }

    @Override
    public void playDeadPose(Mob mob, boolean dead) {
        if (dead) {
            mob.getPathfinder().stopPathfinding();
            models.get().animate(mob, PLAY_DEAD, AnimationSpec.LOOP);
        } else {
            models.get().stopAnimation(mob, PLAY_DEAD);
        }
    }

    private static Location offset(Mob mob, int dx, int dy, int dz) {
        Location base = mob.getLocation();
        World w = mob.getWorld();
        int x = base.getBlockX() + dx;
        int y = base.getBlockY() + dy;
        int z = base.getBlockZ() + dz;
        if (y <= w.getMinHeight() || y >= w.getMaxHeight() - 1 || !w.isChunkLoaded(x >> 4, z >> 4)
                || !Bukkit.isOwnedByCurrentRegion(w, x >> 4, z >> 4)) {
            return null;
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    /** Vanilla {@code LandRandomPos.movePosUpOutOfSolid} then a dry spot with ground below. */
    private static Location standUp(Location at) {
        World w = at.getWorld();
        int x = at.getBlockX();
        int y = at.getBlockY();
        int z = at.getBlockZ();
        int top = w.getMaxHeight() - 1;
        while (y < top && w.getBlockAt(x, y, z).isSolid()) {
            y++;
        }
        Block below = w.getBlockAt(x, y - 1, z);
        Block feet = w.getBlockAt(x, y, z);
        if (!below.isSolid() || water(feet) || !feet.isPassable()) {
            return null;
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }
}
