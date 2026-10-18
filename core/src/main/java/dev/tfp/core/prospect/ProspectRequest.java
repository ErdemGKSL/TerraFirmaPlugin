/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/PropickItem.java @ c3b4d9536
 */
package dev.tfp.core.prospect;

import java.util.List;

/**
 * The input of one prospector pick, built on the server thread from a snapshot and decided by {@link ProspectRules}. Immutable.
 *
 * @param x                    the clicked block x
 * @param y                    the clicked block y
 * @param z                    the clicked block z
 * @param clickedBlock         the clicked block id
 * @param clickedProspectable  whether the clicked block carries the {@code prospectable} tag
 * @param toolLevel            the metal tier level of the pick (TFC {@code LevelTier.level()}), clamped to 0..5 for accuracy
 * @param cube                 the prospectable blocks found in the scan cube around the clicked block
 */
public record ProspectRequest(int x, int y, int z, String clickedBlock, boolean clickedProspectable, int toolLevel, List<ScanFact> cube) {
    public ProspectRequest {
        if (clickedBlock == null || cube == null) {
            throw new IllegalArgumentException("clickedBlock and cube must not be null");
        }
        cube = List.copyOf(cube);
    }
}
