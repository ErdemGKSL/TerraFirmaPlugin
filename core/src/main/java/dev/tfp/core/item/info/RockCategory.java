/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/RockCategory.java @ c3b4d9536 (the names and ItemType)
 */
package dev.tfp.core.item.info;

import java.util.Locale;
import java.util.Optional;

/** TFC's {@code RockCategory}: the four rock categories of the stone tools and their lore. */
public enum RockCategory {
    IGNEOUS_EXTRUSIVE,
    IGNEOUS_INTRUSIVE,
    METAMORPHIC,
    SEDIMENTARY;

    /** {@code getSerializedName}. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The category named {@code name} ({@code igneous_extrusive}...). */
    public static Optional<RockCategory> byName(String name) {
        for (RockCategory c : values()) {
            if (c.serializedName().equals(name)) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }
}
