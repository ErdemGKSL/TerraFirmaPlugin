/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.recipe.fluid;

/** A fluid id and an amount in mB (a fluid in a container, as seen by ingredient tests). */
public record FluidAmount(String fluid, int amount) {
}
