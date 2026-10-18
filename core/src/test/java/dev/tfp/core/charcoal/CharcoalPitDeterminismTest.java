/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (createCharcoal, getCharcoalAmount)
 */
package dev.tfp.core.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Determinism and conservation of the column conversion. Given the same piles and the same float sequence, the result is the
 * same on every call and on four threads at once. Charcoal is conserved: what the cells and the pile below gain, plus what
 * is lost, equals the sum of the per-pile amounts. The burning cells fill from the bottom, so the non-empty cells form a
 * prefix.
 */
class CharcoalPitDeterminismTest {
    @Test
    void sameInputsGiveTheSameConversionOnFourThreads() throws Exception {
        List<Case> cases = randomCases(new Random(0xDE7E4A11L), 2_000);
        List<CharcoalPit.Conversion> expected = new ArrayList<>();
        for (Case c : cases) {
            expected.add(c.run());
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<CharcoalPit.Conversion>>> futures = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                futures.add(pool.submit(() -> {
                    List<CharcoalPit.Conversion> out = new ArrayList<>();
                    for (Case c : cases) {
                        out.add(c.run());
                    }
                    return out;
                }));
            }
            for (Future<List<CharcoalPit.Conversion>> future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void charcoalIsConservedAndCellsFillFromTheBottom() {
        for (Case c : randomCases(new Random(0x5EEDL), 5_000)) {
            if (c.blocked) {
                continue;
            }
            // Re-draw the same floats to get the per-pile amounts.
            Random draws = new Random(c.seed);
            int total = 0;
            for (int logs : c.piles) {
                total += CharcoalPit.charcoalFromLogs(logs, draws.nextFloat());
            }
            CharcoalPit.Conversion result = c.run();
            assertThat(result.converted()).isTrue();
            int placed = 0;
            boolean seenEmpty = false;
            for (int cell : result.layers()) {
                assertThat(cell).isBetween(0, CharcoalPit.MAX_LAYERS);
                if (cell == 0) {
                    seenEmpty = true;
                } else {
                    assertThat(seenEmpty).as("non-empty cell above an empty one in %s", c).isFalse();
                    placed += cell;
                }
            }
            int belowGain = c.layersBelow >= 0 ? result.layersBelow() - c.layersBelow : 0;
            assertThat(placed + belowGain + result.lost()).as("charcoal of %s", c).isEqualTo(total);
            assertThat(belowGain).isGreaterThanOrEqualTo(0);
        }
    }

    /** One column input: the float source is a seeded {@link Random}, so the case can be run again. */
    private record Case(List<Integer> piles, long seed, boolean blocked, int layersBelow) {
        CharcoalPit.Conversion run() {
            Random source = new Random(seed);
            return CharcoalPit.convert(piles, source::nextFloat, blocked, layersBelow);
        }
    }

    private static List<Case> randomCases(Random random, int count) {
        int[] logChoices = {1, 2, 4, 7, 8, 9, 12, 15, 16};
        List<Case> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int height = 1 + random.nextInt(5);
            List<Integer> piles = new ArrayList<>(height);
            for (int h = 0; h < height; h++) {
                piles.add(logChoices[random.nextInt(logChoices.length)]);
            }
            int layersBelow = random.nextInt(10) - 1;
            if (layersBelow > CharcoalPit.MAX_LAYERS) {
                layersBelow = CharcoalPit.MAX_LAYERS;
            }
            out.add(new Case(List.copyOf(piles), random.nextLong(), random.nextInt(8) == 0, layersBelow));
        }
        return out;
    }
}
