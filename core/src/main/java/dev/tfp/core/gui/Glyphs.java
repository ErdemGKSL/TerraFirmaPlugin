/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

/**
 * PAR-INFRA-03 {@code fw-alignment}: the vanilla bitmap glyph metrics a title image is placed with (verified against
 * CraftEngine's own {@code item_browser} GUI, see the craftengine-gui-furniture skill §4.2).
 *
 * <ul>
 *   <li>A glyph's top is at {@code titleLabelY + 7 - ascent}, so with {@code titleLabelY = 6} it is {@code 13 - ascent}.</li>
 *   <li>After a 1:1 bitmap glyph of width {@code w} the pen moves {@code w + 1}.</li>
 *   <li>A {@code <shift:n>} moves the pen by {@code n}.</li>
 * </ul>
 * All in GUI pixels, the same at every GUI scale.
 */
public final class Glyphs {
    /** {@code titleLabelY + 7}: the window y of a glyph with ascent 0. */
    public static final int BASELINE = ChestGeometry.TITLE_Y + 7;

    private Glyphs() {
    }

    /** The ascent that puts a glyph's top row at window y {@code top}. */
    public static int ascent(int top) {
        return BASELINE - top;
    }

    /** The window y of the top row of a glyph with {@code ascent}. */
    public static int top(int ascent) {
        return BASELINE - ascent;
    }

    /** The pen advance after a 1:1 glyph {@code width} pixels wide. */
    public static int advance(int width) {
        return width + 1;
    }
}
