/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536
 */
package dev.tfp.core.prospect;

/**
 * One prospectable block the world scan found, relative to the pick position. The caller passes only blocks that carry TFC's
 * {@code prospectable} tag; the rules drop any fact outside the scan radius. Immutable.
 *
 * @param dx    the x offset from the clicked block
 * @param dy    the y offset from the clicked block
 * @param dz    the z offset from the clicked block
 * @param block the block id, for example {@code tfc:ore/poor_copper/granite}
 */
public record ScanFact(int dx, int dy, int dz, String block) {
    public ScanFact {
        if (block == null) {
            throw new IllegalArgumentException("block must not be null");
        }
    }
}
