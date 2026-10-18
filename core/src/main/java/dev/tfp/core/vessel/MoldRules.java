/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/component/mold/Mold.java @ c3b4d9536 (fill, isMolten, updateFluid),
 *       src/main/java/net/dries007/tfc/common/component/fluid/FluidComponent.java @ c3b4d9536 (fill),
 *       src/main/java/net/dries007/tfc/common/component/heat/HeatCapability.java @ c3b4d9536 (POTTERY_HEAT_CAPACITY),
 *       src/main/java/net/dries007/tfc/common/items/MoldItem.java @ c3b4d9536 (the break chance draw),
 *       src/main/java/net/dries007/tfc/common/recipes/HeatingRecipe.java @ c3b4d9536 (isValidTemperature),
 *       src/generated/resources/data/tfc/recipe/heating/ceramic/*_mold.json @ c3b4d9536 (1399 for every mold)
 */
package dev.tfp.core.vessel;

/**
 * The pure rules of a mold: how much fluid fits, the solid and molten test, the heat capacity of the mold, the casting
 * break chance, and the firing temperature of the unfired clay mold. Nothing here draws random numbers or reads time.
 */
public final class MoldRules {
    /** {@code HeatCapability.POTTERY_HEAT_CAPACITY}: the heat capacity of an empty pottery item. */
    public static final float POTTERY_HEAT_CAPACITY = 1.2f;
    /** The heating recipe temperature that turns an unfired clay mold into a fired one (1399 for every mold in TFC). */
    public static final float FIRING_TEMPERATURE = 1399f;

    private MoldRules() {
    }

    /**
     * The outcome of a fill: the new content and the amount that was accepted.
     *
     * @param content the content after the fill
     * @param filled  the amount taken from the input, {@code 0..input}; the rest of the input is the overflow
     */
    public record FillResult(FluidAmount content, int filled) {
    }

    /**
     * {@code FluidComponent.fill}. A fluid the mold cannot contain fills nothing. Into an empty mold the input fills up to
     * the capacity. A different fluid fills nothing. A same-fluid fill adds up to the capacity. The overflow is the input
     * minus {@code filled}.
     *
     * @param content    the current content
     * @param input      the fluid offered
     * @param capacity   the capacity in mB
     * @param canContain whether the mold accepts this fluid at all (TFC's {@code canContainFluid})
     */
    public static FillResult fill(FluidAmount content, FluidAmount input, int capacity, boolean canContain) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must be >= 0: " + capacity);
        }
        if (input.isEmpty() || !canContain) {
            return new FillResult(content, 0);
        }
        if (content.isEmpty()) {
            int amount = Math.min(input.amount(), capacity);
            return new FillResult(new FluidAmount(input.fluid(), amount), amount);
        }
        if (!content.fluid().equals(input.fluid())) {
            return new FillResult(content, 0);
        }
        int amount = Math.min(content.amount() + input.amount(), capacity);
        return new FillResult(new FluidAmount(content.fluid(), amount), amount - content.amount());
    }

    /** {@code Mold.isMolten}: at or above the melt temperature. (The vessel uses a strict comparison, see VesselRules.) */
    public static boolean isMolten(float temperature, float meltTemperature) {
        return temperature >= meltTemperature;
    }

    /** {@code Mold.updateFluid}: the pottery heat capacity plus the fluid's specific heat times its amount, in float. */
    public static float heatCapacity(float specificHeat, int amount) {
        return POTTERY_HEAT_CAPACITY + specificHeat * (float) amount;
    }

    /** {@code MoldItem.overrideOtherStackedOnMe}: the mold breaks when the draw is below the break chance. */
    public static boolean breaks(float draw, float breakChance) {
        return draw < breakChance;
    }

    /** {@code HeatingRecipe.isValidTemperature}: an unfired mold fires once it is at or above the firing temperature. */
    public static boolean firesAt(float temperature) {
        return temperature >= FIRING_TEMPERATURE;
    }
}
