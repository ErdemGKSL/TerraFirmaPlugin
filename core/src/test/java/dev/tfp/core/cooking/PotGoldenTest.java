/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PotBlockEntity.java @ c3b4d9536 (handleCooking, PRE_BOIL_TIME)
 */
package dev.tfp.core.cooking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.cooking.PotRules.PotState;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-03 golden values: the pot's pre-boil and boil counters against TFC's {@code PotBlockEntity.handleCooking} at
 * c3b4d9536 (PRE_BOIL_TIME 100), with a constant boiling predicate over each window.
 */
class PotGoldenTest {
    @Test
    void lazyMatchesTfcPotGolden() throws IOException {
        var rows = FirepitGoldenTest.rows("pot.txt");
        assertThat(rows).hasSize(594);
        for (String[] r : rows) {
            PotRules.Advance out = PotRules.advance(
                new PotState(Integer.parseInt(r[0]), Integer.parseInt(r[1])),
                Long.parseLong(r[4]), r[3].equals("1"), Integer.parseInt(r[2]));
            assertThat(out.state().preBoilingTicks()).as(String.join("|", r)).isEqualTo(Integer.parseInt(r[5]));
            assertThat(out.state().boilingTicks()).as(String.join("|", r)).isEqualTo(Integer.parseInt(r[6]));
            assertThat(out.finishedAtTick()).as(String.join("|", r)).isEqualTo(Long.parseLong(r[7]));
        }
    }

    @Test
    void recipeTakesOutputOnTheTickAfterTheBoilCompletes() {
        // 100 pre-boil ticks, 40 boil ticks, then the output on tick 141
        PotRules.Advance out = PotRules.advance(PotState.IDLE, 200, true, 40);
        assertThat(out.finishedAtTick()).isEqualTo(141);
        assertThat(out.state()).isEqualTo(PotState.IDLE);
    }
}
