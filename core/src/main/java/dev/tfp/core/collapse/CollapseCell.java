/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.collapse;

import java.util.Objects;

/**
 * What the collapse rules need to know about one block: its kind id (for example {@code tfc:rock/raw/granite}), whether it is a
 * full collision block, and whether its upper face is sturdy. The platform adapter fills these from the block state.
 */
public record CollapseCell(String kind, boolean fullBlock, boolean sturdyUp) {
    public CollapseCell {
        Objects.requireNonNull(kind, "kind");
    }
}
