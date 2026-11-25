/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import java.util.Map;

/**
 * Advance widths (in GUI pixels, spacing included) of the vanilla default font, for laying out written book pages on
 * the server (PAR-DATA-04). ASCII is exact; other Latin-1 letters count 6; anything else counts {@link #WIDE}, wider than
 * any glyph the client draws for it, so a line measured here never wraps again on the client. Bold adds one pixel.
 */
public final class BookFont {
    /** A vanilla written book page: 114 px wide, 14 lines. */
    public static final int PAGE_WIDTH = 114;
    public static final int PAGE_LINES = 14;
    /** The width this layout fills, kept under {@link #PAGE_WIDTH} so a rounding difference never wraps a line. */
    public static final int LINE_WIDTH = 112;
    /** The width counted for a character outside ASCII and Latin-1. */
    public static final int WIDE = 9;

    private static final int[] ASCII = new int[128];
    /** Conservative widths of the key names the guide uses (their English labels, which are the longest). */
    private static final Map<String, Integer> KEYS = Map.of(
        "key.use", 70, "key.attack", 62, "key.sneak", 54, "key.drop", 8, "key.inventory", 8, "key.jump", 32,
        "key.sprint", 64, "key.swapOffhand", 8, "key.pickItem", 74);
    /** Width counted for any other key name. */
    public static final int KEY_DEFAULT = 74;

    static {
        java.util.Arrays.fill(ASCII, 6);
        set(" ", 4);
        set("!',.:;i|", 2);
        set("`l", 3);
        set("\"()*I[]t{}", 4);
        set("<>fk", 5);
        set("@~", 7);
    }

    private BookFont() {
    }

    private static void set(String chars, int width) {
        for (char c : chars.toCharArray()) {
            ASCII[c] = width;
        }
    }

    /** The advance of one code point. */
    public static int width(int codePoint, boolean bold) {
        int w;
        if (codePoint < 128) {
            w = codePoint < 32 ? 0 : ASCII[codePoint];
        } else if (codePoint >= 0xC0 && codePoint <= 0xFF) {
            w = 6;
        } else {
            w = WIDE;
        }
        return w + (bold && w > 0 ? 1 : 0);
    }

    /** The width of {@code text}. */
    public static int width(String text, boolean bold) {
        int sum = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            sum += width(cp, bold);
            i += Character.charCount(cp);
        }
        return sum;
    }

    /** The width counted for the client's name of {@code key}. */
    public static int keyWidth(String key, boolean bold) {
        int w = KEYS.getOrDefault(key, KEY_DEFAULT);
        return bold ? w + w / 5 : w;
    }
}
