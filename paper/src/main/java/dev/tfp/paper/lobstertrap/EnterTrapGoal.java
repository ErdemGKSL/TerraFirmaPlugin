/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/EnterLobsterTrapGoal.java @ c3b4d9536
 */
package dev.tfp.paper.lobstertrap;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.world.BlockPos;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Mob;

/**
 * TFC's {@code EnterLobsterTrapGoal}: look for the closest baited trap within {@value #SEARCH_RANGE} blocks, walk to it at speed
 * 1.0 and, within {@value #ACCEPTED_DISTANCE} blocks of it, be caught. Owning thread of the animal.
 */
final class EnterTrapGoal extends AnimalGoal {
    static final int SEARCH_RANGE = 16;
    static final double SPEED = 1.0;
    static final double ACCEPTED_DISTANCE = 1.8;

    /** What the goal needs from the module. */
    interface Site {
        Optional<BlockPos> nearest(Mob mob);

        /** The animal reached the trap at {@code at}: the catch runs on the trap's region. */
        void reached(Mob mob, BlockPos at);
    }

    private final Site site;
    private BlockPos target;

    EnterTrapGoal(Mob mob, Site site) {
        super(mob, "enter_lobster_trap", 10, GoalType.MOVE);
        this.site = site;
    }

    @Override
    protected boolean wants() {
        target = site.nearest(mob).orElse(null);
        return target != null;
    }

    @Override
    protected boolean keepsGoing() {
        target = site.nearest(mob).orElse(null);
        return target != null;
    }

    @Override
    protected void begin() {
        approach();
    }

    @Override
    protected void step() {
        if (target == null) {
            return;
        }
        Location at = mob.getLocation();
        if (TrapFinder.dist2(target, at.getX(), at.getY(), at.getZ()) <= ACCEPTED_DISTANCE * ACCEPTED_DISTANCE) {
            BlockPos reached = target;
            target = null;
            site.reached(mob, reached);
            return;
        }
        if (!mob.getPathfinder().hasPath()) {
            approach();
        }
    }

    private void approach() {
        if (target != null) {
            mob.getPathfinder().moveTo(new Location(mob.getWorld(), target.x() + 0.5, target.y() + 0.5, target.z() + 0.5), SPEED);
        }
    }

    @Override
    protected void end() {
        target = null;
    }
}
