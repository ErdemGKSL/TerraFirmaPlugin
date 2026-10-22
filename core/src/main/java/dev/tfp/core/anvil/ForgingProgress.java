/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/Forging.java @ c3b4d9536 (addStep, work, isWorked, matches) and
 * src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java (work, the overwork guard and the destroy check)
 */
package dev.tfp.core.anvil;

import dev.tfp.core.forge.ForgeStep;
import dev.tfp.core.forge.ForgeSteps;

/**
 * The forging state of one item on an anvil: the step history and the current work offset. Pure, immutable and thread-safe.
 *
 * <p>The offset is the sum of the steps taken, each step moving it by {@link ForgeStep#step()}. An offset below zero or above
 * {@link ForgeStep#LIMIT} destroys the item (see {@link #isDestroyed()}).
 *
 * @param steps the step history, as {@link ForgeSteps}
 * @param work  the current work offset
 */
public record ForgingProgress(ForgeSteps steps, int work) {
    public static final ForgingProgress EMPTY = new ForgingProgress(ForgeSteps.EMPTY, 0);

    /**
     * @return {@code false} when {@code step} would destroy an item that has not been worked yet. A negative step on untouched work
     *         is refused, as TFC does, so that a player cannot destroy the input by overworking it on the first hit.
     */
    public boolean canApply(ForgeStep step) {
        return steps.isWorked() || work != 0 || step.step() >= 0;
    }

    /**
     * @return the progress after {@code step}. Does not check {@link #canApply(ForgeStep)}.
     */
    public ForgingProgress apply(ForgeStep step) {
        return new ForgingProgress(steps.withStep(step), work + step.step());
    }

    /**
     * @return {@code true} if the work offset has left {@code [0, ForgeStep.LIMIT]}, which destroys the item.
     */
    public boolean isDestroyed() {
        return work < 0 || work > ForgeStep.LIMIT;
    }
}
