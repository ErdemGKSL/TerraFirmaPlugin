/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/fluids/FluidHelpers.java @ c3b4d9536 (transferBetweenWorldAndItem,
 *       pickupFluidInto, pickupFluid, emptyFluidFrom, BUCKET_VOLUME, with/updateContainerItem),
 *       src/main/java/net/dries007/tfc/common/items/FluidContainerItem.java @ c3b4d9536 (use)
 */
package dev.tfp.core.bucket;

import dev.tfp.core.fluid.FluidContainerRules;
import dev.tfp.core.vessel.FluidAmount;
import java.util.Objects;

/**
 * The world side of a bucket used on a block, as TFC's {@code FluidHelpers} decides it. Pure: the caller reads the block
 * facts, these rules pick the change, the caller writes it.
 *
 * <p>An empty bucket picks up ({@link #pickup}): a fluid source or a waterlogged block offers {@value #BUCKET_VOLUME} mB,
 * the tank takes what fits and, when it takes anything, the whole source is removed (TFC's "aggressive" pickup). A bucket
 * that holds fluid empties all of it into the world ({@link #place}), following {@code emptyFluidFrom} branch for branch.
 */
public final class BucketRules {
    /** TFC {@code FluidHelpers.BUCKET_VOLUME}. */
    public static final int BUCKET_VOLUME = 1000;

    private BucketRules() {
    }

    /** The result of a pickup. */
    public record Pickup(FluidAmount content, int filled) {
        /** Whether the world block must lose its fluid. */
        public boolean removesSource() {
            return filled > 0;
        }
    }

    /**
     * A pickup into an empty tank.
     *
     * @param offered    the fluid of the source ({@link WorldFluid#pickedUp}), {@value #BUCKET_VOLUME} mB of it
     * @param capacity   the tank capacity
     * @param canContain whether the tank may hold that fluid (its {@code tfc:usable_in_*} tag)
     */
    public static Pickup pickup(String offered, int capacity, boolean canContain) {
        FluidContainerRules.Fill fill = FluidContainerRules.fill(FluidAmount.EMPTY,
                new FluidAmount(offered, BUCKET_VOLUME), capacity, canContain);
        return new Pickup(fill.content(), fill.filled());
    }

    /** What emptying the tank does to the target block. */
    public enum Outcome {
        /** The target cannot take fluid: try the block on the clicked face instead (only on the first attempt). */
        TRY_ADJACENT,
        /** Nothing happens and the tank keeps its fluid. */
        REFUSE,
        /** Water in an ultrawarm dimension: the tank empties with the extinguish sound and smoke, no block changes. */
        VAPORIZE,
        /** A waterloggable block becomes waterlogged. */
        WATERLOG,
        /** A source block of the fluid is set. */
        PLACE_SOURCE,
        /** A flowing block of level 1 ({@code getFlowing(1, false)}) is set: it does not spread far and dries up. */
        PLACE_FLOWING,
        /** The target already holds this fluid: the tank empties into it, the block is unchanged. */
        ABSORB
    }

    /**
     * The facts of the target block.
     *
     * @param air            the block is air
     * @param replaceable    TFC {@code state.canBeReplaced(fluid)}: a replaceable or non-solid block
     * @param liquid         the block is a fluid block ({@code state.liquid()})
     * @param present        the world fluid of the block, {@code null} for none
     * @param presentSource  the present fluid is a source
     * @param canPlaceLiquid a waterloggable block that is not waterlogged, for water ({@code LiquidBlockContainer})
     */
    public record Target(boolean air, boolean replaceable, boolean liquid, WorldFluid present, boolean presentSource,
            boolean canPlaceLiquid) {
        public static final Target AIR = new Target(true, true, false, null, false, false);
    }

    /**
     * The decision.
     *
     * @param outcome    what happens
     * @param breakFirst the target is a replaceable non-fluid block (grass, snow layer) that breaks with drops first
     *                   ({@code level.destroyBlock(pos, true)})
     */
    public record Placement(Outcome outcome, boolean breakFirst) {
        /** Whether the tank empties. */
        public boolean drains() {
            return switch (outcome) {
                case VAPORIZE, WATERLOG, PLACE_SOURCE, PLACE_FLOWING, ABSORB -> true;
                case TRY_ADJACENT, REFUSE -> false;
            };
        }
    }

    /**
     * TFC {@code emptyFluidFrom}: emptying {@code amount} mB of {@code fluid} into {@code target}.
     *
     * @param firstAttempt the target is the clicked block (TFC: {@code hit != null}); a refusal there tries the adjacent
     *                     block once
     * @param ultrawarm    the dimension vaporizes water ({@code isVaporizedOnPlacement})
     */
    public static Placement place(WorldFluid fluid, int amount, boolean allowPlacingSourceBlocks, Target target,
            boolean ultrawarm, boolean firstAttempt) {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(target, "target");
        boolean canPlaceLiquid = target.canPlaceLiquid() && fluid == WorldFluid.WATER;
        boolean willReplace = target.air() || target.replaceable() || (canPlaceLiquid && allowPlacingSourceBlocks);
        if (!willReplace) {
            return new Placement(firstAttempt ? Outcome.TRY_ADJACENT : Outcome.REFUSE, false);
        }
        if (fluid == WorldFluid.WATER && ultrawarm) {
            return new Placement(Outcome.VAPORIZE, false);
        }
        if (canPlaceLiquid && amount >= BUCKET_VOLUME) {
            // TFC: a waterloggable target without source placement denies the whole behaviour
            return new Placement(allowPlacingSourceBlocks ? Outcome.WATERLOG : Outcome.REFUSE, false);
        }
        Outcome outcome;
        if (allowPlacingSourceBlocks && amount >= BUCKET_VOLUME) {
            // toPlace = the source; no change when the target already is that source
            outcome = target.present() == fluid && target.presentSource() ? Outcome.ABSORB : Outcome.PLACE_SOURCE;
        } else if (target.present() == fluid) {
            outcome = Outcome.ABSORB; // "don't replace an existing fluid block with a non-source block"
        } else {
            outcome = Outcome.PLACE_FLOWING;
        }
        boolean breakFirst = outcome != Outcome.ABSORB && target.replaceable() && !target.liquid() && !target.air();
        return new Placement(outcome, breakFirst);
    }

    /** How the held stack changes after a transfer (TFC {@code FluidHelpers.with} + {@code updateContainerItem}). */
    public enum HandUpdate {
        /** One item: the held stack becomes the changed container. */
        REPLACE,
        /** Creative with one item: the held stack is left as it was (the world still changes). */
        KEEP,
        /** A stack of more: one item is taken off (not in creative) and the changed container is given to the player. */
        SPLIT
    }

    /** The hand update for a stack of {@code count} items. */
    public static HandUpdate handUpdate(int count, boolean creative) {
        if (count > 1) {
            return HandUpdate.SPLIT;
        }
        return creative ? HandUpdate.KEEP : HandUpdate.REPLACE;
    }
}
