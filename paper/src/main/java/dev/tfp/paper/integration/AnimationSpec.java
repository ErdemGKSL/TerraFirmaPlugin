/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.integration;

/**
 * How an animation is layered (bettermodel skill, "Animation rules"): per bone the highest
 * {@code priority} wins; {@code override} limits the animation to its own keyframed bones so the rest
 * keep playing the lower animation; {@code once} plays it a single time instead of looping.
 */
public record AnimationSpec(int priority, boolean override, boolean once) {
    public static final AnimationSpec LOOP = new AnimationSpec(0, false, false);

    public static AnimationSpec once(int priority) {
        return new AnimationSpec(priority, true, true);
    }
}
