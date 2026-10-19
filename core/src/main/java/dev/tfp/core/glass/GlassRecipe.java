/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/GlassworkingRecipe.java @ c3b4d9536 (the matches rule: the operation
 * list equals the steps in order, and the batch ingredient accepts the batch)
 */
package dev.tfp.core.glass;

import dev.tfp.core.craft.CraftOutput;
import java.util.List;

/**
 * One glassworking recipe. It matches a blowpipe when its operations equal the blowpipe's steps in order and its batch
 * ingredient accepts the blowpipe's batch.
 *
 * @param id         the recipe id, {@code glassworking/} plus the TFC file name
 * @param batch      the batch ingredient as written in the JSON: an item id, or {@code #tag}
 * @param operations the operation ids in order
 * @param result     the result item and count
 */
public record GlassRecipe(String id, String batch, List<String> operations, CraftOutput result) {
    public GlassRecipe {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("empty glass recipe id");
        if (batch == null || batch.isEmpty()) throw new IllegalArgumentException("glass recipe " + id + " has no batch");
        if (operations == null) throw new IllegalArgumentException("glass recipe " + id + " has no operations");
        if (result == null) throw new IllegalArgumentException("glass recipe " + id + " has no result");
        operations = List.copyOf(operations);
    }
}
