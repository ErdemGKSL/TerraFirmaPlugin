/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.chest;

import dev.tfp.core.persist.VersionedCodec;

/**
 * PAR-DECO-04: the device state of a chest. TFC's {@code TFCChestBlockEntity} keeps only its 18 slots (vanilla also keeps
 * the lid and a custom name, which our chests do not have), so the state is empty; the slots live in the device record.
 */
public record ChestState() {
    public static final ChestState INSTANCE = new ChestState();

    public static final VersionedCodec<ChestState> CODEC = VersionedCodec.<ChestState>builder("chest", 1)
        .build((s, out) -> { }, in -> INSTANCE);
}
