/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.landslide;

/**
 * The one random draw of the landslide rules: which open side a block slides into. Passed in so that a decision is a function of its
 * inputs. As in TFC, the draw happens once whenever at least one side is open, even when there is only one.
 */
@FunctionalInterface
public interface LandslideRandom {
    /** A uniform int in [0, bound). */
    int nextInt(int bound);
}
