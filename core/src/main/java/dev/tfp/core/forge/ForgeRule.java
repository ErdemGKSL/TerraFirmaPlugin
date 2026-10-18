/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/forge/ForgeRule.java @ c3b4d9536 (isConsistent, conflict3,
 * calculateOptimalStepsToTarget, matches)
 */
package dev.tfp.core.forge;

import static dev.tfp.core.forge.ForgeStep.*;

import java.util.List;

/**
 * A forging rule: a step type, optionally constrained to a position among the last three steps. Pure and thread-safe.
 *
 * <p>Positions: {@code ANY} matches any of the last three steps, {@code LAST}, {@code SECOND_LAST} and {@code THIRD_LAST} match one
 * exact position, and {@code NOT_LAST} matches either of the last two. A light hit matches any of the three hit kinds.
 *
 * <p>Rule order and matching are TFC's: a rule set is consistent when some sequence satisfies all of it, and
 * {@link #calculateOptimalStepsToTarget(int, List)} gives the fewest steps needed to reach a target offset while satisfying it.
 */
public enum ForgeRule {
    HIT_ANY(Order.ANY, HIT_LIGHT),
    HIT_NOT_LAST(Order.NOT_LAST, HIT_LIGHT),
    HIT_LAST(Order.LAST, HIT_LIGHT),
    HIT_SECOND_LAST(Order.SECOND_LAST, HIT_LIGHT),
    HIT_THIRD_LAST(Order.THIRD_LAST, HIT_LIGHT),
    DRAW_ANY(Order.ANY, DRAW),
    DRAW_LAST(Order.LAST, DRAW),
    DRAW_NOT_LAST(Order.NOT_LAST, DRAW),
    DRAW_SECOND_LAST(Order.SECOND_LAST, DRAW),
    DRAW_THIRD_LAST(Order.THIRD_LAST, DRAW),
    PUNCH_ANY(Order.ANY, PUNCH),
    PUNCH_LAST(Order.LAST, PUNCH),
    PUNCH_NOT_LAST(Order.NOT_LAST, PUNCH),
    PUNCH_SECOND_LAST(Order.SECOND_LAST, PUNCH),
    PUNCH_THIRD_LAST(Order.THIRD_LAST, PUNCH),
    BEND_ANY(Order.ANY, BEND),
    BEND_LAST(Order.LAST, BEND),
    BEND_NOT_LAST(Order.NOT_LAST, BEND),
    BEND_SECOND_LAST(Order.SECOND_LAST, BEND),
    BEND_THIRD_LAST(Order.THIRD_LAST, BEND),
    UPSET_ANY(Order.ANY, UPSET),
    UPSET_LAST(Order.LAST, UPSET),
    UPSET_NOT_LAST(Order.NOT_LAST, UPSET),
    UPSET_SECOND_LAST(Order.SECOND_LAST, UPSET),
    UPSET_THIRD_LAST(Order.THIRD_LAST, UPSET),
    SHRINK_ANY(Order.ANY, SHRINK),
    SHRINK_LAST(Order.LAST, SHRINK),
    SHRINK_NOT_LAST(Order.NOT_LAST, SHRINK),
    SHRINK_SECOND_LAST(Order.SECOND_LAST, SHRINK),
    SHRINK_THIRD_LAST(Order.THIRD_LAST, SHRINK);

    public static final ForgeRule[] VALUES = values();

    /**
     * @return {@code true} if a set of {@code rules} is self-consistent, meaning there exists at least one possible solution which satisfies all rules.
     */
    public static boolean isConsistent(ForgeRule... rules) {
        return isConsistent(List.of(rules));
    }

    /**
     * @return {@code true} if the rules are self-consistent. Sets of more than three rules, and the empty set, are not.
     */
    public static boolean isConsistent(List<ForgeRule> rules) {
        if (rules.isEmpty() || rules.size() > 3) {
            return false;
        }
        ForgeRule last = null, secondLast = null, thirdLast = null, notLast1 = null, notLast2 = null;
        for (ForgeRule rule : rules) {
            if (rule == last || rule == secondLast || rule == thirdLast || rule == notLast1 || rule == notLast2) {
                continue;
            }
            switch (rule.order) {
                case THIRD_LAST -> {
                    if (thirdLast != null) {
                        return false;
                    }
                    thirdLast = rule;
                }
                case SECOND_LAST -> {
                    if (secondLast != null) {
                        return false;
                    }
                    secondLast = rule;
                }
                case LAST -> {
                    if (last != null) {
                        return false;
                    }
                    last = rule;
                }
                case NOT_LAST -> {
                    if (notLast2 != null) {
                        return false;
                    }
                    notLast2 = notLast1;
                    notLast1 = rule;
                }
                default -> {
                    // ANY rules never pin a position, so they never conflict on their own.
                }
            }
        }
        return conflict3(notLast1, secondLast, thirdLast)
            && conflict3(secondLast, notLast1, notLast2)
            && conflict3(thirdLast, notLast1, notLast2);
    }

    private static boolean conflict3(ForgeRule rule1, ForgeRule rule2, ForgeRule rule3) {
        return rule1 == null || rule2 == null || rule3 == null || rule1.type == rule2.type || rule1.type == rule3.type;
    }

    /**
     * Calculates the minimum number of steps to reach a total offset of {@code target} while satisfying the {@code rules}.
     * Assumes the rules are consistent as determined by {@link #isConsistent(List)}.
     */
    public static int calculateOptimalStepsToTarget(int target, List<ForgeRule> rules) {
        final ForgeRule[] lastSteps = {null, null, null};
        for (final ForgeRule rule : rules) {
            switch (rule.order) {
                case LAST -> lastSteps[0] = rule;
                case SECOND_LAST -> lastSteps[1] = rule;
                case THIRD_LAST -> lastSteps[2] = rule;
                default -> {
                    // Placed below, once the positional rules are fixed.
                }
            }
        }
        for (final ForgeRule rule : rules) {
            if (rule.order == Order.NOT_LAST || rule.order == Order.ANY) {
                boolean placed = false;
                for (int i = 2; i >= 0; i--) {
                    if (lastSteps[i] != null && lastSteps[i].type == rule.type && (rule.order == Order.ANY || i > 0)) {
                        lastSteps[i] = rule;
                        placed = true;
                        break;
                    }
                }
                if (!placed) {
                    for (int i = 2; i >= 0; i--) {
                        if (lastSteps[i] == null) {
                            lastSteps[i] = rule;
                            break;
                        }
                    }
                }
            }
        }
        int requiredSteps = 0, requiredHits = 0;
        for (ForgeRule rule : lastSteps) {
            if (rule != null) {
                requiredSteps++;
                target -= rule.type.step();
                if (rule.type == HIT_LIGHT) {
                    requiredHits++;
                }
            }
        }
        int minimumSteps = ForgeStep.getOptimalStepsToTarget(target);
        for (int hit = 0; hit < requiredHits * 2; hit++) {
            target -= HIT_LIGHT.step();
            minimumSteps = Math.min(minimumSteps, ForgeStep.getOptimalStepsToTarget(target));
        }
        return requiredSteps + minimumSteps;
    }

    private final Order order;
    private final ForgeStep type;

    ForgeRule(Order order, ForgeStep type) {
        this.order = order;
        this.type = type;

        assert type != HIT_MEDIUM && type != HIT_HARD;
    }

    /**
     * @return {@code true} if the step history, given as the last three steps (any may be {@code null} when absent), satisfies this rule.
     */
    public boolean matches(ForgeStep last, ForgeStep secondLast, ForgeStep thirdLast) {
        return switch (order) {
            case ANY -> matches(last) || matches(secondLast) || matches(thirdLast);
            case NOT_LAST -> matches(secondLast) || matches(thirdLast);
            case LAST -> matches(last);
            case SECOND_LAST -> matches(secondLast);
            case THIRD_LAST -> matches(thirdLast);
        };
    }

    private boolean matches(ForgeStep step) {
        if (this.type == HIT_LIGHT) {
            return step == HIT_LIGHT || step == HIT_MEDIUM || step == HIT_HARD;
        }
        return type == step;
    }

    private enum Order {
        ANY,
        LAST,
        NOT_LAST,
        SECOND_LAST,
        THIRD_LAST
    }
}
