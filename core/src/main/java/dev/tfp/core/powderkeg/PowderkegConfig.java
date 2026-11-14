/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (powderKegEnabled,
 *       powderKegOnlyBreaksNaturalBlocks, powderKegEnableAutomation, powderKegStrengthModifier, powderKegFuseTime)
 */
package dev.tfp.core.powderkeg;

/**
 * PAR-PROC-18: TFC's powderkeg server options, with TFC's defaults and ranges.
 *
 * @param enabled                 {@code powderKegEnabled}: whether kegs can be lit
 * @param onlyBreaksNaturalBlocks {@code powderKegOnlyBreaksNaturalBlocks}
 * @param automation              {@code powderKegEnableAutomation} (hoppers inserting from above)
 * @param strengthModifier        {@code powderKegStrengthModifier}, 0 to 64
 * @param fuseTicks               {@code powderKegFuseTime}, at least 1
 */
public record PowderkegConfig(boolean enabled, boolean onlyBreaksNaturalBlocks, boolean automation,
        double strengthModifier, int fuseTicks) {
    /** TFC's defaults: enabled, breaks everything not explosion proof, automation on, modifier 1, fuse 80 ticks. */
    public static final PowderkegConfig DEFAULTS = new PowderkegConfig(true, false, true, 1d, 80);

    public PowderkegConfig {
        if (!(strengthModifier >= 0 && strengthModifier <= 64)) {
            throw new IllegalArgumentException("strengthModifier outside 0..64: " + strengthModifier);
        }
        if (fuseTicks < 1) {
            throw new IllegalArgumentException("fuseTicks < 1: " + fuseTicks);
        }
    }

    /** {@link PowderkegRules#strength(int, double)} with this modifier. */
    public int strength(int itemCount) {
        return PowderkegRules.strength(itemCount, strengthModifier);
    }
}
