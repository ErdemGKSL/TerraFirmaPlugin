/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/test/java/net/dries007/tfc/test/util/ForgeRuleTest.java @ c3b4d9536
 */
package dev.tfp.core.forge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ForgeRuleTest {
    @Test
    void getOptimalStepsToTarget() {
        for (int i = 0; i < ForgeStep.LIMIT; i++) {
            final int minStepHeuristic = (int) Math.ceil(i / 16f);
            final int maxStepHeuristic = minStepHeuristic + 1 + 1; // steps to N % 15 + steps to N % 3 + steps to N

            assertThat(ForgeStep.getOptimalStepsToTarget(i)).isGreaterThanOrEqualTo(minStepHeuristic);
            assertThat(ForgeStep.getOptimalStepsToTarget(i)).isLessThanOrEqualTo(maxStepHeuristic);
        }

        assertThat(ForgeStep.getOptimalStepsToTarget(0)).isEqualTo(0);
        assertThat(ForgeStep.getOptimalStepsToTarget(1)).isEqualTo(2); // +16 -15
        assertThat(ForgeStep.getOptimalStepsToTarget(2)).isEqualTo(1); // +2
        assertThat(ForgeStep.getOptimalStepsToTarget(3)).isEqualTo(3); // +2 +7 -6
        assertThat(ForgeStep.getOptimalStepsToTarget(4)).isEqualTo(2); // +7 -3
        assertThat(ForgeStep.getOptimalStepsToTarget(5)).isEqualTo(3); // +7 +7 -9
        assertThat(ForgeStep.getOptimalStepsToTarget(6)).isEqualTo(3); // +2 +7 -3
    }

    @Test
    void isConsistent() {
        for (ForgeRule r1 : ForgeRule.VALUES) {
            for (ForgeRule r2 : ForgeRule.VALUES) {
                for (ForgeRule r3 : ForgeRule.VALUES) {
                    assertThat(ForgeRule.isConsistent(r1, r2, r3)).isEqualTo(anyMatch(r1, r2, r3));
                }
            }
        }
    }

    private static boolean anyMatch(ForgeRule r1, ForgeRule r2, ForgeRule r3) {
        for (ForgeStep s1 : ForgeStep.VALUES) {
            for (ForgeStep s2 : ForgeStep.VALUES) {
                for (ForgeStep s3 : ForgeStep.VALUES) {
                    if (r1.matches(s1, s2, s3) && r2.matches(s1, s2, s3) && r3.matches(s1, s2, s3)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void calculateOptimalStepsToTarget() {
        for (ForgeRule r1 : ForgeRule.VALUES) {
            for (ForgeRule r2 : ForgeRule.VALUES) {
                for (ForgeRule r3 : ForgeRule.VALUES) {
                    if (ForgeRule.isConsistent(r1, r2, r3)) {
                        assertThat(ForgeRule.calculateOptimalStepsToTarget(75, List.of(r1, r2, r3)))
                            .isEqualTo(calculateExpectedOptimalSteps(r1, r2, r3));
                    }
                }
            }
        }
    }

    private static int calculateExpectedOptimalSteps(ForgeRule r1, ForgeRule r2, ForgeRule r3) {
        int expected = Integer.MAX_VALUE;
        for (ForgeStep s1 : ForgeStep.VALUES) {
            for (ForgeStep s2 : ForgeStep.VALUES) {
                for (ForgeStep s3 : ForgeStep.VALUES) {
                    final int distance = 3 + ForgeStep.getOptimalStepsToTarget(75 - s1.step() - s2.step() - s3.step());
                    if (distance < expected && r1.matches(s1, s2, s3) && r2.matches(s1, s2, s3) && r3.matches(s1, s2, s3)) {
                        expected = distance;
                    }
                }
            }
        }
        return expected;
    }
}
