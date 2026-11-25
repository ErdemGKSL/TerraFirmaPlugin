/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/rotation/WindmillBlock.java @ c3b4d9536 (useItemOn lines 66-103),
 *       src/main/java/net/dries007/tfc/common/blockentities/rotation/WindmillBlockEntity.java @ c3b4d9536 (updateState lines
 *       130-150, getSlotStackLimit, SLOTS), src/main/java/net/dries007/tfc/common/items/WindmillBladeItem.java @ c3b4d9536
 *       (useOn: an axle that is not vertical becomes a windmill with one blade)
 */
package dev.tfp.core.windmill;

import dev.tfp.core.power.Windmill;
import java.util.List;

/**
 * The blade rules of a windmill: what a right click does, and the block state the blades give. Pure.
 */
public final class WindmillBlades {
    private WindmillBlades() {}

    /** TFC {@code tfc.tooltip.windmill_not_enough_space}: the blade item's refusal on an obstructed axle. */
    public static final String NOT_ENOUGH_SPACE = "windmill.not_enough_space";

    /** What a right click on a windmill does. */
    public enum Click {
        /** A blade goes into the first empty slot (TFC {@code insertAllSlots} of one item). */
        INSERT,
        /** The blade in slot {@code count - 1} comes out (to the player, or is discarded in creative). */
        EXTRACT,
        /** Nothing: the default block interaction runs. */
        PASS
    }

    /**
     * TFC {@code WindmillBlock.useItemOn}: with fewer than {@link Windmill#SLOTS} blades and a non-empty hand, try to insert
     * one (a stack that is not a blade does not go in, and the click passes); with all five blades or an empty hand, take the
     * last blade out.
     *
     * @param count     the {@code count} property, 1 to 5
     * @param handEmpty whether the clicking hand is empty
     * @param isBlade   whether the held stack is a windmill blade ({@code #tfc:windmill_blades}, TFC {@code isItemValid})
     */
    public static Click click(int count, boolean handEmpty, boolean isBlade) {
        if (count < Windmill.SLOTS && !handEmpty) {
            return isBlade ? Click.INSERT : Click.PASS;
        }
        if (count == Windmill.SLOTS || handEmpty) {
            return Click.EXTRACT;
        }
        return Click.PASS;
    }

    /** The slot TFC extracts from: {@code count - 1}. */
    public static int extractSlot(int count) {
        return count - 1;
    }

    /**
     * TFC {@code updateState}: the number of filled slots. 0 means the windmill turns back into its axle; otherwise it is the
     * new {@code count}.
     */
    public static int count(List<Boolean> filled) {
        int n = 0;
        for (boolean f : filled) {
            if (f) {
                n++;
            }
        }
        return n;
    }

    /**
     * Whether a blade used on an axle makes a windmill: TFC {@code WindmillBladeItem.useOn} needs an axle whose axis is not
     * vertical, and refuses with {@link #NOT_ENOUGH_SPACE} when the obstruction disk holds a solid block.
     */
    public enum Conversion { CONVERT, NOT_ENOUGH_SPACE, PASS }

    /**
     * @param axleAxis   the axle's {@code axis} property ({@code x}, {@code y}, {@code z})
     * @param obstructed {@link Windmill#isObstructed} around the axle
     */
    public static Conversion convert(String axleAxis, boolean obstructed) {
        if (!"x".equals(axleAxis) && !"z".equals(axleAxis)) {
            return Conversion.PASS;
        }
        return obstructed ? Conversion.NOT_ENOUGH_SPACE : Conversion.CONVERT;
    }
}
