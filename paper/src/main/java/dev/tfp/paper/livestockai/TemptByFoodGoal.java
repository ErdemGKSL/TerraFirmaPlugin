/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/entities/ai/livestock/ (temptation by the species' food) @ c3b4d9536
 */
package dev.tfp.paper.livestockai;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.function.Predicate;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** An animal walks towards a player within {@value #RANGE} blocks who holds its food, and stops about 2 blocks away. */
final class TemptByFoodGoal extends AnimalGoal {
    static final double RANGE = 10.0;
    static final double STOP_DISTANCE = 2.0;
    private final Predicate<ItemStack> food;
    private Player target;

    TemptByFoodGoal(Mob mob, Predicate<ItemStack> food) {
        super(mob, "livestock_tempt", 10, GoalType.MOVE);
        this.food = food;
    }

    private Player find() {
        Player best = null;
        double bestDistance = RANGE * RANGE;
        for (Player p : mob.getWorld().getNearbyPlayers(mob.getLocation(), RANGE)) {
            if (p.isDead() || !(holds(p.getInventory().getItemInMainHand()) || holds(p.getInventory().getItemInOffHand()))) {
                continue;
            }
            double d = p.getLocation().distanceSquared(mob.getLocation());
            if (d < bestDistance) {
                bestDistance = d;
                best = p;
            }
        }
        return best;
    }

    private boolean holds(ItemStack stack) {
        return !stack.isEmpty() && food.test(stack);
    }

    @Override
    protected boolean wants() {
        target = find();
        return target != null;
    }

    @Override
    protected void step() {
        if (mob.getTicksLived() % 10 != 0) {
            return; // repath at most twice a second
        }
        if (target == null) {
            return;
        }
        if (mob.getLocation().distanceSquared(target.getLocation()) > STOP_DISTANCE * STOP_DISTANCE) {
            mob.getPathfinder().moveTo(target, 1.1);
        } else {
            mob.getPathfinder().stopPathfinding();
            mob.lookAt(target);
        }
    }

    @Override
    protected void end() {
        mob.getPathfinder().stopPathfinding();
        target = null;
    }
}
