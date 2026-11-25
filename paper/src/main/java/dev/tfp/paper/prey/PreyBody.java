/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.prey;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

/**
 * What the prey goals ask of and do to the world, in one seam: the server implementation is {@link PaperPreyBody},
 * tests use a recorder. Every call runs on the animal's owning thread (the goal tick or its damage event).
 */
interface PreyBody {
    /**
     * Living entities within {@code range} blocks (a cube around the animal's box), alive and owned by the current region;
     * line of sight is not tested ({@link #sees}). {@code now} is the current tick: scans in one tick may be shared.
     */
    List<LivingEntity> nearby(Mob mob, double range, long now);

    /** Line of sight from the animal to {@code e} (vanilla {@code Sensing.hasLineOfSight}). */
    boolean sees(Mob mob, LivingEntity e);

    /** Living entities in the animal's bounding box inflated by {@code reach} (the ram hit box). */
    List<LivingEntity> touching(Mob mob, double reach);

    /** A standable block position at the animal's block plus the offset (moved up out of solid ground), or null. */
    Location standable(Mob mob, int dx, int dy, int dz);

    boolean moveTo(Mob mob, Location to, double speed);

    boolean moveTo(Mob mob, Entity to, double speed);

    void stop(Mob mob);

    boolean hasPath(Mob mob);

    void lookAt(Mob mob, Entity at);

    boolean inWater(Mob mob);

    /** Vanilla's swim tick: adds upward speed. */
    void swimUp(Mob mob, double speed);

    /** The ram hit: damage as a no-aggro mob attack from {@code mob}, then the knockback along {@code -(x, z)}. */
    void ram(Mob mob, LivingEntity target, double damage, double strength, double x, double z);

    /** A sound at the animal (the TFC event id under {@code tfp_animals:}). */
    void sound(Mob mob, String event);
}
