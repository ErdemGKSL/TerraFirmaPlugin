/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/knapping/ @ c3b4d9536 (the 101 recipes), file: src/generated/resources/data/tfc/tfc/knapping_type/ @ c3b4d9536 (the 5 types)
 */
package dev.tfp.core.knap;

import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the transcribed TFC knapping data and its golden rows from test resources. Both files are generated from the TFC JSON at
 * c3b4d9536 by a scratch script; the recipe format is one {@code RECIPE} line per recipe with the pattern rows joined by {@code /}
 * (a {@code .} is an off cell).
 */
final class KnappingFixture {
    static final String RECIPES = "/dev/tfp/core/knap/knapping-recipes.txt";
    static final String GOLDEN = "/dev/tfp/core/knap/knapping-golden.txt";

    /** {@code MATCH <grid> <type> <ids or ->}: the recipes of the type whose pattern the grid matches. */
    record MatchRow(String grid, String type, List<String> ids) {}

    /** {@code PICK <grid> <type> <item> <id or ->}: the recipe {@link KnappingRules#find} gives. */
    record PickRow(String grid, String type, String item, String id) {}

    /** {@code ITEM <item> <type or ->}: the knapping type of an item. */
    record ItemRow(String item, String type) {}

    record Data(List<KnappingType> types, List<KnappingRecipe> recipes, TagLookup tags, int tagLines,
                List<MatchRow> matches, List<PickRow> picks, List<ItemRow> items) {}

    private KnappingFixture() {}

    static Data load() {
        final List<KnappingType> types = new ArrayList<>();
        final List<KnappingRecipe> recipes = new ArrayList<>();
        final Map<String, Set<String>> tags = new HashMap<>();
        for (String line : lines(RECIPES)) {
            final String[] t = line.split(" ");
            switch (t[0]) {
                case "TYPE" -> {
                    if (t.length != 10) throw new IllegalStateException("TYPE line: " + line);
                    types.add(new KnappingType(t[1], KnappingIngredient.parse(t[2]), Integer.parseInt(t[3]), Integer.parseInt(t[4]),
                        Boolean.parseBoolean(t[5]), Boolean.parseBoolean(t[6]), Boolean.parseBoolean(t[7]), t[8], t[9]));
                }
                case "TAG" -> {
                    final Set<String> members = new HashSet<>();
                    for (int i = 2; i < t.length; i++) members.add(t[i]);
                    tags.put(t[1], members);
                }
                case "RECIPE" -> recipes.add(recipe(t, line));
                default -> throw new IllegalStateException("unknown line: " + line);
            }
        }
        final TagLookup lookup = new MapTags(tags);
        final List<MatchRow> matches = new ArrayList<>();
        final List<PickRow> picks = new ArrayList<>();
        final List<ItemRow> items = new ArrayList<>();
        for (String line : lines(GOLDEN)) {
            final String[] t = line.split(" ");
            switch (t[0]) {
                case "MATCH" -> matches.add(new MatchRow(t[1], t[2], t[3].equals("-") ? List.of() : List.of(t[3].split(","))));
                case "PICK" -> picks.add(new PickRow(t[1], t[2], t[3], t[4].equals("-") ? null : t[4]));
                case "ITEM" -> items.add(new ItemRow(t[1], t[2].equals("-") ? null : t[2]));
                default -> throw new IllegalStateException("unknown golden line: " + line);
            }
        }
        return new Data(List.copyOf(types), List.copyOf(recipes), lookup, tags.size(), List.copyOf(matches), List.copyOf(picks), List.copyOf(items));
    }

    /** The grid string of a pattern rows list, for the golden rows: {@code #} on, {@code .} off, row by row. */
    static String cells(KnappingPattern p) {
        final StringBuilder sb = new StringBuilder(25);
        for (int i = 0; i < 25; i++) sb.append(p.get(i) ? '#' : '.');
        return sb.toString();
    }

    private static KnappingRecipe recipe(String[] t, String line) {
        if (t.length != 9) throw new IllegalStateException("RECIPE line: " + line);
        final List<String> rows = new ArrayList<>();
        for (String row : t[8].split("/")) rows.add(row.replace('.', ' '));
        final Optional<Boolean> defaultOn = t[3].equals("-") ? Optional.empty() : Optional.of(Boolean.parseBoolean(t[3]));
        final KnappingPattern pattern = KnappingPattern.of(rows, defaultOn);
        final KnappingIngredient ingredient = t[4].equals("-") ? null : KnappingIngredient.parse(t[4]);
        final Map<String, String> components = new LinkedHashMap<>();
        if (!t[7].equals("-")) {
            for (String kv : t[7].split(";")) {
                final int eq = kv.indexOf('=');
                components.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        return new KnappingRecipe(t[1], t[2], pattern, ingredient, new KnappingResult(t[5], Integer.parseInt(t[6]), components));
    }

    private static List<String> lines(String resource) {
        final List<String> out = new ArrayList<>();
        try (InputStream in = KnappingFixture.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) out.add(line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }

    /** Item tags from the TAG lines, nested tags already flattened by the generator. */
    private record MapTags(Map<String, Set<String>> tags) implements TagLookup {
        @Override
        public boolean contains(TagType type, String tag, String id) {
            return type == TagType.ITEM && tags.getOrDefault(tag, Set.of()).contains(id);
        }

        @Override
        public boolean exists(TagType type, String tag) {
            return type == TagType.ITEM && tags.containsKey(tag);
        }
    }
}
