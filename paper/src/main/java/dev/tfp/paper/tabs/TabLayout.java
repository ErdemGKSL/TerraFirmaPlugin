/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/button/PlayerInventoryTabButton.java @ c3b4d9536 (Tab: icon u/v and
 *       the tab positions right of the window; the button sprites at v 16, u 0 inactive and u 24 active),
 *       src/main/java/net/dries007/tfc/client/screen/NutritionScreen.java @ c3b4d9536 (renderBg: one 93 x 5 bar per
 *       nutrient at (76, 18 + 11 * ordinal), width (int) (value * 93)),
 *       src/main/java/net/dries007/tfc/client/screen/CalendarScreen.java @ c3b4d9536 (the night sprite at (154, 3))
 */
package dev.tfp.paper.tabs;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.ArrayList;
import java.util.List;

/**
 * PAR-SURV-07: where the player tab pages draw. TFC's 176 x 166 player screens become a 4-row chest (176 x 186): TFC's art
 * keeps its place above the inventory, an 18-pixel row is added under it for the tab buttons (chest slots 27 to 30,
 * where the page has no art), and the player inventory part moves down 19 pixels to the chest's (datagen
 * {@code tabs/PlayerTabArt} lays the art out with the same numbers; {@code PlayerTabLayoutConsistencyTest} compares them
 * with the generated images). TFC draws its tabs right of the window, where a click cannot be told apart from any other
 * click outside it, so the tab buttons are slots inside the window instead.
 *
 * <p>The inventory screen (E) gets TFC's tab column drawn right of its window through CraftEngine's {@code lang} override
 * of {@code container.crafting} ({@link #INVENTORY_TABS}); a click anywhere outside the window opens the calendar page.
 */
public final class TabLayout {
    public static final ChestGeometry CHEST = new ChestGeometry(4);
    /** TFC's player screen height ({@code AbstractContainerScreen} default). */
    public static final int TFC_HEIGHT = 166;
    /** The row of the tab buttons. */
    public static final int TAB_ROW = 3;
    public static final int BUTTON_SIZE = 18;

    /** The four tabs TFC shows (BOOK needs Patchouli and is left out). */
    public enum Tab {
        INVENTORY(0),
        CALENDAR(16),
        NUTRITION(32),
        CLIMATE(48);

        /** TFC {@code Tab.iconU} in {@code icons.png} (v 0, 16 x 16). */
        public final int iconU;

        Tab(int iconU) {
            this.iconU = iconU;
        }

        /** The chest slot of this tab's button. */
        public int slot() {
            return TAB_ROW * ChestGeometry.COLUMNS + ordinal();
        }

        /** The button art: 18 x 18 over the slot frame, frame 0 inactive and 1 active. */
        public GuiImage image() {
            return BUTTONS.get(ordinal());
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The tab whose button is in chest slot {@code slot}, or null. */
        public static Tab atSlot(int slot) {
            for (Tab t : values()) {
                if (t.slot() == slot) {
                    return t;
                }
            }
            return null;
        }
    }

    /** The three pages; INVENTORY is not a page (it closes the screen). */
    public enum Page {
        CALENDAR(Tab.CALENDAR),
        NUTRITION(Tab.NUTRITION),
        CLIMATE(Tab.CLIMATE);

        public final Tab tab;

        Page(Tab tab) {
            this.tab = tab;
        }

        public GuiImage background() {
            return GuiImage.of("tfp:gui/player_" + key(), 0, 0, ChestGeometry.WIDTH, CHEST.height());
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The page of {@code tab}, or null for INVENTORY. */
        public static Page of(Tab tab) {
            for (Page p : values()) {
                if (p.tab == tab) {
                    return p;
                }
            }
            return null;
        }
    }

    public static final int FRAME_INACTIVE = 0;
    public static final int FRAME_ACTIVE = 1;
    private static final List<GuiImage> BUTTONS;

    static {
        List<GuiImage> b = new ArrayList<>();
        for (Tab t : Tab.values()) {
            int slot = t.slot();
            b.add(new GuiImage("tfp:gui/player_tab_" + t.key(), CHEST.slotX(slot) - ChestGeometry.FRAME_INSET,
                CHEST.slotY(slot) - ChestGeometry.FRAME_INSET, BUTTON_SIZE, BUTTON_SIZE, 2, 1));
        }
        BUTTONS = List.copyOf(b);
    }

    /** NutritionScreen: bars at x 76, y 18 + 11 * ordinal, 93 x 5. */
    public static final int NUTRITION_X = 76;
    public static final int NUTRITION_Y0 = 18;
    public static final int NUTRITION_PITCH = 11;
    public static final int NUTRITION_WIDTH = 93;
    public static final int NUTRITION_HEIGHT = 5;
    /** Frames of a nutrient bar: widths 0 to 93 (frame 0 draws nothing and is not drawn). */
    public static final int NUTRITION_FRAMES = NUTRITION_WIDTH + 1;

    /** The bar of nutrient {@code ordinal}: frame {@code k} is the bar {@code k} pixels wide. */
    public static GuiImage nutritionBar(int ordinal) {
        return new GuiImage("tfp:gui/player_nutrition_bar_" + ordinal, NUTRITION_X, NUTRITION_Y0 + NUTRITION_PITCH * ordinal,
            NUTRITION_WIDTH, NUTRITION_HEIGHT, NUTRITION_FRAMES, 1);
    }

    /** CalendarScreen: the night sprite (u 176, v 0, 16 x 16) at (154, 3) from 20:00 to 04:59. */
    public static final GuiImage NIGHT = GuiImage.of("tfp:gui/player_calendar_night", 154, 3, 16, 16);

    /**
     * The inventory screen's tab column, as TFC places it right of the window: INVENTORY active at x 173 (176 - 3), the
     * others inactive at x 174, y 4 + 23 * i, each 24 x 22, so the image spans x 173 to 197 and y 4 to 94. It is drawn by
     * CraftEngine's {@code lang} override of {@code container.crafting} in {@code minecraft:default} (the only font a
     * translation keeps), so it is not a {@link GuiImage} of the {@code tfp:gui} font.
     */
    public static final int INVENTORY_TABS_X = 173;
    public static final int INVENTORY_TABS_Y = 4;
    public static final int INVENTORY_TABS_WIDTH = 25;
    public static final int INVENTORY_TABS_HEIGHT = 91;
    /** {@code InventoryScreen.titleLabelX}: where the {@code container.crafting} title starts. */
    public static final int INVENTORY_TITLE_X = 97;
    public static final String INVENTORY_TABS = "tfp:gui/inventory_tabs";

    /** Overlay keys. */
    public static final String NIGHT_LAYER = "night";

    public static String barLayer(int ordinal) {
        return "bar_" + ordinal;
    }

    public static String tabLayer(Tab tab) {
        return "tab_" + tab.key();
    }

    private TabLayout() {
    }

    /** The background and overlays of {@code page}. */
    public static GuiLayers layers(Page page) {
        List<GuiLayers.Layer> layers = new ArrayList<>();
        for (Tab t : Tab.values()) {
            layers.add(new GuiLayers.Layer(tabLayer(t), 10, t.image()));
        }
        switch (page) {
            case CALENDAR -> layers.add(new GuiLayers.Layer(NIGHT_LAYER, 20, NIGHT));
            case NUTRITION -> {
                for (int i = 0; i < 5; i++) {
                    layers.add(new GuiLayers.Layer(barLayer(i), 20, nutritionBar(i)));
                }
            }
            case CLIMATE -> {
            }
        }
        return new GuiLayers(page.background(), layers);
    }

    /** Every {@code tfp:gui} image of the tab screens. */
    public static List<GuiImage> images() {
        List<GuiImage> out = new ArrayList<>();
        for (Page p : Page.values()) {
            out.add(p.background());
        }
        out.addAll(BUTTONS);
        for (int i = 0; i < 5; i++) {
            out.add(nutritionBar(i));
        }
        out.add(NIGHT);
        return List.copyOf(out);
    }
}
