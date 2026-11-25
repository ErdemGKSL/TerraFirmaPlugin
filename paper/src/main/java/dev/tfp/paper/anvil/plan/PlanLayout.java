/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/AnvilPlanScreen.java @ c3b4d9536 (init: the button grid, the arrows)
 */
package dev.tfp.paper.anvil.plan;

import dev.tfp.core.anvil.AnvilRecipePage;
import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.List;
import java.util.Map;

/**
 * PAR-METAL-04: where TFC's anvil plan screen lands in a 3-row chest. TFC's 18 plan buttons (2 rows of 9 at (7 + 18 column,
 * 17 + 18 row), 18 x 18 each) are chest slots 0 to 17 (a slot's frame is the button's); the page arrows (9 x 13 at (7, 56) and
 * (160, 56)) are the buttons on chest slots 18 (left) and 26 (right), whose art is an overlay. The numbers equal the datagen's
 * ({@code AnvilPlanArt}); {@code AnvilPlanArtTest} checks them.
 */
public final class PlanLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(3);
    public static final int LEFT_SLOT = 18;
    public static final int RIGHT_SLOT = 26;

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/anvil_plan", 0, 0, ChestGeometry.WIDTH, CHEST.height());
    public static final GuiImage BUTTONS = new GuiImage("tfp:gui/anvil_plan_buttons", 7, 17, 162, 36, 4, 5);
    public static final GuiImage ARROWS = new GuiImage("tfp:gui/anvil_plan_arrows", 7, 56, 162, 13, 2, 2);
    public static final String BUTTONS_LAYER = "buttons";
    public static final String ARROWS_LAYER = "arrows";
    public static final GuiLayers LAYERS = new GuiLayers(BACKGROUND, List.of(
        new GuiLayers.Layer(BUTTONS_LAYER, 10, BUTTONS), new GuiLayers.Layer(ARROWS_LAYER, 20, ARROWS)));

    private PlanLayout() {
    }

    /** The overlay frames of a page showing {@code onPage} plans, page {@code page} of {@code total} plans. */
    public static Map<String, Integer> frames(int onPage, int page, int total) {
        int arrows = (AnvilRecipePage.hasPrevious(page) ? 1 : 0) | (AnvilRecipePage.hasNext(page, total) ? 2 : 0);
        return Map.of(BUTTONS_LAYER, onPage, ARROWS_LAYER, arrows);
    }
}
