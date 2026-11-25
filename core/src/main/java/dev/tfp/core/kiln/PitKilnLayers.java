/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (STRAW_END, LOG_START, LOG_END,
 *       LIT, useItemOn stage checks, hasFuel, strawValue),
 *       src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (isValid, tryLight checks),
 *       src/main/java/net/dries007/tfc/common/blockentities/PlacedItemBlockEntity.java @ c3b4d9536
 *       (convertPlacedItemToPitKiln: stage and straw slot),
 *       src/main/java/net/dries007/tfc/client/render/blockentity/PitKilnBlockEntityRenderer.java @ c3b4d9536 (stage > 9)
 */
package dev.tfp.core.kiln;

/**
 * The layer rules of a pit kiln. The kiln's {@code STAGE} is its layer count: stages {@code 0..7} are straw layers (a single
 * straw adds 1 stage, a bundle adds 4), and stages {@code 8..15} are logs (one stage per log). Stage {@link #LIT} is the lit
 * kiln. Pure: the caller checks the world (the fire above, the sides and the base) and passes the answers in.
 */
public final class PitKilnLayers {
    /** The last straw stage: a kiln holds at most 7 straw layers. */
    public static final int STRAW_END = 7;
    /** The first log stage. */
    public static final int LOG_START = 8;
    /** The last stage that holds fuel: 8 logs above the straw. */
    public static final int LOG_END = 15;
    /** The stage of a lit kiln (the fire is burning above it). */
    public static final int LIT = 16;
    /** Logs a kiln can hold ({@code LOG_END - STRAW_END}). */
    public static final int MAX_LOGS = LOG_END - STRAW_END;
    /** {@code PitKilnBlock.strawValue} of a single straw item. */
    public static final int STRAW_SINGLE = 1;
    /** {@code PitKilnBlock.strawValue} of a straw bundle item. */
    public static final int STRAW_BUNDLE = 4;

    private PitKilnLayers() {
    }

    /** Whether {@code stage} is an unlit kiln stage, {@code 0..LOG_END}. */
    public static boolean isUnlitStage(int stage) {
        return stage >= 0 && stage <= LOG_END;
    }

    /** Straw layers in an unlit kiln: {@code min(stage, STRAW_END)}. */
    public static int strawLayers(int stage) {
        requireUnlit(stage);
        return Math.min(stage, STRAW_END);
    }

    /** Logs in an unlit kiln: {@code max(0, stage - STRAW_END)}. */
    public static int logCount(int stage) {
        requireUnlit(stage);
        return Math.max(0, stage - STRAW_END);
    }

    /** The stage of a kiln with {@code logs} logs on top of the full straw layer. */
    public static int stageForLogs(int logs) {
        if (logs < 0 || logs > MAX_LOGS) {
            throw new IllegalArgumentException("logs must be in [0, " + MAX_LOGS + "]: " + logs);
        }
        return STRAW_END + logs;
    }

    /**
     * TFC's straw rule: a straw item of value {@code strawValue} fits when {@code stage <= STRAW_END - strawValue}. The lit
     * kiln and any kiln with logs never takes straw.
     */
    public static boolean canAddStraw(int stage, int strawValue) {
        if (strawValue != STRAW_SINGLE && strawValue != STRAW_BUNDLE) {
            throw new IllegalArgumentException("strawValue must be 1 or 4: " + strawValue);
        }
        return isUnlitStage(stage) && stage <= STRAW_END - strawValue;
    }

    /** TFC's log rule: a log goes on from the top straw layer, up to {@code LOG_END}. */
    public static boolean canAddLog(int stage) {
        return isUnlitStage(stage) && stage >= STRAW_END && stage < LOG_END;
    }

    /** The stage after one straw item of value {@code strawValue} is added (the caller checks {@link #canAddStraw}). */
    public static int stageAfterStraw(int stage, int strawValue) {
        if (!canAddStraw(stage, strawValue)) {
            throw new IllegalArgumentException("straw of value " + strawValue + " does not fit at stage " + stage);
        }
        return stage + strawValue;
    }

    /** The stage after one log is added (the caller checks {@link #canAddLog}). */
    public static int stageAfterLog(int stage) {
        if (!canAddLog(stage)) {
            throw new IllegalArgumentException("a log does not fit at stage " + stage);
        }
        return stage + 1;
    }

    /** TFC's {@code hasFuel}: the kiln holds its full 8 logs. */
    public static boolean hasFuel(int stage) {
        return stage == LOG_END;
    }

    /** Whether the stage is a valid layer count: unlit stages only, with the straw under the logs. */
    public static boolean isValidStage(int stage) {
        return isUnlitStage(stage);
    }

    /**
     * TFC's ignition check ({@code PitKilnBlockEntity.tryLight}): full fuel, an unlit kiln, a valid structure, fire that can
     * be placed above, and the four sides sturdy.
     *
     * @param stage            the stage
     * @param lit              whether the kiln is already lit
     * @param structureValid   {@link #structureValid}
     * @param firePlaceable    {@code BaseFireBlock.canBePlacedAt} above the kiln
     * @param sidesSturdy      all four horizontal faces are sturdy (no flammability check, as in {@code tryLight})
     */
    public static boolean canLight(int stage, boolean lit, boolean structureValid, boolean firePlaceable, boolean sidesSturdy) {
        return hasFuel(stage) && !lit && structureValid && firePlaceable && sidesSturdy;
    }

    /**
     * TFC's {@code isValid}: every horizontal face is sturdy and not flammable, and the base is sturdy.
     *
     * @param sidesSturdyNotFlammable all four horizontal faces are sturdy and not flammable
     * @param baseSturdy              the block below has a sturdy top face
     */
    public static boolean structureValid(boolean sidesSturdyNotFlammable, boolean baseSturdy) {
        return sidesSturdyNotFlammable && baseSturdy;
    }

    /**
     * TFC {@code strawValue(item)}: 4 for {@code #tfc:pit_kiln_4_straw}, 1 for {@code #tfc:pit_kiln_straw}, else 0 (the
     * 4-straw tag is checked first).
     */
    public static int strawValue(boolean fourStraw, boolean straw) {
        return fourStraw ? STRAW_BUNDLE : straw ? STRAW_SINGLE : 0;
    }

    /**
     * TFC {@code convertPlacedItemToPitKiln}: the stage of the kiln a straw item of {@code strawValue} makes from a placed
     * item block ({@code strawValue - 1}); the straw goes into straw slot {@code stage}.
     */
    public static int stageFromPlaced(int strawValue) {
        if (strawValue != STRAW_SINGLE && strawValue != STRAW_BUNDLE) {
            throw new IllegalArgumentException("strawValue must be 1 or 4: " + strawValue);
        }
        return strawValue - 1;
    }

    /** TFC {@code useItemOn}: straw added at {@code stage} goes into straw slot {@code stage + strawValue}. */
    public static int strawSlotAfterAdd(int stage, int strawValue) {
        return stageAfterStraw(stage, strawValue);
    }

    /** TFC {@code useItemOn}: a log added at {@code stage} goes into log slot {@code stage - LOG_START + 1}. */
    public static int logSlotAfterAdd(int stage) {
        if (!canAddLog(stage)) {
            throw new IllegalArgumentException("a log does not fit at stage " + stage);
        }
        return stage - LOG_START + 1;
    }

    /**
     * What an empty-hand click on an unlit kiln takes (TFC {@code useItemOn} with an empty hand): from stage
     * {@code LOG_START} up the log of log slot {@code stage - LOG_START}, which removes one stage; below it the straw of
     * straw slot {@code stage}, which removes as many stages as its straw value (0 for an empty slot).
     *
     * @param log  whether a log slot is taken (else a straw slot)
     * @param slot the log or straw slot index ({@code 0..7})
     */
    public record Take(boolean log, int slot) {
    }

    /** The slot an empty-hand click takes at {@code stage}; the lit kiln gives nothing (empty). */
    public static java.util.Optional<Take> take(int stage) {
        if (!isUnlitStage(stage)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(stage >= LOG_START ? new Take(true, stage - LOG_START) : new Take(false, stage));
    }

    /**
     * The stage after taking {@code stagesToRemove} stages at {@code stage}, or {@code -1} when the kiln turns back into a
     * placed item block (TFC: {@code stage == stagesToRemove - 1}). A result below 0 that TFC would reject as a block
     * state value also returns {@code -1}.
     */
    public static int stageAfterTake(int stage, int stagesToRemove) {
        requireUnlit(stage);
        if (stagesToRemove < 0) {
            throw new IllegalArgumentException("stagesToRemove must be >= 0: " + stagesToRemove);
        }
        if (stage == stagesToRemove - 1) {
            return -1;
        }
        int next = stage - stagesToRemove;
        return next < 0 ? -1 : next;
    }

    /** TFC {@code PitKilnBlockEntityRenderer}: the contents are drawn up to stage 9 and hidden above. */
    public static boolean contentsVisible(int stage) {
        return stage <= 9;
    }

    private static void requireUnlit(int stage) {
        if (!isUnlitStage(stage)) {
            throw new IllegalArgumentException("stage must be in [0, " + LOG_END + "]: " + stage);
        }
    }
}
