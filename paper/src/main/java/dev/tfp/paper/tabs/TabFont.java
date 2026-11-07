/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tabs;

import dev.tfp.core.gui.ChestGeometry;
import java.util.HashMap;
import java.util.Map;

/**
 * PAR-SURV-07: text lines below the title line. A container title is one line of text at y 6; TFC's pages draw several
 * lines. Each line y gets its own font ({@code tfp:tabs/line_<y>}, written by datagen {@code tabs/PlayerTabArt}): the
 * vanilla {@code ascii.png} and {@code accented.png} glyph sheets with the ascent lowered by {@code y - 6}, so text in that
 * font is drawn {@code y - 6} pixels lower than the title. Horizontal placement is a CraftEngine shift, so the server needs
 * the text width: the advances of the vanilla bitmap glyphs (the glyph width plus one, a space is 4).
 *
 * <p>Only the characters of those two sheets that the shipped languages use are in the fonts; anything else becomes
 * {@code ?} ({@link #sanitize}).
 */
public final class TabFont {
    /** Font id prefix; the full id is {@code tfp:tabs/line_<y>}. */
    public static final String PREFIX = "tfp:tabs/line_";
    /** The y of a title glyph with the vanilla ascent ({@code titleLabelY}). */
    public static final int TITLE_Y = ChestGeometry.TITLE_Y;
    /** Every line y the pages use (calendar 22/33/44/55, nutrition 17 + 11i, climate 18/32/46/57): one font each. */
    public static final java.util.List<Integer> LINES = java.util.List.of(17, 18, 22, 28, 32, 33, 39, 44, 46, 50, 55, 57, 61);

    /** Advances of the vanilla {@code ascii.png} glyphs (bitmap width + 1). */
    private static final String[] ASCII_ADVANCES = {
        "2", "!',.:;i|",
        "3", "`l",
        "4", " \"()*I[]t{}",
        "5", "<>fkªº°²",
        "6", "#$%&+-/0123456789=?ABCDEFGHJKLMNOPQRSTUVWXYZ\\^_abcdeghjmnopqrsuvwxyz£¬±÷",
        "7", "@~«»",
    };
    /**
     * The {@code accented.png} letters in the fonts (the Turkish letters of our language files), with the advance of
     * their base letter, and their cell (row, column) in the 16 x 75 sheet; datagen writes the same table.
     */
    static final Object[][] ACCENTED = {
        {'Ç', 6, 0, 7}, {'Ö', 6, 1, 6}, {'Ü', 6, 1, 10}, {'ç', 6, 2, 3}, {'ö', 6, 2, 13},
        {'ü', 6, 3, 1}, {'Ğ', 6, 5, 4}, {'ğ', 6, 5, 5}, {'İ', 4, 6, 6}, {'ı', 2, 6, 7},
        {'Ş', 6, 9, 0}, {'ş', 6, 9, 1},
    };
    private static final Map<Character, Integer> ADVANCE = new HashMap<>();

    static {
        for (int i = 0; i < ASCII_ADVANCES.length; i += 2) {
            int a = Integer.parseInt(ASCII_ADVANCES[i]);
            for (char c : ASCII_ADVANCES[i + 1].toCharArray()) {
                ADVANCE.put(c, a);
            }
        }
        for (Object[] row : ACCENTED) {
            ADVANCE.put((Character) row[0], (Integer) row[1]);
        }
    }

    private TabFont() {
    }

    /** The font id of text whose top is at window y {@code y}. */
    public static String font(int y) {
        return PREFIX + y;
    }

    /** True if the line fonts have a glyph for {@code c}. */
    public static boolean supported(char c) {
        return ADVANCE.containsKey(c);
    }

    /** {@code text} with every character the fonts lack replaced by {@code ?}. */
    public static String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(supported(c) ? c : '?');
        }
        return sb.toString();
    }

    /** The width of {@code text} in GUI pixels (vanilla {@code Font.width}); unsupported characters count as {@code ?}. */
    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += ADVANCE.getOrDefault(text.charAt(i), ADVANCE.get('?'));
        }
        return w;
    }
}
