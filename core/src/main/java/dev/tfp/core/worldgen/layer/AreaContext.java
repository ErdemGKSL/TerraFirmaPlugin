/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/layer/framework/AreaContext.java @ c3b4d9536
 */
package dev.tfp.core.worldgen.layer;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * The seeded random and the per-position seeding of one layer chain. Owned by one {@link Area} at a time: not thread-safe.
 */
public final class AreaContext {
    private final long seed;
    private final XoroshiroRandomSource random;

    public AreaContext(long seed) {
        this.seed = LayerMath.murmurHash3(seed);
        this.random = new XoroshiroRandomSource(seed);
    }

    public XoroshiroRandomSource random() {
        return random;
    }

    /** Re-seeds the random from the position, so each output depends only on the layer seed and (x, z). */
    public void setSeed(long x, long z) {
        random.setSeed(((x * 501125321L) ^ (z * 1136930381L) ^ seed) * 0x27d4eb2dL);
    }

    public int choose(int first, int second) {
        return random.nextBoolean() ? first : second;
    }

    public int choose(int first, int second, int third, int fourth) {
        return switch (random.nextInt(4)) {
            case 0 -> first;
            case 1 -> second;
            case 2 -> third;
            default -> fourth;
        };
    }

    public int choose(int[] choices) {
        return choices[random.nextInt(choices.length)];
    }

    public <A> A choose(A first, A second) {
        return random.nextBoolean() ? first : second;
    }

    public <A> A choose(A first, A second, A third, A fourth) {
        return switch (random.nextInt(4)) {
            case 0 -> first;
            case 1 -> second;
            case 2 -> third;
            default -> fourth;
        };
    }

    public <A> A choose(A[] choices) {
        return choices[random.nextInt(choices.length)];
    }
}
