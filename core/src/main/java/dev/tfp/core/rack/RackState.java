/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.rack;

import dev.tfp.core.persist.VersionedCodec;

/**
 * PAR-DECO-05: the device state of a tool rack, a bookshelf, a shelf and a lectern. TFC's block entities keep only their slots
 * ({@code ToolRackBlockEntity}: 4, vanilla {@code ChiseledBookShelfBlockEntity}: 6), so the state is empty; the slots live in
 * the device record.
 */
public record RackState() {
    public static final RackState INSTANCE = new RackState();

    public static final VersionedCodec<RackState> CODEC = VersionedCodec.<RackState>builder("rack", 1)
        .build((s, out) -> { }, in -> INSTANCE);
}
