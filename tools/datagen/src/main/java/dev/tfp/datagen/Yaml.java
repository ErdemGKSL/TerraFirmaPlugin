/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Minimal deterministic block-style YAML writer for generated CraftEngine config: maps keep insertion order,
 * 2-space indent, LF, no tabs. Strings are plain when unambiguous, otherwise single-quoted.
 */
public final class Yaml {
    private static final Pattern PLAIN = Pattern.compile("[A-Za-z_][A-Za-z0-9_:/.=\\-]*");
    private static final Set<String> RESERVED = Set.of(
        "true", "false", "yes", "no", "on", "off", "null", "y", "n", "~");

    private Yaml() {
    }

    public static String write(String header, Map<String, ?> root) {
        StringBuilder sb = new StringBuilder();
        for (String line : header.split("\n")) {
            sb.append("# ").append(line).append('\n');
        }
        map(sb, root, 0);
        return sb.toString();
    }

    private static void map(StringBuilder sb, Map<?, ?> m, int indent) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            pad(sb, indent).append(scalar(String.valueOf(e.getKey()))).append(':');
            value(sb, e.getValue(), indent);
        }
    }

    private static void value(StringBuilder sb, Object v, int indent) {
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) {
                sb.append(" {}\n");
            } else {
                sb.append('\n');
                map(sb, m, indent + 2);
            }
        } else if (v instanceof List<?> l) {
            if (l.isEmpty()) {
                sb.append(" []\n");
            } else {
                sb.append('\n');
                list(sb, l, indent + 2);
            }
        } else {
            sb.append(' ').append(scalar(v)).append('\n');
        }
    }

    private static void list(StringBuilder sb, List<?> l, int indent) {
        for (Object item : l) {
            pad(sb, indent).append('-');
            if (item instanceof Map<?, ?> m && !m.isEmpty()) {
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (first) {
                        sb.append(' ');
                        first = false;
                    } else {
                        pad(sb, indent + 2);
                    }
                    sb.append(scalar(String.valueOf(e.getKey()))).append(':');
                    value(sb, e.getValue(), indent + 2);
                }
            } else if (item instanceof List<?> inner && !inner.isEmpty()) {
                sb.append('\n');
                list(sb, inner, indent + 2);
            } else {
                value(sb, item, indent);
            }
        }
    }

    static String scalar(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Boolean || v instanceof Integer || v instanceof Long) {
            return v.toString();
        }
        if (v instanceof BigDecimal d) {
            String s = d.stripTrailingZeros().toPlainString();
            return s.contains(".") ? s : s + ".0";
        }
        if (v instanceof Number) {
            throw new IllegalArgumentException("use Integer/Long/BigDecimal for YAML numbers, got " + v.getClass());
        }
        String s = v.toString();
        if (PLAIN.matcher(s).matches() && !RESERVED.contains(s.toLowerCase(java.util.Locale.ROOT))) {
            return s;
        }
        return "'" + s.replace("'", "''") + "'";
    }

    private static StringBuilder pad(StringBuilder sb, int n) {
        return sb.repeat(' ', n);
    }
}
