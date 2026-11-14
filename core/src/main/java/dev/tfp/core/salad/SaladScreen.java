/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/SaladContainer.java @ c3b4d9536 (the slot indices,
 *       addContainerSlots: inputs at 44 + 18 i, 24, bowls at 44, 56, output at 116, 56; moveStack: shift-clicks fill
 *       the inputs, then the bowl slot),
 *       src/main/java/net/dries007/tfc/client/screen/SaladScreen.java @ c3b4d9536 (textures/gui/salad.png)
 */
package dev.tfp.core.salad;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.List;

/**
 * PAR-PROC-14: the salad screen in a 3-row chest. TFC's five input slots sit on one row at y 24, the bowl and output
 * slots 32 px lower at y 56; chest rows are 18 px apart, so the art cannot keep both distances. The inputs move up 6 px
 * onto the first chest row (columns 2 to 6, TFC's x 44 to 116 are chest columns), the bowl and output slots onto the
 * third row (TFC y 56 to chest y 54), under the same columns as TFC.
 *
 * <p>The background ({@link #BACKGROUND}, TFC's {@code textures/gui/salad.png} re-laid on those rows) is not generated
 * yet (follow-up PAR-PROC-36); until then the screen shows a plain title and filler panes, see the paper side.
 */
public final class SaladScreen {
    /** TFC slot indices. */
    public static final int SLOT_INPUT_START = 0;
    public static final int SLOT_INPUT_END = 4;
    public static final int SLOT_BOWLS = 5;
    public static final int SLOT_OUTPUT = 6;
    public static final int INPUTS = SLOT_INPUT_END - SLOT_INPUT_START + 1;

    public static final ChestGeometry CHEST = new ChestGeometry(3);

    /** Chest slot of the first input (TFC x 44 = chest column 2, row 0). */
    public static final int FIRST_INPUT_CHEST_SLOT = 2;
    /** Chest slot of the bowls (TFC x 44, third chest row). */
    public static final int BOWL_CHEST_SLOT = 2 * ChestGeometry.COLUMNS + 2;
    /** Chest slot of the salad (TFC x 116 = chest column 6, third chest row). */
    public static final int OUTPUT_CHEST_SLOT = 2 * ChestGeometry.COLUMNS + 6;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/salad", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of());

    private SaladScreen() {
    }

    /** The chest slot of TFC slot {@code tfcSlot} (0..6). */
    public static int chestSlot(int tfcSlot) {
        if (tfcSlot >= SLOT_INPUT_START && tfcSlot <= SLOT_INPUT_END) {
            return FIRST_INPUT_CHEST_SLOT + tfcSlot;
        }
        return switch (tfcSlot) {
            case SLOT_BOWLS -> BOWL_CHEST_SLOT;
            case SLOT_OUTPUT -> OUTPUT_CHEST_SLOT;
            default -> throw new IllegalArgumentException("salad slots are 0 to 6, not " + tfcSlot);
        };
    }

    /** The TFC slot of chest slot {@code chestSlot}, or -1 for a decoration slot. */
    public static int tfcSlot(int chestSlot) {
        for (int i = SLOT_INPUT_START; i <= SLOT_OUTPUT; i++) {
            if (chestSlot(i) == chestSlot) {
                return i;
            }
        }
        return -1;
    }
}
