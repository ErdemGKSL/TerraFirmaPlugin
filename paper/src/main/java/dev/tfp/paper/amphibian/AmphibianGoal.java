/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.amphibian;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.function.DoubleSupplier;

/**
 * The amphibian brain as one Paper goal (PAR-FAUNA-12): always active, it runs {@link AmphibianBrain#think} once per tick
 * (TFC's {@code customServerAiStep}) and, for a carrier that cannot swim by itself, the in-water half of TFC's
 * {@code TrueAmphibiousMoveControl} ({@link AmphibiousMover}). It holds the move, look and jump controls so no other goal
 * moves the mob. Owning thread only.
 */
final class AmphibianGoal extends AnimalGoal {
    private final AmphibianBrain brain;
    private final boolean mover;
    private final DoubleSupplier movementSpeed;

    AmphibianGoal(AmphibianBrain brain, boolean mover, DoubleSupplier movementSpeed) {
        super(brain.mob, "amphibian_brain", 1, GoalType.MOVE, GoalType.LOOK, GoalType.JUMP);
        this.brain = brain;
        this.mover = mover;
        this.movementSpeed = movementSpeed;
    }

    @Override
    protected boolean wants() {
        return brain.mob.isValid();
    }

    @Override
    protected void step() {
        brain.think(brain.mob.getTicksLived());
        if (mover) {
            double speed = Double.isNaN(brain.walkSpeed) ? 1.0 : brain.walkSpeed;
            AmphibiousMover.step(brain.mob, speed, movementSpeed.getAsDouble(), brain.memory.playingDead());
        }
    }
}
