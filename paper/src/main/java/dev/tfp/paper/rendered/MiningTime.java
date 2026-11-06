/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/Metal.java @ c3b4d9536
 * (block strength 5/6 and requiresCorrectToolForDrops of Metal.BlockType.blockProperties; tool speeds of
 * common/TFCTiers.java; the progress rule is vanilla's BlockBehaviour.getDestroyProgress, which TFC keeps)
 */
package dev.tfp.paper.rendered;

/**
 * PAR-INFRA-32: the break time TFC (vanilla's rule) gives a block, counted in server ticks since the dig started.
 * Progress per tick is {@code speed / hardness / (correctForDrops ? 30 : 100)}; the block breaks on the first tick at
 * which the {@code float} sum reaches 1, as the vanilla client ({@code MultiPlayerGameMode.continueDestroyBlock}) and
 * CraftEngine ({@code BukkitServerPlayer.tickBlockDestroy}) accumulate it. Summing loses a little: a copper block by
 * hand is 500 ticks in exact arithmetic and 501 summed, stone by hand 150 and 151.
 */
public final class MiningTime {
    /** TFC {@code Metal.BlockType.blockProperties}: {@code strength(5, 6)}. */
    public static final float METAL_BLOCK_HARDNESS = 5f;
    /** TFC {@code TFCTiers.BRONZE} mining speed. */
    public static final float BRONZE_SPEED = 7.3f;

    private MiningTime() {
    }

    /**
     * Progress per server tick.
     *
     * @param hardness the block's destroy time ({@code -1} unbreakable)
     * @param speed the held item's destroy speed on this block (1 for an empty hand or a tool of another kind)
     * @param requiresCorrectTool the block's {@code requiresCorrectToolForDrops}
     * @param correctTool whether the held item is correct for the block's drops
     */
    public static float progressPerTick(float hardness, float speed, boolean requiresCorrectTool, boolean correctTool) {
        if (hardness == -1f) {
            return 0f;
        }
        int divisor = !requiresCorrectTool || correctTool ? 30 : 100;
        return speed / hardness / divisor;
    }

    /** Server ticks from the dig start to the break (1 for an instant break), or -1 for an unbreakable block. */
    public static int ticksToBreak(float hardness, float speed, boolean requiresCorrectTool, boolean correctTool) {
        float perTick = progressPerTick(hardness, speed, requiresCorrectTool, correctTool);
        if (perTick <= 0f) {
            return -1;
        }
        float progress = 0f;
        int ticks = 0;
        while (progress < 1f) {
            progress += perTick;
            ticks++;
        }
        return ticks;
    }
}
