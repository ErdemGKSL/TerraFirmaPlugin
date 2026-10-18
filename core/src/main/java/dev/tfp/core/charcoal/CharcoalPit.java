/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/BurningLogPileBlockEntity.java @ c3b4d9536 (serverTick, createCharcoal,
 *       getCharcoalAmount),
 *       src/main/java/net/dries007/tfc/common/blockentities/TickCounterBlockEntity.java @ c3b4d9536 (getTicksSinceUpdate, resetCounter),
 *       src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (charcoalTicks),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (LAYERS, updateShape layers)
 */
package dev.tfp.core.charcoal;

import java.util.ArrayList;
import java.util.List;

/**
 * The burn and conversion rules of a charcoal pit. A lit log pile becomes a burning pile, and the burning pile turns into
 * charcoal once its calendar ticks since it was lit exceed {@code charcoalTicks}. Nothing ticks: the caller evaluates the
 * phase at any tick ({@link #hasConverted}, {@link #convertTick}), and the conversion of a whole column (the burning piles
 * under the top one) is {@link #convert}. Pure: the caller reads the world, passes the logs and the random floats in, and
 * writes the blocks it is told to.
 *
 * <p>The charcoal does not depend on the wood: {@code getCharcoalAmount} uses the pile's log count only, so every accepted
 * log burns the same way.
 */
public final class CharcoalPit {
    /** TFC's {@code charcoalTicks} default: 18000 calendar ticks, 15 hours. */
    public static final int DEFAULT_CHARCOAL_TICKS = 18000;
    /** The most layers a charcoal pile block holds ({@code CharcoalPileBlock.LAYERS}, 1..8; 8 is a full block). */
    public static final int MAX_LAYERS = 8;
    /** The "never" tick: a pit that cannot convert (see {@link #convertTick}). */
    public static final long NEVER = Long.MAX_VALUE;

    private CharcoalPit() {
    }

    /** A source of the floats TFC draws with {@code RandomSource.nextFloat()}, each in {@code [0, 1)}. */
    @FunctionalInterface
    public interface FloatSource {
        float nextFloat();
    }

    /**
     * The result of converting a column of burning piles.
     *
     * @param converted   whether the column converted (false when a pile sits above it, the TFC early return)
     * @param layers      the charcoal layers of each burning pile's cell, bottom first ({@code 0} is air); empty when not converted
     * @param layersBelow the layers of the charcoal pile under the column after the conversion, or the input value when none
     * @param lost        the charcoal that did not fit in the cells (TFC drops it)
     */
    public record Conversion(boolean converted, List<Integer> layers, int layersBelow, int lost) {
        public Conversion {
            layers = List.copyOf(layers);
        }
    }

    /**
     * TFC's conversion test in {@code serverTick}: a pile lit at {@code litTick} has converted by {@code now} when its
     * {@code lastUpdateTick} is positive and {@code now - litTick > charcoalTicks}. The positive check is TFC's own guard, so a
     * pile lit at calendar tick 0 or earlier never converts.
     */
    public static boolean hasConverted(long now, long litTick, int charcoalTicks) {
        if (now < litTick) {
            throw new IllegalArgumentException("now " + now + " is before the lit tick " + litTick);
        }
        return litTick > 0 && now - litTick > charcoalTicks;
    }

    /**
     * The first tick at which {@link #hasConverted} is true, or {@link #NEVER} when a pile lit at {@code litTick} never
     * converts. It is {@code litTick + max(0, charcoalTicks + 1)}.
     */
    public static long convertTick(long litTick, int charcoalTicks) {
        if (litTick <= 0) {
            return NEVER;
        }
        return litTick + Math.max(0L, (long) charcoalTicks + 1L);
    }

    /** The ticks left before the pile converts at {@code now}, {@code >= 0}, or {@link #NEVER}. */
    public static long remainingTicks(long now, long litTick, int charcoalTicks) {
        long convert = convertTick(litTick, charcoalTicks);
        if (convert == NEVER) {
            return NEVER;
        }
        return Math.max(0L, convert - now);
    }

    /**
     * TFC {@code getCharcoalAmount}: {@code (int) Mth.clamp(logs * (0.25 + 0.25 * nextFloat()), 0, 8)}, computed in the same
     * double arithmetic.
     *
     * @param logs the pile's log count, {@code 0..16}
     * @param roll a {@code nextFloat()} value in {@code [0, 1)}
     */
    public static int charcoalFromLogs(int logs, float roll) {
        if (logs < 0) {
            throw new IllegalArgumentException("logs must be >= 0: " + logs);
        }
        if (!(roll >= 0f && roll < 1f)) {
            throw new IllegalArgumentException("roll must be in [0, 1): " + roll);
        }
        double amount = logs * (0.25 + 0.25 * roll);
        return (int) Math.min((double) MAX_LAYERS, Math.max(0.0, amount));
    }

    /**
     * TFC {@code createCharcoal} for one column. The caller has already found that the column's top pile has converted, and
     * passes the column's state.
     *
     * <p>TFC returns at once when a pile sits above the top pile ({@code blockedAbove}), before any draw. Otherwise it draws one
     * float per burning pile, top first (the top pile, then the piles below it), adds up the charcoal, puts as much as fits into
     * the charcoal pile below the bottom pile (up to 8 layers), then fills the burning cells bottom first, 8 layers each, and drops
     * the rest.
     *
     * @param pilesTopFirst the log count of each burning pile in the column, top first, {@code >= 1} piles
     * @param rolls         the float source, drawn once per pile in list order (not drawn when blocked)
     * @param blockedAbove  whether a pile or charcoal pile is directly above the top pile
     * @param layersBelow   the layers of the charcoal pile under the bottom pile, or {@code -1} when there is none
     */
    public static Conversion convert(List<Integer> pilesTopFirst, FloatSource rolls, boolean blockedAbove, int layersBelow) {
        if (layersBelow < -1 || layersBelow > MAX_LAYERS) {
            throw new IllegalArgumentException("layersBelow must be -1 or in [0, " + MAX_LAYERS + "]: " + layersBelow);
        }
        if (blockedAbove) {
            return new Conversion(false, List.of(), layersBelow, 0);
        }
        if (pilesTopFirst.isEmpty()) {
            throw new IllegalArgumentException("a column has at least one burning pile");
        }
        long charcoal = 0;
        for (int logs : pilesTopFirst) {
            charcoal += charcoalFromLogs(logs, rolls.nextFloat());
        }
        int below = layersBelow;
        if (below >= 0) {
            int amount = (int) Math.min(MAX_LAYERS - below, Math.max(0L, charcoal));
            if (amount > 0) {
                charcoal -= amount;
                below += amount;
            }
        }
        // TFC fills from the bottom cell upwards, so the list is bottom first in the order it is filled.
        List<Integer> cells = new ArrayList<>(pilesTopFirst.size());
        for (int i = 0; i < pilesTopFirst.size(); i++) {
            if (charcoal > 0) {
                int amount = (int) Math.min(MAX_LAYERS, charcoal);
                charcoal -= amount;
                cells.add(amount);
            } else {
                cells.add(0);
            }
        }
        return new Conversion(true, cells, below, (int) charcoal);
    }
}
