/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.plant.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.plant.lifecycle.PlantLifecycle.Params;
import dev.tfp.core.plant.lifecycle.PlantLifecycle.Stage;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Golden values of TFC's {@code PlantBlockModel.getModelFromBlockState} (@ c3b4d9536), exported by a scratch harness
 * outside the repo that runs TFC's method bodies verbatim (with Mojang's {@code Mth}) over the grid below. One letter per
 * case: L blooming, U budding, O dormant, Y dying, S seeding, P sprouting.
 */
class PlantLifecycleTest {
    private static final String GOLDEN =
        "OOOPPPUUULLLLLLSSSYYYLLLLLLSSSOOOPPPUUULLLOOOPPPLLLLLLLLLYYYOOOLLLLLLYYYOOOPPPLLLLLLSSSUUULLLLLLSSSL" +
        "LLSSSLLLSSSLLLSSSUUULLLLLLYYYPPPPPPLLLLLLLLLYYYLLLLLLLLLYYYPPPPPPLLLSSSOOOPPPUUULLLLLLSSSUUULLLLLLSS" +
        "SOOOPPPUUULLLUUULLLSSSLLLLLLLLLSSSLLLLLLLLLUUULLLSSSOOOPPPUUULLLLLLSSSYYYLLLLLLSSSOOOPPPUUULLLOOOPPP" +
        "UUULLLLLLSSSYYYLLLLLLSSSOOOPPPUUULLLLLLSSSLLLLLLLLLSSSLLLLLLLLLSSSLLLSSSLLLLLLOOOPPPUUULLLSSSYYYOOOL" +
        "LLSSSYYYOOOPPPUUULLLOOOUUULLLLLLSSSOOOOOOLLLSSSOOOOOOPPPLLLLLLSSSLLLLLLSSSUUULLLSSSSSSLLLLLLSSSUUULL" +
        "LSSSOOOPPPUUULLLSSSYYYOOOLLLSSSYYYOOOPPPUUULLLOOOOOOPPPLLLLLLSSSYYYLLLLLLSSSOOOOOOPPPUUUSSSLLLLLLUUU" +
        "LLLLLLSSSUUULLLLLLSSSLLLLLLSSSOOOPPPUUULLLSSSYYYOOOLLLSSSYYYOOOPPPUUULLLOOOPPPUUULLLSSSYYYOOOLLLSSSY" +
        "YYOOOPPPUUULLLLLLSSSLLLLLLSSSLLLLLLLLLSSSLLLLLLSSSLLLLLLLLLLLLSSSOOOOOOUUULLLLLLLLLSSSOOOOOOUUULLLLL" +
        "LSSSUUULLLLLLUUULLLLLLSSSUUULLLLLLUUULLLOOOOOOPPPLLLLLLLLLYYYOOOOOOPPPLLLLLLLLLYYYUUULLLLLLSSSOOOOOO" +
        "PPPUUULLLLLLSSSOOOOOOPPPLLLLLLLLLSSSUUULLLLLLLLLLLLLLLSSSUUULLLLLLYYYOOOPPPUUULLLLLLSSSYYYOOOPPPUUUL" +
        "LLLLLSSSLLLLLLLLLOOOOOOPPPUUULLLLLLLLLOOOOOOPPPUUULLLLLLSSSLLLLLLLLLUUULLLLLLSSSLLLLLLLLLUUUOOOOOOPP" +
        "PLLLLLLLLLYYYOOOOOOPPPLLLLLLLLLYYYULUYYYOOOOOOPPPULUULUULUYYYOOOOOOPPPULUULUSSSUUUULUSSSUUUULUULUSSS" +
        "UUUULUSSSUUUULUULUOOOPPPUUUULUSSSYYYOOOOOOPPPUUUULUSSSYYYOOOULUULUYYYOOOOOOPPPUUUULUULUYYYOOOOOOPPPU" +
        "UUUUUULUSSSUUUULUSSSUUUUUUULUSSSUUUULUSSSUUUOOOPPPUUUULUSSSYYYOOOOOOPPPUUUULUSSSYYYOOOULUSSSYYYOOOPP" +
        "PUUUULUULUSSSYYYOOOPPPUUUULUULUSSSUUUULUSSSUUUULUULUSSSUUUULUSSSUUUULUOOOPPPUUUULUSSSYYYOOOOOOPPPUUU" +
        "ULUSSSYYYOOOOOOPPPUULUULUULYYYOOOUULUULYYYOOOPPPUULUULOOOUUUUULUULSSSOOOOOOUULSSSOOOOOOUUUUULUULSSSU" +
        "ULUULSSSUUUUULSSSSSSUUUUULSSSUULUULUULOOOPPPUULUULUULYYYOOOUULUULYYYOOOPPPUULUULYYYOOOPPPUULUULSSSYY" +
        "YUULUULSSSYYYOOOPPPUULSSSUULUULUUUUULUULSSSUUUUULUULSSSUULUULSSSOOOPPPUULUULUULYYYOOOUULUULYYYOOOPPP" +
        "UULUULOOOPPPUULUULUULYYYOOOUULSSSYYYOOOPPPUULUULUULSSSUULUULSSSUULUULUULSSSUULUULSSSUULUUL";

    private static final Map<Character, Stage> LETTER = Map.of('L', Stage.BLOOMING, 'U', Stage.BUDDING, 'O', Stage.DORMANT,
        'Y', Stage.DYING, 'S', Stage.SEEDING, 'P', Stage.SPROUTING);

    @Test
    void matchesTfcGoldenGrid() {
        // canna, athyrium fern (defaults), pampas grass (wet season), lotus (wet season, day window), sacred datura (window wraps)
        Params[] plants = {
            new Params(false, -0.1f, 0.43f, 0.51f, 0.6f, 0.74f, 0.93f, 0, 0),
            Params.defaults(false),
            new Params(true, 0f, 0.4f, 0.45f, 0.5f, 0.8f, 0.9f, 0, 0),
            new Params(true, -0.12f, 0.25f, 0.35f, 0.5f, 0.75f, 0.92f, 1000, 13000),
            new Params(false, -0.2f, 0.4f, 0.5f, 0.6f, 0.8f, 0.95f, 12000, 0),
        };
        int[][] pos = {{0, 0}, {123, -456}, {-9000, 7777}};
        float[][] climate = {{-0.2f, 5f}, {0.1f, 20f}, {0.35f, 28f}};
        float[] toys = {0.0f, 0.17f, 0.33f, 0.5f, 0.66f, 0.83f, 0.95f};
        long[] dts = {500, 6000, 15000};
        int i = 0;
        for (Params p : plants) {
            for (int[] xz : pos) {
                for (float[] c : climate) {
                    for (boolean north : new boolean[] {true, false}) {
                        for (float t : toys) {
                            for (long d : dts) {
                                assertThat(PlantLifecycle.stage(p, xz[0], xz[1], c[0], c[1], north, t, d))
                                    .as("case %d", i).isEqualTo(LETTER.get(GOLDEN.charAt(i)));
                                i++;
                            }
                        }
                    }
                }
            }
        }
        assertThat(i).isEqualTo(GOLDEN.length()).isEqualTo(1890);
    }

    @Test
    void withoutPlantFollowsTheDefaultCalendar() {
        assertThat(PlantLifecycle.stageWithoutPlant(0.0f)).isEqualTo(Stage.DORMANT);
        assertThat(PlantLifecycle.stageWithoutPlant(0.17f)).isEqualTo(Stage.SPROUTING);
        assertThat(PlantLifecycle.stageWithoutPlant(0.33f)).isEqualTo(Stage.BUDDING);
        assertThat(PlantLifecycle.stageWithoutPlant(0.5f)).isEqualTo(Stage.BLOOMING);
        assertThat(PlantLifecycle.stageWithoutPlant(0.66f)).isEqualTo(Stage.SEEDING);
        assertThat(PlantLifecycle.stageWithoutPlant(0.83f)).isEqualTo(Stage.DYING);
        assertThat(PlantLifecycle.stageWithoutPlant(0.95f)).isEqualTo(Stage.DORMANT);
    }

    @Test
    void everyStageOccursInACoolYear() {
        Set<Stage> seen = EnumSet.noneOf(Stage.class);
        for (int k = 0; k < 100; k++) {
            seen.add(PlantLifecycle.stage(Params.defaults(false), 17, 42, 0f, 10f, true, k / 100f, 0));
        }
        assertThat(seen).containsExactlyInAnyOrder(Stage.values());
    }
}
