/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/screen/KnappingScreen.java @ c3b4d9536 (the 5 x 5 buttons of 16 x 16 at
 *       12 + 16x, 12 + 16y, button id x + 5y, the button textures textures/gui/knapping/<item path>[_disabled].png,
 *       drawn when on, the disabled texture when off and the type has one),
 *       src/main/java/net/dries007/tfc/common/container/KnappingContainer.java @ c3b4d9536 (the output slot at 128, 46)
 */
package dev.tfp.core.knap;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.core.gui.GuiImage;
import dev.tfp.core.gui.GuiLayers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PAR-PROC-01: the knapping screen in a 6-row chest. A vanilla client only takes clicks on chest slots, so each of TFC's
 * 25 knapping buttons is a chest slot: grid cell {@code (x, y)} is chest slot {@code 9y + 1 + x} (columns 1 to 5, rows 0
 * to 4), the output is chest slot 26 (column 8, row 2), and TFC's arrow and output frame move with it by
 * ({@link #ART_DX}, {@link #ART_DY}). The cells are drawn on the chest's 18-pixel slot pitch instead of TFC's touching
 * 16-pixel buttons (a recorded divergence), so the slot hover matches the cell.
 *
 * <p>Each cell is one title overlay: frame 0 is the item's knapping texture (the cell is on), frame 1 its disabled
 * texture (the cell is off and the texture has one); an off cell without a disabled texture shows the plain panel, as in
 * TFC. Cells of one row share one font image (the ascent is the row's); the x is a title shift.
 */
public final class KnappingScreen {
    public static final ChestGeometry CHEST = new ChestGeometry(6);
    /** The grid's first chest column. */
    public static final int GRID_COLUMN = 1;
    public static final int OUTPUT_SLOT = 2 * ChestGeometry.COLUMNS + 8;
    public static final int CELL = 16;

    /** TFC's screen: 176 x 186, the player inventory 20 below the default ({@code init(playerInventory, 20)}). */
    public static final int TFC_HEIGHT = 186;
    public static final int TFC_INVENTORY_OFFSET = 20;
    /** TFC {@code KnappingScreen.init}: the first button's corner and the button size. */
    public static final int TFC_BUTTON_X0 = 12;
    public static final int TFC_BUTTON_Y0 = 12;
    /** TFC {@code KnappingContainer.addContainerSlots}: the output slot's item position. */
    public static final int TFC_OUTPUT_X = 128;
    public static final int TFC_OUTPUT_Y = 46;
    /** The arrow and output frame of {@code knapping.png} (x 97 to 144, y 45 to 62), moved as one block. */
    public static final int TFC_ART_X = 97;
    public static final int TFC_ART_Y = 45;
    public static final int TFC_ART_WIDTH = 48;
    public static final int TFC_ART_HEIGHT = 18;
    public static final int ART_DX = CHEST.slotX(OUTPUT_SLOT) - TFC_OUTPUT_X;
    public static final int ART_DY = CHEST.slotY(OUTPUT_SLOT) - TFC_OUTPUT_Y;
    /** The player inventory part of the art moves from TFC's y 104 to the chest's. */
    public static final int PLAYER_DY = CHEST.playerRowY(0) - (84 + TFC_INVENTORY_OFFSET);

    public static final GuiImage BACKGROUND = GuiImage.of("tfp:gui/knapping", 0, 0, ChestGeometry.WIDTH, CHEST.height());

    /** TFC's 21 rocks, each with a loose and a mossy loose knapping texture. */
    public static final List<String> ROCKS = List.of("andesite", "basalt", "chalk", "chert", "claystone", "conglomerate",
        "dacite", "diorite", "dolomite", "gabbro", "gneiss", "granite", "limestone", "marble", "phyllite", "quartzite",
        "rhyolite", "schist", "shale", "slate", "tuff");
    /** The knapping textures with a {@code _disabled} variant. */
    public static final Set<String> WITH_DISABLED = Set.of("clay_ball", "fire_clay", "goat_horn");

    /** Every knapping texture of TFC's {@code textures/gui/knapping} (paths without {@code .png}), in a fixed order. */
    public static final List<String> TEXTURES;

    static {
        List<String> t = new ArrayList<>();
        ROCKS.forEach(r -> t.add("rock/loose/" + r));
        ROCKS.forEach(r -> t.add("rock/mossy_loose/" + r));
        t.add("groundcover/obsidian_shard");
        t.addAll(List.of("clay_ball", "fire_clay", "goat_horn", "leather"));
        TEXTURES = List.copyOf(t);
    }

    private static final Map<String, GuiLayers> LAYERS = new HashMap<>();

    static {
        for (String texture : TEXTURES) {
            List<GuiLayers.Layer> layers = new ArrayList<>();
            for (int i = 0; i < KnappingRules.GRID * KnappingRules.GRID; i++) {
                layers.add(new GuiLayers.Layer(cellKey(i), 10, cellImage(texture, i % KnappingRules.GRID, i / KnappingRules.GRID)));
            }
            LAYERS.put(texture, new GuiLayers(BACKGROUND, layers));
        }
    }

    private KnappingScreen() {
    }

    /** The chest slot of grid cell {@code index} ({@code x + 5y}). */
    public static int cellSlot(int index) {
        int x = index % KnappingRules.GRID;
        int y = index / KnappingRules.GRID;
        KnappingRules.buttonIndex(x, y); // range check
        return y * ChestGeometry.COLUMNS + GRID_COLUMN + x;
    }

    /** The grid cell of chest slot {@code slot}, or -1 when it is not a cell. */
    public static int cellAt(int slot) {
        if (slot < 0 || slot >= CHEST.size()) {
            return -1;
        }
        int x = slot % ChestGeometry.COLUMNS - GRID_COLUMN;
        int y = slot / ChestGeometry.COLUMNS;
        return x >= 0 && x < KnappingRules.GRID && y < KnappingRules.GRID ? x + KnappingRules.GRID * y : -1;
    }

    /** The overlay key of cell {@code index}. */
    public static String cellKey(int index) {
        return "cell" + index;
    }

    /** The font image of a texture's row; the cell's x is the image's x. */
    public static GuiImage cellImage(String texture, int x, int y) {
        int slot = cellSlot(KnappingRules.buttonIndex(x, y));
        return new GuiImage(rowImageId(texture, y), CHEST.slotX(slot), CHEST.slotY(slot), CELL, CELL, 1,
            WITH_DISABLED.contains(texture) ? 2 : 1);
    }

    /** The image id of a texture's row {@code y}: {@code tfp:gui/knapping/<texture>_r<y>}. */
    public static String rowImageId(String texture, int y) {
        return "tfp:gui/knapping/" + texture + "_r" + y;
    }

    /** One image per texture and row (x of column 0), the set the datagen writes. */
    public static List<GuiImage> rowImages() {
        List<GuiImage> out = new ArrayList<>();
        for (String texture : TEXTURES) {
            for (int y = 0; y < KnappingRules.GRID; y++) {
                out.add(cellImage(texture, 0, y));
            }
        }
        return List.copyOf(out);
    }

    /**
     * TFC {@code getButtonLocation}: the knapping texture of an item, its id's path ({@code tfp:rock/loose/granite} gives
     * {@code rock/loose/granite}, {@code minecraft:clay_ball} gives {@code clay_ball}); {@code fallback} when TFC has no
     * texture for the item (an addon item in a knapping tag).
     */
    public static String texture(String itemId, String fallback) {
        int colon = itemId.indexOf(':');
        String path = colon < 0 ? itemId : itemId.substring(colon + 1);
        return LAYERS.containsKey(path) ? path : fallback;
    }

    /** The screen's layers for a texture of {@link #TEXTURES}. */
    public static GuiLayers layers(String texture) {
        GuiLayers l = LAYERS.get(texture);
        if (l == null) {
            throw new IllegalArgumentException("no knapping texture " + texture);
        }
        return l;
    }

    /**
     * The overlay frames for a grid: 0 for an on cell, 1 for an off cell when the type draws off cells and the texture
     * has a disabled variant (TFC {@code getLowTexture}), else -1 (hidden).
     */
    public static Map<String, Integer> frames(KnappingPattern grid, String texture, boolean typeHasOffTexture) {
        boolean off = typeHasOffTexture && WITH_DISABLED.contains(texture);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < KnappingRules.GRID * KnappingRules.GRID; i++) {
            out.put(cellKey(i), grid.get(i) ? 0 : off ? 1 : -1);
        }
        return out;
    }
}
