/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.worldgen.layer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the layer helpers and for the random the framework draws from. The murmur hash, mix, packed chunk
 * positions, power-of-two rounding and the {@link AreaContext} draws were printed by TFC's own code at c3b4d9536:
 * fastutil's {@code HashCommon} (fastutil 8.5.15), {@code Helpers.packChunkPos}, {@code Mth.smallestEncompassingPowerOfTwo}
 * and {@code AreaContext} on Minecraft 1.21.11's {@code XoroshiroRandomSource}, in the same scratch harness as
 * {@link LayerGoldenTest}. Exact comparisons.
 */
class LayerHelpersGoldenTest {
    @Test
    void murmurHash3MatchesTfc() {
        assertThat(LayerMath.murmurHash3(0L)).isEqualTo(0L);
        assertThat(LayerMath.murmurHash3(42L)).isEqualTo(-9148929187392628276L);
        assertThat(LayerMath.murmurHash3(-7L)).isEqualTo(4818444927845109502L);
        assertThat(LayerMath.murmurHash3(1234567890123L)).isEqualTo(5768311928185057474L);
    }

    @Test
    void packChunkPosAndMixMatchTfc() {
        assertThat(LayerMath.packChunkPos(0, 0)).isEqualTo(0L);
        assertThat(LayerMath.mix(0L)).isEqualTo(0L);
        assertThat(LayerMath.packChunkPos(3, -5)).isEqualTo(-21474836477L);
        assertThat(LayerMath.mix(-21474836477L)).isEqualTo(6787591978171127569L);
        assertThat(LayerMath.packChunkPos(-1000, 77)).isEqualTo(335007448088L);
        assertThat(LayerMath.mix(335007448088L)).isEqualTo(-4611987069139851583L);
        assertThat(LayerMath.packChunkPos(123456, -654321)).isEqualTo(-2810287295962560L);
        assertThat(LayerMath.mix(-2810287295962560L)).isEqualTo(-4068563778861395534L);
    }

    @Test
    void smallestEncompassingPowerOfTwoMatchesMth() {
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(0)).isEqualTo(0);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(1)).isEqualTo(1);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(2)).isEqualTo(2);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(3)).isEqualTo(4);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(1000)).isEqualTo(1024);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(1024)).isEqualTo(1024);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(1025)).isEqualTo(2048);
        assertThat(LayerMath.smallestEncompassingPowerOfTwo(1 << 20)).isEqualTo(1 << 20);
    }

    /** {@code new AreaContext(seed)}, then {@code setSeed(3, -5)}: the first draws of the context random. */
    @Test
    void contextDrawsMatchTfcSeed0() {
        final AreaContext context = new AreaContext(0L);
        context.setSeed(3, -5);
        assertThat(context.random().nextLong()).isEqualTo(2995329804460575536L);
        assertThat(context.random().nextLong()).isEqualTo(5247938325419749867L);
        assertThat(context.random().nextLong()).isEqualTo(6889978607558822169L);
        assertThat(context.random().nextInt(10)).isEqualTo(3);
        assertThat(context.random().nextInt(10)).isEqualTo(4);
        assertThat(context.random().nextInt(10)).isEqualTo(9);
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextInt()).isEqualTo(95717515);
        assertThat(context.random().nextInt()).isEqualTo(107269542);
        context.setSeed(-17, 1000);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
        assertThat(context.random().nextInt(4)).isEqualTo(1);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
    }

    @Test
    void contextDrawsMatchTfcSeed42() {
        final AreaContext context = new AreaContext(42L);
        context.setSeed(3, -5);
        assertThat(context.random().nextLong()).isEqualTo(-3385418181737664126L);
        assertThat(context.random().nextLong()).isEqualTo(-6449591727804398456L);
        assertThat(context.random().nextLong()).isEqualTo(-1245366119771304436L);
        assertThat(context.random().nextInt(10)).isEqualTo(5);
        assertThat(context.random().nextInt(10)).isEqualTo(7);
        assertThat(context.random().nextInt(10)).isEqualTo(7);
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextInt()).isEqualTo(2122600444);
        assertThat(context.random().nextInt()).isEqualTo(-28687925);
        context.setSeed(-17, 1000);
        assertThat(context.random().nextInt(4)).isEqualTo(2);
        assertThat(context.random().nextInt(4)).isEqualTo(0);
        assertThat(context.random().nextInt(4)).isEqualTo(1);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
    }

    @Test
    void contextDrawsMatchTfcSeedNeg7() {
        final AreaContext context = new AreaContext(-7L);
        context.setSeed(3, -5);
        assertThat(context.random().nextLong()).isEqualTo(-9002795033008449373L);
        assertThat(context.random().nextLong()).isEqualTo(-8124179841676273132L);
        assertThat(context.random().nextLong()).isEqualTo(-7131588634763516053L);
        assertThat(context.random().nextInt(10)).isEqualTo(9);
        assertThat(context.random().nextInt(10)).isEqualTo(2);
        assertThat(context.random().nextInt(10)).isEqualTo(6);
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextInt()).isEqualTo(1578194149);
        assertThat(context.random().nextInt()).isEqualTo(1106692721);
        context.setSeed(-17, 1000);
        assertThat(context.random().nextInt(4)).isEqualTo(2);
        assertThat(context.random().nextInt(4)).isEqualTo(1);
        assertThat(context.random().nextInt(4)).isEqualTo(2);
        assertThat(context.random().nextInt(4)).isEqualTo(0);
    }

    @Test
    void contextDrawsMatchTfcSeedLarge() {
        final AreaContext context = new AreaContext(1234567890123L);
        context.setSeed(3, -5);
        assertThat(context.random().nextLong()).isEqualTo(8777738902306047437L);
        assertThat(context.random().nextLong()).isEqualTo(-2454442785170770002L);
        assertThat(context.random().nextLong()).isEqualTo(7342840798756669208L);
        assertThat(context.random().nextInt(10)).isEqualTo(6);
        assertThat(context.random().nextInt(10)).isEqualTo(3);
        assertThat(context.random().nextInt(10)).isEqualTo(2);
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isTrue();
        assertThat(context.random().nextBoolean()).isFalse();
        assertThat(context.random().nextInt()).isEqualTo(59783935);
        assertThat(context.random().nextInt()).isEqualTo(-593442901);
        context.setSeed(-17, 1000);
        assertThat(context.random().nextInt(4)).isEqualTo(0);
        assertThat(context.random().nextInt(4)).isEqualTo(2);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
        assertThat(context.random().nextInt(4)).isEqualTo(3);
    }
}
