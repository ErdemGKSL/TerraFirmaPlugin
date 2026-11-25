/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/commands/TFCCommands.java @ c3b4d9536 (the command glue)
 */
package dev.tfp.paper.command;

import dev.tfp.core.command.ClearWorldCommand.Kind;

/**
 * The {@link Kind} of a block for {@code /tfp clearworld}'s presets (TFC {@code ClearWorldCommand.Preset}: the raw,
 * hardened and gravel rock blocks, dirt, grass and sand of TFC's block maps, and the vein ore blocks). Pure: it reads
 * the custom block id the generated content gives each family ({@code tfp:rock/raw/<rock>}, {@code tfp:dirt/<soil>}...).
 * A vanilla block is {@link Kind#OTHER} (or {@link Kind#AIR}); a TFC world has none of the families above in vanilla form.
 */
final class BlockKinds {
    private BlockKinds() {
    }

    /** The kind of the custom block {@code id}, or {@link Kind#OTHER} when it is in none of the families. */
    static Kind ofCustom(String id) {
        if (id.startsWith("tfp:rock/raw/")) {
            return Kind.RAW_ROCK;
        }
        if (id.startsWith("tfp:rock/hardened/")) {
            return Kind.HARDENED_ROCK;
        }
        if (id.startsWith("tfp:rock/gravel/")) {
            return Kind.GRAVEL_ROCK;
        }
        if (id.startsWith("tfp:dirt/")) {
            return Kind.DIRT;
        }
        if (id.startsWith("tfp:grass/")) {
            return Kind.GRASS;
        }
        if (id.startsWith("tfp:sand/")) {
            return Kind.SAND;
        }
        if (id.startsWith("tfp:ore/")) {
            return Kind.ORE;
        }
        return Kind.OTHER;
    }
}
