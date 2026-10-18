/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/ComposterBlockEntity.java @ c3b4d9536 (MAX_AMOUNT, getReadyTicks,
 * getRottenCount, the stage in finishUse), src/main/java/net/dries007/tfc/config/ServerConfig.java @ c3b4d9536 (composterTicks
 * default 288000)
 */
package dev.tfp.core.compost;

/**
 * The composter's constants and pure arithmetic. The climate (rainfall) and the neighbouring composters are plain inputs:
 * the Paper glue samples them at the moment it settles a bin. Every float operation runs in {@code float}, as in TFC, so the
 * truncations match TFC's casts.
 */
public final class CompostRules {

    /** The most green or brown amount a bin holds (TFC's {@code MAX_AMOUNT}). */
    public static final int MAX_AMOUNT = 16;

    /** TFC's default {@code composterTicks}, in calendar ticks (10 game days). */
    public static final long DEFAULT_READY_TICKS = 288_000L;

    /** TFC's initial {@code lastUpdateTick} ({@code Integer.MIN_VALUE}): a bin never filled reads as updated long ago. */
    public static final long NEVER = Integer.MIN_VALUE;

    /** The result of {@link CompostBin#ripenedAt(long)} for a bin that does not ripen by itself. */
    public static final long NOT_RIPENING = Long.MAX_VALUE;

    /** The most composters that can touch a bin (one per horizontal side). */
    public static final int MAX_NEIGHBOURS = 4;

    private CompostRules() {
    }

    /**
     * The ticks a bin needs to ripen, as TFC's {@code getReadyTicks}: the base scaled by a rainfall trapezoid (dry below 150,
     * wet above 350, one step per 50 units), then by 1.05 for each neighbouring composter. Each neighbour truncates the
     * running value to a {@code long}, as the compound assignment does in TFC.
     *
     * @param baseTicks         the {@code composterTicks} config, {@code >= 0}
     * @param rainfall          the average rainfall at the bin, in the units of {@code Climate.getAverageRainfall}
     * @param adjacentComposters the composters on the horizontal sides, {@code 0..4}
     * @return the ready ticks
     */
    public static long readyTicks(long baseTicks, float rainfall, int adjacentComposters) {
        if (baseTicks < 0) {
            throw new IllegalArgumentException("baseTicks must be >= 0: " + baseTicks);
        }
        if (adjacentComposters < 0 || adjacentComposters > MAX_NEIGHBOURS) {
            throw new IllegalArgumentException("adjacentComposters must be 0..4: " + adjacentComposters);
        }
        long ready = baseTicks;
        if (rainfall < 150f) {
            ready *= (long) ((150f - rainfall) / 50f + 1f);
        } else if (rainfall > 350f) {
            ready *= (long) ((rainfall - 350f) / 50f + 1f);
        }
        for (int i = 0; i < adjacentComposters; i++) {
            ready = (long) (ready * 1.05f);
        }
        return ready;
    }

    /**
     * The block-state stage TFC shows for a bin: {@code (green + brown) / 4}, and at least 1 once anything is inside.
     *
     * @param green the green amount, {@code 0..16}
     * @param brown the brown amount, {@code 0..16}
     * @return the stage, {@code 0..8}
     */
    public static int stage(int green, int brown) {
        checkAmount(green, "green");
        checkAmount(brown, "brown");
        final int sum = green + brown;
        final int stage = sum / 4;
        return sum > 0 ? Math.max(stage, 1) : 0;
    }

    /**
     * The rotten compost a rotten bin yields: {@code min(green, brown) / 4}, as TFC's {@code getRottenCount}.
     *
     * @param green the green amount, {@code 0..16}
     * @param brown the brown amount, {@code 0..16}
     * @return the rotten count, {@code 0..4}
     */
    public static int rottenCount(int green, int brown) {
        checkAmount(green, "green");
        checkAmount(brown, "brown");
        return Math.min(green, brown) / 4;
    }

    static void checkAmount(int amount, String name) {
        if (amount < 0 || amount > MAX_AMOUNT) {
            throw new IllegalArgumentException(name + " must be 0..16: " + amount);
        }
    }
}
