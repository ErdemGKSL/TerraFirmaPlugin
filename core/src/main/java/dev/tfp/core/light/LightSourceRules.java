/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/TFCTorchBlock.java @ c3b4d9536 (onRandomTick),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCCandleBlock.java @ c3b4d9536 (onRandomTick, LIGHTING_SCALE),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (getTicksSinceUpdate),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (torch, wall torch, candle cake light levels),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (torchTicks, candleTicks defaults)
 */
package dev.tfp.core.light;

/**
 * Torch and candle burn time and light level ({@code TFCTorchBlock}, {@code TFCCandleBlock}). Pure and lazy: nothing ticks.
 *
 * <p>A torch or candle records the calendar tick it was placed or lit at (TFC's {@code TickCounterBlockEntity}). The
 * elapsed time is {@code now - litTick}, and the source has burnt out once that elapsed time is strictly greater than the
 * configured limit. A limit of {@code -1} (or any limit that is not above 0) disables burnout. TFC checks this on random
 * ticks, but the answer depends only on the elapsed time, so a lazy evaluation at any moment gives the same answer as a
 * stepped one.
 */
public final class LightSourceRules {
    /** TFC {@code torchTicks} default: 72 in-game hours of 1200 ticks each. */
    public static final int DEFAULT_TORCH_TICKS = 86_400;
    /** TFC {@code candleTicks} default: 240 in-game hours of 1200 ticks each. */
    public static final int DEFAULT_CANDLE_TICKS = 288_000;
    /** The light a lit torch (standing or wall) emits. */
    public static final int TORCH_LIGHT = 14;
    /** The light a lit candle cake emits. */
    public static final int CANDLE_CAKE_LIGHT = 3;
    /** The most candles one candle block holds (vanilla {@code CANDLES}). */
    public static final int MAX_CANDLES = 4;
    /** Ticks reported for a source that never burns out. */
    public static final long NEVER = Long.MAX_VALUE;

    private LightSourceRules() {
    }

    /**
     * TFC {@code torchTicks > 0 && getTicksSinceUpdate() > torchTicks}: whether a lit torch or candle has burnt out.
     *
     * @param ticksSinceUpdate calendar ticks since it was placed or lit
     * @param limitTicks       the configured limit; {@code -1} (or any value {@code <= 0}) means it never burns out
     */
    public static boolean burnedOut(long ticksSinceUpdate, int limitTicks) {
        return limitTicks > 0 && ticksSinceUpdate > limitTicks;
    }

    /**
     * The burn time left, lazily: {@code limit - elapsed}, clamped at 0. It reaches 0 at the limit; the source is burnt out
     * one tick later (see {@link #burnedOut}). A source that never burns out reports {@link #NEVER}.
     */
    public static long remainingTicks(long ticksSinceUpdate, int limitTicks) {
        if (limitTicks <= 0) {
            return NEVER;
        }
        return Math.max(0L, limitTicks - ticksSinceUpdate);
    }

    /** TFC torch and wall torch {@code lightLevel(14)} while lit, and 0 when the torch is dead ({@code DeadTorchBlock}). */
    public static int torchLight(boolean lit) {
        return lit ? TORCH_LIGHT : 0;
    }

    /** TFC {@code LIGHTING_SCALE}: {@code lit ? 3 * candles + 2 : 0}. Stage 1 gives 5, stage 4 gives 14. */
    public static int candleLight(int candles, boolean lit) {
        if (candles < 1 || candles > MAX_CANDLES) {
            throw new IllegalArgumentException("candles must be in [1, " + MAX_CANDLES + "]: " + candles);
        }
        return lit ? 3 * candles + 2 : 0;
    }

    /** TFC candle cake {@code litBlockEmission(3)}. */
    public static int candleCakeLight(boolean lit) {
        return lit ? CANDLE_CAKE_LIGHT : 0;
    }
}
