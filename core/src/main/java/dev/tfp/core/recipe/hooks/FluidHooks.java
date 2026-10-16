/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.hooks;

import dev.tfp.core.recipe.fluid.FluidAmount;
import java.util.Optional;

/** Fluid-container queries used by {@code tfc:fluid_content}. Implemented by the fluid-container system. */
public interface FluidHooks<S> {
    /** TFC {@code FluidHelpers.getContainedFluid}: the fluid in an item container, empty if none. */
    Optional<FluidAmount> containedFluid(S stack);
}
