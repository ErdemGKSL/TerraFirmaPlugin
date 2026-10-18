/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (cookContents)
 */
package dev.tfp.core.kiln;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The kiln rules are pure: the same inputs give the same outputs, whatever the call order, the thread or the number of
 * earlier calls. TFC's conversion has no random input, so the rules take none.
 */
class PitKilnDeterminismTest {
    private static final PitKilnRules.Config CFG = PitKilnRules.Config.DEFAULT;

    @Test
    void repeatedAndReorderedEvaluationsAgree() {
        long lit = 500_000L;
        List<Long> nows = new ArrayList<>();
        for (long e = 0; e <= 7300; e += 13) {
            nows.add(lit + e);
        }
        List<String> forward = new ArrayList<>();
        for (long now : nows) {
            forward.add(describe(now, lit));
        }
        List<Long> shuffled = new ArrayList<>(nows);
        Collections.shuffle(shuffled, new Random(9L));
        for (long now : shuffled) {
            assertThat(describe(now, lit)).isEqualTo(forward.get(nows.indexOf(now)));
        }
        for (int i = 0; i < nows.size(); i++) {
            assertThat(describe(nows.get(i), lit)).isEqualTo(forward.get(i));
        }
    }

    @Test
    void sameSeedSameTableInTwoRuns() {
        assertThat(table(123L)).isEqualTo(table(123L));
        assertThat(table(123L)).isNotEqualTo(table(124L));
    }

    @Test
    void convertedDependsOnlyOnItsInputs() {
        PitKilnRules.Outcome out = PitKilnRules.evaluate(true, 0L, 7200L, CFG, PitKilnRules.NEVER);
        assertThat(PitKilnRules.converted(true, 1400f, 0L, out, CFG)).isTrue();
        assertThat(PitKilnRules.converted(false, 1400f, 0L, out, CFG)).isFalse();
        assertThat(PitKilnRules.converted(true, 1401f, 0L, out, CFG)).isFalse();
    }

    private static String describe(long now, long lit) {
        PitKilnRules.Outcome out = PitKilnRules.evaluate(true, lit, now, CFG, PitKilnRules.NEVER);
        return out.phase() + "|" + out.cookedThrough() + "|" + PitKilnRules.temperatureAt(now, lit, CFG)
            + "|" + PitKilnRules.converted(true, 600f, lit, out, CFG);
    }

    private static List<String> table(long seed) {
        Random random = new Random(seed);
        List<String> rows = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            long lit = random.nextInt(1_000_000);
            long failed = random.nextBoolean() ? PitKilnRules.NEVER : lit + random.nextInt(9000);
            PitKilnRules.Outcome out = PitKilnRules.evaluate(true, lit, lit + random.nextInt(9000), CFG, failed);
            rows.add(out.phase() + "|" + out.cookedThrough() + "|" + PitKilnRules.convertTick(random.nextInt(1500), lit, CFG));
        }
        return rows;
    }
}
