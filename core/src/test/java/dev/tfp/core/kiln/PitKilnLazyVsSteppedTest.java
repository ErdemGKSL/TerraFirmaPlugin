/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (serverTick, cookContents)
 */
package dev.tfp.core.kiln;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Lazy versus stepped. The stepped side is a per-tick copy of TFC's {@code serverTick} (failure check, cooking, the
 * remaining-time check) and it is run for every tick of a random kiln. The lazy side is {@link PitKilnRules#evaluate} and
 * {@link PitKilnRules#converted} at the same tick. They must agree on the phase, the last cooked tick and the conversion of
 * every recipe, for every tick checked.
 */
class PitKilnLazyVsSteppedTest {
    private static final float[] RECIPES = {0f, 1f, 200f, 600f, 1000f, 1399f, 1400f, 1401f};

    @Test
    void lazyEqualsSteppedOnRandomKilns() {
        Random random = new Random(0x7F1B_0C01L);
        int checks = 0;
        for (int kiln = 0; kiln < 300; kiln++) {
            long duration = 20 + random.nextInt(3000);
            float max = random.nextInt(1500);
            PitKilnRules.Config cfg = new PitKilnRules.Config(duration, max);
            long lit = random.nextInt(200_000);
            // a failure at most once, at a random tick around the burn (or never)
            long failed = random.nextInt(4) == 0 ? PitKilnRules.NEVER : lit + random.nextInt((int) duration + 40);
            Stepped.Result[] expected = new Stepped(cfg, lit, failed).run(lit + duration + 5);
            for (long now = lit; now <= lit + duration + 5; now++) {
                Stepped.Result step = expected[(int) (now - lit)];
                PitKilnRules.Outcome lazy = PitKilnRules.evaluate(true, lit, now, cfg, failed);
                assertThat(lazy.phase()).as("kiln %d now %d", kiln, now).isEqualTo(step.phase());
                assertThat(lazy.cookedThrough()).as("kiln %d now %d", kiln, now).isEqualTo(step.cookedThrough());
                for (float recipe : RECIPES) {
                    boolean lazyConverted = PitKilnRules.converted(true, recipe, lit, lazy, cfg);
                    assertThat(lazyConverted)
                        .as("kiln %d now %d recipe %s", kiln, now, recipe)
                        .isEqualTo(step.converted(recipe));
                }
                checks++;
            }
        }
        assertThat(checks).isGreaterThan(300_000);
    }

    @Test
    void convertTickIsTheFirstSteppedTickAtTheRecipeTemperature() {
        Random random = new Random(42L);
        for (int i = 0; i < 200; i++) {
            long duration = 20 + random.nextInt(9000);
            PitKilnRules.Config cfg = new PitKilnRules.Config(duration, 1 + random.nextInt(1600));
            long lit = random.nextInt(100_000);
            float recipe = random.nextInt(1800);
            long lazy = PitKilnRules.convertTick(recipe, lit, cfg);
            long stepped = PitKilnRules.NEVER;
            for (long t = lit; t <= lit + duration; t++) {
                if (PitKilnRules.temperatureAt(t, lit, cfg) >= recipe) {
                    stepped = t;
                    break;
                }
            }
            assertThat(lazy).isEqualTo(stepped);
        }
    }

    /** One TFC {@code serverTick} per calendar tick, from the lighting tick. */
    private static final class Stepped {
        private final PitKilnRules.Config cfg;
        private final long lit;
        private final long failed;

        Stepped(PitKilnRules.Config cfg, long lit, long failed) {
            this.cfg = cfg;
            this.lit = lit;
            this.failed = failed;
        }

        record Result(PitKilnRules.Phase phase, long cookedThrough, long[] firstCook) {
            boolean converted(float recipe) {
                for (int i = 0; i < RECIPES.length; i++) {
                    if (RECIPES[i] == recipe) {
                        return firstCook[i] != PitKilnRules.NEVER && firstCook[i] <= cookedThrough;
                    }
                }
                throw new IllegalArgumentException("no recipe " + recipe);
            }
        }

        /** One step per calendar tick from the lighting tick to {@code until}; index {@code i} is tick {@code lit + i}. */
        Result[] run(long until) {
            int n = (int) (until - lit) + 1;
            Result[] out = new Result[n];
            long[] firstCook = new long[RECIPES.length];
            java.util.Arrays.fill(firstCook, PitKilnRules.NEVER);
            PitKilnRules.Phase phase = PitKilnRules.Phase.LIT;
            long cooked = lit - 1;
            for (int i = 0; i < n; i++) {
                long t = lit + i;
                if (phase == PitKilnRules.Phase.LIT) {
                    if (failed != PitKilnRules.NEVER && t >= failed) {
                        phase = PitKilnRules.Phase.FAILED;
                    } else {
                        float temperature = PitKilnRules.temperatureAt(t, lit, cfg);
                        for (int r = 0; r < RECIPES.length; r++) {
                            if (firstCook[r] == PitKilnRules.NEVER && temperature >= RECIPES[r]) {
                                firstCook[r] = t;
                            }
                        }
                        cooked = t;
                        if (t - lit >= cfg.durationTicks()) {
                            phase = PitKilnRules.Phase.FIRED;
                        }
                    }
                }
                out[i] = new Result(phase, cooked, firstCook.clone());
            }
            return out;
        }
    }
}
