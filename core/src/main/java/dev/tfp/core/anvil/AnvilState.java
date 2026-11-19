/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/AnvilBlockEntity.java @ c3b4d9536 (lastRecipe, saved with the
 * block entity)
 */
package dev.tfp.core.anvil;

import dev.tfp.core.persist.VersionedCodec;

/**
 * The anvil device's own state (PAR-METAL-03): TFC {@code AnvilBlockEntity.lastRecipe}, the anvil recipe id the player chose
 * last, which a new main input of an item with several recipes starts with (PAR-METAL-04). The four slots are the device
 * record's own. Immutable, any thread.
 *
 * @param lastRecipe the recipe id, empty for none (TFC's {@code null})
 */
public record AnvilState(String lastRecipe) {
    /** A freshly placed anvil. */
    public static final AnvilState FRESH = new AnvilState("");

    public static final VersionedCodec<AnvilState> CODEC = VersionedCodec.<AnvilState>builder("anvil", 1)
        .build((s, out) -> out.putString("lastRecipe", s.lastRecipe()), in -> new AnvilState(in.getString("lastRecipe", "")));

    public AnvilState {
        if (lastRecipe == null) {
            throw new IllegalArgumentException("lastRecipe must not be null (empty for none)");
        }
    }

    public AnvilState withLastRecipe(String id) {
        return new AnvilState(id);
    }
}
