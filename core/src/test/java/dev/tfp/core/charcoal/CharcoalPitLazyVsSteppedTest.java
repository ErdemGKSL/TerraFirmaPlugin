/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (serverTick)
 */
package dev.tfp.core.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Lazy versus stepped. The stepped side runs TFC's {@code serverTick} once per calendar tick from the lighting tick, with
 * its own counter, and the lazy side is {@link CharcoalPit#hasConverted} and {@link CharcoalPit#convertTick} evaluated at the
 * same tick (or jumped to directly, as a chunk load would). They must agree on the phase at every tick and on the tick it
 * converts.
 */
class CharcoalPitLazyVsSteppedTest {
    @Test
    void lazyEqualsSteppedForEveryTickOfRandomPiles() {
        Random random = new Random(0xC4A7C0A1L);
        long checks = 0;
        for (int pile = 0; pile < 400; pile++) {
            long lit = random.nextInt(4) == 0 ? random.nextInt(5) - 2 : random.nextInt(300_000);
            int charcoalTicks = random.nextInt(4) == 0 ? -1 + random.nextInt(3) : random.nextInt(30_000);
            long[] stepped = stepPerTick(lit, charcoalTicks, charcoalTicks + 20);
            long convert = CharcoalPit.convertTick(lit, charcoalTicks);
            for (int offset = 0; offset < stepped.length; offset++) {
                long now = lit + offset;
                boolean steppedConverted = stepped[offset] == 1L;
                assertThat(CharcoalPit.hasConverted(now, lit, charcoalTicks))
                    .as("pile %d lit %d cc %d now %d", pile, lit, charcoalTicks, now)
                    .isEqualTo(steppedConverted);
                checks++;
            }
            if (lit > 0) {
                assertThat(convert).isEqualTo(lit + Math.max(0, charcoalTicks + 1));
            } else {
                assertThat(convert).isEqualTo(CharcoalPit.NEVER);
            }
        }
        assertThat(checks).isGreaterThan(100_000L);
    }

    @Test
    void jumpingAheadMatchesSteppingToTheSameTick() {
        Random random = new Random(7L);
        for (int i = 0; i < 2_000; i++) {
            long lit = 1 + random.nextInt(500_000);
            int charcoalTicks = random.nextInt(40_000);
            long now = lit + random.nextInt(60_000);
            boolean stepped = steppedConvertedAt(lit, charcoalTicks, now);
            assertThat(CharcoalPit.hasConverted(now, lit, charcoalTicks))
                .as("lit %d cc %d now %d", lit, charcoalTicks, now)
                .isEqualTo(stepped);
        }
    }

    @Test
    void remainingTicksCountsDownToTheConversionTick() {
        Random random = new Random(11L);
        for (int i = 0; i < 500; i++) {
            long lit = 1 + random.nextInt(100_000);
            int charcoalTicks = random.nextInt(20_000);
            long convert = CharcoalPit.convertTick(lit, charcoalTicks);
            for (int offset = 0; offset <= charcoalTicks + 2; offset++) {
                long now = lit + offset;
                long remaining = CharcoalPit.remainingTicks(now, lit, charcoalTicks);
                assertThat(remaining).isEqualTo(Math.max(0L, convert - now));
                assertThat(remaining == 0L).isEqualTo(CharcoalPit.hasConverted(now, lit, charcoalTicks));
            }
        }
    }

    /** One TFC {@code serverTick} per calendar tick from {@code lit}: 1 once the pile converts, else 0. */
    private static long[] stepPerTick(long lit, int charcoalTicks, long horizon) {
        long[] out = new long[(int) horizon + 1];
        long lastUpdateTick = lit;
        boolean converted = false;
        for (int offset = 0; offset <= horizon; offset++) {
            long now = lit + offset;
            if (!converted && lastUpdateTick > 0 && now - lastUpdateTick > charcoalTicks) {
                converted = true;
            }
            out[offset] = converted ? 1L : 0L;
        }
        return out;
    }

    private static boolean steppedConvertedAt(long lit, int charcoalTicks, long now) {
        long[] stepped = stepPerTick(lit, charcoalTicks, now - lit);
        return stepped[(int) (now - lit)] == 1L;
    }
}
