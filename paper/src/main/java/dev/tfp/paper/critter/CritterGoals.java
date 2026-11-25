/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java @ c3b4d9536 (registerGoals, CritterEscapeGoal)
 */
package dev.tfp.paper.critter;

import dev.tfp.core.critter.CritterEscape;
import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * The two avoid goals of a water critter (the third, random swimming, is the carrier's own). Owning thread only.
 */
final class CritterGoals {
    private CritterGoals() {
    }

    /** A spot {@code distance} blocks straight away from {@code threat}, level with {@code mob}. */
    static Location away(Mob mob, Location threat, double distance) {
        Location at = mob.getLocation();
        Vector dir = at.toVector().subtract(threat.toVector()).setY(0);
        if (dir.lengthSquared() < 1.0E-6) {
            dir = new Vector(1, 0, 0);
        }
        return at.clone().add(dir.normalize().multiply(distance));
    }

    static Entity nearest(Mob mob, double range, Predicate<Entity> test) {
        Entity best = null;
        double bestSq = range * range;
        for (Entity e : mob.getNearbyEntities(range, range, range)) {
            if (e == mob || !e.isValid() || !test.test(e)) {
                continue;
            }
            double d = e.getLocation().distanceSquared(mob.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = e;
            }
        }
        return best;
    }

    /** {@code CritterEscapeGoal}: after a hit by a living mob, run from the nearest player at speed 2 until far away. */
    static final class Escape extends AnimalGoal {
        private final ToLongFunction<Mob> ticksSinceHurt;
        private Player avoided;

        Escape(Mob mob, ToLongFunction<Mob> ticksSinceHurt) {
            super(mob, "critter_escape", 5, GoalType.MOVE);
            this.ticksSinceHurt = ticksSinceHurt;
        }

        @Override
        protected boolean wants() {
            if (!CritterEscape.shouldEscape(ticksSinceHurt.applyAsLong(mob))) {
                return false;
            }
            avoided = (Player) nearest(mob, CritterEscape.PLAYER_DISTANCE, e -> e instanceof Player);
            return avoided != null;
        }

        @Override
        protected boolean keepsGoing() {
            return avoided != null && avoided.isValid() && avoided.getWorld().equals(mob.getWorld())
                    && CritterEscape.continues(!mob.getPathfinder().hasPath(), mob.getLocation().distanceSquared(avoided.getLocation()));
        }

        @Override
        protected void begin() {
            flee();
        }

        @Override
        protected void step() {
            if (avoided != null && !mob.getPathfinder().hasPath()) {
                flee();
            }
        }

        private void flee() {
            if (avoided != null) {
                mob.getPathfinder().moveTo(away(mob, avoided.getLocation(), 2 * CritterEscape.PLAYER_DISTANCE), CritterEscape.PLAYER_SPEED);
            }
        }

        @Override
        protected void end() {
            avoided = null;
        }
    }

    /** {@code AvoidEntityGoal(this, WaterAnimal.class, 8, 5, 5.4, ocean predators)}: walk away, sprint when within 7 blocks. */
    static final class AvoidPredators extends AnimalGoal {
        private final Predicate<Entity> predator;
        private LivingEntity avoided;

        AvoidPredators(Mob mob, Predicate<Entity> predator) {
            super(mob, "critter_avoid_predators", 5, GoalType.MOVE);
            this.predator = predator;
        }

        @Override
        protected boolean wants() {
            Entity e = nearest(mob, CritterEscape.PREDATOR_DISTANCE, predator);
            avoided = e instanceof LivingEntity living ? living : null;
            return avoided != null;
        }

        @Override
        protected boolean keepsGoing() {
            return avoided != null && avoided.isValid()
                    && (mob.getPathfinder().hasPath() || mob.getLocation().distanceSquared(avoided.getLocation()) < CritterEscape.PREDATOR_DISTANCE * CritterEscape.PREDATOR_DISTANCE);
        }

        @Override
        protected void begin() {
            flee();
        }

        @Override
        protected void step() {
            if (avoided != null && !mob.getPathfinder().hasPath()) {
                flee();
            }
        }

        private void flee() {
            if (avoided != null) {
                double sq = mob.getLocation().distanceSquared(avoided.getLocation());
                double speed = sq < CritterEscape.CONTINUE_DISTANCE_SQ ? CritterEscape.PREDATOR_SPRINT_SPEED : CritterEscape.PREDATOR_WALK_SPEED;
                mob.getPathfinder().moveTo(away(mob, avoided.getLocation(), 2 * CritterEscape.PREDATOR_DISTANCE), speed);
            }
        }

        @Override
        protected void end() {
            avoided = null;
        }
    }
}
