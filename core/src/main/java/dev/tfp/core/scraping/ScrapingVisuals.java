/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/client/model/ScrapingBlockModel.java @ c3b4d9536 (drawTiles: one 4 x 4 px tile
 * per cell, the input texture where the cell is unscraped, the output texture where it is scraped, tinted by colour 1 / 2)
 */
package dev.tfp.core.scraping;

import dev.tfp.core.craft.ScrapingRules;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The names shared by datagen (which writes the models) and the Paper side (which shows them) for the scraping surface's
 * look. TFC draws the surface with a dynamic block model: for each of the 16 cells a quad cut from the recipe's input or
 * output texture. Vanilla clients cannot run that, so the surface is one item display whose 1.21.4 item model definition
 * holds the 16 cells as {@code condition} models on the item's {@code custom_model_data} flags (flag {@code i} true =
 * cell {@code i} scraped) and tints them with its colours (colour 0 = TFC's colour 1, colour 1 = colour 2).
 *
 * <ul>
 *   <li>A recipe texture id ({@code tfp:item/hide/medium/soaked}, {@code tfp:block/paper}) has the key of its path without
 *       the {@code item/} or {@code block/} folder ({@code hide/medium/soaked}, {@code paper}).</li>
 *   <li>Texture {@code tfp:scraping/<key>}; per cell a parent model {@code tfp:block/scraping/cell_<i>} and a tile model
 *       {@code tfp:block/scraping/<key>/cell_<i>}.</li>
 *   <li>One item model per recipe, named by its input texture: {@code tfp:scraping/<input key>}.</li>
 * </ul>
 */
public final class ScrapingVisuals {
    /** Our namespace (the recipe ids and textures are moved to it by datagen). */
    public static final String NAMESPACE = "tfp";
    /** The item model folder and texture folder. */
    public static final String FOLDER = "scraping";
    /** The {@code custom_model_data} colour index of the unscraped cells (TFC {@code getColor1}). */
    public static final int INPUT_TINT = 0;
    /** The {@code custom_model_data} colour index of the scraped cells (TFC {@code getColor2}). */
    public static final int OUTPUT_TINT = 1;
    /** TFC draws the quads at {@code y = 0.01} of the block; in model units (16 per block). */
    public static final double SURFACE_Y = 0.16;

    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:(item|block)/([a-z0-9_./-]+)");

    private ScrapingVisuals() {}

    /** The key of a recipe texture id ({@code tfp:item/hide/medium/soaked} to {@code hide/medium/soaked}). */
    public static String key(String textureId) {
        var m = TEXTURE.matcher(textureId == null ? "" : textureId);
        if (!m.matches()) throw new IllegalArgumentException("not an item/ or block/ texture id: " + textureId);
        return m.group(2);
    }

    /** The item model that draws a recipe's surface, by its input texture ({@code tfp:scraping/hide/medium/soaked}). */
    public static String itemModel(String inputTexture) {
        return NAMESPACE + ":" + FOLDER + "/" + key(inputTexture);
    }

    /** The texture resource of a recipe texture ({@code tfp:scraping/hide/medium/soaked}). */
    public static String texture(String textureId) {
        return NAMESPACE + ":" + FOLDER + "/" + key(textureId);
    }

    /** The parent model of one cell ({@code tfp:block/scraping/cell_5}). */
    public static String cellParent(int cell) {
        requireCell(cell);
        return NAMESPACE + ":block/" + FOLDER + "/cell_" + cell;
    }

    /** The tile model of one cell in one texture ({@code tfp:block/scraping/hide/medium/soaked/cell_5}). */
    public static String tile(String textureId, int cell) {
        requireCell(cell);
        return NAMESPACE + ":block/" + FOLDER + "/" + key(textureId) + "/cell_" + cell;
    }

    /** The column ({@code x}) of a cell: TFC's {@code xOffset}. */
    public static int column(int cell) {
        requireCell(cell);
        return cell % ScrapingRules.GRID;
    }

    /** The row ({@code z}) of a cell: TFC's {@code zOffset}. */
    public static int row(int cell) {
        requireCell(cell);
        return cell / ScrapingRules.GRID;
    }

    /** The {@code custom_model_data} flags of a mask: flag {@code i} is whether cell {@code i} is scraped. */
    public static List<Boolean> flags(int mask) {
        List<Boolean> out = new ArrayList<>(ScrapingRules.CELLS);
        for (int i = 0; i < ScrapingRules.CELLS; i++) {
            out.add(ScrapingRules.isScraped(mask, i));
        }
        return List.copyOf(out);
    }

    private static void requireCell(int cell) {
        if (cell < 0 || cell >= ScrapingRules.CELLS) throw new IllegalArgumentException("cell out of range: " + cell);
    }
}
