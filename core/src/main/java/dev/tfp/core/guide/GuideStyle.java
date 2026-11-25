/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

/**
 * The style of one run of field guide text (PAR-DATA-04). Immutable.
 *
 * @param color   an RGB colour, or {@link #NO_COLOR} for the book's default (black)
 * @param link    a {@link GuideLink} target string, or null
 * @param tooltip hover text, or null
 */
public record GuideStyle(int color, boolean bold, boolean italic, boolean underlined, boolean strikethrough,
                         boolean obfuscated, String link, String tooltip) {
    public static final int NO_COLOR = -1;
    public static final GuideStyle PLAIN = new GuideStyle(NO_COLOR, false, false, false, false, false, null, null);

    public GuideStyle withColor(int rgb) {
        return new GuideStyle(rgb, bold, italic, underlined, strikethrough, obfuscated, link, tooltip);
    }

    public GuideStyle withBold(boolean b) {
        return new GuideStyle(color, b, italic, underlined, strikethrough, obfuscated, link, tooltip);
    }

    public GuideStyle withItalic(boolean b) {
        return new GuideStyle(color, bold, b, underlined, strikethrough, obfuscated, link, tooltip);
    }

    public GuideStyle withUnderlined(boolean b) {
        return new GuideStyle(color, bold, italic, b, strikethrough, obfuscated, link, tooltip);
    }

    public GuideStyle withStrikethrough(boolean b) {
        return new GuideStyle(color, bold, italic, underlined, b, obfuscated, link, tooltip);
    }

    public GuideStyle withObfuscated(boolean b) {
        return new GuideStyle(color, bold, italic, underlined, strikethrough, b, link, tooltip);
    }

    public GuideStyle withLink(String target) {
        return new GuideStyle(color, bold, italic, underlined, strikethrough, obfuscated, target, tooltip);
    }

    public GuideStyle withTooltip(String text) {
        return new GuideStyle(color, bold, italic, underlined, strikethrough, obfuscated, link, text);
    }
}
