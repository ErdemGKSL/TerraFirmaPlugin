/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/GetHookedGoal.java @ c3b4d9536
 */
package dev.tfp.paper.fishing;

import dev.tfp.core.fishing.HookedSpecies;
import dev.tfp.paper.animal.AnimalGoal;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.function.Predicate;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Mob;

/**
 * TFC's {@code GetHookedGoal}: swim to a baited hook within {@value HookedSpecies#HOOK_RADIUS} blocks that its tier of bait suits
 * and, on reaching it, get hooked. Runs on the animal's thread; the hook is read through {@link Site}, which answers from the
 * module's table.
 */
final class HookGoal extends AnimalGoal {
    /** What the goal needs from the module. */
    interface Site {
        /** The hook this animal would go for among {@code candidates}' hooks: bait of the right tier, nobody hooked, owner online. */
        boolean wants(Mob mob, FishHook hook);

        /** The animal reached {@code hook}: hook it (and maybe eat the bait). */
        void hooked(Mob mob, FishHook hook);

        /** Whether the hook may still be taken. */
        boolean free(FishHook hook);
    }

    private static final double REACH_SQ = 1.5 * 1.5;

    private final Site site;
    private FishHook target;

    HookGoal(Mob mob, Site site) {
        super(mob, "get_hooked", 5, GoalType.MOVE);
        this.site = site;
    }

    private FishHook nearest() {
        FishHook best = null;
        double bestSq = Double.MAX_VALUE;
        int r = HookedSpecies.HOOK_RADIUS;
        Predicate<Entity> hook = e -> e instanceof FishHook h && e.isValid() && site.wants(mob, h);
        for (Entity e : mob.getNearbyEntities(r, r, r)) {
            if (hook.test(e)) {
                double d = e.getLocation().distanceSquared(mob.getLocation());
                if (d < bestSq) {
                    bestSq = d;
                    best = (FishHook) e;
                }
            }
        }
        return best;
    }

    @Override
    protected boolean wants() {
        target = nearest();
        return target != null;
    }

    @Override
    protected boolean keepsGoing() {
        return target != null && target.isValid() && site.free(target);
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
        if (mob.getLocation().distanceSquared(target.getLocation()) <= REACH_SQ) {
            FishHook reached = target;
            target = null;
            site.hooked(mob, reached);
            return;
        }
        if (!mob.getPathfinder().hasPath()) {
            approach();
        }
    }

    private void approach() {
        if (target != null) {
            mob.getPathfinder().moveTo(target.getLocation(), HookedSpecies.SPEED);
        }
    }

    @Override
    protected void end() {
        target = null;
    }
}
