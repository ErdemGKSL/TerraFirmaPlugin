/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (serverTick and
 *       clientTick, replayed tick by tick below), src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java
 *       @ c3b4d9536 (useItemOn)
 */
package dev.tfp.core.windmill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.power.Windmill;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class WindmillSampleTest {

    /** Lazy-vs-stepped: with the count and wind held between checks, one closed-form step equals 40 TFC ticks. */
    @Test
    void lazyChecksMatchTheSteppedTicks() {
        Random random = new Random(7);
        float speed = 0f;
        boolean obstructed = false;
        WindmillSample lazy = WindmillSample.FRESH;
        for (int i = 0; i < 300; i++) {
            int count = 1 + random.nextInt(5);
            float wind = random.nextFloat();
            boolean obstructedNow = random.nextInt(10) == 0;
            float target = Windmill.targetSpeed(count, wind);
            for (int t = 0; t < 40; t++) {
                speed = obstructed ? 0f
                    : target > speed ? Math.min(target, speed + Windmill.LERP_SPEED) : Math.max(target, speed - Windmill.LERP_SPEED);
            }
            obstructed = obstructedNow;
            lazy = lazy.sample(40, count, wind, obstructedNow);
            assertThat(lazy.speed()).as("check %d", i).isCloseTo(speed, within(1e-5f));
            assertThat(lazy.obstructed()).isEqualTo(obstructed);
        }
    }

    /**
     * Five blades in a 0.5 wind (TFC caps the wind there): target = 2 * (MAX_SPEED + MIN_SPEED) = 2 * 2 pi / 160 rad/t, one
     * turn every 80 ticks. From rest it takes target / LERP_SPEED = 500 ticks, so 13 checks of 40 ticks reach it.
     */
    @Test
    void fiveBladesInStrongWindReachTheTopSpeed() {
        float top = Windmill.targetSpeed(5, 0.9f);
        assertThat(top).isCloseTo((float) (2 * Math.PI / 80), within(1e-6f));
        WindmillSample s = WindmillSample.FRESH;
        for (int i = 0; i < 12; i++) {
            s = s.sample(40, 5, 0.9f, false);
        }
        assertThat(s.speed()).isLessThan(top);
        s = s.sample(40, 5, 0.9f, false);
        assertThat(s.speed()).isEqualTo(top);
    }

    /** One blade: TFC maps count 1 to 0, and adds MIN_SPEED only for more than one blade, so a single blade never turns. */
    @Test
    void oneBladeStaysStill() {
        WindmillSample s = WindmillSample.FRESH;
        for (int i = 0; i < 20; i++) {
            s = s.sample(40, 1, 1f, false);
        }
        assertThat(s.speed()).isZero();
    }

    @Test
    void unreadableDiskKeepsTheFlag() {
        assertThat(new WindmillSample(0.01f, true).sample(40, 3, 0.4f, null)).isEqualTo(new WindmillSample(0f, true));
        assertThat(new WindmillSample(0f, false).sample(40, 3, 0.4f, null).obstructed()).isFalse();
    }

    /** TFC useItemOn: insert below five with an item, extract at five or with an empty hand, a non-blade passes. */
    @Test
    void bladeClicks() {
        assertThat(WindmillBlades.click(1, false, true)).isEqualTo(WindmillBlades.Click.INSERT);
        assertThat(WindmillBlades.click(4, false, true)).isEqualTo(WindmillBlades.Click.INSERT);
        assertThat(WindmillBlades.click(3, false, false)).isEqualTo(WindmillBlades.Click.PASS);
        assertThat(WindmillBlades.click(5, false, true)).isEqualTo(WindmillBlades.Click.EXTRACT);
        assertThat(WindmillBlades.click(5, false, false)).isEqualTo(WindmillBlades.Click.EXTRACT);
        assertThat(WindmillBlades.click(2, true, false)).isEqualTo(WindmillBlades.Click.EXTRACT);
        assertThat(WindmillBlades.extractSlot(5)).isEqualTo(4);
        assertThat(WindmillBlades.count(List.of(true, true, false, false, false))).isEqualTo(2);
        assertThat(WindmillBlades.count(List.of(false, false, false, false, false))).isZero();
    }

    /** TFC WindmillBladeItem.useOn: only a horizontal axle, and only with a clear disk. */
    @Test
    void bladeOnAxle() {
        assertThat(WindmillBlades.convert("x", false)).isEqualTo(WindmillBlades.Conversion.CONVERT);
        assertThat(WindmillBlades.convert("z", true)).isEqualTo(WindmillBlades.Conversion.NOT_ENOUGH_SPACE);
        assertThat(WindmillBlades.convert("y", false)).isEqualTo(WindmillBlades.Conversion.PASS);
    }
}
