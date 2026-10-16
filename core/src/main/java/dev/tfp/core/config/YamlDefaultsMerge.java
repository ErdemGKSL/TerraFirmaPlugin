/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comment-preserving defaults merge for our {@code config.yml}, done on text because YAML libraries
 * drop comments on a load/save round trip.
 *
 * <p>The owner's file is never rewritten: each top-level key of the shipped defaults that is missing
 * from it is appended at the end, together with the comment block written directly above it in the
 * defaults (a blank line between a comment and its key detaches the comment). Missing keys nested
 * inside a section the owner already has are not added; the typed parse fills them with defaults.
 */
public final class YamlDefaultsMerge {
    private static final Pattern TOP_LEVEL_KEY =
            Pattern.compile("^(?:\"([^\"]*)\"|'([^']*)'|([^\\s#:\\-\"'][^:#]*?))\\s*:(?:\\s|$)");

    private YamlDefaultsMerge() {
    }

    /** @param text the merged file; equal to the input when {@code added} is empty */
    public record Result(String text, List<String> added) {
        public Result {
            added = List.copyOf(added);
        }

        public boolean changed() {
            return !added.isEmpty();
        }
    }

    public static Result merge(String existing, String defaults) {
        Set<String> present = topLevelKeys(existing);
        Map<String, List<String>> blocks = blocks(defaults);
        String nl = existing.contains("\r\n") ? "\r\n" : "\n";
        StringBuilder out = new StringBuilder(existing);
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : blocks.entrySet()) {
            if (present.contains(e.getKey())) {
                continue;
            }
            if (out.length() > 0 && !endsWithNewline(out)) {
                out.append(nl);
            }
            if (out.length() > 0) {
                out.append(nl);
            }
            for (String line : e.getValue()) {
                out.append(line).append(nl);
            }
            added.add(e.getKey());
        }
        return new Result(added.isEmpty() ? existing : out.toString(), added);
    }

    /** The top-level mapping keys in file order (quotes removed). */
    public static Set<String> topLevelKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : lines(text)) {
            String k = topLevelKey(line);
            if (k != null) {
                keys.add(k);
            }
        }
        return keys;
    }

    /** Each top-level key of {@code text} with its attached comment block and its body, in file order. */
    static Map<String, List<String>> blocks(String text) {
        Map<String, List<String>> blocks = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();
        List<String> current = null;
        for (String line : lines(text)) {
            String key = topLevelKey(line);
            if (key != null) {
                trimTrailingBlank(current);
                current = new ArrayList<>(pendingComments);
                current.add(line);
                blocks.put(key, current);
                pendingComments.clear();
            } else if (line.startsWith("#")) {
                trimTrailingBlank(current);
                current = null;
                pendingComments.add(line);
            } else if (line.isBlank()) {
                pendingComments.clear();
                if (current != null) {
                    current.add(line);
                }
            } else if (current != null) {
                current.add(line);
            }
        }
        trimTrailingBlank(current);
        return blocks;
    }

    static String topLevelKey(String line) {
        if (line.isEmpty() || Character.isWhitespace(line.charAt(0)) || line.startsWith("---") || line.startsWith("...")) {
            return null;
        }
        Matcher m = TOP_LEVEL_KEY.matcher(line);
        if (!m.find()) {
            return null;
        }
        for (int g = 1; g <= 3; g++) {
            if (m.group(g) != null) {
                return m.group(g);
            }
        }
        return null;
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }

    private static boolean endsWithNewline(CharSequence s) {
        return s.charAt(s.length() - 1) == '\n';
    }

    private static void trimTrailingBlank(List<String> block) {
        while (block != null && !block.isEmpty() && block.getLast().isBlank()) {
            block.removeLast();
        }
    }
}
