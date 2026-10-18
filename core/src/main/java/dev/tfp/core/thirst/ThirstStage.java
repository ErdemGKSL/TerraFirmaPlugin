/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/player/PlayerInfo.java @ c3b4d9536 (lack-of-thirst branches)
 */
package dev.tfp.core.thirst;

/**
 * The effect a thirst level gives, under TFC's {@code PlayerInfo.tick} (checked every 100 ticks, not while peaceful):
 * below {@code slowedBelow} movement and digging slow at amplifier 0; below {@code severeBelow} they slow at
 * amplifier 1; at 0 or less the player is also hurt by dehydration.
 */
public enum ThirstStage {
    NONE(-1, false),
    SLOWED(0, false),
    SEVERE(1, false),
    DEHYDRATED(1, true);

    private final int slowdownAmplifier;
    private final boolean damages;

    ThirstStage(int slowdownAmplifier, boolean damages) {
        this.slowdownAmplifier = slowdownAmplifier;
        this.damages = damages;
    }

    /** The amplifier of movement and digging slowdown, or -1 for none. */
    public int slowdownAmplifier() {
        return slowdownAmplifier;
    }

    /** Whether the player takes dehydration damage (1 per check). */
    public boolean damages() {
        return damages;
    }

    /** The stage for a thirst level under {@code config}. */
    public static ThirstStage of(float thirst, ThirstConfig config) {
        if (thirst <= 0f) {
            return DEHYDRATED;
        }
        if (thirst < config.severeBelow()) {
            return SEVERE;
        }
        if (thirst < config.slowedBelow()) {
            return SLOWED;
        }
        return NONE;
    }
}
