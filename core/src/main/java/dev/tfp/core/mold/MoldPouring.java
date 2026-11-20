/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/MoldLikeAlloyContainer.java @ c3b4d9536 (broadcastChanges),
 *       src/main/java/net/dries007/tfc/common/component/heat/IHeat.java @ c3b4d9536 (addTemperatureFromSourceWithHeatCapacity)
 */
package dev.tfp.core.mold;

import dev.tfp.core.vessel.FluidAmount;
import dev.tfp.core.vessel.MoldRules;

/**
 * PAR-POT-05: the pour from a molten vessel (or molten mold) into a mold, as TFC's {@code MoldLikeAlloyContainer} does it:
 * one mB per step, the mold's temperature mixed with the poured mB by heat capacity. Pure, immutable.
 */
public final class MoldPouring {
    /** TFC moves one mB per server tick while the screen is open. */
    public static final int STEP = 1;

    private MoldPouring() {
    }

    /**
     * One step of a pour.
     *
     * @param moved       the mB taken from the source, 0 or {@link #STEP}
     * @param content     the mold's content after the step
     * @param temperature the mold's temperature after the step
     */
    public record Step(int moved, FluidAmount content, float temperature) {
    }

    /**
     * {@code IHeat.addTemperatureFromSourceWithHeatCapacity}: the temperature of two bodies mixed by heat capacity.
     */
    public static float mix(float currentTemperature, float currentHeatCapacity, float sourceTemperature, float sourceHeatCapacity) {
        float total = currentHeatCapacity + sourceHeatCapacity;
        return currentTemperature * currentHeatCapacity / total + sourceTemperature * sourceHeatCapacity / total;
    }

    /**
     * One step: the fluid of the source goes into the mold when the mold takes it (nothing moves for a different fluid, a
     * full mold or a fluid the mold cannot contain); the mold's heat capacity then includes the new mB, and the mold mixes
     * with the mB at the source's temperature.
     *
     * @param sourceFluid       the fluid in the source (a molten vessel or mold)
     * @param sourceTemperature the source's temperature
     * @param moldContent       the mold's content
     * @param moldTemperature   the mold's temperature
     * @param capacity          the mold's capacity in mB
     * @param canContain        whether the mold accepts the fluid
     * @param specificHeat      the specific heat per mB of the poured fluid
     */
    public static Step step(FluidAmount sourceFluid, float sourceTemperature, FluidAmount moldContent, float moldTemperature,
                            int capacity, boolean canContain, float specificHeat) {
        if (sourceFluid.isEmpty()) {
            return new Step(0, moldContent, moldTemperature);
        }
        MoldRules.FillResult result = MoldRules.fill(moldContent, new FluidAmount(sourceFluid.fluid(), STEP), capacity, canContain);
        if (result.filled() != STEP) {
            return new Step(0, moldContent, moldTemperature);
        }
        float capacityAfter = MoldRules.heatCapacity(specificHeat, result.content().amount());
        float mixed = mix(moldTemperature, capacityAfter, sourceTemperature, specificHeat * STEP);
        return new Step(STEP, result.content(), mixed);
    }
}
