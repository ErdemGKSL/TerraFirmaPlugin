/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/world/TFCChunkGenerator.java @ c3b4d9536 (makeBedrock)
 */
package dev.tfp.core.worldgen.settings;

import dev.tfp.core.worldgen.random.XoroshiroRandomSource;

/**
 * TFC's bedrock floor ({@code makeBedrock}): with {@code flat_bedrock} one layer at the bottom; otherwise vanilla-like
 * natural bedrock, where block {@code y} (0..5 above the bottom) is bedrock when {@code nextInt(6) < 6 - y}, drawn from
 * {@code new XoroshiroRandomSource(chunkX * 2369412341L, chunkZ * 8192836412341L)} in TFC's order (x, then z, then y).
 */
public final class BedrockLayer {
    /** The layers natural bedrock can reach, from the bottom. */
    public static final int HEIGHT = 6;

    private BedrockLayer() {}

    /**
     * @return per column {@code x + 16 * z} a bit mask of the bedrock blocks: bit {@code y} set when the block {@code y}
     *         above the bottom of the world is bedrock. Bit 0 is always set.
     */
    public static int[] columns(int chunkX, int chunkZ, boolean flat) {
        final int[] masks = new int[256];
        if (flat) {
            java.util.Arrays.fill(masks, 1);
            return masks;
        }
        final XoroshiroRandomSource random = new XoroshiroRandomSource(chunkX * 2369412341L, chunkZ * 8192836412341L);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int mask = 0;
                for (int y = 0; y < HEIGHT; y++) {
                    if (random.nextInt(6) < 6 - y) {
                        mask |= 1 << y;
                    }
                }
                masks[x + 16 * z] = mask;
            }
        }
        return masks;
    }
}
