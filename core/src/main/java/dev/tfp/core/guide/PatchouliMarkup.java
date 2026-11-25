/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses the formatting codes of Patchouli book text (the markup TFC's field guide is written in) into styled
 * {@link GuideRun}s (PAR-DATA-04). Written from Patchouli's documented text format, not from its code.
 *
 * <p>Codes: {@code $()} resets everything (colour, formatting, link, tooltip); {@code $(br)} breaks the line,
 * {@code $(br2)} / {@code $(p)} leave a blank line, {@code $(li)} starts a bullet line; {@code $(0)}..{@code $(f)} are the
 * sixteen chat colours, {@code $(#rgb)} / {@code $(#rrggbb)} a hex colour; {@code $(l)} / {@code $(bold)} bold,
 * {@code $(o)} / {@code $(italic)} italic, {@code $(n)} underline, {@code $(m)} strikethrough, {@code $(k)} obfuscated,
 * {@code $(r)} resets colour and formatting; the macros {@code $(thing)} ({@code #490}) and {@code $(item)}
 * ({@code #b0b}); {@code $(l:target)} starts a link ({@link #LINK_COLOR}) until {@code $(/l)}; {@code $(t:text)} a
 * tooltip until {@code $(/t)}; {@code $(k:key.use)} the client's key name. An unknown code is dropped. A literal newline
 * is a line break. The book's {@code $(cfg:...)} values are resolved by datagen before this.
 */
public final class PatchouliMarkup {
    /** Patchouli's default link colour. */
    public static final int LINK_COLOR = 0x0000EE;
    /** {@code $(thing)}. */
    public static final int THING_COLOR = 0x449900;
    /** {@code $(item)}. */
    public static final int ITEM_COLOR = 0xBB00BB;
    /** The bullet {@code $(li)} starts a line with. */
    public static final String BULLET = "• ";

    /** The sixteen chat colours by code. */
    private static final int[] CHAT = {
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    };
    private static final Map<String, String> MACROS = Map.of(
        "bold", "l", "italic", "o", "italics", "o", "strike", "m", "obf", "k", "reset", "", "clear", "",
        "p", "br2", "2br", "br2", "nocolor", "r");

    private PatchouliMarkup() {
    }

    /** The runs of {@code text}, starting from {@code base} (which {@code $()} returns to). */
    public static List<GuideRun> parse(String text, GuideStyle base, String namespace) {
        List<GuideRun> out = new ArrayList<>();
        GuideStyle style = base;
        StringBuilder buf = new StringBuilder();
        boolean lineStart = true;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '(') {
                int close = text.indexOf(')', i + 2);
                if (close < 0) {
                    buf.append(text, i, text.length());
                    break;
                }
                String code = text.substring(i + 2, close);
                i = close + 1;
                lineStart = flush(out, buf, style, lineStart);
                code = MACROS.getOrDefault(code, code);
                switch (code) {
                    case "" -> style = base;
                    case "br" -> {
                        out.add(GuideRun.BREAK);
                        lineStart = true;
                    }
                    case "br2" -> {
                        out.add(GuideRun.BREAK);
                        out.add(GuideRun.BREAK);
                        lineStart = true;
                    }
                    case "li", "li2", "li3" -> {
                        if (!lineStart) {
                            out.add(GuideRun.BREAK);
                        }
                        String indent = "  ".repeat(code.length() == 2 ? 0 : code.charAt(2) - '1');
                        out.add(new GuideRun.Text(indent + BULLET, base));
                        lineStart = false;
                    }
                    case "l" -> style = style.withBold(true);
                    case "o" -> style = style.withItalic(true);
                    case "n" -> style = style.withUnderlined(true);
                    case "m" -> style = style.withStrikethrough(true);
                    case "k" -> style = style.withObfuscated(true);
                    case "r" -> style = new GuideStyle(base.color(), base.bold(), base.italic(), base.underlined(),
                        base.strikethrough(), base.obfuscated(), style.link(), style.tooltip());
                    case "thing" -> style = style.withColor(THING_COLOR);
                    case "item" -> style = style.withColor(ITEM_COLOR);
                    case "/l" -> style = style.withLink(null).withColor(base.color());
                    case "/t" -> style = style.withTooltip(null);
                    default -> style = complex(code, style, base, namespace, out);
                }
                if (code.startsWith("k:")) {
                    lineStart = false;
                }
                continue;
            }
            if (c == '\n') {
                lineStart = flush(out, buf, style, lineStart);
                out.add(GuideRun.BREAK);
                lineStart = true;
            } else if (c != '\r') {
                buf.append(c);
            }
            i++;
        }
        flush(out, buf, style, lineStart);
        return out;
    }

    private static GuideStyle complex(String code, GuideStyle style, GuideStyle base, String namespace,
                                      List<GuideRun> out) {
        if (code.length() == 1) {
            int idx = Character.digit(code.charAt(0), 16);
            return idx >= 0 ? style.withColor(CHAT[idx]) : style;
        }
        if (code.startsWith("#")) {
            int rgb = hex(code.substring(1));
            return rgb >= 0 ? style.withColor(rgb) : style;
        }
        if (code.startsWith("l:")) {
            return style.withLink(GuideLink.fromPatchouli(code.substring(2), namespace)).withColor(LINK_COLOR);
        }
        if (code.startsWith("t:")) {
            return style.withTooltip(code.substring(2));
        }
        if (code.startsWith("k:")) {
            out.add(new GuideRun.Keybind(code.substring(2), style));
            return style;
        }
        return style; // unknown codes (Patchouli's own player/game functions) are dropped
    }

    /** {@code rgb} or {@code rrggbb} as an int, or -1. */
    static int hex(String s) {
        String h = s.toLowerCase(Locale.ROOT);
        if (!h.matches("[0-9a-f]{3}|[0-9a-f]{6}")) {
            return -1;
        }
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        return Integer.parseInt(h, 16);
    }

    private static boolean flush(List<GuideRun> out, StringBuilder buf, GuideStyle style, boolean lineStart) {
        if (buf.isEmpty()) {
            return lineStart;
        }
        out.add(new GuideRun.Text(buf.toString(), style));
        buf.setLength(0);
        return false;
    }

    /** The plain text of {@code runs} (key bindings as their key id, breaks as newlines). */
    public static String plain(List<GuideRun> runs) {
        StringBuilder sb = new StringBuilder();
        for (GuideRun r : runs) {
            switch (r) {
                case GuideRun.Text t -> sb.append(t.text());
                case GuideRun.Keybind k -> sb.append('[').append(k.key()).append(']');
                case GuideRun.Break b -> sb.append('\n');
                case GuideRun.PageBreak p -> sb.append('\n');
            }
        }
        return sb.toString();
    }
}
