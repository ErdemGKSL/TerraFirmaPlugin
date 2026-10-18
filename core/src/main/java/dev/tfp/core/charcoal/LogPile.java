/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (SLOTS, getSlotStackLimit),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (getShapeByDirByCount, insertAndPushUp),
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlockStateProperties.java @ c3b4d9536 (COUNT_1_16)
 */
package dev.tfp.core.charcoal;

/**
 * The count rules of a log pile. A pile holds {@code 1..16} logs, one per slot (TFC's slot stack limit is 1), and its
 * {@code count} block state is {@code 1..16}. A pile that empties is removed. The logs sit in four layers of four:
 * {@code LogPileBlock.getShapeByDirByCount} uses {@code i = count - 1}, layer {@code i / 4} and row {@code i % 4 + 1}.
 * Pure: the caller checks the world.
 */
public final class LogPile {
    /** TFC {@code LogPileBlockEntity.SLOTS}: the most logs one pile holds. */
    public static final int SLOTS = 16;
    /** The smallest count a placed pile can have. */
    public static final int MIN_COUNT = 1;
    /** The largest count a placed pile can have ({@code COUNT_1_16}). */
    public static final int MAX_COUNT = SLOTS;

    private LogPile() {
    }

    /** Whether {@code logs} is a count a placed pile can have, {@code 1..16}. */
    public static boolean isValidCount(int logs) {
        return logs >= MIN_COUNT && logs <= MAX_COUNT;
    }

    /** TFC's insert rule for the pile clicked: one log goes in while a slot is free. */
    public static boolean canInsert(int logs) {
        return isValidCount(logs) && logs < SLOTS;
    }

    /** A full pile sends the next log into a new pile above it ({@code insertAndPushUp}). */
    public static boolean isFull(int logs) {
        return isValidCount(logs) && logs == SLOTS;
    }

    /** The visual layer of the top log, {@code 0..3}: {@code (count - 1) / 4}. */
    public static int layer(int logs) {
        requireValid(logs);
        return (logs - 1) / 4;
    }

    /** The visual row of the top log, {@code 1..4}: {@code (count - 1) % 4 + 1}. */
    public static int row(int logs) {
        requireValid(logs);
        return (logs - 1) % 4 + 1;
    }

    private static void requireValid(int logs) {
        if (!isValidCount(logs)) {
            throw new IllegalArgumentException("a log pile holds 1 to " + MAX_COUNT + " logs: " + logs);
        }
    }
}
