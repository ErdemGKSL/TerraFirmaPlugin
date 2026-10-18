/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BarrelBlockEntity.java @ c3b4d9536 (onSeal, onUnseal, sealedTick and
 * recipeTick, getRemainingTicks), src/main/java/net/dries007/tfc/common/component/block/BarrelComponent.java @ c3b4d9536
 * (the sealed tick and recipe tick defaults)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.vessel.FluidAmount;
import java.util.List;
import java.util.Objects;

/**
 * A barrel's contents and seal, recorded at the calendar tick of the last change. Immutable, and nothing ticks: the recipe
 * progress is evaluated lazily by {@link BarrelProgress}.
 *
 * <p>Not modelled here: the fluid container slots (dropped on seal in TFC), the rain fill and the pouring, the on-seal and
 * on-unseal item modifiers (the Paper side applies them to the main stack).
 *
 * @param fluid      the tank
 * @param main       the main item slot
 * @param excess     the stack that did not fit the main slot (TFC's excess list, one stack at most in this model)
 * @param sealed     whether the barrel is sealed
 * @param sealedTick the tick the barrel was sealed, or {@code 0} when unsealed
 * @param recipeTick the tick the current recipe started, or {@link BarrelRules#NO_TICK}
 * @param asOf       the calendar tick this state is valid at; progress is settled from here
 */
public record BarrelState(FluidAmount fluid, BarrelStack main, BarrelStack excess, boolean sealed, long sealedTick,
                          long recipeTick, long asOf) {

    public BarrelState {
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(main, "main");
        Objects.requireNonNull(excess, "excess");
    }

    /** An unsealed, empty barrel. */
    public static BarrelState empty() {
        return new BarrelState(FluidAmount.EMPTY, BarrelStack.EMPTY, BarrelStack.EMPTY, false, 0L, BarrelRules.NO_TICK, 0L);
    }

    /** An unsealed barrel holding the given contents. Contents can only change while unsealed. */
    public static BarrelState unsealed(FluidAmount fluid, BarrelStack main) {
        return new BarrelState(fluid, main, BarrelStack.EMPTY, false, 0L, BarrelRules.NO_TICK, 0L);
    }

    /**
     * The result of a fluid fill.
     *
     * @param state    the barrel after the fill
     * @param accepted the amount taken, mB
     */
    public record FillOutcome(BarrelState state, int accepted) {
    }

    /** {@code BarrelInventory.fill}: takes what {@link BarrelRules#fillAccepted} allows. */
    public FillOutcome fill(FluidAmount offered, boolean usable, int capacity) {
        final int accepted = BarrelRules.fillAccepted(fluid, sealed, usable, offered, capacity);
        if (accepted == 0) {
            return new FillOutcome(this, 0);
        }
        final FluidAmount next = fluid.isEmpty()
            ? new FluidAmount(offered.fluid(), accepted)
            : new FluidAmount(fluid.fluid(), fluid.amount() + accepted);
        return new FillOutcome(with(next, main, excess), accepted);
    }

    /**
     * The result of a fluid drain.
     *
     * @param state   the barrel after the drain
     * @param drained the amount given out, mB
     */
    public record DrainOutcome(BarrelState state, int drained) {
    }

    /** {@code BarrelInventory.drain}: gives out what {@link BarrelRules#drainAccepted} allows. */
    public DrainOutcome drain(int maxDrain) {
        final int drained = BarrelRules.drainAccepted(fluid, sealed, maxDrain);
        if (drained == 0) {
            return new DrainOutcome(this, 0);
        }
        final FluidAmount next = drained == fluid.amount()
            ? FluidAmount.EMPTY
            : new FluidAmount(fluid.fluid(), fluid.amount() - drained);
        return new DrainOutcome(with(next, main, excess), drained);
    }

    /**
     * {@code BarrelBlockEntity.onSeal}: records the seal tick, and starts the recipe at that tick when a recipe matches.
     * The recipe tick is kept when no recipe matches.
     *
     * @throws IllegalStateException if already sealed
     */
    public BarrelState seal(long now, List<BarrelRecipe> recipes) {
        if (sealed) {
            throw new IllegalStateException("the barrel is already sealed");
        }
        final long tick = BarrelRecipe.select(recipes, this) != null ? now : recipeTick;
        return new BarrelState(fluid, main, excess, true, now, tick, now);
    }

    /**
     * {@code BarrelBlockEntity.onUnseal}: clears the seal tick and the recipe tick. Progress is lost, as in TFC.
     *
     * @param now the calendar tick of the unseal
     * @throws IllegalStateException if not sealed, or {@code now} is before {@link #asOf()}
     */
    public BarrelState unseal(long now) {
        if (!sealed) {
            throw new IllegalStateException("the barrel is not sealed");
        }
        if (now < asOf) {
            throw new IllegalStateException("unseal at " + now + " is before the recorded tick " + asOf);
        }
        return new BarrelState(fluid, main, excess, false, 0L, BarrelRules.NO_TICK, now);
    }

    /**
     * {@code BarrelBlockEntity.getRemainingTicks}: the recipe duration less the time since its tick, or {@code 0} with no
     * recipe. Only meaningful while sealed.
     */
    public long remainingTicks(long now, List<BarrelRecipe> recipes) {
        final BarrelRecipe recipe = BarrelRecipe.select(recipes, this);
        return recipe == null ? 0 : recipe.duration() - (now - recipeTick);
    }

    /** A copy with the recipe tick replaced. Used by the progress rules. */
    BarrelState withRecipeTick(long tick) {
        return new BarrelState(fluid, main, excess, sealed, sealedTick, tick, asOf);
    }

    /** A copy valid at {@code now}. Used by the progress rules. */
    BarrelState at(long now) {
        return new BarrelState(fluid, main, excess, sealed, sealedTick, recipeTick, now);
    }

    /** A copy with the given contents, keeping the seal and the ticks. Used by the progress rules. */
    BarrelState with(FluidAmount nextFluid, BarrelStack nextMain, BarrelStack nextExcess) {
        return new BarrelState(nextFluid, nextMain, nextExcess, sealed, sealedTick, recipeTick, asOf);
    }
}
