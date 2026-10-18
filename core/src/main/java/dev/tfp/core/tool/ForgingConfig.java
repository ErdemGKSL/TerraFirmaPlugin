/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536
 */
package dev.tfp.core.tool;

/**
 * The anvil forging settings that set the forging bonuses: the ratio thresholds of each bonus and the maximum multipliers
 * of the perfectly forged bonus. Immutable. {@link #DEFAULTS} are TFC's server config defaults.
 *
 * @param modestThreshold   the largest ratio of steps taken to optimal steps that is still modestly forged or better
 * @param wellThreshold     the largest ratio for well forged or better
 * @param expertThreshold   the largest ratio for expertly forged or better
 * @param perfectThreshold  the largest ratio for perfectly forged
 * @param maxEfficiency     the mining speed multiplier of a perfectly forged tool
 * @param maxDurability     the probability that a point of damage is ignored on a perfectly forged tool
 * @param maxDamage         the attack damage multiplier of a perfectly forged tool
 */
public record ForgingConfig(
    double modestThreshold,
    double wellThreshold,
    double expertThreshold,
    double perfectThreshold,
    double maxEfficiency,
    double maxDurability,
    double maxDamage
) {
    /** TFC's server config defaults. */
    public static final ForgingConfig DEFAULTS = new ForgingConfig(10.0, 5.0, 2.0, 1.5, 1.8, 0.5, 1.5);
}
