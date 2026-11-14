/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/PowderkegBlockEntity.java @ c3b4d9536 (SLOTS, MAX_STRENGTH,
 *       getStrength), src/main/java/net/dries007/tfc/util/PowderKegExplosion.java @ c3b4d9536 (finalizeExplosion: the
 *       explosion_proof / powderkeg_breaking_blocks filter), src/main/java/net/dries007/tfc/common/TFCTags.java @ c3b4d9536
 *       (POWDERKEG_CANNOT_BREAK, POWDERKEG_CAN_BREAK, POWDER_KEG_FUEL)
 */
package dev.tfp.core.powderkeg;

import java.util.List;

/**
 * PAR-PROC-18: TFC's powderkeg numbers. Pure, any thread.
 */
public final class PowderkegRules {
    /** TFC {@code PowderkegBlockEntity.SLOTS}: 4 columns by 3 rows. */
    public static final int SLOTS = 12;
    /** TFC {@code PowderkegBlockEntity.MAX_STRENGTH}: every explosion is capped to this size. */
    public static final int MAX_STRENGTH = 64;
    /** TFC {@code TFCTags.Items.POWDER_KEG_FUEL}: what the slots accept. */
    public static final String FUEL_TAG = "tfc:usable_in_powder_keg";
    /** TFC {@code TFCTags.Blocks.POWDERKEG_CANNOT_BREAK}. */
    public static final String CANNOT_BREAK_TAG = "tfc:explosion_proof";
    /** TFC {@code TFCTags.Blocks.POWDERKEG_CAN_BREAK} (the only blocks broken with {@code powderKegOnlyBreaksNaturalBlocks}). */
    public static final String CAN_BREAK_TAG = "tfc:powderkeg_breaking_blocks";

    private PowderkegRules() {
    }

    /**
     * TFC {@code getStrength(int itemCount)}: {@code min(MAX_STRENGTH, floor(modifier * itemCount / SLOTS))}, the product
     * in double as TFC's {@code Supplier<Double>} config value makes it.
     */
    public static int strength(int itemCount, double modifier) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount < 0: " + itemCount);
        }
        return Math.min(MAX_STRENGTH, (int) Math.floor(modifier * itemCount / SLOTS));
    }

    /** TFC {@code getStrength(IItemHandler)}: the sum of every slot's count. */
    public static int strength(List<Integer> slotCounts, double modifier) {
        int count = 0;
        for (int c : slotCounts) {
            count += c;
        }
        return strength(count, modifier);
    }

    /**
     * TFC {@code PowderKegExplosion.finalizeExplosion}'s filter of one affected block: with
     * {@code onlyBreaksNaturalBlocks} only {@code #tfc:powderkeg_breaking_blocks} break, otherwise everything but
     * {@code #tfc:explosion_proof}.
     */
    public static boolean breaks(boolean onlyBreaksNaturalBlocks, boolean explosionProof, boolean powderkegBreaking) {
        return onlyBreaksNaturalBlocks ? powderkegBreaking : !explosionProof;
    }
}
