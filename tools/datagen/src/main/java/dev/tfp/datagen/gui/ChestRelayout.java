/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/container/Container.java @ c3b4d9536 (addPlayerInventorySlots: the
 *       player inventory at y 84 and the hotbar at 142 of a 166-pixel screen)
 */
package dev.tfp.datagen.gui;

import dev.tfp.core.gui.ChestGeometry;
import dev.tfp.datagen.DatagenException;
import java.awt.image.BufferedImage;

/**
 * PAR-INFRA-03 {@code fw-alignment} for the art: TFC's screen background (176 x {@code srcHeight}, top-left of its
 * 256 x 256 texture) re-laid out row by row into a 9 x N chest's window (176 x {@code 114 + 18N}), so that the slot frames
 * of the art sit under the slots the vanilla client draws.
 *
 * <p>The rows are moved in four bands: the 4-row top border stays; the container part moves by {@code dyTop} (the slot
 * layout's offset); the player inventory part moves by {@code dyPlayer} so TFC's inventory at y 84 lands on the chest's;
 * the 4-row bottom border goes to the new bottom. The bands meet at {@code split}, a row of plain panel in the gap above
 * the player inventory; rows the moves leave empty repeat that plain row, and a row covered twice must be plain in both.
 * A screen without such a gap, or one whose moves would cover art, fails here instead of producing misaligned art.
 */
public final class ChestRelayout {
    /** TFC's player inventory item y ({@code Container.addPlayerInventorySlots}). */
    public static final int TFC_PLAYER_Y = 84;
    public static final int TFC_HOTBAR_Y = 142;
    /** Rows of the top and bottom border (frame, highlight and the inner corner pixel), kept in place. */
    static final int BORDER = 4;

    private ChestRelayout() {
    }

    /** The re-laid-out window. {@code src} must be at least 176 wide and {@code srcHeight} tall. */
    public static BufferedImage compose(BufferedImage src, int srcHeight, ChestGeometry chest, int dyTop, String what) {
        int w = ChestGeometry.WIDTH;
        int h = chest.height();
        if (src.getWidth() < w || src.getHeight() < srcHeight) {
            throw new DatagenException(what + ": texture is " + src.getWidth() + "x" + src.getHeight() + ", smaller than the "
                + w + "x" + srcHeight + " screen");
        }
        int dyPlayer = chest.playerRowY(0) - TFC_PLAYER_Y;
        if (chest.hotbarY() - TFC_HOTBAR_Y != dyPlayer) {
            throw new DatagenException(what + ": the chest's hotbar does not move with its inventory");
        }
        int split = split(src, srcHeight, what);
        int[] rowSource = new int[h];
        java.util.Arrays.fill(rowSource, -1);
        for (int s = BORDER; s < split; s++) {
            put(src, rowSource, s + dyTop, s, h, what);
        }
        for (int s = split; s < srcHeight - BORDER; s++) {
            put(src, rowSource, s + dyPlayer, s, h, what);
        }
        for (int i = 0; i < BORDER; i++) { // the borders win over moved rows next to them
            rowSource[i] = i;
            rowSource[h - BORDER + i] = srcHeight - BORDER + i;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int t = 0; t < h; t++) {
            int s = rowSource[t];
            if (s < 0) {
                s = split; // a gap: repeat the plain row
            }
            for (int x = 0; x < w; x++) {
                out.setRGB(x, t, src.getRGB(x, s));
            }
        }
        return out;
    }

    private static void put(BufferedImage src, int[] rowSource, int t, int s, int h, String what) {
        if (t < BORDER || t >= h - BORDER) {
            if (!plain(src, s)) {
                throw new DatagenException(what + ": moving the art pushes source row " + s + " into the border");
            }
            return;
        }
        if (rowSource[t] >= 0 && !(plain(src, s) && plain(src, rowSource[t]))) {
            throw new DatagenException(what + ": moving the art puts source rows " + rowSource[t] + " and " + s
                + " on one row, and they are not both plain panel");
        }
        rowSource[t] = s;
    }

    /**
     * The split row: the middle of the plain band directly above the player inventory's top frame row
     * ({@code 84 - 1}). Fails when there is no plain row there.
     */
    static int split(BufferedImage src, int srcHeight, String what) {
        int bottom = TFC_PLAYER_Y - 1; // first row of the player frames
        int top = bottom;
        while (top - 1 >= BORDER && plain(src, top - 1)) {
            top--;
        }
        if (top == bottom) {
            throw new DatagenException(what + ": no plain panel row above the player inventory to split the art at");
        }
        return (top + bottom) / 2;
    }

    /** A row whose pixels between the side borders all have one colour (the panel). */
    static boolean plain(BufferedImage src, int y) {
        int c = src.getRGB(BORDER, y);
        if ((c >>> 24) == 0) {
            return false;
        }
        for (int x = BORDER; x < ChestGeometry.WIDTH - BORDER; x++) {
            if (src.getRGB(x, y) != c) {
                return false;
            }
        }
        return true;
    }
}
