/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.sign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * PAR-DECO-03: the text of a sign (TFC's signs are vanilla {@code SignBlockEntity}s, so this is vanilla's {@code SignText}):
 * a front and a back of {@value #LINES} lines each, a dye colour, a glow flag per side, and wax on the whole sign. Immutable,
 * platform-free and thread-safe. A line is plain text (the editor sends plain strings); {@link #sanitize} applies the editor's
 * limit ({@value #MAX_LINE_WIDTH} font pixels per line) with a width function the platform supplies.
 */
public final class SignText {
    public static final int LINES = 4;
    /** Vanilla {@code SignEditScreen}: a line is at most 90 font pixels wide. */
    public static final int MAX_LINE_WIDTH = 90;
    /** The dye colours (vanilla {@code DyeColor} names); black is the default. */
    public static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
        "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    public static final String DEFAULT_COLOR = "black";

    /** One side: its lines, the dye and the glow ink. */
    public record Side(List<String> lines, String color, boolean glowing) {
        public static final Side EMPTY = new Side(List.of("", "", "", ""), DEFAULT_COLOR, false);

        public Side {
            if (lines.size() != LINES) {
                throw new IllegalArgumentException("a sign side has " + LINES + " lines, got " + lines.size());
            }
            if (!COLORS.contains(color)) {
                throw new IllegalArgumentException("unknown dye colour " + color);
            }
            lines = List.copyOf(lines);
        }

        public boolean isEmpty() {
            return lines.stream().allMatch(String::isEmpty);
        }

        public Side withLine(int index, String text) {
            List<String> l = new ArrayList<>(lines);
            l.set(index, text);
            return new Side(l, color, glowing);
        }

        public Side withColor(String dye) {
            return new Side(lines, dye, glowing);
        }

        public Side withGlowing(boolean glow) {
            return new Side(lines, color, glow);
        }
    }

    public static final SignText EMPTY = new SignText(Side.EMPTY, Side.EMPTY, false);

    private final Side front;
    private final Side back;
    private final boolean waxed;

    public SignText(Side front, Side back, boolean waxed) {
        this.front = Objects.requireNonNull(front);
        this.back = Objects.requireNonNull(back);
        this.waxed = waxed;
    }

    public Side front() {
        return front;
    }

    public Side back() {
        return back;
    }

    public boolean waxed() {
        return waxed;
    }

    public Side side(boolean frontSide) {
        return frontSide ? front : back;
    }

    public SignText withSide(boolean frontSide, Side side) {
        return frontSide ? new SignText(side, back, waxed) : new SignText(front, side, waxed);
    }

    public SignText withWaxed(boolean wax) {
        return new SignText(front, back, wax);
    }

    /**
     * What the editor's lines become on the server: control characters removed, then each line cut to
     * {@link #MAX_LINE_WIDTH} font pixels (whole characters) by {@code width}; missing lines are empty, extra lines dropped.
     */
    public static List<String> sanitize(List<String> raw, ToIntFunction<String> width) {
        List<String> out = new ArrayList<>(LINES);
        for (int i = 0; i < LINES; i++) {
            String s = i < raw.size() && raw.get(i) != null ? raw.get(i) : "";
            StringBuilder b = new StringBuilder();
            s.codePoints().filter(c -> c >= 32 && c != 127 && !(c >= 0x80 && c < 0xA0)).forEach(b::appendCodePoint);
            String line = b.toString();
            while (!line.isEmpty() && width.applyAsInt(line) > MAX_LINE_WIDTH) {
                line = line.substring(0, line.offsetByCodePoints(line.length(), -1));
            }
            out.add(line);
        }
        return out;
    }

    // ---- persistence (the PDC string of the sign) -------------------------------------------------------------------------

    /** {@code <waxed 0/1>} line, then per side {@code color|glow|l0\tl1\tl2\tl3}, lines escaped ({@code \\}, tab, newline). */
    public String encode() {
        return (waxed ? "1" : "0") + "\n" + encodeSide(front) + "\n" + encodeSide(back);
    }

    public static SignText decode(String s) {
        String[] p = s.split("\n", -1);
        if (p.length != 3 || !(p[0].equals("0") || p[0].equals("1"))) {
            throw new IllegalArgumentException("not a sign text: " + s);
        }
        return new SignText(decodeSide(p[1]), decodeSide(p[2]), p[0].equals("1"));
    }

    private static String encodeSide(Side side) {
        StringBuilder b = new StringBuilder(side.color()).append('|').append(side.glowing() ? '1' : '0').append('|');
        for (int i = 0; i < LINES; i++) {
            if (i > 0) {
                b.append('\t');
            }
            b.append(side.lines().get(i).replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n"));
        }
        return b.toString();
    }

    private static Side decodeSide(String s) {
        String[] p = s.split("\\|", 3);
        if (p.length != 3) {
            throw new IllegalArgumentException("not a sign side: " + s);
        }
        String[] raw = p[2].split("\t", -1);
        List<String> lines = new ArrayList<>();
        for (String r : raw) {
            lines.add(unescape(r));
        }
        return new Side(lines, p[0], p[1].equals("1"));
    }

    private static String unescape(String r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.length(); i++) {
            char c = r.charAt(i);
            if (c == '\\' && i + 1 < r.length()) {
                char n = r.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SignText t && front.equals(t.front) && back.equals(t.back) && waxed == t.waxed;
    }

    @Override
    public int hashCode() {
        return Objects.hash(front, back, waxed);
    }

    @Override
    public String toString() {
        return "SignText" + Arrays.asList(front, back, waxed);
    }
}
