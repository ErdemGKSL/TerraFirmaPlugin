/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (STRAW_END, LOG_START, LOG_END,
 *       LIT, useItemOn stage checks)
 */
package dev.tfp.core.kiln;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * M3-POT-01 golden values for the pit kiln layer rules at c3b4d9536. Row:
 * {@code stage|canAddStraw(1)|canAddStraw(4)|canAddLog|hasFuel|strawLayers|logs}, with {@code -1} for the lit stage.
 */
class PitKilnLayersGoldenTest {
    // from TFC at c3b4d9536
    private static final String GOLDEN = """
            # stage|add1|add4|log|fuel|strawLayers|logs
            0|1|1|0|0|0|0
            1|1|1|0|0|1|0
            2|1|1|0|0|2|0
            3|1|1|0|0|3|0
            4|1|0|0|0|4|0
            5|1|0|0|0|5|0
            6|1|0|0|0|6|0
            7|0|0|1|0|7|0
            8|0|0|1|0|7|1
            9|0|0|1|0|7|2
            10|0|0|1|0|7|3
            11|0|0|1|0|7|4
            12|0|0|1|0|7|5
            13|0|0|1|0|7|6
            14|0|0|1|0|7|7
            15|0|0|0|1|7|8
            16|0|0|0|0|-1|-1
            """;

    @Test
    void layerRulesMatchTfcRowForRow() {
        int rows = 0;
        for (String line : GOLDEN.strip().split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] r = line.strip().split("\\|", -1);
            int stage = Integer.parseInt(r[0]);
            String where = line;
            assertThat(PitKilnLayers.canAddStraw(stage, PitKilnLayers.STRAW_SINGLE) ? 1 : 0).as(where).isEqualTo(Integer.parseInt(r[1]));
            assertThat(PitKilnLayers.canAddStraw(stage, PitKilnLayers.STRAW_BUNDLE) ? 1 : 0).as(where).isEqualTo(Integer.parseInt(r[2]));
            assertThat(PitKilnLayers.canAddLog(stage) ? 1 : 0).as(where).isEqualTo(Integer.parseInt(r[3]));
            assertThat(PitKilnLayers.hasFuel(stage) ? 1 : 0).as(where).isEqualTo(Integer.parseInt(r[4]));
            if (Integer.parseInt(r[5]) >= 0) {
                assertThat(PitKilnLayers.strawLayers(stage)).as(where).isEqualTo(Integer.parseInt(r[5]));
                assertThat(PitKilnLayers.logCount(stage)).as(where).isEqualTo(Integer.parseInt(r[6]));
            } else {
                assertThat(PitKilnLayers.isValidStage(stage)).as(where).isFalse();
            }
            rows++;
        }
        assertThat(rows).isEqualTo(17);
    }

    @Test
    void capacityIsSevenStrawLayersAndEightLogs() {
        assertThat(PitKilnLayers.STRAW_END).isEqualTo(7);
        assertThat(PitKilnLayers.MAX_LOGS).isEqualTo(8);
        assertThat(PitKilnLayers.stageForLogs(8)).isEqualTo(PitKilnLayers.LOG_END);
        assertThat(PitKilnLayers.hasFuel(PitKilnLayers.stageForLogs(8))).isTrue();
    }

    @Test
    void bundlesAndSinglesStackToSevenThenLogsToFifteen() {
        int stage = 0;
        stage = PitKilnLayers.stageAfterStraw(stage, PitKilnLayers.STRAW_BUNDLE);
        assertThat(stage).isEqualTo(4);
        // a bundle no longer fits at 4 (4 + 4 > 7), a single does
        assertThat(PitKilnLayers.canAddStraw(stage, PitKilnLayers.STRAW_BUNDLE)).isFalse();
        for (int i = 0; i < 3; i++) {
            stage = PitKilnLayers.stageAfterStraw(stage, PitKilnLayers.STRAW_SINGLE);
        }
        assertThat(stage).isEqualTo(PitKilnLayers.STRAW_END);
        // straw is full, logs start
        assertThat(PitKilnLayers.canAddStraw(stage, PitKilnLayers.STRAW_SINGLE)).isFalse();
        for (int i = 0; i < PitKilnLayers.MAX_LOGS; i++) {
            stage = PitKilnLayers.stageAfterLog(stage);
        }
        assertThat(stage).isEqualTo(PitKilnLayers.LOG_END);
        assertThat(PitKilnLayers.canAddLog(stage)).isFalse();
    }

    @Test
    void logsNeedTheFullStrawAndTheLitStageTakesNothing() {
        assertThat(PitKilnLayers.canAddLog(6)).isFalse();
        assertThat(PitKilnLayers.canAddLog(PitKilnLayers.LIT)).isFalse();
        assertThat(PitKilnLayers.canAddStraw(PitKilnLayers.LIT, 1)).isFalse();
        assertThatThrownBy(() -> PitKilnLayers.stageAfterLog(6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PitKilnLayers.stageForLogs(9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PitKilnLayers.canAddStraw(3, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ignitionNeedsFullFuelAndAValidKiln() {
        assertThat(PitKilnLayers.canLight(15, false, true, true, true)).isTrue();
        assertThat(PitKilnLayers.canLight(14, false, true, true, true)).isFalse();
        assertThat(PitKilnLayers.canLight(15, true, true, true, true)).isFalse();
        assertThat(PitKilnLayers.canLight(15, false, false, true, true)).isFalse();
        assertThat(PitKilnLayers.canLight(15, false, true, false, true)).isFalse();
        assertThat(PitKilnLayers.canLight(15, false, true, true, false)).isFalse();
        assertThat(PitKilnLayers.structureValid(true, true)).isTrue();
        assertThat(PitKilnLayers.structureValid(true, false)).isFalse();
        assertThat(PitKilnLayers.structureValid(false, true)).isFalse();
    }
}
