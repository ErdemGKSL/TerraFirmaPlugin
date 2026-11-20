/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.animal;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;

/**
 * Base of the goals FAUNA features give their animals through {@link SpeciesBehaviour#goals} (PAR-FAUNA-21). A goal asks
 * its question only every {@code period} ticks (staggered per animal by its tick count), so hundreds of animals cost
 * little; between asks it keeps its last answer while it runs. All calls are on the animal's owning thread.
 */
public abstract class AnimalGoal implements Goal<Mob> {
    protected final Mob mob;
    private final GoalKey<Mob> key;
    private final EnumSet<GoalType> types;
    private final int period;
    private boolean last;

    protected AnimalGoal(Mob mob, String name, int period, GoalType... types) {
        this.mob = mob;
        this.key = GoalKey.of(Mob.class, new NamespacedKey("tfp", name));
        this.types = types.length == 0 ? EnumSet.noneOf(GoalType.class) : EnumSet.copyOf(java.util.List.of(types));
        this.period = Math.max(1, period);
    }

    /** The question: should this goal run now? Called at most once per period. */
    protected abstract boolean wants();

    /** Whether a running goal continues; by default as long as {@link #wants()}. */
    protected boolean keepsGoing() {
        return wants();
    }

    protected void begin() {
    }

    protected void end() {
    }

    protected void step() {
    }

    @Override
    public final boolean shouldActivate() {
        if ((mob.getTicksLived() + mob.getEntityId()) % period != 0) {
            return false;
        }
        last = wants();
        return last;
    }

    @Override
    public final boolean shouldStayActive() {
        if ((mob.getTicksLived() + mob.getEntityId()) % period == 0) {
            last = keepsGoing();
        }
        return last;
    }

    @Override
    public final void start() {
        begin();
    }

    @Override
    public final void stop() {
        end();
    }

    @Override
    public final void tick() {
        step();
    }

    @Override
    public final GoalKey<Mob> getKey() {
        return key;
    }

    @Override
    public final EnumSet<GoalType> getTypes() {
        return types;
    }
}
