/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PhysicalDamage.java @ c3b4d9536 (resistance values, Multiplier.value)
 */
package dev.tfp.core.health;

import java.util.Optional;

/**
 * TFC's {@code PhysicalDamage}: a resistance (positive) or vulnerability (negative) per physical type, in TFC's
 * units. Used for armour pieces, entity types and damage sources alike.
 *
 * @param crushing the crushing resistance
 * @param piercing the piercing resistance
 * @param slashing the slashing resistance
 */
public record PhysicalResistance(float crushing, float piercing, float slashing) {
    /** No resistance to anything. */
    public static final PhysicalResistance NONE = new PhysicalResistance(0f, 0f, 0f);

    /**
     * TFC {@code Multiplier.value(type)}: the resistance to {@code type}, or the highest of the three when the source
     * has no type ({@link Optional#empty()}).
     */
    public float value(Optional<PhysicalDamageType> type) {
        if (type.isEmpty()) {
            return Math.max(crushing, Math.max(piercing, slashing));
        }
        return switch (type.get()) {
            case CRUSHING -> crushing;
            case SLASHING -> slashing;
            case PIERCING -> piercing;
        };
    }
}
