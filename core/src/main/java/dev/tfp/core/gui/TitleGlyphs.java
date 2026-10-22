/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * PAR-INFRA-03 {@code fw-title-glyphs}: the glyph part of a container title, as CraftEngine tags. CraftEngine rewrites
 * {@code <image:...>} and {@code <shift:n>} written literally in any container title into font glyphs
 * ({@code network.intercept-packets.container}), so the Paper side only puts {@link #tags()} into a white text component.
 *
 * <p>The builder tracks the pen: it starts at {@link ChestGeometry#TITLE_X}; {@link Builder#draw} shifts it to the image's
 * x, draws the frame and moves the pen past it; {@link Builder#returnToLabel} shifts it back so a text label after the
 * glyphs starts where the vanilla title would. Adjacent shifts are merged and zero shifts dropped.
 *
 * @param tokens the shifts and images in order
 * @param pen    the window x of the pen after the last token
 */
public record TitleGlyphs(List<Token> tokens, int pen) {
    /** The largest shift CraftEngine pre-registers; longer shifts are written as several tags. */
    public static final int MAX_SHIFT_TAG = 256;

    public TitleGlyphs {
        tokens = List.copyOf(tokens);
    }

    /** One part of the title. */
    public sealed interface Token {
    }

    /** Moves the pen by {@code px} (negative = left). */
    public record Shift(int px) implements Token {
    }

    /** Draws frame {@code (row, col)} of an image; {@code grid} false writes the plain {@code <image:id>} tag. */
    public record Glyph(String id, int row, int col, boolean grid) implements Token {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The CraftEngine tag text, e.g. {@code <shift:-8><image:tfp:gui/large_vessel><shift:-169>}. */
    public String tags() {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            switch (t) {
                case Shift s -> {
                    int left = s.px();
                    while (left != 0) {
                        int step = Math.max(-MAX_SHIFT_TAG, Math.min(MAX_SHIFT_TAG, left));
                        sb.append("<shift:").append(step).append('>');
                        left -= step;
                    }
                }
                case Glyph g -> {
                    sb.append("<image:").append(g.id());
                    if (g.grid()) {
                        sb.append(':').append(g.row()).append(':').append(g.col());
                    }
                    sb.append('>');
                }
            }
        }
        return sb.toString();
    }

    public static final class Builder {
        private final List<Token> tokens = new ArrayList<>();
        private int pen = ChestGeometry.TITLE_X;

        private Builder() {
        }

        /** Moves the pen by {@code px}. */
        public Builder shift(int px) {
            if (px == 0) {
                return this;
            }
            pen += px;
            if (!tokens.isEmpty() && tokens.getLast() instanceof Shift last) {
                int merged = last.px() + px;
                tokens.removeLast();
                if (merged != 0) {
                    tokens.add(new Shift(merged));
                }
            } else {
                tokens.add(new Shift(px));
            }
            return this;
        }

        /** Draws frame {@code frame} of {@code image} at the image's window x. */
        public Builder draw(GuiImage image, int frame) {
            int row = image.frameRow(frame);
            int col = image.frameCol(frame);
            shift(image.x() - pen);
            tokens.add(new Glyph(image.id(), row, col, image.frames() > 1));
            pen += image.advance();
            return this;
        }

        /** Shifts the pen back to {@link ChestGeometry#TITLE_X}. */
        public Builder returnToLabel() {
            return shift(ChestGeometry.TITLE_X - pen);
        }

        public int pen() {
            return pen;
        }

        public TitleGlyphs build() {
            return new TitleGlyphs(tokens, pen);
        }
    }
}
