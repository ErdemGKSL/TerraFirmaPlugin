/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.tooltip.info;

import dev.tfp.paper.tooltip.TooltipText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * TFC's {@code Component.translatable(key, args...)} for lang strings that carry {@code §} formatting codes (such as
 * {@code "§7Deals §fSlashing§7 Damage"}), translated on the server for the client's language. The text between the
 * arguments is parsed as legacy text; each argument keeps its own style and otherwise inherits the line's base style,
 * as a translatable argument does on the client (a {@code §} code in the lang string does not reach the argument).
 */
final class LegacyLines {
    private LegacyLines() {}

    private static final char OPEN = '';
    private static final char CLOSE = '';
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** The line for {@code key} in {@code locale} with {@code args} in place of its {@code {n}} placeholders. */
    static Component translate(TooltipText text, String locale, String key, Component... args) {
        Object[] tokens = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            tokens[i] = OPEN + Integer.toString(i) + CLOSE;
        }
        String pattern = text.get(locale, key, tokens);
        TextComponent.Builder out = Component.text();
        int i = 0;
        StringBuilder segment = new StringBuilder();
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            int close = c == OPEN ? pattern.indexOf(CLOSE, i) : -1;
            if (close > i + 1) {
                String index = pattern.substring(i + 1, close);
                if (index.chars().allMatch(Character::isDigit)) {
                    int n = Integer.parseInt(index);
                    if (n < args.length) {
                        flush(segment, out);
                        out.append(args[n]);
                        i = close + 1;
                        continue;
                    }
                }
            }
            segment.append(c);
            i++;
        }
        flush(segment, out);
        return out.build();
    }

    private static void flush(StringBuilder segment, TextComponent.Builder out) {
        if (!segment.isEmpty()) {
            out.append(LEGACY.deserialize(segment.toString()));
            segment.setLength(0);
        }
    }
}
