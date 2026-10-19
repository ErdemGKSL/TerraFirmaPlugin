/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/InteractionManager.java (doIngotPiling) and
 * src/main/java/net/dries007/tfc/common/blocks/devices/IngotPileBlock.java (useItemOn) @ c3b4d9536
 */
package dev.tfp.core.pile;

/**
 * One block of a column as the pile rules see it: air, a pile of a kind with its count, or any other block. The platform
 * reads the blocks and maps them to these cells. A pile of the other kind is {@link Filled} with that kind, and is a different
 * block to the rules, as TFC's {@code Helpers.isBlock(state, block)} compares the exact block.
 */
public sealed interface PileCell permits PileCell.Air, PileCell.Other, PileCell.Filled {
    /** Air, a place where a new pile can be put. */
    PileCell AIR = new Air();
    /** Any block that is neither air nor a pile of the rules' kinds. */
    PileCell OTHER = new Other();

    /** Air. */
    record Air() implements PileCell {}

    /** A block that is not air and not a pile. */
    record Other() implements PileCell {}

    /**
     * A pile block of a kind.
     *
     * @param kind  the kind of the pile block
     * @param count its count, {@code 1..kind.capacity()}
     */
    record Filled(PileKind kind, int count) implements PileCell {
        public Filled {
            if (kind == null) throw new IllegalArgumentException("null pile kind");
            if (count < 1 || count > kind.capacity()) {
                throw new IllegalArgumentException("count out of range for " + kind + ": " + count);
            }
        }
    }
}
