/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/QuernBlockEntity.java @ c3b4d9536 (clientTick, MANUAL_RECIPE_PER_TICK)
 */
package dev.tfp.core.quern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Determinism: the lookup does not depend on the order the recipes were loaded in, repeated calls agree, and the lazy progress
 * matches a stepped simulation of TFC's per-tick float arithmetic (the stepped form is test-only).
 */
class QuernDeterminismTest {
    @Test
    void lookupIsIndependentOfLoadOrder() throws IOException {
        QuernGoldenTest.Table table = QuernGoldenTest.Table.load();
        List<String[]> rows = QuernGoldenTest.Table.rows("quern-golden.txt");
        for (long seed = 1; seed <= 5; seed++) {
            List<QuernRecipe> shuffled = new ArrayList<>(table.recipes);
            Collections.shuffle(shuffled, new Random(seed));
            for (String[] r : rows) {
                boolean rotten = r[1].equals("1");
                Optional<QuernRecipe> a = QuernRules.find(table.recipes, r[0], rotten, table.tags);
                Optional<QuernRecipe> b = QuernRules.find(shuffled, r[0], rotten, table.tags);
                assertThat(b).as(seed + " " + String.join(" ", r)).isEqualTo(a);
            }
        }
    }

    @Test
    void repeatedCallsAgree() {
        double first = QuernRules.progress(0.37);
        for (int i = 0; i < 100; i++) {
            assertThat(QuernRules.progress(0.37)).isEqualTo(first);
        }
    }

    /**
     * TFC's network grind subtracts {@code speed * (90 / TWO_PI)} from the timer each tick (float32) and finishes when the timer
     * is at or below zero. The lazy form finishes at {@code ceil(2pi / speed)} ticks. The stepped form may land one tick later
     * on an exact tie, and that is the only allowed difference.
     */
    @Test
    void lazyProgressMatchesSteppedTfcTimer() {
        float twoPi = 6.2831855f; // Mth.TWO_PI
        float[] speeds = {0.05f, 0.1f, 0.25f, 0.5f, 1.0f, 2.5f};
        for (float speed : speeds) {
            float timer = QuernRules.TICKS_PER_GRIND;
            float perTick = speed * (QuernRules.TICKS_PER_GRIND / twoPi);
            long stepped = 0;
            while (timer > 0) {
                timer -= perTick;
                stepped++;
            }
            long lazy = (long) Math.ceil(twoPi / speed);
            assertThat(Math.abs(stepped - lazy)).as("speed " + speed).isLessThanOrEqualTo(1);
            // The lazy turns at the stepped finish tick are already finished.
            double radians = speed * stepped;
            assertThat(QuernRules.isFinished(QuernRules.turnsFromRadians(radians))).isTrue();
            assertThat(QuernRules.progress(QuernRules.turnsFromRadians(radians)))
                .isCloseTo(QuernRules.TICKS_PER_GRIND, within(1e-3));
        }
    }
}
