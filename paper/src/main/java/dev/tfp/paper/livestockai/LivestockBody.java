/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.livestockai;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * What the livestock goals ask of and do to the world, in one seam: {@link PaperLivestockBody} on the server, a
 * recorder in tests. Every call runs on the animal's owning thread.
 */
interface LivestockBody {
    /**
     * Living entities within {@code range} blocks (a cube around the animal's box), alive and owned by the current region;
     * line of sight is not tested ({@link #sees}). {@code now} is the current tick: scans in one tick may be shared.
     */
    List<LivingEntity> nearby(Mob mob, double range, long now);

    /** Line of sight from the animal to {@code e} (vanilla {@code Sensing.hasLineOfSight}). */
    boolean sees(Mob mob, LivingEntity e);

    /** Players within {@code range} blocks of the animal, owned by the current region (line of sight ignored). */
    List<Player> players(Mob mob, double range);

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
}
