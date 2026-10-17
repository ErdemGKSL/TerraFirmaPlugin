/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.climate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the RNG and the sin table the climate model uses. The expected numbers were printed by calling
 * Minecraft 1.21.11's own {@code XoroshiroRandomSource}, {@code LinearCongruentialGenerator} and {@code Mth.sin} from the
 * Paper server jar (no server was started). TFC's climate model uses the same classes, so these pin the port to them.
 */
class XoroshiroGoldenTest {
    @Test
    void linearCongruentialStepMatchesMinecraft() {
        assertThat(TfcMath.lcgNext(0L, 719283741234L)).isEqualTo(719283741234L);
        assertThat(TfcMath.lcgNext(1000L, 719283741234L)).isEqualTo(-5079323217109949430L);
    }

    @Test
    void sinTableMatchesMinecraftMth() {
        assertThat(TfcMath.sin(0.0)).isZero();
        assertThat(TfcMath.sin(0.1f)).isEqualTo(0.09982981f);
        assertThat(TfcMath.sin(0.5f)).isEqualTo(0.47940964f);
        assertThat(TfcMath.sin(1.0f)).isEqualTo(0.8414514f);
        assertThat(TfcMath.sin(1.5707964f)).isEqualTo(1.0f);
        assertThat(TfcMath.sin(3.1415927f)).isEqualTo(1.2246469E-16f);
        assertThat(TfcMath.sin(-0.7f)).isEqualTo(-0.64419824f);
        assertThat(TfcMath.sin(6.2831855f)).isZero();
        assertThat(TfcMath.sin(12.345f)).isEqualTo(-0.21956894f);
    }

    @Test
    void xoroshiroSeedOneTwo() {
        final XoroshiroRandom r = new XoroshiroRandom(1L, 2L);
        assertThat(r.nextFloat()).isEqualTo(0.0f);
        assertThat(r.nextFloat()).isEqualTo(3.6239624E-5f);
        assertThat(r.nextFloat()).isEqualTo(0.09391475f);
        assertThat(r.nextFloat()).isEqualTo(0.6177128f);
        assertThat(r.nextInt(10)).isEqualTo(4);
        assertThat(r.nextInt(10)).isEqualTo(7);
        assertThat(r.nextInt(10)).isEqualTo(8);
        assertThat(r.nextIntBetweenInclusive(-3, 7)).isEqualTo(-2);
    }

    @Test
    void xoroshiroDailySeed() {
        final XoroshiroRandom r = new XoroshiroRandom(123456789L, 1986239412341L);
        assertThat(r.nextFloat()).isEqualTo(0.014113903f);
        assertThat(r.nextFloat()).isEqualTo(0.71072423f);
        assertThat(r.nextFloat()).isEqualTo(0.8820921f);
        assertThat(r.nextFloat()).isEqualTo(0.2893871f);
        assertThat(r.nextInt(10)).isEqualTo(6);
        assertThat(r.nextInt(10)).isEqualTo(3);
        assertThat(r.nextInt(10)).isEqualTo(0);
        assertThat(r.nextIntBetweenInclusive(-3, 7)).isEqualTo(4);
    }

    @Test
    void zeroSeedUsesMinecraftFallback() {
        final XoroshiroRandom r = new XoroshiroRandom(0L, 0L);
        assertThat(r.nextFloat()).isEqualTo(0.36905473f);
        assertThat(r.nextFloat()).isEqualTo(0.28597373f);
        assertThat(r.nextFloat()).isEqualTo(0.89791507f);
        assertThat(r.nextFloat()).isEqualTo(0.5944388f);
        assertThat(r.nextInt(10)).isEqualTo(6);
        assertThat(r.nextInt(10)).isZero();
        assertThat(r.nextInt(10)).isZero();
        assertThat(r.nextIntBetweenInclusive(-3, 7)).isEqualTo(3);
    }
}
