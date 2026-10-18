/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.anvil;

import dev.tfp.core.craft.CraftOutput;
import dev.tfp.core.forge.ForgeRule;
import dev.tfp.core.recipe.hooks.FluidHooks;
import dev.tfp.core.recipe.hooks.FoodHooks;
import dev.tfp.core.recipe.hooks.HeatHooks;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.IngredientContext;
import dev.tfp.core.tag.TagLookup;
import dev.tfp.core.tag.TagType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads the golden TFC transcriptions of the anvil and welding recipes. The TSV files were written by a scratch script from TFC's recipe
 * JSON at c3b4d9536, together with the item tag membership the recipes reference (nested tags flattened).
 */
final class AnvilFixtures {
    static final String ANVIL_TSV = "/dev/tfp/core/anvil/tfc-anvil-recipes.tsv";
    static final String WELDING_TSV = "/dev/tfp/core/anvil/tfc-welding-recipes.tsv";
    static final String TAGS_TSV = "/dev/tfp/core/anvil/tfc-item-tags.tsv";

    /** An item that is in none of the golden tags, so no recipe input accepts it. */
    static final String NOT_AN_INGREDIENT = "minecraft:air";

    /** One anvil recipe row: its file under {@code recipe/anvil}, the ingredient, the tier, the rules, the bonus flag and the result. */
    record AnvilRow(String file, Ingredient input, int tier, List<ForgeRule> rules, boolean applyBonus, CraftOutput output) {
        String id() {
            return "tfc:anvil/" + file;
        }

        AnvilRecipe recipe() {
            return new AnvilRecipe(id(), input, tier, rules, applyBonus, output);
        }
    }

    /** One welding recipe row: its file under {@code recipe/welding}, the two inputs, the tier, the bonus rule and the result. */
    record WeldingRow(String file, Ingredient first, Ingredient second, int tier, WeldingRecipe.Bonus bonus, CraftOutput output) {
        String id() {
            return "tfc:welding/" + file;
        }

        WeldingRecipe recipe() {
            return new WeldingRecipe(id(), first, second, tier, output, bonus);
        }
    }

    private AnvilFixtures() {}

    static List<String> lines(String resource) {
        try (InputStream in = AnvilFixtures.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The file count the scratch script wrote in the {@code # files} header line of a recipe TSV. */
    static int fileCount(String resource) {
        for (String line : lines(resource)) {
            if (line.startsWith("# files\t")) {
                return Integer.parseInt(line.substring("# files\t".length()).trim());
            }
        }
        throw new IllegalStateException("no '# files' line in " + resource);
    }

    private static List<String[]> rows(String resource, String kind) {
        return lines(resource).stream()
            .filter(l -> !l.startsWith("#") && !l.isEmpty())
            .map(l -> l.split("\t", -1))
            .filter(c -> c[0].equals(kind))
            .toList();
    }

    private static Ingredient ingredient(String kind, String id) {
        return switch (kind) {
            case "tag" -> new Ingredient.Tag(id);
            case "item" -> new Ingredient.Item(id);
            default -> throw new IllegalStateException("unknown ingredient kind " + kind);
        };
    }

    static List<AnvilRow> anvilRows() {
        final List<AnvilRow> out = new ArrayList<>();
        for (String[] c : rows(ANVIL_TSV, "anvil")) {
            // kind, file, ingredient kind, ingredient id, tier, rules, apply_bonus, result item, result count
            final List<ForgeRule> rules = new ArrayList<>();
            for (String r : c[5].split(",")) {
                rules.add(ForgeRule.valueOf(r.toUpperCase(Locale.ROOT)));
            }
            out.add(new AnvilRow(c[1], ingredient(c[2], c[3]), Integer.parseInt(c[4]), rules, c[6].equals("1"),
                new CraftOutput(c[7], Integer.parseInt(c[8]))));
        }
        return out;
    }

    static List<WeldingRow> weldingRows() {
        final List<WeldingRow> out = new ArrayList<>();
        for (String[] c : rows(WELDING_TSV, "welding")) {
            // kind, file, first kind, first id, second kind, second id, tier, bonus, result item, result count
            out.add(new WeldingRow(c[1], ingredient(c[2], c[3]), ingredient(c[4], c[5]), Integer.parseInt(c[6]),
                WeldingRecipe.Bonus.valueOf(c[7].toUpperCase(Locale.ROOT)), new CraftOutput(c[8], Integer.parseInt(c[9]))));
        }
        return out;
    }

    /** The members of each item tag the recipes reference, from the golden tag TSV, in file order. */
    static Map<String, Set<String>> tagMembers() {
        final Map<String, Set<String>> out = new HashMap<>();
        for (String line : lines(TAGS_TSV)) {
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            final String[] c = line.split("\t", -1);
            out.computeIfAbsent(c[0], k -> new LinkedHashSet<>()).add(c[1]);
        }
        return out;
    }

    /** The items an ingredient accepts among the golden tags: the item itself, or every member of the tag. */
    static Set<String> members(Ingredient ingredient, Map<String, Set<String>> tags) {
        return switch (ingredient) {
            case Ingredient.Item item -> Set.of(item.item());
            case Ingredient.Tag tag -> tags.getOrDefault(tag.tag(), Set.of());
            default -> throw new IllegalStateException("unexpected ingredient " + ingredient);
        };
    }

    /** A context over plain item id strings, with the golden tag membership and no food, heat or fluid data. */
    static IngredientContext<String> context(Map<String, Set<String>> tags) {
        final TagLookup lookup = new TagLookup() {
            @Override
            public boolean contains(TagType type, String tag, String id) {
                return type == TagType.ITEM && tags.getOrDefault(tag, Set.of()).contains(id);
            }

            @Override
            public boolean exists(TagType type, String tag) {
                return type == TagType.ITEM && tags.containsKey(tag);
            }
        };
        return new IngredientContext<>() {
            @Override
            public String itemId(String stack) {
                return stack;
            }

            @Override
            public TagLookup tags() {
                return lookup;
            }

            @Override
            public FoodHooks<String> food() {
                return new FoodHooks<>() {
                    @Override
                    public boolean isRotten(String stack) {
                        return false;
                    }

                    @Override
                    public boolean hasTrait(String stack, String trait) {
                        return false;
                    }
                };
            }

            @Override
            public HeatHooks<String> heat() {
                return stack -> 0f;
            }

            @Override
            public FluidHooks<String> fluids() {
                return stack -> java.util.Optional.empty();
            }
        };
    }
}
