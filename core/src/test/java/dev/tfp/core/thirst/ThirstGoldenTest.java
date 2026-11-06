/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.thirst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * M3-SURV-03 golden values, produced by TFC's own arithmetic at c3b4d9536 ({@code PlayerInfo}: the heat share of
 * {@code getThirstContributionFromTemperature} with {@code Mth.clampedMap}, the per-point loss of the passive tick,
 * {@code setThirst} clamping, {@code Drinkable.attemptDrink}, and the lack-of-thirst thresholds of {@code tick}),
 * copied into a scratch oracle with minimal {@code Mth} stubs and TFC's default config (thirst modifier 5, heat share
 * 0.4 over 22..34, max thirst 100).
 *
 * <p>Rows:
 * <ul>
 *   <li>{@code C|temperature|heat share|loss per point}: exact.</li>
 *   <li>{@code D|start|points|temperature|end}: TFC subtracts per point in float, clamping at 0; compared within 1e-3.</li>
 *   <li>{@code K|thirst|water|mayDrinkWhenFull|new thirst|canDrink}: exact.</li>
 *   <li>{@code S|thirst|stage}: the stage under TFC's thresholds 20 / 10 / 0, exact.</li>
 *   <li>{@code R|start|ticks|end} (PAR-SURV-04): {@code ticks} ticks of drinking in the rain, TFC adding
 *       {@code thirstGainedFromDrinkingInTheRain} (5/24 as a float) per tick with {@code setThirst}'s clamp; within
 *       1e-3.</li>
 *   <li>{@code E|amplifier|duration|start|end} (PAR-SURV-04): a whole {@code tfc:thirst} effect, vanilla's effect tick
 *       order with {@code ThirstEffect.shouldApplyEffectTickThisTick} and {@code applyEffectTick}; exact.</li>
 * </ul>
 */
class ThirstGoldenTest {
    private static final ThirstConfig CFG = ThirstConfig.DEFAULT;

    // from TFC at c3b4d9536
    private static final String GOLDEN = """
            C|-20.0|0.0|5.0
            C|0.0|0.0|5.0
            C|10.0|0.0|5.0
            C|21.9|0.0|5.0
            C|22.0|0.0|5.0
            C|25.0|0.1|5.5
            C|28.0|0.2|6.0
            C|33.9|0.39666674|6.9833336
            C|34.0|0.4|7.0
            C|40.0|0.4|7.0
            C|60.0|0.4|7.0
            D|100.0|0|10.0|100.0
            D|100.0|0|28.0|100.0
            D|100.0|0|40.0|100.0
            D|100.0|1|10.0|95.0
            D|100.0|1|28.0|94.0
            D|100.0|1|40.0|93.0
            D|100.0|2|10.0|90.0
            D|100.0|2|28.0|88.0
            D|100.0|2|40.0|86.0
            D|100.0|3|10.0|85.0
            D|100.0|3|28.0|82.0
            D|100.0|3|40.0|79.0
            D|100.0|7|10.0|65.0
            D|100.0|7|28.0|58.0
            D|100.0|7|40.0|51.0
            D|100.0|20|10.0|0.0
            D|100.0|20|28.0|0.0
            D|100.0|20|40.0|0.0
            D|100.0|100|10.0|0.0
            D|100.0|100|28.0|0.0
            D|100.0|100|40.0|0.0
            D|100.0|1000|10.0|0.0
            D|100.0|1000|28.0|0.0
            D|100.0|1000|40.0|0.0
            D|75.5|0|10.0|75.5
            D|75.5|0|28.0|75.5
            D|75.5|0|40.0|75.5
            D|75.5|1|10.0|70.5
            D|75.5|1|28.0|69.5
            D|75.5|1|40.0|68.5
            D|75.5|2|10.0|65.5
            D|75.5|2|28.0|63.5
            D|75.5|2|40.0|61.5
            D|75.5|3|10.0|60.5
            D|75.5|3|28.0|57.5
            D|75.5|3|40.0|54.5
            D|75.5|7|10.0|40.5
            D|75.5|7|28.0|33.5
            D|75.5|7|40.0|26.5
            D|75.5|20|10.0|0.0
            D|75.5|20|28.0|0.0
            D|75.5|20|40.0|0.0
            D|75.5|100|10.0|0.0
            D|75.5|100|28.0|0.0
            D|75.5|100|40.0|0.0
            D|75.5|1000|10.0|0.0
            D|75.5|1000|28.0|0.0
            D|75.5|1000|40.0|0.0
            D|30.0|0|10.0|30.0
            D|30.0|0|28.0|30.0
            D|30.0|0|40.0|30.0
            D|30.0|1|10.0|25.0
            D|30.0|1|28.0|24.0
            D|30.0|1|40.0|23.0
            D|30.0|2|10.0|20.0
            D|30.0|2|28.0|18.0
            D|30.0|2|40.0|16.0
            D|30.0|3|10.0|15.0
            D|30.0|3|28.0|12.0
            D|30.0|3|40.0|9.0
            D|30.0|7|10.0|0.0
            D|30.0|7|28.0|0.0
            D|30.0|7|40.0|0.0
            D|30.0|20|10.0|0.0
            D|30.0|20|28.0|0.0
            D|30.0|20|40.0|0.0
            D|30.0|100|10.0|0.0
            D|30.0|100|28.0|0.0
            D|30.0|100|40.0|0.0
            D|30.0|1000|10.0|0.0
            D|30.0|1000|28.0|0.0
            D|30.0|1000|40.0|0.0
            D|12.25|0|10.0|12.25
            D|12.25|0|28.0|12.25
            D|12.25|0|40.0|12.25
            D|12.25|1|10.0|7.25
            D|12.25|1|28.0|6.25
            D|12.25|1|40.0|5.25
            D|12.25|2|10.0|2.25
            D|12.25|2|28.0|0.25
            D|12.25|2|40.0|0.0
            D|12.25|3|10.0|0.0
            D|12.25|3|28.0|0.0
            D|12.25|3|40.0|0.0
            D|12.25|7|10.0|0.0
            D|12.25|7|28.0|0.0
            D|12.25|7|40.0|0.0
            D|12.25|20|10.0|0.0
            D|12.25|20|28.0|0.0
            D|12.25|20|40.0|0.0
            D|12.25|100|10.0|0.0
            D|12.25|100|28.0|0.0
            D|12.25|100|40.0|0.0
            D|12.25|1000|10.0|0.0
            D|12.25|1000|28.0|0.0
            D|12.25|1000|40.0|0.0
            D|5.0|0|10.0|5.0
            D|5.0|0|28.0|5.0
            D|5.0|0|40.0|5.0
            D|5.0|1|10.0|0.0
            D|5.0|1|28.0|0.0
            D|5.0|1|40.0|0.0
            D|5.0|2|10.0|0.0
            D|5.0|2|28.0|0.0
            D|5.0|2|40.0|0.0
            D|5.0|3|10.0|0.0
            D|5.0|3|28.0|0.0
            D|5.0|3|40.0|0.0
            D|5.0|7|10.0|0.0
            D|5.0|7|28.0|0.0
            D|5.0|7|40.0|0.0
            D|5.0|20|10.0|0.0
            D|5.0|20|28.0|0.0
            D|5.0|20|40.0|0.0
            D|5.0|100|10.0|0.0
            D|5.0|100|28.0|0.0
            D|5.0|100|40.0|0.0
            D|5.0|1000|10.0|0.0
            D|5.0|1000|28.0|0.0
            D|5.0|1000|40.0|0.0
            D|0.5|0|10.0|0.5
            D|0.5|0|28.0|0.5
            D|0.5|0|40.0|0.5
            D|0.5|1|10.0|0.0
            D|0.5|1|28.0|0.0
            D|0.5|1|40.0|0.0
            D|0.5|2|10.0|0.0
            D|0.5|2|28.0|0.0
            D|0.5|2|40.0|0.0
            D|0.5|3|10.0|0.0
            D|0.5|3|28.0|0.0
            D|0.5|3|40.0|0.0
            D|0.5|7|10.0|0.0
            D|0.5|7|28.0|0.0
            D|0.5|7|40.0|0.0
            D|0.5|20|10.0|0.0
            D|0.5|20|28.0|0.0
            D|0.5|20|40.0|0.0
            D|0.5|100|10.0|0.0
            D|0.5|100|28.0|0.0
            D|0.5|100|40.0|0.0
            D|0.5|1000|10.0|0.0
            D|0.5|1000|28.0|0.0
            D|0.5|1000|40.0|0.0
            D|0.0|0|10.0|0.0
            D|0.0|0|28.0|0.0
            D|0.0|0|40.0|0.0
            D|0.0|1|10.0|0.0
            D|0.0|1|28.0|0.0
            D|0.0|1|40.0|0.0
            D|0.0|2|10.0|0.0
            D|0.0|2|28.0|0.0
            D|0.0|2|40.0|0.0
            D|0.0|3|10.0|0.0
            D|0.0|3|28.0|0.0
            D|0.0|3|40.0|0.0
            D|0.0|7|10.0|0.0
            D|0.0|7|28.0|0.0
            D|0.0|7|40.0|0.0
            D|0.0|20|10.0|0.0
            D|0.0|20|28.0|0.0
            D|0.0|20|40.0|0.0
            D|0.0|100|10.0|0.0
            D|0.0|100|28.0|0.0
            D|0.0|100|40.0|0.0
            D|0.0|1000|10.0|0.0
            D|0.0|1000|28.0|0.0
            D|0.0|1000|40.0|0.0
            K|0.0|0.0|false|0.0|true
            K|0.0|0.0|true|0.0|true
            K|0.0|5.0|false|5.0|true
            K|0.0|5.0|true|5.0|true
            K|0.0|20.0|false|20.0|true
            K|0.0|20.0|true|20.0|true
            K|0.0|100.0|false|100.0|true
            K|0.0|100.0|true|100.0|true
            K|40.0|0.0|false|40.0|true
            K|40.0|0.0|true|40.0|true
            K|40.0|5.0|false|45.0|true
            K|40.0|5.0|true|45.0|true
            K|40.0|20.0|false|60.0|true
            K|40.0|20.0|true|60.0|true
            K|40.0|100.0|false|100.0|true
            K|40.0|100.0|true|100.0|true
            K|99.5|0.0|false|99.5|true
            K|99.5|0.0|true|99.5|true
            K|99.5|5.0|false|100.0|true
            K|99.5|5.0|true|100.0|true
            K|99.5|20.0|false|100.0|true
            K|99.5|20.0|true|100.0|true
            K|99.5|100.0|false|100.0|true
            K|99.5|100.0|true|100.0|true
            K|100.0|0.0|false|100.0|true
            K|100.0|0.0|true|100.0|true
            K|100.0|5.0|false|100.0|false
            K|100.0|5.0|true|100.0|true
            K|100.0|20.0|false|100.0|false
            K|100.0|20.0|true|100.0|true
            K|100.0|100.0|false|100.0|false
            K|100.0|100.0|true|100.0|true
            S|100.0|NONE
            S|20.5|NONE
            S|20.0|NONE
            S|19.99|SLOWED
            S|10.5|SLOWED
            S|10.0|SLOWED
            S|9.99|SEVERE
            S|0.5|SEVERE
            S|0.0|DEHYDRATED
            S|-1.0|DEHYDRATED
            R|0.0|1|0.20833333
            R|0.0|20|4.166666
            R|0.0|24|5.0
            R|0.0|100|20.833338
            R|0.0|480|100.0
            R|0.0|1000|100.0
            R|10.0|1|10.208333
            R|10.0|20|14.16666
            R|10.0|24|14.999992
            R|10.0|100|30.83337
            R|10.0|480|100.0
            R|10.0|1000|100.0
            R|50.0|1|50.208332
            R|50.0|20|54.16664
            R|50.0|24|54.99997
            R|50.0|100|70.83333
            R|50.0|480|100.0
            R|50.0|1000|100.0
            R|99.0|1|99.208336
            R|99.0|20|100.0
            R|99.0|24|100.0
            R|99.0|100|100.0
            R|99.0|480|100.0
            R|99.0|1000|100.0
            R|99.9|1|100.0
            R|99.9|20|100.0
            R|99.9|24|100.0
            R|99.9|100|100.0
            R|99.9|480|100.0
            R|99.9|1000|100.0
            R|100.0|1|100.0
            R|100.0|20|100.0
            R|100.0|24|100.0
            R|100.0|100|100.0
            R|100.0|480|100.0
            R|100.0|1000|100.0
            E|0|600|100.0|99.76004
            E|0|600|50.0|49.759995
            E|0|600|1.0|0.7600002
            E|0|600|0.06|0.04
            E|0|600|0.05|0.05
            E|0|600|0.0|0.0
            E|1|600|100.0|99.03998
            E|1|600|50.0|49.039978
            E|1|600|1.0|0.03999982
            E|1|600|0.06|0.02
            E|1|600|0.05|0.05
            E|1|600|0.0|0.0
            E|1|80|100.0|99.88
            E|1|80|50.0|49.879997
            E|1|80|1.0|0.87999994
            E|1|80|0.06|0.02
            E|1|80|0.05|0.05
            E|1|80|0.0|0.0
            E|2|100|100.0|99.52002
            E|2|100|50.0|49.51999
            E|2|100|1.0|0.52
            E|2|100|0.06|0.0
            E|2|100|0.05|0.05
            E|2|100|0.0|0.0
            E|5|40|100.0|95.19989
            E|5|40|50.0|45.200043
            E|5|40|1.0|0.03999997
            E|5|40|0.06|0.0
            E|5|40|0.05|0.05
            E|5|40|0.0|0.0
            E|6|30|100.0|95.80002
            E|6|30|50.0|45.80002
            E|6|30|1.0|0.02000007
            E|6|30|0.06|0.0
            E|6|30|0.05|0.05
            E|6|30|0.0|0.0
            E|1|1|100.0|100.0
            E|1|1|50.0|50.0
            E|1|1|1.0|1.0
            E|1|1|0.06|0.06
            E|1|1|0.05|0.05
            E|1|1|0.0|0.0
            E|0|49|100.0|100.0
            E|0|49|50.0|50.0
            E|0|49|1.0|1.0
            E|0|49|0.06|0.06
            E|0|49|0.05|0.05
            E|0|49|0.0|0.0
            E|0|50|100.0|99.98
            E|0|50|50.0|49.98
            E|0|50|1.0|0.98
            E|0|50|0.06|0.04
            E|0|50|0.05|0.05
            E|0|50|0.0|0.0
            """;

    private static String[] rows(String kind) {
        return GOLDEN.lines().map(String::strip).filter(l -> l.startsWith(kind + "|")).toArray(String[]::new);
    }

    @Test
    void heatShareAndLossPerPointMatchTfc() {
        String[] rows = rows("C");
        assertThat(rows).hasSize(11);
        for (String row : rows) {
            String[] f = row.split("\\|");
            float temp = Float.parseFloat(f[1]);
            assertThat(ThirstState.temperatureContribution(temp, CFG)).as(row).isEqualTo(Float.parseFloat(f[2]));
            assertThat(ThirstState.lossPerPoint(temp, CFG)).as(row).isEqualTo(Float.parseFloat(f[3]));
        }
    }

    @Test
    void decayPerHungerPointMatchesTfcStepping() {
        String[] rows = rows("D");
        assertThat(rows).hasSize(168);
        for (String row : rows) {
            String[] f = row.split("\\|");
            float start = Float.parseFloat(f[1]);
            long points = Long.parseLong(f[2]);
            float temp = Float.parseFloat(f[3]);
            float expected = Float.parseFloat(f[4]);
            ThirstState end = new ThirstState(start, 0).advance(1, points, temp, CFG);
            assertThat(end.thirst()).as(row).isCloseTo(expected, within(1e-3f));
        }
    }

    @Test
    void drinkCapsAtMaxAndGatesOnFullness() {
        String[] rows = rows("K");
        assertThat(rows).hasSize(32);
        for (String row : rows) {
            String[] f = row.split("\\|");
            ThirstState s = new ThirstState(Float.parseFloat(f[1]), 0);
            float water = Float.parseFloat(f[2]);
            boolean may = Boolean.parseBoolean(f[3]);
            assertThat(s.drink(water, CFG).thirst()).as(row).isEqualTo(Float.parseFloat(f[4]));
            assertThat(s.canDrink(water, may, CFG)).as(row).isEqualTo(Boolean.parseBoolean(f[5]));
        }
    }

    @Test
    void rainDrinkingMatchesTfcPerTickGain() {
        String[] rows = rows("R");
        assertThat(rows).hasSize(36);
        for (String row : rows) {
            String[] f = row.split("\\|");
            ThirstState s = new ThirstState(Float.parseFloat(f[1]), 0);
            assertThat(s.drinkRain(Long.parseLong(f[2]), CFG).thirst()).as(row)
                    .isCloseTo(Float.parseFloat(f[3]), within(1e-3f));
        }
    }

    @Test
    void thirstEffectDrainMatchesTfcEffectTicks() {
        String[] rows = rows("E");
        assertThat(rows).hasSize(54);
        for (String row : rows) {
            String[] f = row.split("\\|");
            int duration = Integer.parseInt(f[2]);
            ThirstEffect.Drain drain = new ThirstEffect(Integer.parseInt(f[1]), duration)
                    .advance(Float.parseFloat(f[3]), duration);
            assertThat(drain.thirst()).as(row).isEqualTo(Float.parseFloat(f[4]));
            assertThat(drain.left()).as(row).isNull();
        }
    }

    @Test
    void thirstEffectInPiecesEqualsTheWholeEffect() {
        for (String row : rows("E")) {
            String[] f = row.split("\\|");
            int duration = Integer.parseInt(f[2]);
            ThirstEffect effect = new ThirstEffect(Integer.parseInt(f[1]), duration);
            float thirst = Float.parseFloat(f[3]);
            while (effect != null) {
                ThirstEffect.Drain d = effect.advance(thirst, 20);
                thirst = d.thirst();
                effect = d.left();
            }
            assertThat(thirst).as(row).isEqualTo(Float.parseFloat(f[4]));
        }
    }

    @Test
    void stagesFollowTfcThresholds() {
        String[] rows = rows("S");
        assertThat(rows).hasSize(10);
        for (String row : rows) {
            String[] f = row.split("\\|");
            assertThat(ThirstStage.of(Float.parseFloat(f[1]), CFG).name()).as(row).isEqualTo(f[2]);
        }
    }
}
