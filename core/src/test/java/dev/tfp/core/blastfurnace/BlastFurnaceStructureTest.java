/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/BlastFurnaceBlock.java @ c3b4d9536 (BLAST_FURNACE_CHIMNEY,
 *       getChimneyLevels)
 */
package dev.tfp.core.blastfurnace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlastFurnaceStructureTest {
    /** A chimney of {@code levels} formed levels; {@code missing} is one insulation offset left out. */
    private static BlastFurnaceStructure.World chimney(int levels, int[] missing, boolean centreBlocked) {
        Set<String> insulation = new HashSet<>();
        for (int y = 1; y <= levels; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if ((dx != 0 || dz != 0) && !(missing != null && missing[0] == dx && missing[1] == y && missing[2] == dz)) {
                        insulation.add(dx + "," + y + "," + dz);
                    }
                }
            }
        }
        return new BlastFurnaceStructure.World() {
            @Override
            public boolean insulation(int dx, int dy, int dz) {
                return insulation.contains(dx + "," + dy + "," + dz);
            }

            @Override
            public boolean centre(int dx, int dy, int dz) {
                return !centreBlocked;
            }
        };
    }

    @Test
    void theLevelsAreTheFormedOnesUpToTheMaximum() {
        assertThat(BlastFurnaceStructure.chimneyLevels(chimney(0, null, false), 5)).isZero();
        assertThat(BlastFurnaceStructure.chimneyLevels(chimney(3, null, false), 5)).isEqualTo(3);
        assertThat(BlastFurnaceStructure.chimneyLevels(chimney(9, null, false), 5)).isEqualTo(5);
    }

    @Test
    void oneMissingInsulationBlockStopsTheChimneyThere() {
        for (int[] corner : new int[][] {{1, 2, 1}, {-1, 2, -1}, {0, 2, 1}, {-1, 2, 0}}) {
            assertThat(BlastFurnaceStructure.chimneyLevels(chimney(4, corner, false), 5)).as("%s", (Object) corner).isEqualTo(1);
        }
    }

    @Test
    void aBlockedCentreStopsTheChimney() {
        assertThat(BlastFurnaceStructure.levelFormed(chimney(2, null, true), 1)).isFalse();
        assertThat(BlastFurnaceStructure.chimneyLevels(chimney(2, null, true), 5)).isZero();
    }

    @Test
    void aLevelBelowOneIsRefused() {
        assertThatThrownBy(() -> BlastFurnaceStructure.levelFormed(chimney(1, null, false), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
