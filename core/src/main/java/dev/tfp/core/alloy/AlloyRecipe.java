/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/AlloyRecipe.java @ c3b4d9536
 */
package dev.tfp.core.alloy;

import java.util.List;

/**
 * An alloy recipe: the fluid ranges it needs and the fluid it makes. Pure and thread-safe.
 *
 * @param result   the fluid id of the alloy
 * @param contents the ingredient ranges
 */
public record AlloyRecipe(String result, List<AlloyRange> contents) {
    public AlloyRecipe {
        contents = List.copyOf(contents);
    }
}
