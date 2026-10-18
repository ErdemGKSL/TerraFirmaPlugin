/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

/**
 * The random draws of the collapse rules, passed in so that a decision is a function of its inputs. The caller supplies
 * a generator seeded per decision; the order of draws is fixed by {@link CollapseRules#trigger}.
 */
public interface CollapseRandom {
    /** A uniform float in [0, 1). */
    float nextFloat();

    /** A uniform int in [0, bound). */
    int nextInt(int bound);
}
