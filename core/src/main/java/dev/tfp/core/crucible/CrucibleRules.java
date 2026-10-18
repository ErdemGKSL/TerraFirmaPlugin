/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/CrucibleBlockEntity.java @ c3b4d9536 (the melt, fill, pour,
 *       output and molten rules; the item and slot handling stays in the platform glue),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (addTemp),
 *       src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (getFluidHandlerIfAppropriate)
 */
package dev.tfp.core.crucible;

import dev.tfp.core.alloy.FluidAlloy;
import dev.tfp.core.heat.HeatConfig;
import dev.tfp.core.metal.Metal;
import java.util.List;
import java.util.Optional;

/**
 * The pure rules of the crucible, its channels and its mold table. Every method is static and takes facts as arguments
 * (item temperature, the alloy, the slot results), so it runs on any thread. The platform glue decides which items are in
 * which slot, asks the rule, and applies the answer on the owning region.
 *
 * <p>Constants are TFC's defaults: crucible capacity 4000 mB, pouring rate 4 ticks per mB, fast pouring rate 1 tick per mB
 * for 20 units after shift is held, and a 5 tick stability window for the target temperature.
 */
public final class CrucibleRules {
    /** TFC {@code crucibleCapacity} default, in mB. */
    public static final int DEFAULT_CAPACITY = 4000;
    /** TFC {@code cruciblePouringRate} default: a mold pours 1 mB every this many ticks. */
    public static final int POURING_RATE = 4;
    /** TFC {@code crucibleFastPouringRate} default: the pour rate while shift is held. */
    public static final int FAST_POURING_RATE = 1;
    /** TFC {@code setFastPouring}: the number of units poured at the fast rate. */
    public static final int FAST_POUR_TICKS = 20;
    /** The melt temperature of TFC's {@code tfc:metal/unknown} fluid heat entry. */
    public static final float UNKNOWN_MELT_TEMPERATURE = 400f;
    /** The number of input slots (TFC {@code SLOT_INPUT_START..SLOT_INPUT_END}). */
    public static final int INPUT_SLOTS = 9;

    private CrucibleRules() {
    }

    /**
     * The melt temperature of a fluid, or empty when the fluid has no fluid heat entry. Such a fluid is never accepted into a
     * crucible, and an alloy of it is always molten (TFC {@code isMolten} with no {@code FluidHeat}).
     *
     * @param fluid the fluid id, such as {@code tfc:metal/copper}
     */
    public static Optional<Float> meltTemperature(String fluid) {
        if (Metal.UNKNOWN_ID.equals(fluid)) {
            return Optional.of(UNKNOWN_MELT_TEMPERATURE);
        }
        return Metal.fromId(fluid).map(Metal::meltTemperature);
    }

    /**
     * TFC {@code CrucibleInventory.isMolten}: the alloy's result is molten when the crucible is strictly above its melt
     * temperature. A result with no fluid heat entry, including the empty alloy, is always molten.
     *
     * @param alloyResult the fluid id of the alloy's result, empty for an empty alloy
     * @param temperature the crucible's temperature
     */
    public static boolean isMolten(String alloyResult, float temperature) {
        Optional<Float> melt = meltTemperature(alloyResult);
        return melt.isEmpty() || temperature > melt.get();
    }

    /**
     * TFC {@code CrucibleInventory.fill} with {@code FluidContainerInfo}: a fluid is accepted only when it has a melt
     * temperature, and then up to the capacity. The rejected case returns the alloy unchanged.
     *
     * @param alloy    the alloy in the crucible
     * @param fluid    the fluid offered
     * @param amount   the amount offered in mB
     * @param capacity the crucible capacity in mB
     */
    public static FluidAlloy.Fill fill(FluidAlloy alloy, String fluid, int amount, int capacity) {
        if (meltTemperature(fluid).isEmpty()) {
            return new FluidAlloy.Fill(alloy, 0);
        }
        return alloy.fill(fluid, amount, capacity);
    }

    /**
     * TFC {@code addTemp(instance, target, modifier)} for an item in a crucible: one heating step, with no cooling inside
     * the step. The item's heat rises by {@code (coolingModifier - 1 + modifier x heatingModifier) / capacity}, capped at
     * the target. A step that does not raise the temperature leaves it as it is.
     *
     * @param temperature   the item's temperature
     * @param heatCapacity  the item's heat capacity (from the item's heat data, or the custom capacity)
     * @param target        the target temperature, the crucible's temperature
     * @param modifier      the heating modifier, see {@link #itemHeatModifier(float)}
     * @param config        the cooling and heating modifiers
     */
    public static float heatItemStep(float temperature, float heatCapacity, float target, float modifier, HeatConfig config) {
        float step = (float) config.coolingModifier() - 1 + modifier * (float) config.heatingModifier();
        float next = temperature + step / heatCapacity;
        if (next > target) {
            next = target;
        }
        return next > temperature ? next : temperature;
    }

    /**
     * TFC's per-tick item heating modifier in the crucible: {@code 2 + crucibleTemperature x 0.0025}. It breaks even with
     * cooling at 400 degrees, so items above that heat up, and hotter crucibles heat items faster.
     *
     * @param crucibleTemperature the crucible's temperature
     */
    public static float itemHeatModifier(float crucibleTemperature) {
        return 2f + crucibleTemperature * 0.0025f;
    }

    /**
     * The outcome for one input slot in one tick: whether the item melts, and what it makes.
     *
     * @param converts whether the item is converted this tick
     * @param fluid    the fluid made, empty when none
     * @param amount   the fluid amount made in mB
     */
    public record Melt(boolean converts, String fluid, int amount) {
    }

    /**
     * TFC crucible server tick, the melt step of one input: a null recipe, or an item below the recipe temperature, does
     * nothing. Otherwise the item converts and the recipe's fluid, with durability scaling, is made. The caller then fills
     * the alloy with it through {@link #fill(FluidAlloy, String, int, int)}.
     *
     * @param recipe       the recipe the item matches, or {@code null}
     * @param itemTemperature the item's temperature
     * @param damage       the item's damage
     * @param maxDamage    the item's maximum damage, or 0 when it cannot wear
     */
    public static Melt melt(CrucibleRecipe recipe, float itemTemperature, int damage, int maxDamage) {
        if (recipe == null || !recipe.isValidTemperature(itemTemperature)) {
            return new Melt(false, "", 0);
        }
        return new Melt(true, recipe.resultFluid(), recipe.fluidAmount(damage, maxDamage));
    }

    /**
     * The pouring state of a crucible: ticks until the next mold may pour, the fast pour counter, and the slot it applies to.
     *
     * @param lastFillTicks the ticks until the next pour is allowed; a pour is allowed at 0 or below
     * @param fastPourTicks the units left at the fast rate, which the shift key sets
     * @param fastPourSlot  the input slot the fast pour applies to
     */
    public record Pour(int lastFillTicks, int fastPourTicks, int fastPourSlot) {
        /** A crucible that is not pouring. */
        public static final Pour IDLE = new Pour(0, 0, 0);

        /** TFC {@code setFastPouring}: the next {@link #FAST_POUR_TICKS} units from {@code slot} pour fast. */
        public Pour setFastPouring(int slot) {
            return new Pour(lastFillTicks, FAST_POUR_TICKS, slot);
        }
    }

    /**
     * TFC crucible server tick, pouring. Whether a pour is allowed is read once at the start of the tick. In each input slot
     * that transferred a unit from its mold, the pour sets the wait to the fast rate when the fast counter is still at or
     * above 0 and the slot is the fast slot (the counter goes down), otherwise to the normal rate. The wait then counts down
     * by one.
     *
     * @param state          the pouring state before the tick
     * @param transferredSlots the input slots, in increasing order, whose mold gave one unit this tick. Only read when a pour
     *                       was allowed at the start of the tick.
     * @param pouringRate   the normal pour rate, in ticks per mB
     * @param fastPouringRate the fast pour rate, in ticks per mB
     */
    public static Pour pourTick(Pour state, List<Integer> transferredSlots, int pouringRate, int fastPouringRate) {
        boolean canFill = state.lastFillTicks() <= 0;
        int lastFill = state.lastFillTicks();
        int fastTicks = state.fastPourTicks();
        if (canFill) {
            for (int slot : transferredSlots) {
                if (fastTicks >= 0 && state.fastPourSlot() == slot) {
                    fastTicks--;
                    lastFill = fastPouringRate;
                } else {
                    lastFill = pouringRate;
                }
            }
        }
        if (lastFill > 0) {
            lastFill--;
        }
        return new Pour(lastFill, fastTicks, state.fastPourSlot());
    }

    /**
     * TFC crucible server tick, output: when the crucible is molten, one mB of the alloy's result moves to the output slot,
     * but only if the slot accepted it. The caller reports whether the output slot took the unit.
     *
     * @param alloy          the alloy in the crucible
     * @param alloyResult    the fluid id of the alloy's result
     * @param temperature    the crucible's temperature
     * @param outputAccepted whether the output slot accepted one mB of {@code alloyResult}
     */
    public static FluidAlloy outputTick(FluidAlloy alloy, String alloyResult, float temperature, boolean outputAccepted) {
        if (alloy.isEmpty() || !isMolten(alloyResult, temperature) || !outputAccepted) {
            return alloy;
        }
        return alloy.drain(1).alloy();
    }

    /**
     * TFC {@code MoldTableBlockEntity.getFluidHandlerIfAppropriate}: a channel or mold may draw from a crucible when the
     * alloy is not empty, the result is the fluid expected by the flow (when one is given), and the crucible is molten.
     *
     * @param alloy        the crucible's alloy
     * @param alloyResult  the fluid id of the alloy's result
     * @param temperature  the crucible's temperature
     * @param expectedFluid the flow's fluid, or empty when the flow has none yet
     */
    public static boolean canDraw(FluidAlloy alloy, String alloyResult, float temperature, Optional<String> expectedFluid) {
        if (alloy.isEmpty()) {
            return false;
        }
        if (expectedFluid.isPresent() && !expectedFluid.get().equals(alloyResult)) {
            return false;
        }
        return isMolten(alloyResult, temperature);
    }
}
