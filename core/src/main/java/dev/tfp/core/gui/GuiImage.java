/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.regex.Pattern;

/**
 * PAR-INFRA-03 {@code fw-title-glyphs}: one CraftEngine font image drawn in a container title, at a fixed place of the
 * window. The datagen writes the PNG and the CraftEngine {@code images:} entry from this record; the Paper side writes
 * {@code <image:id[:row:col]>} tags for it. Both read the same record, so the PNG size, the ascent and the shift agree.
 *
 * <p>A grid image holds {@code rows x cols} frames of {@code width x height} each (CraftEngine {@code grid_size}); every
 * frame is drawn at the same place, so progress bars and button states are frame indices.
 *
 * <p>Vertical placement is baked into the font (the ascent); horizontal placement is a shift in the title
 * ({@link TitleGlyphs}).
 *
 * @param id     CraftEngine image id, {@code tfp:gui/<name>}
 * @param x      window x of the frame's left edge (GUI pixels)
 * @param y      window y of the frame's top edge
 * @param width  frame width (pixels of the PNG, drawn 1:1)
 * @param height frame height
 * @param rows   grid rows (1 for a plain image)
 * @param cols   grid columns
 */
public record GuiImage(String id, int x, int y, int width, int height, int rows, int cols) {
    /** CraftEngine's glyph cell limit. */
    public static final int MAX_CELL = 256;
    /** Font of every GUI image: a custom font, so players cannot type the glyphs. */
    public static final String FONT = "tfp:gui";
    private static final Pattern ID = Pattern.compile("tfp:gui/[a-z0-9_/]+");

    public GuiImage {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("GUI image ids are tfp:gui/<name>, not " + id);
        }
        if (width < 1 || height < 1 || width > MAX_CELL || height > MAX_CELL) {
            throw new IllegalArgumentException(id + ": frame " + width + "x" + height + " is outside 1 to " + MAX_CELL);
        }
        if (rows < 1 || cols < 1) {
            throw new IllegalArgumentException(id + ": grid " + rows + "x" + cols);
        }
        if (Glyphs.ascent(y) > height) {
            // CraftEngine refuses ascent > height; the art must be padded at the bottom to go that high
            throw new IllegalArgumentException(id + ": top " + y + " needs ascent " + Glyphs.ascent(y) + " > height " + height);
        }
    }

    /** A plain (one-frame) image. */
    public static GuiImage of(String id, int x, int y, int width, int height) {
        return new GuiImage(id, x, y, width, height, 1, 1);
    }

    /** The CraftEngine ascent that puts the frame's top at {@link #y}. */
    public int ascent() {
        return Glyphs.ascent(y);
    }

    /** How far the title pen moves after this glyph. */
    public int advance() {
        return Glyphs.advance(width);
    }

    public int frames() {
        return rows * cols;
    }

    /** The grid row of frame {@code frame} (frames run along rows). */
    public int frameRow(int frame) {
        checkFrame(frame);
        return frame / cols;
    }

    public int frameCol(int frame) {
        checkFrame(frame);
        return frame % cols;
    }

    /** Pixel size of the whole PNG. */
    public int pngWidth() {
        return width * cols;
    }

    public int pngHeight() {
        return height * rows;
    }

    /** The name after {@code tfp:gui/}. */
    public String name() {
        return id.substring("tfp:gui/".length());
    }

    /** The CraftEngine {@code file:} value, {@code tfp:font/gui/<name>.png}. */
    public String file() {
        return "tfp:font/gui/" + name() + ".png";
    }

    /** The PNG's path inside the resource pack's {@code assets/tfp/}. */
    public String packPath() {
        return "textures/font/gui/" + name() + ".png";
    }

    private void checkFrame(int frame) {
        if (frame < 0 || frame >= frames()) {
            throw new IndexOutOfBoundsException(id + " has " + frames() + " frames, not frame " + frame);
        }
    }
}
