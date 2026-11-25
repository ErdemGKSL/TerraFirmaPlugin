/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/MoldTableBlockEntity.java @ c3b4d9536 (onRightClick, isItemValid,
 *       getSlotStackLimit),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/MoldTableBlock.java @ c3b4d9536 (getAnalogOutputSignal)
 */
package dev.tfp.core.channel;

/** PAR-METAL-13: the pure rules of the mold table's right click and signal. */
public final class MoldTableRules {
    /** TFC {@code MOLD_SLOT} and {@code OUTPUT_SLOT}. */
    public static final int MOLD_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    /** TFC {@code getSlotStackLimit}: one item per slot. */
    public static final int SLOT_LIMIT = 1;
    /** TFC {@code inventory.extractItem(OUTPUT_SLOT, 99, ...)}. */
    public static final int OUTPUT_TAKE = 99;
    /** TFC {@code level.getGameTime() % 2 == 0}: a table draws every second tick. */
    public static final int DRAW_PERIOD = 2;

    private MoldTableRules() {
    }

    /** What a right click does. */
    public enum Click {
        /** The held mold goes in, the one on the table (and the output) comes out to the player. */
        SWAP,
        /** The mold (and the output) comes out to the player. */
        TAKE_MOLD,
        /** The held mold goes in. */
        INSERT,
        /** One output item comes out. */
        TAKE_OUTPUT,
        /** Nothing: the click passes to the default block interaction. */
        PASS
    }

    /**
     * TFC {@code onRightClick}: with sneaking or an empty mold slot the click works on the mold slot (swap, take or insert);
     * otherwise it takes the output.
     *
     * @param sneaking    the player is sneaking
     * @param moldPresent the mold slot holds a mold
     * @param outputPresent the output slot holds an item
     * @param heldValid   the main hand holds an item the mold slot accepts (usable in a mold table)
     */
    public static Click click(boolean sneaking, boolean moldPresent, boolean outputPresent, boolean heldValid) {
        boolean moldSlot = sneaking || !moldPresent;
        if (moldSlot) {
            if (moldPresent) {
                return heldValid ? Click.SWAP : Click.TAKE_MOLD;
            }
            return heldValid ? Click.INSERT : Click.PASS;
        }
        return outputPresent ? Click.TAKE_OUTPUT : Click.PASS;
    }

    /**
     * TFC {@code getAnalogOutputSignal}: 15 with an output, otherwise {@code 1 + 13 * amount / capacity} for a mold (0 for
     * none).
     */
    public static int signal(boolean outputPresent, boolean moldPresent, int amount, int capacity) {
        if (outputPresent) {
            return 15;
        }
        if (moldPresent && capacity > 0) {
            return 1 + 13 * amount / capacity;
        }
        return 0;
    }
}
