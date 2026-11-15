/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-HEAT-09: {@link PotMatch#perfectMatchExists} against TFC's {@code Helpers.perfectMatchExists} (the symbolic
 * determinant, transcribed here literally from the pinned sources) on every boolean matrix up to 3 x 3 and random ones up
 * to 5 x 5.
 */
class PotMatchTest {

    /** TFC {@code Helpers.perfectMatchExists} with {@code tests.get(i).test(inputs.get(j))} as {@code m[i][j]}. */
    static boolean tfc(boolean[][] m) {
        final int size = m.length;
        final boolean[][] matrices = new boolean[size][];
        for (int i = 0; i < size; i++) {
            matrices[i] = new boolean[(size + 1) * (size + 1)];
        }
        final boolean[] matrix = matrices[size - 1];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                matrix[i + size * j] = m[i][j];
            }
        }
        return det(matrices, size);
    }

    private static boolean det(boolean[][] matrices, int size) {
        final boolean[] matrix = matrices[size - 1];
        return switch (size) {
            case 1 -> matrix[0];
            case 2 -> (matrix[0] && matrix[3]) || (matrix[1] && matrix[2]);
            default -> {
                for (int c = 0; c < size; c++) {
                    if (matrix[c]) {
                        sub(matrices, size, c);
                        if (det(matrices, size - 1)) {
                            yield true;
                        }
                    }
                }
                yield false;
            }
        };
    }

    private static void sub(boolean[][] matrices, int size, int dc) {
        final int subSize = size - 1;
        final boolean[] matrix = matrices[subSize];
        final boolean[] sub = matrices[subSize - 1];
        for (int c = 0; c < subSize; c++) {
            final int c0 = c + (c >= dc ? 1 : 0);
            for (int r = 0; r < subSize; r++) {
                sub[c + subSize * r] = matrix[c0 + size * (r + 1)];
            }
        }
    }

    static boolean ours(boolean[][] m) {
        List<Integer> inputs = new ArrayList<>();
        List<Integer> tests = new ArrayList<>();
        for (int i = 0; i < m.length; i++) {
            inputs.add(i);
            tests.add(i);
        }
        return PotMatch.perfectMatchExists(inputs, tests, (test, input) -> m[test][input]);
    }

    @Test
    void everySmallMatrixAgreesWithTfc() {
        for (int size = 1; size <= 3; size++) {
            int cells = size * size;
            for (int bits = 0; bits < (1 << cells); bits++) {
                boolean[][] m = new boolean[size][size];
                for (int k = 0; k < cells; k++) {
                    m[k / size][k % size] = (bits >> k & 1) == 1;
                }
                assertThat(ours(m)).as("size %d bits %d", size, bits).isEqualTo(tfc(m));
            }
        }
    }

    @Test
    void randomMatricesUpToFiveAgreeWithTfc() {
        Random r = new Random(5);
        int matched = 0;
        for (int n = 0; n < 20_000; n++) {
            int size = 4 + r.nextInt(2);
            double density = 0.2 + r.nextDouble() * 0.6;
            boolean[][] m = new boolean[size][size];
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    m[i][j] = r.nextDouble() < density;
                }
            }
            boolean expected = tfc(m);
            assertThat(ours(m)).as("case %d", n).isEqualTo(expected);
            if (expected) {
                matched++;
            }
        }
        assertThat(matched).isBetween(1000, 19_000);
    }

    @Test
    void matchesNeedEqualSizesAndAllowNothingForNothing() {
        assertThat(PotMatch.matches(List.of(), List.of(), (a, b) -> true)).isTrue();
        assertThat(PotMatch.matches(List.of("a"), List.of(), (a, b) -> true)).isFalse();
        assertThat(PotMatch.matches(List.of(), List.of("x"), (a, b) -> true)).isFalse();
        assertThat(PotMatch.matches(List.of("carrot", "egg"), List.of("egg", "carrot"), String::equals)).isTrue();
        assertThat(PotMatch.matches(List.of("egg", "egg"), List.of("egg", "carrot"), String::equals)).isFalse();
    }
}
