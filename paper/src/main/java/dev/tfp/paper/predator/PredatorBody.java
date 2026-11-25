/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.predator;

import dev.tfp.core.predator.PredatorMelee;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * What the predator goals ask of and do to the world, in one seam: the server implementation is
 * {@link PaperPredatorBody}, tests use a recorder. Every call runs on the predator's owning thread.
 */
interface PredatorBody {
    /** Living entities within {@code range} blocks (a cube) that the predator can see, owned by the current region. */
    List<LivingEntity> visible(Mob mob, double range);

    /** Living entities within {@code range} blocks (a cube), seen or not, owned by the current region. */
    List<LivingEntity> nearby(Mob mob, double range);

    /** Living entities other than the predator inside its bounding box. */
    List<LivingEntity> touching(Mob mob);

    /** Players within {@code range} blocks (a cube) that are not spectators, owned by the current region. */
    default List<org.bukkit.entity.Player> players(Mob mob, double range) {
        return List.of();
    }

    /** Whether {@code mob} can see {@code target} within {@code range} blocks (the visible-entities memory). */
    boolean sees(Mob mob, LivingEntity target, double range);

    /** A standable block position at the predator's block plus the offset, or null. */
    Location standable(Mob mob, int dx, int dy, int dz);

    boolean moveTo(Mob mob, Location to, double speed);

    boolean moveTo(Mob mob, LivingEntity to, double speed);

    void stop(Mob mob);

    boolean hasPath(Mob mob);

    void lookAt(Mob mob, Entity at);

    /** In water or a bubble column. */
    boolean inWater(Mob mob);

    /** Vanilla {@code JumpControl.jump()} plus an extra velocity (the aggressive swim). */
    void jump(Mob mob, double vx, double vy, double vz);

    /** The bounding box. */
    PredatorMelee.Box box(Entity e);

    /** The level day time ({@code level.getDayTime()}). */
    long dayTime(Mob mob);

    /** The world's difficulty is peaceful. */
    boolean peaceful(Mob mob);

    /** {@code doHurtTarget}: swing, hit with the attack damage attribute, attack animation and sound. */
    void attack(Mob mob, LivingEntity target, String sound);

    /** The sleeping pose on or off (model animation, sitting carrier). */
    void sleepPose(Mob mob, boolean asleep);

    /** A vanilla sound key at the predator ({@code entity.wolf.howl}). */
    void vanillaSound(Mob mob, String key, float volume, float pitch);

    /** {@code setAggressive}. */
    void aggressive(Mob mob, boolean aggressive);
}
