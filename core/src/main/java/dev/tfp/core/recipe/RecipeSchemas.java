/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/TFCRecipeSerializers.java @ c3b4d9536
 */
package dev.tfp.core.recipe;

import static dev.tfp.core.json.ObjectSchema.optional;
import static dev.tfp.core.json.ObjectSchema.required;

import com.google.gson.JsonElement;
import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.DecodeContext;
import dev.tfp.core.json.ObjectSchema;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.json.TypeDispatch;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.recipe.block.BlockStateRef;
import dev.tfp.core.recipe.fluid.FluidStackTemplate;
import dev.tfp.core.recipe.fluid.SizedFluidIngredient;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackProvider;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagType;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict schemas for every recipe type in TFC's data (field names, optionality and value types
 * taken from each recipe class's {@code CODEC}). Decoding yields a {@link SchemaRecord}; the
 * feature tasks that port each recipe (M3/M4: heating, anvil, barrel, ...) replace their entry with
 * a typed record. Unknown recipe types and unknown keys fail the load.
 */
public final class RecipeSchemas {
    private RecipeSchemas() {
    }

    /** Registry name used for knapping type references (our {@code knapping_type} data registry). */
    public static final String KNAPPING_TYPE = "knapping_type";

    /** Shaped-recipe key: single non-space character → ingredient. */
    public static final Codec<Map<Character, Ingredient>> KEY = (json, ctx) -> {
        if (!json.isJsonObject()) {
            throw ctx.error("expected an object of symbol → ingredient, got " + Codecs.describe(json));
        }
        Map<Character, Ingredient> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
            DecodeContext c = ctx.field(e.getKey());
            if (e.getKey().length() != 1 || e.getKey().equals(" ")) {
                throw c.error("key symbol must be a single non-space character");
            }
            out.put(e.getKey().charAt(0), Ingredient.CODEC.decode(e.getValue(), c));
        }
        return Collections.unmodifiableMap(out);
    };

    /** Rows of equal width, {@code 1..maxRows} rows of {@code 1..maxWidth} chars. */
    public static Codec<List<String>> pattern(int maxRows, int maxWidth) {
        return Codecs.STRING.validate(s -> !s.isEmpty() && s.length() <= maxWidth, "pattern rows must be 1.." + maxWidth + " chars")
                .listOf(1, maxRows)
                .validate(rows -> rows.stream().mapToInt(String::length).distinct().count() == 1, "pattern rows must have equal width");
    }

    private static final ObjectSchema.Check SHAPED_KEYS = (record, ctx) -> {
        @SuppressWarnings("unchecked")
        Map<Character, Ingredient> key = record.require("key", Map.class);
        @SuppressWarnings("unchecked")
        List<String> pattern = record.require("pattern", List.class);
        Set<Character> used = new HashSet<>();
        for (String row : pattern) {
            for (char ch : row.toCharArray()) {
                if (ch != ' ') {
                    if (!key.containsKey(ch)) {
                        throw ctx.field("pattern").error("symbol '" + ch + "' is not defined in key");
                    }
                    used.add(ch);
                }
            }
        }
        if (!used.containsAll(key.keySet())) {
            throw ctx.field("key").error("key defines symbols not used in pattern: " + key.keySet().stream().filter(k -> !used.contains(k)).toList());
        }
    };

    private static final Codec<String> CRAFTING_CATEGORY = Codecs.oneOf("building", "redstone", "equipment", "misc");
    private static final Codec<List<Ingredient>> SHAPELESS_INGREDIENTS = Ingredient.CODEC.listOf(1, 9);
    private static final Codec<String> FORGE_RULE = Codecs.oneOf(forgeRules());
    private static final Codec<String> SOUND = Codecs.ID;
    private static final Codec<String> FLUID = Codecs.ref(TagType.FLUID.directory());

    private static final ObjectSchema BARREL = ObjectSchema.of("tfc:barrel",
            optional("input_item", SizedIngredient.CODEC),
            required("input_fluid", SizedFluidIngredient.CODEC),
            optional("output_item", ItemStackProvider.CODEC),
            optional("output_fluid", FluidStackTemplate.CODEC),
            optional("sound", SOUND));

    private static final ObjectSchema POT = ObjectSchema.of("tfc:pot",
            required("ingredients", Ingredient.CODEC.listOf(0, 5)),
            required("fluid_ingredient", SizedFluidIngredient.CODEC),
            required("duration", Codecs.INT),
            required("temperature", Codecs.FLOAT));

    private static final ObjectSchema BLOCK_RECIPE = ObjectSchema.of("tfc:block_recipe",
            required("ingredient", BlockIngredient.CODEC),
            optional("result", BlockStateRef.CODEC));

    private static final ObjectSchema ALLOY_RANGE = ObjectSchema.of("tfc:alloy_range",
            required("fluid", FLUID),
            required("min", Codecs.DOUBLE),
            required("max", Codecs.DOUBLE));

    /** Every recipe type in TFC's data, keyed by {@code "type"}. */
    public static final TypeDispatch<SchemaRecord> TYPES = TypeDispatch.<SchemaRecord>builder("recipe")
            .register("minecraft:crafting_shaped", ObjectSchema.of("minecraft:crafting_shaped",
                    optional("group", Codecs.STRING),
                    optional("category", CRAFTING_CATEGORY),
                    required("key", KEY),
                    required("pattern", pattern(3, 3)),
                    required("result", ItemStackTemplate.CODEC),
                    optional("show_notification", Codecs.BOOL)).checked(SHAPED_KEYS))
            .register("minecraft:crafting_shapeless", ObjectSchema.of("minecraft:crafting_shapeless",
                    optional("group", Codecs.STRING),
                    optional("category", CRAFTING_CATEGORY),
                    required("ingredients", SHAPELESS_INGREDIENTS),
                    required("result", ItemStackTemplate.CODEC)))
            .register("tfc:advanced_shaped_crafting", ObjectSchema.of("tfc:advanced_shaped_crafting",
                    required("key", KEY),
                    required("pattern", pattern(3, 3)),
                    optional("show_notification", Codecs.BOOL),
                    required("result", ItemStackProvider.CODEC),
                    optional("remainder", ItemStackProvider.CODEC),
                    optional("input_row", Codecs.NON_NEGATIVE_INT),
                    optional("input_column", Codecs.NON_NEGATIVE_INT)).checked(SHAPED_KEYS))
            .register("tfc:advanced_shapeless_crafting", ObjectSchema.of("tfc:advanced_shapeless_crafting",
                    required("ingredients", SHAPELESS_INGREDIENTS),
                    required("result", ItemStackProvider.CODEC),
                    optional("remainder", ItemStackProvider.CODEC),
                    optional("primary_ingredient", Ingredient.CODEC)))
            .register("tfc:food_combining", ObjectSchema.of("tfc:food_combining", optional("category", CRAFTING_CATEGORY)))
            .register("tfc:casting_crafting", ObjectSchema.of("tfc:casting_crafting", optional("category", CRAFTING_CATEGORY)))
            .register("tfc:heating", ObjectSchema.of("tfc:heating",
                    required("ingredient", Ingredient.CODEC),
                    optional("result_item", ItemStackProvider.CODEC),
                    optional("result_fluid", FluidStackTemplate.CODEC),
                    required("temperature", Codecs.FLOAT),
                    optional("use_durability", Codecs.BOOL)))
            .register("tfc:collapse", BLOCK_RECIPE.with("tfc:collapse"))
            .register("tfc:landslide", BLOCK_RECIPE.with("tfc:landslide"))
            .register("tfc:chisel", ObjectSchema.of("tfc:chisel",
                    required("ingredient", BlockIngredient.CODEC),
                    required("result", BlockStateRef.CODEC),
                    required("mode", Codecs.oneOfTfcIds("tfc:smooth", "tfc:stair", "tfc:slab")),
                    optional("item_output", ItemStackProvider.CODEC)))
            .register("tfc:anvil", ObjectSchema.of("tfc:anvil",
                    required("ingredient", Ingredient.CODEC),
                    optional("tier", Codecs.INT),
                    required("rules", FORGE_RULE.listOf()),
                    optional("apply_bonus", Codecs.BOOL),
                    required("result", ItemStackProvider.CODEC)))
            .register("tfc:welding", ObjectSchema.of("tfc:welding",
                    required("first_input", Ingredient.CODEC),
                    required("second_input", Ingredient.CODEC),
                    optional("tier", Codecs.INT),
                    required("result", ItemStackProvider.CODEC),
                    optional("bonus", Codecs.oneOf("copy_best", "copy_worst", "ignore"))))
            .register("tfc:barrel_sealed", BARREL.with("tfc:barrel_sealed",
                    required("duration", Codecs.INT),
                    optional("on_seal", ItemStackProvider.CODEC),
                    optional("on_unseal", ItemStackProvider.CODEC)))
            .register("tfc:barrel_instant", BARREL.with("tfc:barrel_instant"))
            .register("tfc:barrel_instant_fluid", ObjectSchema.of("tfc:barrel_instant_fluid",
                    required("primary_fluid", SizedFluidIngredient.CODEC),
                    required("added_fluid", SizedFluidIngredient.CODEC),
                    optional("output_fluid", FluidStackTemplate.CODEC),
                    optional("sound", SOUND)))
            .register("tfc:pot", POT.with("tfc:pot",
                    optional("fluid_output", FluidStackTemplate.CODEC),
                    optional("item_output", ItemStackProvider.CODEC.listOf(0, 5)),
                    optional("uses_all_fluid", Codecs.BOOL)))
            .register("tfc:pot_soup", POT.with("tfc:pot_soup"))
            .register("tfc:pot_jam", POT.with("tfc:pot_jam",
                    required("unsealed_result", ItemStackTemplate.CODEC),
                    required("sealed_result", ItemStackTemplate.CODEC),
                    required("texture", Codecs.ID)))
            .register("tfc:casting", ObjectSchema.of("tfc:casting",
                    required("mold", Ingredient.CODEC),
                    required("fluid", SizedFluidIngredient.CODEC),
                    required("result", ItemStackProvider.CODEC),
                    optional("break_chance", Codecs.FLOAT)))
            .register("tfc:knapping", ObjectSchema.of("tfc:knapping",
                    required("knapping_type", Codecs.ref(KNAPPING_TYPE)),
                    required("pattern", pattern(5, 5)),
                    optional("default_on", Codecs.BOOL),
                    optional("ingredient", Ingredient.CODEC),
                    required("result", ItemStackTemplate.CODEC)))
            .register("tfc:quern", ObjectSchema.of("tfc:quern",
                    required("ingredient", Ingredient.CODEC),
                    required("result", ItemStackProvider.CODEC)))
            .register("tfc:glassworking", ObjectSchema.of("tfc:glassworking",
                    required("operations", Codecs.ID.listOf(1, Integer.MAX_VALUE)),
                    required("batch", Ingredient.CODEC),
                    required("result", ItemStackTemplate.CODEC)))
            .register("tfc:sewing", ObjectSchema.of("tfc:sewing",
                    required("stitches", Codecs.grid(9, 5, "# ")),
                    required("squares", Codecs.grid(8, 4, "BW ")),
                    required("result", ItemStackTemplate.CODEC)))
            .register("tfc:alloy", ObjectSchema.of("tfc:alloy",
                    required("contents", ALLOY_RANGE.codec().listOf(1, Integer.MAX_VALUE)),
                    required("result", FLUID)))
            .register("tfc:scraping", ObjectSchema.of("tfc:scraping",
                    required("ingredient", Ingredient.CODEC),
                    required("result", ItemStackProvider.CODEC),
                    required("input_texture", Codecs.ID),
                    required("output_texture", Codecs.ID),
                    optional("result_item", ItemStackProvider.CODEC)))
            .register("tfc:loom", ObjectSchema.of("tfc:loom",
                    required("ingredient", SizedIngredient.CODEC),
                    required("result", ItemStackProvider.CODEC),
                    required("steps", Codecs.INT),
                    required("texture", Codecs.ID)))
            .register("tfc:bloomery", ObjectSchema.of("tfc:bloomery",
                    required("fluid", SizedFluidIngredient.CODEC),
                    required("catalyst", SizedIngredient.CODEC),
                    required("result", ItemStackProvider.CODEC),
                    required("duration", Codecs.INT)))
            .register("tfc:blast_furnace", ObjectSchema.of("tfc:blast_furnace",
                    required("fluid", SizedFluidIngredient.CODEC),
                    required("catalyst", Ingredient.CODEC),
                    required("result", FluidStackTemplate.CODEC)))
            .build();

    public static final Codec<SchemaRecord> CODEC = TYPES.codec();

    /** TFC {@code ForgeRule} serialized names: {action}_{order}. */
    private static String[] forgeRules() {
        List<String> actions = List.of("hit", "draw", "punch", "bend", "upset", "shrink");
        List<String> orders = List.of("any", "last", "not_last", "second_last", "third_last");
        return actions.stream().flatMap(a -> orders.stream().map(o -> a + "_" + o)).toArray(String[]::new);
    }
}
