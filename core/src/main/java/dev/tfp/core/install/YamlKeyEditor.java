/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.install;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-based, comment-preserving edits of one key in another plugin's YAML file
 * ({@code dependency-bootstrap} §6). YAML libraries drop comments on a round trip, so we never re-serialize
 * the file: only the line holding the value (or the list item) changes; every other byte stays.
 *
 * <p>Supports the subset these config files use: block mappings with consistent indentation, plain or
 * quoted scalars with trailing {@code # comments}, and lists in flow ({@code [a, b]}) or block
 * ({@code - a}, at the key's indentation or deeper) style. Paths are dot-separated mapping keys
 * ({@code misc.delay-configuration-load}). Anything else (anchors, multi-line scalars at the edited key)
 * is refused with {@link IllegalArgumentException} rather than guessed at.
 */
public final class YamlKeyEditor {
    private static final Pattern KEY_LINE = Pattern.compile(
            "^(\\s*)(?:\"([^\"]*)\"|'([^']*)'|([^\\s#:\\-\"'\\[{][^:#]*?|-[^\\s:#][^:#]*?))\\s*:(?=\\s|$)");
    private static final Pattern ITEM_LINE = Pattern.compile("^(\\s*)-(?:\\s+(.*))?$");

    private final List<String> lines;
    private final String newline;
    private final boolean trailingNewline;

    private YamlKeyEditor(String text) {
        this.newline = text.contains("\r\n") ? "\r\n" : "\n";
        this.trailingNewline = text.isEmpty() || text.endsWith("\n");
        this.lines = new ArrayList<>(text.lines().toList());
    }

    public static YamlKeyEditor of(String text) {
        return new YamlKeyEditor(text);
    }

    public String text() {
        String joined = String.join(newline, lines);
        return trailingNewline && !lines.isEmpty() ? joined + newline : joined;
    }

    // ---- queries ----

    /** The scalar at {@code path} with quotes removed; empty if the key is absent or holds a block. */
    public Optional<String> scalar(String path) {
        int i = find(path);
        if (i < 0) {
            return Optional.empty();
        }
        String raw = valueOf(lines.get(i));
        if (raw.isEmpty() || raw.startsWith("[") || raw.startsWith("{")) {
            return Optional.empty();
        }
        return Optional.of(unquote(raw));
    }

    /** The list at {@code path} (flow or block style), items unquoted; empty if the key is absent or not a list. */
    public Optional<List<String>> list(String path) {
        int i = find(path);
        if (i < 0) {
            return Optional.empty();
        }
        String raw = valueOf(lines.get(i));
        if (raw.startsWith("[")) {
            return Optional.of(parseFlow(raw));
        }
        if (!raw.isEmpty()) {
            return Optional.empty();
        }
        List<String> items = new ArrayList<>();
        for (int j : blockItems(i)) {
            Matcher m = ITEM_LINE.matcher(lines.get(j));
            if (m.matches()) {
                items.add(unquote(stripComment(m.group(2) == null ? "" : m.group(2)).strip()));
            }
        }
        return Optional.of(items);
    }

    public boolean has(String path) {
        return find(path) >= 0;
    }

    // ---- edits ----

    /**
     * Sets the scalar at {@code path} to the literal {@code yamlValue} (already quoted if needed), keeping
     * the key's spelling and any trailing comment. Missing keys (and missing parent sections) are appended
     * at the end of the deepest existing parent section.
     */
    public YamlKeyEditor setScalar(String path, String yamlValue) {
        int i = find(path);
        if (i >= 0) {
            String line = lines.get(i);
            if (valueOf(line).isEmpty() && !blockChildren(i).isEmpty()) {
                throw new IllegalArgumentException(path + " is a section, not a scalar");
            }
            lines.set(i, replaceValue(line, yamlValue));
            return this;
        }
        insertMissing(path, yamlValue);
        return this;
    }

    /** Removes every item equal to {@code item} from the list at {@code path}. Returns true if it changed. */
    public boolean removeListItem(String path, String item) {
        int i = find(path);
        if (i < 0) {
            return false;
        }
        String line = lines.get(i);
        String raw = valueOf(line);
        if (raw.startsWith("[")) {
            List<String> items = parseFlow(raw);
            List<String> kept = new ArrayList<>();
            for (String s : items) {
                if (!s.equals(item)) {
                    kept.add(s);
                }
            }
            if (kept.size() == items.size()) {
                return false;
            }
            lines.set(i, replaceValue(line, flow(kept)));
            return true;
        }
        if (!raw.isEmpty()) {
            throw new IllegalArgumentException(path + " is not a list");
        }
        List<Integer> items = blockItems(i);
        List<Integer> remove = new ArrayList<>();
        for (int j : items) {
            Matcher m = ITEM_LINE.matcher(lines.get(j));
            if (m.matches() && unquote(stripComment(m.group(2) == null ? "" : m.group(2)).strip()).equals(item)) {
                remove.add(j);
            }
        }
        if (remove.isEmpty()) {
            return false;
        }
        for (int k = remove.size() - 1; k >= 0; k--) {
            lines.remove((int) remove.get(k));
        }
        if (remove.size() == items.size()) {
            lines.set(i, replaceValue(lines.get(i), "[]"));
        }
        return true;
    }

    /** Appends {@code yamlItem} (a literal) to the list at {@code path} unless {@code item} is already there. */
    public boolean addListItem(String path, String item, String yamlItem) {
        Optional<List<String>> current = list(path);
        if (current.isPresent() && current.get().contains(item)) {
            return false;
        }
        int i = find(path);
        if (i < 0) {
            insertMissing(path, "[" + yamlItem + "]");
            return true;
        }
        String line = lines.get(i);
        String raw = valueOf(line);
        if (raw.startsWith("[")) {
            List<String> literals = new ArrayList<>(splitFlowLiterals(raw));
            literals.add(yamlItem);
            lines.set(i, replaceValue(line, "[" + String.join(", ", literals) + "]"));
            return true;
        }
        if (!raw.isEmpty()) {
            throw new IllegalArgumentException(path + " is not a list");
        }
        List<Integer> items = blockItems(i);
        if (items.isEmpty()) {
            lines.set(i, replaceValue(line, "[" + yamlItem + "]"));
            return true;
        }
        int last = items.getLast();
        Matcher m = ITEM_LINE.matcher(lines.get(last));
        String indent = m.matches() ? m.group(1) : indentOf(line) + "  ";
        lines.add(last + 1, indent + "- " + yamlItem);
        return true;
    }

    // ---- structure ----

    /** Index of the line holding the key at {@code path}, or -1. */
    private int find(String path) {
        String[] segs = path.split("\\.");
        int from = 0;
        int to = lines.size();
        int parentIndent = -1;
        int found = -1;
        for (String seg : segs) {
            found = -1;
            int childIndent = -1;
            for (int j = from; j < to; j++) {
                String line = lines.get(j);
                if (isIgnorable(line)) {
                    continue;
                }
                int ind = indentOf(line).length();
                if (ind <= parentIndent) {
                    break;
                }
                if (childIndent < 0) {
                    childIndent = ind;
                }
                if (ind != childIndent) {
                    continue;
                }
                String key = keyOf(line);
                if (seg.equals(key)) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                return -1;
            }
            parentIndent = indentOf(lines.get(found)).length();
            from = found + 1;
            to = blockEnd(found);
        }
        return found;
    }

    /** Exclusive end of the block owned by the key at {@code i}: the next line at the same or lower indentation. */
    private int blockEnd(int i) {
        int ind = indentOf(lines.get(i)).length();
        int end = i + 1;
        int lastContent = i;
        for (int j = i + 1; j < lines.size(); j++) {
            String line = lines.get(j);
            if (isIgnorable(line)) {
                continue;
            }
            int li = indentOf(line).length();
            // A block list may sit at the key's own indentation ("key:\n- a").
            boolean sameIndentItem = li == ind && line.strip().startsWith("-") && ITEM_LINE.matcher(line).matches();
            if (li <= ind && !sameIndentItem) {
                break;
            }
            lastContent = j;
        }
        end = lastContent + 1;
        return end;
    }

    private List<Integer> blockChildren(int i) {
        List<Integer> out = new ArrayList<>();
        for (int j = i + 1; j < blockEnd(i); j++) {
            if (!isIgnorable(lines.get(j))) {
                out.add(j);
            }
        }
        return out;
    }

    private List<Integer> blockItems(int i) {
        List<Integer> out = new ArrayList<>();
        int itemIndent = -1;
        for (int j : blockChildren(i)) {
            Matcher m = ITEM_LINE.matcher(lines.get(j));
            if (!m.matches()) {
                if (itemIndent < 0) {
                    throw new IllegalArgumentException("line " + (j + 1) + " is not a list item");
                }
                continue; // continuation of a nested item
            }
            int ind = m.group(1).length();
            if (itemIndent < 0) {
                itemIndent = ind;
            }
            if (ind == itemIndent) {
                out.add(j);
            }
        }
        return out;
    }

    private void insertMissing(String path, String yamlValue) {
        String[] segs = path.split("\\.");
        int depth = segs.length - 1;
        int parent = -1;
        while (depth > 0) {
            parent = find(String.join(".", java.util.Arrays.copyOf(segs, depth)));
            if (parent >= 0) {
                break;
            }
            depth--;
        }
        if (parent >= 0 && !valueOf(lines.get(parent)).isEmpty()) {
            throw new IllegalArgumentException(String.join(".", java.util.Arrays.copyOf(segs, depth)) + " is a scalar, not a section");
        }
        String baseIndent;
        int insertAt;
        String step = "  ";
        if (parent < 0) {
            baseIndent = "";
            insertAt = lines.size();
            while (insertAt > 0 && lines.get(insertAt - 1).isBlank()) {
                insertAt--;
            }
        } else {
            List<Integer> children = blockChildren(parent);
            String parentIndent = indentOf(lines.get(parent));
            baseIndent = children.isEmpty() ? parentIndent + step : indentOf(lines.get(children.getFirst()));
            if (baseIndent.length() > parentIndent.length()) {
                step = baseIndent.substring(parentIndent.length());
            }
            insertAt = blockEnd(parent);
        }
        List<String> add = new ArrayList<>();
        String indent = baseIndent;
        for (int k = depth; k < segs.length; k++) {
            boolean leaf = k == segs.length - 1;
            add.add(indent + quoteKey(segs[k]) + ":" + (leaf ? " " + yamlValue : ""));
            indent = indent + step;
        }
        lines.addAll(insertAt, add);
    }

    // ---- line helpers ----

    private static boolean isIgnorable(String line) {
        String s = line.strip();
        return s.isEmpty() || s.startsWith("#") || s.equals("---") || s.equals("...");
    }

    private static String indentOf(String line) {
        int n = 0;
        while (n < line.length() && (line.charAt(n) == ' ' || line.charAt(n) == '\t')) {
            n++;
        }
        return line.substring(0, n);
    }

    static String keyOf(String line) {
        Matcher m = KEY_LINE.matcher(line);
        if (!m.find()) {
            return null;
        }
        for (int g = 2; g <= 4; g++) {
            if (m.group(g) != null) {
                return m.group(g).strip();
            }
        }
        return null;
    }

    /** The value text after {@code key:}, comment removed, stripped. */
    static String valueOf(String line) {
        Matcher m = KEY_LINE.matcher(line);
        if (!m.find()) {
            throw new IllegalArgumentException("not a key line: " + line);
        }
        return stripComment(line.substring(m.end())).strip();
    }

    private static String replaceValue(String line, String yamlValue) {
        Matcher m = KEY_LINE.matcher(line);
        if (!m.find()) {
            throw new IllegalArgumentException("not a key line: " + line);
        }
        String head = line.substring(0, m.end());
        String rest = line.substring(m.end());
        int c = commentStart(rest);
        String comment = "";
        if (c >= 0) {
            String before = rest.substring(0, c);
            String value = before.strip();
            // keep the original gap between value and comment
            int gapStart = before.length();
            while (gapStart > 0 && Character.isWhitespace(before.charAt(gapStart - 1))) {
                gapStart--;
            }
            String gap = value.isEmpty() ? " " : before.substring(gapStart);
            comment = (gap.isEmpty() ? " " : gap) + rest.substring(c);
        }
        return head + " " + yamlValue + comment;
    }

    /** Index of a {@code #} starting a comment (preceded by whitespace or at 0, outside quotes), or -1. */
    private static int commentStart(String s) {
        boolean sq = false;
        boolean dq = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\'' && !dq) {
                sq = !sq;
            } else if (ch == '"' && !sq) {
                dq = !dq;
            } else if (ch == '#' && !sq && !dq && (i == 0 || Character.isWhitespace(s.charAt(i - 1)))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripComment(String s) {
        int c = commentStart(s);
        return c < 0 ? s : s.substring(0, c);
    }

    static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static List<String> parseFlow(String raw) {
        List<String> out = new ArrayList<>();
        for (String lit : splitFlowLiterals(raw)) {
            out.add(unquote(lit));
        }
        return out;
    }

    private static List<String> splitFlowLiterals(String raw) {
        String s = raw.strip();
        if (!s.startsWith("[") || !s.endsWith("]")) {
            throw new IllegalArgumentException("unsupported flow list: " + raw);
        }
        String body = s.substring(1, s.length() - 1);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean sq = false;
        boolean dq = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'' && !dq) {
                sq = !sq;
            } else if (ch == '"' && !sq) {
                dq = !dq;
            } else if ((ch == '[' || ch == '{') && !sq && !dq) {
                throw new IllegalArgumentException("nested flow collections are not supported: " + raw);
            }
            if (ch == ',' && !sq && !dq) {
                out.add(cur.toString().strip());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().strip());
        }
        out.removeIf(String::isEmpty);
        return out;
    }

    private static String flow(List<String> items) {
        List<String> lits = new ArrayList<>();
        for (String s : items) {
            lits.add(quote(s));
        }
        return "[" + String.join(", ", lits) + "]";
    }

    /**
     * A YAML literal for a scalar value: booleans and integers plain (so they stay booleans/integers),
     * other text as {@link #quote}.
     */
    public static String literal(String value) {
        if (value.matches("(?i)true|false") || value.matches("-?\\d+")) {
            return value;
        }
        return quote(value);
    }

    /** A YAML literal for a string: plain if safe, else double-quoted. */
    public static String quote(String s) {
        if (s.matches("[A-Za-z0-9_./-]+") && !s.matches("(?i)true|false|yes|no|on|off|null|~") && !s.startsWith("-")) {
            return s;
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String quoteKey(String key) {
        return key.matches("[A-Za-z0-9_-]+") ? key : "\"" + key + "\"";
    }
}
