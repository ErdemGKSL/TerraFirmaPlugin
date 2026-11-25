/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PestFeastBehavior.java @ c3b4d9536 and
 * src/main/java/net/dries007/tfc/common/entities/ai/pet/MoveOntoBlockBehavior.java
 */
package dev.tfp.paper.critter;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.critter.PestFeast;
import dev.tfp.paper.animal.AnimalGoal;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

/**
 * TFC's {@code PestFeastBehavior}: a pest that smelled a container with food walks onto it for at most 100 ticks, then takes one
 * food item and holds it. With nothing to take it forgets the smell. Owning thread only; the pest's goals run on the thread of
 * the region it stands in, which is the container's region.
 */
final class PestFeastGoal extends AnimalGoal {
    /** What the goal needs from the module. */
    interface Site {
        /** The smelled container block of {@code mob}, or null. */
        Location smelly(Mob mob);

        boolean holdsItem(Mob mob);

        /** {@code isTargetAt}: the block still has a container. */
        boolean isContainer(Location block);

        /** {@code afterReached}: take the food or forget the smell. */
        void reached(Mob mob, Location block);
    }

    /** The pest is on the container when within this horizontal distance of its centre and one block of its height. */
    static final double ON_BLOCK = 0.9;

    private final Site site;
    private Location target;
    private int walkLeft;
    private boolean done;

    PestFeastGoal(Mob mob, Site site) {
        super(mob, "pest_feast", 5, GoalType.MOVE);
        this.site = site;
    }

    @Override
    protected boolean wants() {
        return mob.isValid() && !mob.isInsideVehicle() && !mob.isSleeping() && site.smelly(mob) != null && !site.holdsItem(mob);
    }

    @Override
    protected boolean keepsGoing() {
        return target != null && !done && walkLeft > 0 && !site.holdsItem(mob) && site.isContainer(target);
    }

    @Override
    protected void begin() {
        target = site.smelly(mob);
        walkLeft = PestFeast.WALK_TICKS;
        done = false;
        walk();
    }

    @Override
    protected void step() {
        if (target == null || done) {
            return;
        }
        if (onTarget()) {
            done = true;
            site.reached(mob, target);
            mob.getPathfinder().stopPathfinding();
            return;
        }
        walkLeft--;
        if (!mob.getPathfinder().hasPath() && walkLeft % 20 == 0) {
            walk();
        }
    }

    @Override
    protected void end() {
        target = null;
    }

    private boolean onTarget() {
        Location at = mob.getLocation();
        double dx = at.getX() - (target.getBlockX() + 0.5);
        double dz = at.getZ() - (target.getBlockZ() + 0.5);
        double dy = at.getY() - target.getBlockY();
        return dx * dx + dz * dz <= ON_BLOCK * ON_BLOCK && dy >= -1 && dy <= 1.5;
    }

    private void walk() {
        mob.getPathfinder().moveTo(target.clone().add(0.5, 0, 0.5), 1.0);
    }
}
