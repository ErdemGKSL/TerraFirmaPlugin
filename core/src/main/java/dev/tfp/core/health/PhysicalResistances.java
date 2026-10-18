/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PhysicalDamageType.java @ c3b4d9536 (calculateMultiplier, the data tables)
 */
package dev.tfp.core.health;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * TFC's damage resistance rules: {@code PhysicalDamageType.calculateMultiplier} and the data tables of
 * {@code entity_damage_resistance/} and {@code item_damage_resistance/} (generated JSON at c3b4d9536).
 *
 * <p>The multiplier is {@code e^(-0.01 x resistance)}, with the resistances of the target's entity type and of each
 * worn armour piece summed first. Natural entity resistances apply only to a typed source, because they can be
 * infinite and would make a typeless punch deal nothing.
 */
public final class PhysicalResistances {
    private PhysicalResistances() {}

    /** {@code minecraft:skeletons}: immune to piercing ({@code 1.0E9}), and crushing is vulnerable (-50). */
    public static final PhysicalResistance SKELETONS = new PhysicalResistance(-50f, 1.0E9f, 0f);

    /** {@code minecraft:zombies}: crushing resistant (50), piercing vulnerable (-25). */
    public static final PhysicalResistance ZOMBIES = new PhysicalResistance(50f, -25f, 0f);

    /** The {@code item_damage_resistance} armour materials, keyed by TFC's material name (c3b4d9536). */
    public static final Map<String, PhysicalResistance> ARMOR = Map.ofEntries(
            Map.entry("leather", new PhysicalResistance(0f, 0f, 3.0f)),
            Map.entry("chainmail", new PhysicalResistance(2.0f, 8.0f, 8.0f)),
            Map.entry("copper", new PhysicalResistance(10.0f, 10.0f, 6.25f)),
            Map.entry("bronze", new PhysicalResistance(12.5f, 12.5f, 8.25f)),
            Map.entry("bismuth_bronze", new PhysicalResistance(15.0f, 10.0f, 8.25f)),
            Map.entry("black_bronze", new PhysicalResistance(10.0f, 15.0f, 8.25f)),
            Map.entry("wrought_iron", new PhysicalResistance(20.0f, 20.0f, 13.2f)),
            Map.entry("steel", new PhysicalResistance(25.0f, 30.0f, 16.5f)),
            Map.entry("black_steel", new PhysicalResistance(50.0f, 45.0f, 33.0f)),
            Map.entry("blue_steel", new PhysicalResistance(62.5f, 50.0f, 50.0f)),
            Map.entry("red_steel", new PhysicalResistance(50.0f, 62.5f, 50.0f)));

    /** TFC {@code PhysicalDamageType.calculateMultiplier} for a summed resistance. */
    public static float multiplier(float resistance) {
        return (float) Math.pow(Math.E, -0.01 * resistance);
    }

    /**
     * The summed resistance of a hit: the target entity's natural resistance (only for a typed source), then each
     * worn armour piece that has a resistance. Entities and armour without data pass {@code null} or are left out.
     *
     * @param type   the source's type, or empty
     * @param entity the target's natural resistance, or {@code null}
     * @param armor  the resistances of the worn pieces that have one
     */
    public static float resistance(Optional<PhysicalDamageType> type, PhysicalResistance entity, List<PhysicalResistance> armor) {
        float resistance = 0;
        if (type.isPresent() && entity != null) {
            resistance += entity.value(type);
        }
        for (PhysicalResistance piece : armor) {
            resistance += piece.value(type);
        }
        return resistance;
    }

    /** {@link #multiplier(float)} of {@link #resistance}. */
    public static float multiplier(Optional<PhysicalDamageType> type, PhysicalResistance entity, List<PhysicalResistance> armor) {
        return multiplier(resistance(type, entity, armor));
    }
}
