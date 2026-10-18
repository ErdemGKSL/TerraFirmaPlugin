/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeSaplingBlock.java @ c3b4d9536 (randomTick, createTree,
 *       getTicksToGrow, canSurvive's branch case, maySplice's count rule),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/GrowingFruitTreeBranchBlock.java @ c3b4d9536 (randomTick reset gate,
 *       isRandomlyTicking, tick's cycle count),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitTreeLeavesBlock.java @ c3b4d9536 (onUpdate with persistent,
 *       isValid),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (fruitBranchGrowthTicks default)
 */
package dev.tfp.core.berry;

import dev.tfp.core.calendar.Month;

/**
 * The fruit tree's stage and fruit rules, as decisions over the facts the world supplies.
 *
 * <ul>
 *   <li>The sapling grows into a branch once its counter passes the tree's growth time, in the growing month, and only when the climate
 *       holds at the stem. It carries the ticks over the growth time into the branch.</li>
 *   <li>A growing branch counts growth cycles from its counter and resets the counter when the climate fails (unless it is a natural
 *       tree). Its geometry (where the next branch and body block go) is the world's and is not here.</li>
 *   <li>Leaves follow the same lifecycle as a berry bush, except persistent leaves (placed by a player) never change stage. Leaves
 *       without a branch next to them are removed. Picking fruit is {@link BerryRules#pick}, the same as a berry bush.</li>
 * </ul>
 *
 * <p>The random draws are inputs: {@code roll} is the boolean that TFC reads when the sapling's internal count is one.
 */
public final class FruitTreeRules {
    /** TFC default {@code fruitBranchGrowthTicks}: 5 default days. */
    public static final long FRUIT_BRANCH_GROWTH_TICKS = 5L * BerryRules.PLAYER_TICKS_IN_DEFAULT_DAY;
    /** A growing branch is randomly ticked while its stage is below this (TFC {@code isRandomlyTicking}). */
    public static final int BRANCH_TICKING_STAGES = 3;

    private FruitTreeRules() {
    }

    /** TFC {@code FruitTreeSaplingBlock.getTicksToGrow}: the tree's growth time scaled by the sapling modifier. */
    public static long ticksToGrow(FruitTree tree, double saplingGrowthModifier) {
        return (long) (tree.defaultTicksToGrow() * saplingGrowthModifier);
    }

    /** The outcome of a sapling's random tick. */
    public sealed interface SaplingTick permits Waiting, Withered, Grown {
    }

    /** Nothing happens: the month is dormant, or the sapling has not grown long enough. */
    public record Waiting() implements SaplingTick {
    }

    /** The climate fails at the stem, so the sapling turns into a dead bush. */
    public record Withered() implements SaplingTick {
    }

    /**
     * The sapling grows into a growing branch.
     *
     * @param saplings     the branch's sapling count (3 when placed on a branch, else the sapling's own count, with a 1 bumped to 2 on the roll)
     * @param stage        the branch's stage (1 when placed on a branch, else 0)
     * @param carriedTicks the ticks past the growth time, carried into the branch's counter
     */
    public record Grown(int saplings, int stage, long carriedTicks) implements SaplingTick {
    }

    /**
     * TFC {@code FruitTreeSaplingBlock.randomTick} with {@code createTree}: the sapling waits in a dormant month and until its counter passes
     * the growth time. Then the climate at the stem decides between a dead bush and the tree.
     *
     * @param ticksSinceUpdate the sapling's counter ({@code getTicksSinceUpdate})
     * @param ticksToGrow      {@link #ticksToGrow}
     * @param climateOk       whether the climate holds at the stem
     * @param saplings        the sapling's stored count
     * @param onBranch        whether the block below is a fruit tree branch
     * @param roll            the boolean TFC reads when the internal count is one
     */
    public static SaplingTick saplingTick(FruitTree tree, Month hemisphericMonth, long ticksSinceUpdate, long ticksToGrow,
                                          boolean climateOk, int saplings, boolean onBranch, boolean roll) {
        if (!tree.lifecycleFor(hemisphericMonth).active() || ticksSinceUpdate <= ticksToGrow) {
            return new Waiting();
        }
        if (!climateOk) {
            return new Withered();
        }
        int internal = onBranch ? 3 : saplings;
        if (internal == 1 && roll) {
            internal += 1;
        }
        return new Grown(internal, onBranch ? 1 : 0, ticksSinceUpdate - ticksToGrow);
    }

    /**
     * TFC {@code GrowingFruitTreeBranchBlock.tick}: the growth cycles the counter owes, {@code ticksSinceUpdate / fruitBranchGrowthTicks}.
     * The branch grows when at least one cycle is owed.
     */
    public static long branchCycles(long ticksSinceUpdate, long fruitBranchGrowthTicks) {
        return ticksSinceUpdate / fruitBranchGrowthTicks;
    }

    /**
     * TFC {@code GrowingFruitTreeBranchBlock.randomTick}: a branch whose climate fails (and that is not natural) resets its counter and does
     * not grow. Returns {@code true} for the reset.
     */
    public static boolean branchResets(boolean climateOk, boolean natural) {
        return !climateOk && !natural;
    }

    /** Whether a growing branch at {@code stage} takes random ticks (TFC {@code isRandomlyTicking}). */
    public static boolean branchRandomTicks(int stage) {
        return stage < BRANCH_TICKING_STAGES;
    }

    /**
     * TFC {@code FruitTreeLeavesBlock.onUpdate}: persistent leaves never change stage. Other leaves follow their month like a berry bush,
     * and their random tick runs the climate check of {@code SeasonalPlantBlock.onUpdate} itself, so {@code climateOk} is read here.
     */
    public static Lifecycle leavesLifecycle(boolean persistent, boolean climateOk, Lifecycle stored, Lifecycle expected, long now,
                                            long lastPickedTick, long pickBloomDelayTicks) {
        if (persistent) {
            return stored;
        }
        return BerryRules.advanceLifecycle(stored, expected, climateOk, now, lastPickedTick, pickBloomDelayTicks);
    }

    /**
     * TFC {@code FruitTreeLeavesBlock.isValid}: leaves stay when they are persistent or touch a branch. Otherwise they are destroyed.
     *
     * @param branchAdjacent whether one of the six neighbours is a fruit tree branch
     */
    public static boolean leavesValid(boolean persistent, boolean branchAdjacent) {
        return persistent || branchAdjacent;
    }
}
