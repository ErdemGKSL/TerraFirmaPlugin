/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/events/StartFireEvent.java @ c3b4d9536 (startFire), common/items/FirestarterItem.java @ c3b4d9536 (chance), common/items/FlintAndPyriteItem.java @ c3b4d9536 (chance, strength), config/ServerConfig.java @ c3b4d9536 (defaults 0.5 and 0.25)
 */
package dev.tfp.core.fuel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fuel.FireStart.Outcome;
import dev.tfp.core.fuel.FireStart.Strength;
import org.junit.jupiter.api.Test;

/**
 * M3-HEAT-02 golden values for the fire-starting rules at c3b4d9536, with the random draws fixed as inputs. Rows:
 * <ul>
 *   <li>{@code C|base|raining|chance}: {@code FirestarterItem} chance (server default 0.5).</li>
 *   <li>{@code F|chance|raining|firepit base}: {@code FlintAndPyriteItem} firepit base (default 0.25).</li>
 *   <li>{@code S|roll|chance|strength}: flint and pyrite strength, {@code nextFloat() < chance}.</li>
 *   <li>{@code P|base|kindling|chance}: firepit chance, {@code base + min(0.1 x kindling, 0.5)} in float.</li>
 *   <li>{@code D|cancelled|strength|canSurvive|firepitBase|sticks|kindling|hasLogs|roll|outcome}: {@code startFire}.</li>
 * </ul>
 * The {@code roll} values are the float32 draws, so the exact same inputs go into both sides.
 */
class FireStartGoldenTest {
    private static final double EPS = 1e-12;

    // from TFC at c3b4d9536 (StartFireEvent, FirestarterItem, FlintAndPyriteItem, ServerConfig defaults)
    private static final String GOLDEN = """
            # C|base|raining|chance (firestarter, fireStarterChance 0.5)
            C|0.5|false|0.5
            C|0.5|true|0.15
            # F|chance|raining|firepit base (flint and pyrite, flintAndPyriteChance 0.25)
            F|0.25|false|0.25
            F|0.25|true|0.3
            # S|roll|chance|strength
            S|0.0|0.25|STRONG
            S|0.10000000149011612|0.25|STRONG
            S|0.25|0.25|WEAK
            S|0.5|0.25|WEAK
            S|0.9900000095367432|0.25|WEAK
            # P|base|kindling|firepit chance
            P|0.5|0|0.5
            P|0.5|1|0.6000000014901161
            P|0.5|2|0.7000000029802322
            P|0.5|3|0.800000011920929
            P|0.5|4|0.9000000059604645
            P|0.5|5|1.0
            P|0.5|6|1.0
            P|0.5|7|1.0
            P|0.5|8|1.0
            P|0.3|0|0.3
            P|0.3|1|0.4000000014901161
            P|0.3|2|0.5000000029802323
            P|0.3|3|0.600000011920929
            P|0.3|4|0.7000000059604645
            P|0.3|5|0.8
            P|0.3|6|0.8
            P|0.3|7|0.8
            P|0.3|8|0.8
            # D|cancelled|strength|canSurvive|firepitBase|sticks|kindling|hasLogs|roll|outcome
            D|false|STRONG|true|0.5|3|0|true|0.0|FIREPIT
            D|false|STRONG|true|0.5|3|0|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|0.5|3|0|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|0.5|3|0|true|0.5|FIREPIT_MISSED
            D|false|STRONG|true|0.5|3|0|true|0.8999999761581421|FIREPIT_MISSED
            D|false|STRONG|true|0.5|3|2|true|0.0|FIREPIT
            D|false|STRONG|true|0.5|3|2|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|0.5|3|2|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|0.5|3|2|true|0.5|FIREPIT
            D|false|STRONG|true|0.5|3|2|true|0.8999999761581421|FIREPIT_MISSED
            D|false|STRONG|true|0.5|3|5|true|0.0|FIREPIT
            D|false|STRONG|true|0.5|3|5|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|0.5|3|5|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|0.5|3|5|true|0.5|FIREPIT
            D|false|STRONG|true|0.5|3|5|true|0.8999999761581421|FIREPIT
            D|false|STRONG|true|0.5|2|5|true|0.0|FIRE
            D|false|STRONG|true|0.5|2|5|true|0.20000000298023224|FIRE
            D|false|STRONG|true|0.5|2|5|true|0.44999998807907104|FIRE
            D|false|STRONG|true|0.5|2|5|true|0.5|FIRE
            D|false|STRONG|true|0.5|2|5|true|0.8999999761581421|FIRE
            D|false|STRONG|true|0.5|4|0|false|0.0|FIRE
            D|false|STRONG|true|0.5|4|0|false|0.20000000298023224|FIRE
            D|false|STRONG|true|0.5|4|0|false|0.44999998807907104|FIRE
            D|false|STRONG|true|0.5|4|0|false|0.5|FIRE
            D|false|STRONG|true|0.5|4|0|false|0.8999999761581421|FIRE
            D|false|STRONG|false|0.5|3|0|true|0.0|FIRE
            D|false|STRONG|false|0.5|3|0|true|0.20000000298023224|FIRE
            D|false|STRONG|false|0.5|3|0|true|0.44999998807907104|FIRE
            D|false|STRONG|false|0.5|3|0|true|0.5|FIRE
            D|false|STRONG|false|0.5|3|0|true|0.8999999761581421|FIRE
            D|false|STRONG|true|-1|3|0|true|0.0|FIRE
            D|false|STRONG|true|-1|3|0|true|0.20000000298023224|FIRE
            D|false|STRONG|true|-1|3|0|true|0.44999998807907104|FIRE
            D|false|STRONG|true|-1|3|0|true|0.5|FIRE
            D|false|STRONG|true|-1|3|0|true|0.8999999761581421|FIRE
            D|false|STRONG|true|1.0|3|0|true|0.0|FIREPIT
            D|false|STRONG|true|1.0|3|0|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|1.0|3|0|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|1.0|3|0|true|0.5|FIREPIT
            D|false|STRONG|true|1.0|3|0|true|0.8999999761581421|FIREPIT
            D|false|WEAK|true|0.5|3|0|true|0.0|NONE
            D|false|WEAK|true|0.5|3|0|true|0.20000000298023224|NONE
            D|false|WEAK|true|0.5|3|0|true|0.44999998807907104|NONE
            D|false|WEAK|true|0.5|3|0|true|0.5|NONE
            D|false|WEAK|true|0.5|3|0|true|0.8999999761581421|NONE
            D|true|STRONG|true|0.5|3|0|true|0.0|CANCELLED
            D|true|STRONG|true|0.5|3|0|true|0.20000000298023224|CANCELLED
            D|true|STRONG|true|0.5|3|0|true|0.44999998807907104|CANCELLED
            D|true|STRONG|true|0.5|3|0|true|0.5|CANCELLED
            D|true|STRONG|true|0.5|3|0|true|0.8999999761581421|CANCELLED
            D|false|STRONG|true|0.25|3|8|true|0.0|FIREPIT
            D|false|STRONG|true|0.25|3|8|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|0.25|3|8|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|0.25|3|8|true|0.5|FIREPIT
            D|false|STRONG|true|0.25|3|8|true|0.8999999761581421|FIREPIT_MISSED
            D|false|STRONG|true|0.5|3|1|true|0.0|FIREPIT
            D|false|STRONG|true|0.5|3|1|true|0.20000000298023224|FIREPIT
            D|false|STRONG|true|0.5|3|1|true|0.44999998807907104|FIREPIT
            D|false|STRONG|true|0.5|3|1|true|0.5|FIREPIT
            D|false|STRONG|true|0.5|3|1|true|0.8999999761581421|FIREPIT_MISSED
            """;

    @Test
    void goldenRowsMatchTfc() {
        int rows = 0;
        for (String line : GOLDEN.strip().split("\n")) {
            if (line.startsWith("#")) {
                continue;
            }
            rows++;
            String[] c = line.split("\\|");
            switch (c[0]) {
                case "C" -> assertThat(FireStart.firestarterChance(Double.parseDouble(c[1]), Boolean.parseBoolean(c[2])))
                    .as(line).isCloseTo(Double.parseDouble(c[3]), within(EPS));
                case "F" -> assertThat(FireStart.flintAndPyriteFirepitBase(Double.parseDouble(c[1]), Boolean.parseBoolean(c[2])))
                    .as(line).isCloseTo(Double.parseDouble(c[3]), within(EPS));
                case "S" -> assertThat(FireStart.flintAndPyriteStrength(Float.parseFloat(c[1]), Double.parseDouble(c[2])).name())
                    .as(line).isEqualTo(c[3]);
                case "P" -> assertThat(FireStart.firepitChance(Double.parseDouble(c[1]), Integer.parseInt(c[2])))
                    .as(line).isCloseTo(Double.parseDouble(c[3]), within(EPS));
                case "D" -> assertThat(FireStart.resolve(Boolean.parseBoolean(c[1]), Strength.valueOf(c[2]),
                        Boolean.parseBoolean(c[3]), Double.parseDouble(c[4]), Integer.parseInt(c[5]), Integer.parseInt(c[6]),
                        Boolean.parseBoolean(c[7]), Float.parseFloat(c[8])).name())
                    .as(line).isEqualTo(c[9]);
                default -> throw new AssertionError("unknown row: " + line);
            }
        }
        assertThat(rows).isEqualTo(GOLDEN_ROWS);
    }

    private static final int GOLDEN_ROWS = 87;

    @Test
    void firepitNeedsSticksLogsAndSurvivableSpot() {
        // Enough sticks, a log, and a roll under the chance: a firepit. Roll 0 always lands.
        assertThat(FireStart.resolve(false, Strength.STRONG, true, 0.5, 3, 0, true, 0f)).isEqualTo(Outcome.FIREPIT);
        // Two sticks is not a firepit, so the fire block is tried instead.
        assertThat(FireStart.resolve(false, Strength.STRONG, true, 0.5, 2, 0, true, 0f)).isEqualTo(Outcome.FIRE);
        // No log, or the spot cannot hold a firepit: the fire block is tried.
        assertThat(FireStart.resolve(false, Strength.STRONG, true, 0.5, 3, 0, false, 0f)).isEqualTo(Outcome.FIRE);
        assertThat(FireStart.resolve(false, Strength.STRONG, false, 0.5, 3, 0, true, 0f)).isEqualTo(Outcome.FIRE);
        // A starter that never builds a firepit (base -1) makes a fire block.
        assertThat(FireStart.resolve(false, Strength.STRONG, true, FireStart.NO_FIREPIT, 3, 0, true, 0f)).isEqualTo(Outcome.FIRE);
    }

    @Test
    void weakAndCancelledStartsDoNothingOrStop() {
        assertThat(FireStart.resolve(false, Strength.WEAK, true, 0.5, 3, 0, true, 0f)).isEqualTo(Outcome.NONE);
        assertThat(FireStart.resolve(true, Strength.STRONG, true, 0.5, 3, 0, true, 0f)).isEqualTo(Outcome.CANCELLED);
    }

    @Test
    void kindlingBonusIsCappedAtHalf() {
        assertThat(FireStart.firepitChance(0.5, 0)).isCloseTo(0.5, within(EPS));
        assertThat(FireStart.firepitChance(0.5, 4)).isCloseTo(0.9, within(1e-6));
        assertThat(FireStart.firepitChance(0.5, 5)).isCloseTo(1.0, within(EPS));
        assertThat(FireStart.firepitChance(0.5, 50)).isCloseTo(1.0, within(EPS));
    }

    @Test
    void firestarterRainScalesByThreeTenths() {
        assertThat(FireStart.firestarterChance(0.5, true)).isCloseTo(0.15, within(EPS));
        assertThat(FireStart.firestarterChance(0.5, false)).isCloseTo(0.5, within(EPS));
    }
}
