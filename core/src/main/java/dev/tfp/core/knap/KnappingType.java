/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/KnappingType.java @ c3b4d9536 (the fields, the input test and amount_to_consume default)
 */
package dev.tfp.core.knap;

import dev.tfp.core.tag.TagLookup;

/**
 * A knapping type: which stacks can be knapped (its input ingredient), how many of them one knapping consumes, when, and the
 * presentation flags. The sound, icon and particles are data for the Paper side; the rules only read the consumption fields.
 *
 * @param id                   the type id, e.g. {@code tfc:clay}
 * @param input                the input ingredient (a tag in TFC's data)
 * @param inputCount           the count the input ingredient asks for (TFC's {@code input.count})
 * @param amountToConsume      how many items are removed (TFC's {@code amount_to_consume}, default the input count)
 * @param consumeAfterComplete whether the consumption waits for the result to be taken or the container to close
 * @param hasOffTexture        whether the cells that are off draw a texture
 * @param spawnsParticles      whether clicking a cell spawns particles
 * @param clickSound           the click sound id
 * @param icon                 the icon item id
 */
public record KnappingType(
    String id,
    KnappingIngredient input,
    int inputCount,
    int amountToConsume,
    boolean consumeAfterComplete,
    boolean hasOffTexture,
    boolean spawnsParticles,
    String clickSound,
    String icon
) {
    public KnappingType {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("knapping type needs an id");
        if (input == null) throw new IllegalArgumentException("knapping type " + id + " needs an input");
        if (inputCount < 1) throw new IllegalArgumentException("knapping type " + id + ": input count must be positive");
        if (amountToConsume < 1) throw new IllegalArgumentException("knapping type " + id + ": amount to consume must be positive");
    }

    /** TFC {@code KnappingType#matches}: whether the item is an input of this type. The count is not part of the test. */
    public boolean accepts(String itemId, TagLookup tags) {
        return input.test(itemId, tags);
    }
}
