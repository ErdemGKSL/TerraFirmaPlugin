/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/PitKilnBlock.java @ c3b4d9536 (STRAW_END, LOG_START, LOG_END,
 *       LIT, useItemOn stage checks, hasFuel, strawValue),
 *       src/main/java/net/dries007/tfc/common/blockentities/PitKilnBlockEntity.java @ c3b4d9536 (isValid, tryLight checks)
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

    private static void requireUnlit(int stage) {
        if (!isUnlitStage(stage)) {
            throw new IllegalArgumentException("stage must be in [0, " + LOG_END + "]: " + stage);
        }
    }
}
