/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/ai/livestock/ (children follow the herd) @ c3b4d9536
 */
package dev.tfp.paper.livestockai;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.animal.AnimalRecord;
import dev.tfp.core.animal.AnimalCheck;
import dev.tfp.core.clock.ClockSnapshot;
import dev.tfp.core.livestock.LivestockAge;
import dev.tfp.paper.animal.AnimalGoal;
import dev.tfp.paper.animal.AnimalPdc;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;

/** A child walks to the nearest adult of its species within {@value #RANGE} blocks when it is more than 3 blocks from it. */
final class FollowAdultGoal extends AnimalGoal {
    static final double RANGE = 16.0;
    static final double CLOSE = 3.0;
    private final Supplier<ClockSnapshot> clock;
    private Entity leader;

    FollowAdultGoal(Mob mob, Supplier<ClockSnapshot> clock) {
        super(mob, "livestock_follow_adult", 20, GoalType.MOVE);
        this.clock = clock;
    }

    private Optional<AnimalRecord> record(Entity e) {
        return AnimalPdc.read(e.getPersistentDataContainer());
    }

    @Override
    protected boolean wants() {
        ClockSnapshot now = clock.get();
        Optional<AnimalRecord> mine = record(mob);
        if (!now.isSet() || mine.isEmpty() || AnimalCheck.ageAt(mine.get(), now.playerTicks()) != LivestockAge.CHILD) {
            return false;
        }
        leader = null;
        double best = RANGE * RANGE;
        for (Entity e : mob.getNearbyEntities(RANGE, RANGE / 2, RANGE)) {
            if (e == mob || !Bukkit.isOwnedByCurrentRegion(e)) {
                continue;
            }
            Optional<AnimalRecord> other = record(e);
            if (other.isEmpty() || !other.get().species().equals(mine.get().species())
                    || AnimalCheck.ageAt(other.get(), now.playerTicks()) == LivestockAge.CHILD) {
                continue;
            }
            double d = e.getLocation().distanceSquared(mob.getLocation());
            if (d > CLOSE * CLOSE && d < best) {
                best = d;
                leader = e;
            }
        }
        return leader != null;
    }

    @Override
    protected void step() {
        if (mob.getTicksLived() % 10 != 0) {
            return; // repath at most twice a second
        }
        if (leader != null && leader.isValid()) {
            mob.getPathfinder().moveTo(leader, 1.0);
        }
    }

    @Override
    protected void end() {
        mob.getPathfinder().stopPathfinding();
        leader = null;
    }
}
