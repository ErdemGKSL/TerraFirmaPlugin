/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/PhysicalDamageType.java @ c3b4d9536 (physical damage types, tag mapping)
 */
package dev.tfp.core.health;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * TFC's three physical damage types ({@code PhysicalDamageType}). A damage source has at most one type; a source with
 * no type is resisted by the highest of the three resistances.
 *
 * <p>The vanilla mapping follows TFC's {@code is_piercing}, {@code is_crushing} and {@code is_slashing} tags, copied
 * from the generated data at c3b4d9536 (TFC's {@code is_slashing} is empty). TFC checks the tags before the attacker's weapon or entity, so this is the
 * first step of {@link #fromVanillaDamageType}; the weapon and entity steps need the server and stay in the glue.
 */
public enum PhysicalDamageType {
    CRUSHING,
    SLASHING,
    PIERCING;

    /** {@code tfc:is_piercing} as listed for vanilla damage types. */
    private static final List<String> VANILLA_PIERCING = List.of(
            "cactus", "sweet_berry_bush", "stalagmite", "falling_stalactite", "sting", "arrow", "trident");

    /** {@code tfc:is_crushing} as listed for vanilla damage types. */
    private static final List<String> VANILLA_CRUSHING = List.of(
            "in_wall", "cramming", "fall", "fly_into_wall", "falling_block", "falling_anvil");

    /** The TFC serialized name, lower case ({@code getSerializedName}), used in translation keys. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The type of a vanilla damage type by its path (for example {@code "arrow"}, or {@code "minecraft:arrow"}), from
     * TFC's tags. Empty when no tag lists it, which means the type comes from the attacker.
     */
    public static Optional<PhysicalDamageType> fromVanillaDamageType(String damageTypeId) {
        String path = damageTypeId.startsWith("minecraft:") ? damageTypeId.substring("minecraft:".length()) : damageTypeId;
        if (VANILLA_PIERCING.contains(path)) {
            return Optional.of(PIERCING);
        }
        if (VANILLA_CRUSHING.contains(path)) {
            return Optional.of(CRUSHING);
        }
        return Optional.empty();
    }
}
