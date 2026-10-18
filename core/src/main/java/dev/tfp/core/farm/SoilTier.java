/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (getSoilModifier)
 */
package dev.tfp.core.farm;

/**
 * The soil tier under a crop, which scales the nutrients it produces and the fertilizer it takes. The tier comes from the
 * block tags ({@code VERY_RICH_FARMLAND} and so on) that the adapter reads; the pure rules take the tier.
 */
public enum SoilTier {
    VERY_RICH(1.2f),
    RICH(1.1f),
    NORMAL(1.0f),
    POOR(0.9f),
    VERY_POOR(0.8f);

    private final float bonus;

    SoilTier(float bonus) {
        this.bonus = bonus;
    }

    /** TFC {@code getSoilModifier}: the multiplier on produced and fertilizer nutrients. */
    public float bonus() {
        return bonus;
    }
}
