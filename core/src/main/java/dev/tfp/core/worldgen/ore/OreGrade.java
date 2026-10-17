/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/Ore.java @ c3b4d9536 (Ore.Grade: poor, normal, rich).
 */
package dev.tfp.core.worldgen.ore;

/**
 * The grade of an ore block. TFC's graded ores ({@code Ore.Grade}) come in poor, normal and rich blocks
 * ({@code tfc:ore/<grade>_<ore>/<rock>}); every other ore (gems, minerals, coal) has one block and no grade.
 */
public enum OreGrade {
    POOR,
    NORMAL,
    RICH,
    /** A mineral, gem or other ore with a single block ({@code tfc:ore/<ore>/<rock>}), or a block that is not an ore. */
    UNGRADED;

    /** The prefix of a graded ore name ({@code poor_}, {@code normal_}, {@code rich_}), or empty for {@link #UNGRADED}. */
    public String prefix() {
        return this == UNGRADED ? "" : name().toLowerCase(java.util.Locale.ROOT) + "_";
    }
}
