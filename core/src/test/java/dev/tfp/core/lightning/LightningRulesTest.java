/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/ForgeEventHandler.java @ c3b4d9536 (onEntityJoinLevel). Golden values are derived by hand
 * from the loop and the radius test; TFC has no data file for them.
 */
package dev.tfp.core.lightning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.lightning.LightningRules.Config;
import dev.tfp.core.lightning.LightningRules.Draws;
import dev.tfp.core.lightning.LightningRules.Offset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LightningRulesTest {
    private static final class Counting implements Draws {
        final float roll;
        final int cell;
        int floats;
        int ints;

        Counting(float roll, int cell) {
            this.roll = roll;
            this.cell = cell;
        }

        @Override
        public float nextFloat() {
            floats++;
            return roll;
        }

        @Override
        public int nextInt(int bound) {
            ints++;
            assertThat(bound).isEqualTo(3);
            return cell;
        }
    }

    @Test
    void defaultsAreTfcs() {
        assertThat(Config.DEFAULTS).isEqualTo(new Config(true, true));
        assertThat(LightningRules.allowed(Config.DEFAULTS)).isTrue();
        assertThat(LightningRules.allowed(new Config(false, true))).isFalse();
    }

    /** Every cell of the box draws; with a draw of 0 every cell within radius 5 (515 of 1331) is taken, in x, y, z order. */
    @Test
    void fullScanHasFiveHundredFifteenCells() {
        Counting d = new Counting(0.19f, 0);
        List<Offset> cells = LightningRules.strippedOffsets(Config.DEFAULTS, d);
        assertThat(cells).hasSize(LightningRules.CELLS_IN_RADIUS);
        assertThat(d.floats).isEqualTo(1);
        assertThat(d.ints).isEqualTo(11 * 11 * 11);
        assertThat(cells.get(0)).isEqualTo(new Offset(-5, 0, 0));
        assertThat(cells.get(1)).isEqualTo(new Offset(-4, -3, 0)); // x = -4: y = -3 is the first with 16 + 9 <= 25, z = 0
        assertThat(cells).doesNotContain(new Offset(5, 5, 5), new Offset(4, 4, 0), new Offset(3, 4, 1));
        assertThat(cells).contains(new Offset(0, 0, 0), new Offset(3, 4, 0), new Offset(0, 0, 5));
    }

    @Test
    void roll_at_or_above_twenty_percent_draws_nothing_more() {
        Counting d = new Counting(0.2f, 0);
        assertThat(LightningRules.strippedOffsets(Config.DEFAULTS, d)).isEmpty();
        assertThat(d.ints).isZero();
    }

    @Test
    void cellDrawOfOneOrTwoTakesNothing() {
        assertThat(LightningRules.strippedOffsets(Config.DEFAULTS, new Counting(0f, 1))).isEmpty();
        assertThat(LightningRules.strippedOffsets(Config.DEFAULTS, new Counting(0f, 2))).isEmpty();
    }

    @Test
    void optionOffDrawsNothing() {
        Counting d = new Counting(0f, 0);
        assertThat(LightningRules.strippedOffsets(new Config(true, false), d)).isEmpty();
        assertThat(d.floats).isZero();
    }

    /** About a third of the radius cells are taken and about a fifth of strikes strip anything. */
    @Test
    void statistics() {
        Random r = new Random(7);
        Draws d = new Draws() {
            @Override
            public float nextFloat() {
                return r.nextFloat();
            }

            @Override
            public int nextInt(int bound) {
                return r.nextInt(bound);
            }
        };
        int strikes = 0;
        long cells = 0;
        for (int i = 0; i < 5000; i++) {
            List<Offset> o = LightningRules.strippedOffsets(Config.DEFAULTS, d);
            if (!o.isEmpty()) {
                strikes++;
                cells += o.size();
            }
        }
        assertThat(strikes / 5000.0).isBetween(0.17, 0.23);
        assertThat((double) cells / strikes).isBetween(515 / 3.0 - 15, 515 / 3.0 + 15);
    }

    @Test
    void strippedMapping() {
        assertThat(LightningRules.stripped("tfp:wood/log/oak")).isEqualTo("tfp:wood/stripped_log/oak");
        assertThat(LightningRules.stripped("tfp:wood/wood/douglas_fir")).isEqualTo("tfp:wood/stripped_wood/douglas_fir");
        assertThat(LightningRules.stripped("tfp:wood/stripped_log/oak")).isNull();
        assertThat(LightningRules.stripped("tfp:wood/planks/oak")).isNull();
        assertThat(LightningRules.stripped("minecraft:oak_log")).isEqualTo("minecraft:stripped_oak_log");
        assertThat(LightningRules.stripped("minecraft:crimson_stem")).isEqualTo("minecraft:stripped_crimson_stem");
        assertThat(LightningRules.stripped("minecraft:bamboo_block")).isEqualTo("minecraft:stripped_bamboo_block");
        assertThat(LightningRules.stripped("minecraft:stripped_oak_log")).isNull();
        assertThat(LightningRules.stripped("minecraft:stone")).isNull();
    }
}
