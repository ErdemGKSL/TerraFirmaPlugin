/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * M3-SURV-04 golden values, produced by TFC's own arithmetic at c3b4d9536 ({@code PhysicalDamageType.calculateMultiplier},
 * {@code ForgeEventHandler.onLivingHurt}, {@code HealthDisplayStyle.TFC}, the regeneration block of
 * {@code PlayerInfo.tick}), copied into a scratch oracle with minimal {@code Mth} stubs and TFC's default config.
 *
 * <p>Rows:
 * <ul>
 *   <li>{@code P|resistance|multiplier}: exact.</li>
 *   <li>{@code E|entity|type|armour|modifier|damage}: a hit of 10 scaled by the physical multiplier and divided by the
 *       health modifier; exact.</li>
 *   <li>{@code H|hp|modifier|shown}: exact. {@code M|modifier|shown max}: exact.</li>
 *   <li>{@code R|food|thirst|multiplier|heal per step}: exact.</li>
 *   <li>{@code G|hp|max|food|thirst|from|to|result}: TFC's per-tick stepping; compared within 1e-3 against the lazy jump.</li>
 * </ul>
 */
class HealthGoldenTest {
    private static final HealthConfig CFG = HealthConfig.DEFAULT;

    // from TFC at c3b4d9536
    private static final String PHYSICAL = """
            P|-50.0|1.6487212
            P|-25.0|1.2840254
            P|-1.0|1.0100502
            P|0.0|1.0
            P|1.0|0.99004984
            P|2.0|0.9801987
            P|3.0|0.9704455
            P|6.25|0.9394131
            P|8.0|0.9231163
            P|8.25|0.9208114
            P|10.0|0.9048374
            P|12.5|0.8824969
            P|15.0|0.860708
            P|16.5|0.8478937
            P|20.0|0.8187308
            P|25.0|0.7788008
            P|30.0|0.7408182
            P|33.0|0.71892375
            P|45.0|0.63762814
            P|50.0|0.60653067
            P|62.5|0.53526145
            P|100.0|0.36787945
            P|1.0E9|0.0
            """;

    // from TFC at c3b4d9536
    private static final String DAMAGE = """
            E|none|null|none|0.85|11.764706
            E|none|null|none|3.0|3.3333333
            E|none|null|leather4|0.85|10.434358
            E|none|null|leather4|3.0|2.9564016
            E|none|null|bronze4|0.85|7.135655
            E|none|null|bronze4|3.0|2.0217688
            E|none|null|red_steel4|0.85|0.9657059
            E|none|null|red_steel4|3.0|0.27361667
            E|none|null|mix4|0.85|4.5272017
            E|none|null|mix4|3.0|1.2827071
            E|none|crushing|none|0.85|11.764706
            E|none|crushing|none|3.0|3.3333333
            E|none|crushing|leather4|0.85|11.764706
            E|none|crushing|leather4|3.0|3.3333333
            E|none|crushing|bronze4|0.85|7.135655
            E|none|crushing|bronze4|3.0|2.0217688
            E|none|crushing|red_steel4|0.85|1.5921797
            E|none|crushing|red_steel4|3.0|0.4511176
            E|none|crushing|mix4|0.85|5.557253
            E|none|crushing|mix4|3.0|1.574555
            E|none|slashing|none|0.85|11.764706
            E|none|slashing|none|3.0|3.3333333
            E|none|slashing|leather4|0.85|10.434358
            E|none|slashing|leather4|3.0|2.9564016
            E|none|slashing|bronze4|0.85|8.457927
            E|none|slashing|bronze4|3.0|2.3964126
            E|none|slashing|red_steel4|0.85|1.5921797
            E|none|slashing|red_steel4|3.0|0.4511176
            E|none|slashing|mix4|0.85|5.8714643
            E|none|slashing|mix4|3.0|1.6635815
            E|none|piercing|none|0.85|11.764706
            E|none|piercing|none|3.0|3.3333333
            E|none|piercing|leather4|0.85|11.764706
            E|none|piercing|leather4|3.0|3.3333333
            E|none|piercing|bronze4|0.85|7.135655
            E|none|piercing|bronze4|3.0|2.0217688
            E|none|piercing|red_steel4|0.85|0.9657059
            E|none|piercing|red_steel4|3.0|0.27361667
            E|none|piercing|mix4|0.85|4.9042587
            E|none|piercing|mix4|3.0|1.3895401
            E|skeleton|null|none|0.85|11.764706
            E|skeleton|null|none|3.0|3.3333333
            E|skeleton|null|leather4|0.85|10.434358
            E|skeleton|null|leather4|3.0|2.9564016
            E|skeleton|null|bronze4|0.85|7.135655
            E|skeleton|null|bronze4|3.0|2.0217688
            E|skeleton|null|red_steel4|0.85|0.9657059
            E|skeleton|null|red_steel4|3.0|0.27361667
            E|skeleton|null|mix4|0.85|4.5272017
            E|skeleton|null|mix4|3.0|1.2827071
            E|skeleton|crushing|none|0.85|19.39672
            E|skeleton|crushing|none|3.0|5.4957376
            E|skeleton|crushing|leather4|0.85|19.39672
            E|skeleton|crushing|leather4|3.0|5.4957376
            E|skeleton|crushing|bronze4|0.85|11.764706
            E|skeleton|crushing|bronze4|3.0|3.3333333
            E|skeleton|crushing|red_steel4|0.85|2.6250608
            E|skeleton|crushing|red_steel4|3.0|0.74376726
            E|skeleton|crushing|mix4|0.85|9.162362
            E|skeleton|crushing|mix4|3.0|2.5960026
            E|skeleton|slashing|none|0.85|11.764706
            E|skeleton|slashing|none|3.0|3.3333333
            E|skeleton|slashing|leather4|0.85|10.434358
            E|skeleton|slashing|leather4|3.0|2.9564016
            E|skeleton|slashing|bronze4|0.85|8.457927
            E|skeleton|slashing|bronze4|3.0|2.3964126
            E|skeleton|slashing|red_steel4|0.85|1.5921797
            E|skeleton|slashing|red_steel4|3.0|0.4511176
            E|skeleton|slashing|mix4|0.85|5.8714643
            E|skeleton|slashing|mix4|3.0|1.6635815
            E|skeleton|piercing|none|0.85|0.0
            E|skeleton|piercing|none|3.0|0.0
            E|skeleton|piercing|leather4|0.85|0.0
            E|skeleton|piercing|leather4|3.0|0.0
            E|skeleton|piercing|bronze4|0.85|0.0
            E|skeleton|piercing|bronze4|3.0|0.0
            E|skeleton|piercing|red_steel4|0.85|0.0
            E|skeleton|piercing|red_steel4|3.0|0.0
            E|skeleton|piercing|mix4|0.85|0.0
            E|skeleton|piercing|mix4|3.0|0.0
            E|zombie|null|none|0.85|11.764706
            E|zombie|null|none|3.0|3.3333333
            E|zombie|null|leather4|0.85|10.434358
            E|zombie|null|leather4|3.0|2.9564016
            E|zombie|null|bronze4|0.85|7.135655
            E|zombie|null|bronze4|3.0|2.0217688
            E|zombie|null|red_steel4|0.85|0.9657059
            E|zombie|null|red_steel4|3.0|0.27361667
            E|zombie|null|mix4|0.85|4.5272017
            E|zombie|null|mix4|3.0|1.2827071
            E|zombie|crushing|none|0.85|7.135655
            E|zombie|crushing|none|3.0|2.0217688
            E|zombie|crushing|leather4|0.85|7.135655
            E|zombie|crushing|leather4|3.0|2.0217688
            E|zombie|crushing|bronze4|0.85|4.3279934
            E|zombie|crushing|bronze4|3.0|1.2262648
            E|zombie|crushing|red_steel4|0.85|0.9657059
            E|zombie|crushing|red_steel4|3.0|0.27361667
            E|zombie|crushing|mix4|0.85|3.3706446
            E|zombie|crushing|mix4|3.0|0.95501596
            E|zombie|slashing|none|0.85|11.764706
            E|zombie|slashing|none|3.0|3.3333333
            E|zombie|slashing|leather4|0.85|10.434358
            E|zombie|slashing|leather4|3.0|2.9564016
            E|zombie|slashing|bronze4|0.85|8.457927
            E|zombie|slashing|bronze4|3.0|2.3964126
            E|zombie|slashing|red_steel4|0.85|1.5921797
            E|zombie|slashing|red_steel4|3.0|0.4511176
            E|zombie|slashing|mix4|0.85|5.8714643
            E|zombie|slashing|mix4|3.0|1.6635815
            E|zombie|piercing|none|0.85|15.10618
            E|zombie|piercing|none|3.0|4.2800846
            E|zombie|piercing|leather4|0.85|15.10618
            E|zombie|piercing|leather4|3.0|4.2800846
            E|zombie|piercing|bronze4|0.85|9.162362
            E|zombie|piercing|bronze4|3.0|2.5960026
            E|zombie|piercing|red_steel4|0.85|1.2399908
            E|zombie|piercing|red_steel4|3.0|0.35133076
            E|zombie|piercing|mix4|0.85|6.297193
            E|zombie|piercing|mix4|3.0|1.7842048
            """;

    // from TFC at c3b4d9536
    private static final String DISPLAY = """
            H|0.0|0.85|0.0
            H|1.0|0.85|42.5
            H|5.0|0.85|212.5
            H|10.0|0.85|425.0
            H|17.0|0.85|722.50006
            H|20.0|0.85|850.0
            H|0.0|1.0|0.0
            H|1.0|1.0|50.0
            H|5.0|1.0|250.0
            H|10.0|1.0|500.0
            H|17.0|1.0|850.0
            H|20.0|1.0|1000.0
            H|0.0|3.0|0.0
            H|1.0|3.0|150.0
            H|5.0|3.0|750.0
            H|10.0|3.0|1500.0
            H|17.0|3.0|2550.0
            H|20.0|3.0|3000.0
            """;

    // from TFC at c3b4d9536
    private static final String MAX = """
            M|0.85|850.0
            M|1.0|1000.0
            M|3.0|3000.0
            """;

    // from TFC at c3b4d9536
    private static final String REGEN = """
            R|0.0|0.0|0.5|0.0019999999
            R|0.0|20.0|0.75|0.0029999998
            R|0.0|20.5|0.75625|0.003025
            R|0.0|60.0|1.25|0.005
            R|0.0|100.0|1.75|0.0069999993
            R|3.0|0.0|0.6875|0.0027499998
            R|3.0|20.0|0.9375|0.0037499997
            R|3.0|20.5|0.94375|0.0037749999
            R|3.0|60.0|1.4375|0.0057499995
            R|3.0|100.0|1.9375|0.0077499994
            R|4.0|0.0|0.75|0.0029999998
            R|4.0|20.0|1.0|0.0039999997
            R|4.0|20.5|1.00625|0.0040249997
            R|4.0|60.0|1.5|0.0059999996
            R|4.0|100.0|2.0|0.007999999
            R|4.5|0.0|0.78125|0.0031249998
            R|4.5|20.0|1.03125|0.0041249995
            R|4.5|20.5|1.0375|0.00415
            R|4.5|60.0|1.53125|0.0061249994
            R|4.5|100.0|2.03125|0.008125
            R|10.0|0.0|1.125|0.0045
            R|10.0|20.0|1.375|0.0054999995
            R|10.0|20.5|1.38125|0.005525
            R|10.0|60.0|1.875|0.0074999994
            R|10.0|100.0|2.375|0.0095
            R|20.0|0.0|1.75|0.0069999993
            R|20.0|20.0|2.0|0.007999999
            R|20.0|20.5|2.00625|0.008024999
            R|20.0|60.0|2.5|0.01
            R|20.0|100.0|3.0|0.011999999
            """;

    // from TFC at c3b4d9536
    private static final String JUMP = """
            G|10.0|20.0|15.0|60.0|0|100|10.0875
            G|10.0|20.0|15.0|60.0|0|1000|10.874996
            G|10.0|20.0|15.0|60.0|5|57|10.04375
            G|10.0|20.0|15.0|60.0|0|6000|15.249977
            G|1.0|20.0|4.0|21.0|0|100|1.0405002
            G|1.0|20.0|4.0|21.0|0|1000|1.4050016
            G|1.0|20.0|4.0|21.0|5|57|1.0202501
            G|1.0|20.0|4.0|21.0|0|6000|3.4300098
            G|19.9|20.0|20.0|100.0|0|100|20.0
            G|19.9|20.0|20.0|100.0|0|1000|20.0
            G|19.9|20.0|20.0|100.0|5|57|19.959995
            G|19.9|20.0|20.0|100.0|0|6000|20.0
            G|4.0|20.0|3.9|60.0|0|100|4.0
            G|4.0|20.0|3.9|60.0|0|1000|4.0
            G|4.0|20.0|3.9|60.0|5|57|4.0
            G|4.0|20.0|3.9|60.0|0|6000|4.0
            """;

    @Test
    void physicalMultiplierMatchesTfc() {
        int n = 0;
        for (String line : PHYSICAL.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            assertThat(PhysicalResistances.multiplier(Float.parseFloat(f[1]))).as(line).isEqualTo(Float.parseFloat(f[2]));
            n++;
        }
        assertThat(n).isEqualTo(23);
    }

    @Test
    void damageScalingMatchesTfc() {
        int n = 0;
        for (String line : DAMAGE.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            PhysicalResistance entity = switch (f[1]) {
                case "none" -> null;
                case "skeleton" -> PhysicalResistances.SKELETONS;
                case "zombie" -> PhysicalResistances.ZOMBIES;
                default -> throw new IllegalArgumentException(f[1]);
            };
            Optional<PhysicalDamageType> type = switch (f[2]) {
                case "null" -> Optional.empty();
                case "crushing" -> Optional.of(PhysicalDamageType.CRUSHING);
                case "slashing" -> Optional.of(PhysicalDamageType.SLASHING);
                case "piercing" -> Optional.of(PhysicalDamageType.PIERCING);
                default -> throw new IllegalArgumentException(f[2]);
            };
            List<PhysicalResistance> armor = new ArrayList<>();
            switch (f[3]) {
                case "none" -> { }
                case "leather4" -> repeat(armor, PhysicalResistances.ARMOR.get("leather"), 4);
                case "bronze4" -> repeat(armor, PhysicalResistances.ARMOR.get("bronze"), 4);
                case "red_steel4" -> repeat(armor, PhysicalResistances.ARMOR.get("red_steel"), 4);
                case "mix4" -> {
                    armor.add(PhysicalResistances.ARMOR.get("bismuth_bronze"));
                    armor.add(PhysicalResistances.ARMOR.get("black_bronze"));
                    armor.add(PhysicalResistances.ARMOR.get("red_steel"));
                    armor.add(PhysicalResistances.ARMOR.get("leather"));
                }
                default -> throw new IllegalArgumentException(f[3]);
            }
            float mod = Float.parseFloat(f[4]);
            float damage = HealthScale.playerDamage(10f, PhysicalResistances.multiplier(type, entity, armor), mod);
            assertThat(damage).as(line).isEqualTo(Float.parseFloat(f[5]));
            n++;
        }
        assertThat(n).isEqualTo(120);
    }

    @Test
    void shownHealthMatchesTfc() {
        for (String line : DISPLAY.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            assertThat(HealthScale.shownHealth(Float.parseFloat(f[1]), Float.parseFloat(f[2]), CFG)).as(line)
                    .isEqualTo(Float.parseFloat(f[3]));
        }
        for (String line : MAX.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            assertThat(HealthScale.shownMaxHealth(Float.parseFloat(f[1]), CFG)).as(line).isEqualTo(Float.parseFloat(f[2]));
        }
    }

    @Test
    void regenerationMultiplierMatchesTfc() {
        for (String line : REGEN.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            float food = Float.parseFloat(f[1]);
            float thirst = Float.parseFloat(f[2]);
            assertThat(HealthRegen.multiplier(food, thirst, CFG)).as(line).isEqualTo(Float.parseFloat(f[3]));
            assertThat(HealthRegen.healPerStep(food, thirst, CFG)).as(line).isEqualTo(Float.parseFloat(f[4]));
        }
    }

    @Test
    void lazyJumpMatchesTfcSteppedRegeneration() {
        for (String line : JUMP.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.trim().split("\\|");
            HealthState start = new HealthState(Float.parseFloat(f[1]), Long.parseLong(f[5]));
            HealthState end = start.advance(Long.parseLong(f[6]), Float.parseFloat(f[3]), Float.parseFloat(f[4]),
                    Float.parseFloat(f[2]), CFG);
            assertThat(end.health()).as(line).isCloseTo(Float.parseFloat(f[7]), within(1e-3f));
        }
    }

    private static void repeat(List<PhysicalResistance> list, PhysicalResistance value, int times) {
        for (int i = 0; i < times; i++) {
            list.add(value);
        }
    }
}
