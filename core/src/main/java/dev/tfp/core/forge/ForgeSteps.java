/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * files: src/main/java/net/dries007/tfc/common/component/forge/ForgeSteps.java and Forging.java (matches, isWorked, totalWorked)
 * @ c3b4d9536
 */
package dev.tfp.core.forge;

import java.util.ArrayList;
import java.util.List;

/**
 * The last three steps taken on an item, and the total number of steps taken since work began. Pure and immutable.
 *
 * @param steps the last three steps, in the order {@code [thirdLast, secondLast, last]}. With fewer than three recorded steps the list
 *              holds fewer elements
 * @param total the total number of steps taken
 */
public record ForgeSteps(List<ForgeStep> steps, int total) {
    public static final ForgeSteps EMPTY = new ForgeSteps(List.of(), 0);

    public ForgeSteps {
        steps = List.copyOf(steps);
    }

    /**
     * @return a new history with {@code step} appended, dropping the oldest of the last three steps when full.
     */
    public ForgeSteps withStep(ForgeStep step) {
        final List<ForgeStep> next = new ArrayList<>(3);
        if (steps.size() == 3) {
            next.add(steps.get(1));
            next.add(steps.get(2));
        } else {
            next.addAll(steps);
        }
        next.add(step);
        return new ForgeSteps(next, total + 1);
    }

    /**
     * @return {@code true} if at least one step has been taken.
     */
    public boolean isWorked() {
        return total > 0;
    }

    /**
     * @return {@code true} if the last steps satisfy {@code rule}, with the positions counted from the most recent step.
     */
    public boolean matches(ForgeRule rule) {
        final int size = steps.size();
        return rule.matches(
            size == 0 ? null : steps.get(size - 1),
            size <= 1 ? null : steps.get(size - 2),
            size <= 2 ? null : steps.get(size - 3)
        );
    }

    /**
     * @return {@code true} if the last steps satisfy every rule in {@code rules}.
     */
    public boolean matches(List<ForgeRule> rules) {
        for (ForgeRule rule : rules) {
            if (!matches(rule)) {
                return false;
            }
        }
        return true;
    }
}
