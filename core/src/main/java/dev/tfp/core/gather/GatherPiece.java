/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rock/LooseRockBlock.java @ c3b4d9536 (the COUNT property, 1 to 3)
 */
package dev.tfp.core.gather;

import java.util.Objects;

/**
 * A block that gathering rules apply to: a groundcover piece, or a loose rock (plain or mossy) with its {@code count} property.
 */
public sealed interface GatherPiece permits GatherPiece.Groundcover, GatherPiece.LooseRock {

    /** A groundcover piece of one type. */
    record Groundcover(GroundcoverType type) implements GatherPiece {
        public Groundcover {
            Objects.requireNonNull(type, "type");
        }
    }

    /**
     * A loose rock block: TFC's {@code LooseRockBlock} with {@code COUNT_1_3}, so {@code count} is 1, 2 or 3.
     */
    record LooseRock(LooseRockType rock, boolean mossy, int count) implements GatherPiece {
        public static final int MAX_COUNT = 3;

        public LooseRock {
            Objects.requireNonNull(rock, "rock");
            if (count < 1 || count > MAX_COUNT) {
                throw new IllegalArgumentException("loose rock count must be 1 to " + MAX_COUNT + ", was " + count);
            }
        }
    }
}
