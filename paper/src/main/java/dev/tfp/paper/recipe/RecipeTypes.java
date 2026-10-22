/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/TFCRecipeTypes.java @ c3b4d9536 (the recipe types and the
 * ingredient each recipe's IndirectHashCollection cache is keyed by)
 */
package dev.tfp.paper.recipe;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The recipe types of TFC's data (by their TFC names, as {@code SchemaRecord.schema()} gives them) and which fields each
 * one is indexed by. TFC keys its caches by the input item ({@code HeatingRecipe.CACHE}, {@code QuernRecipe.CACHE}, ...)
 * or block ({@code CollapseRecipe.CACHE}, {@code ChiselRecipe.CACHE}); we index every type in the same three key
 * spaces (item, block, fluid) plus a plain string key (the knapping type), so each device lookup is a hash lookup.
 */
public final class RecipeTypes {
    private RecipeTypes() {
    }

    public static final String CRAFTING_SHAPED = "minecraft:crafting_shaped";
    public static final String CRAFTING_SHAPELESS = "minecraft:crafting_shapeless";
    public static final String ADVANCED_SHAPED = "tfc:advanced_shaped_crafting";
    public static final String ADVANCED_SHAPELESS = "tfc:advanced_shapeless_crafting";
    public static final String FOOD_COMBINING = "tfc:food_combining";
    public static final String CASTING_CRAFTING = "tfc:casting_crafting";
    public static final String HEATING = "tfc:heating";
    public static final String COLLAPSE = "tfc:collapse";
    public static final String LANDSLIDE = "tfc:landslide";
    public static final String CHISEL = "tfc:chisel";
    public static final String ANVIL = "tfc:anvil";
    public static final String WELDING = "tfc:welding";
    public static final String BARREL_SEALED = "tfc:barrel_sealed";
    public static final String BARREL_INSTANT = "tfc:barrel_instant";
    public static final String BARREL_INSTANT_FLUID = "tfc:barrel_instant_fluid";
    public static final String POT = "tfc:pot";
    public static final String POT_SOUP = "tfc:pot_soup";
    public static final String POT_JAM = "tfc:pot_jam";
    public static final String CASTING = "tfc:casting";
    public static final String KNAPPING = "tfc:knapping";
    public static final String QUERN = "tfc:quern";
    public static final String GLASSWORKING = "tfc:glassworking";
    public static final String SEWING = "tfc:sewing";
    public static final String ALLOY = "tfc:alloy";
    public static final String SCRAPING = "tfc:scraping";
    public static final String LOOM = "tfc:loom";
    public static final String BLOOMERY = "tfc:bloomery";
    public static final String BLAST_FURNACE = "tfc:blast_furnace";

    /**
     * The fields a type is indexed by. A key space with no fields makes every recipe of the type a wildcard there
     * (it is returned for every key); a listed field that is absent on a recipe (an optional input) does too.
     *
     * @param item  fields holding item ingredients ({@code Ingredient}, {@code SizedIngredient}, lists, shaped keys)
     * @param block fields holding a {@code BlockIngredient}
     * @param fluid fields holding fluid ingredients or fluid ids
     * @param key   a field holding a plain string key, if any
     */
    public record KeySpec(List<String> item, List<String> block, List<String> fluid, Optional<String> key) {
        public KeySpec {
            item = List.copyOf(item);
            block = List.copyOf(block);
            fluid = List.copyOf(fluid);
        }

        static KeySpec items(String... fields) {
            return new KeySpec(List.of(fields), List.of(), List.of(), Optional.empty());
        }

        KeySpec fluids(String... fields) {
            return new KeySpec(item, block, List.of(fields), key);
        }

        KeySpec key(String field) {
            return new KeySpec(item, block, fluid, Optional.of(field));
        }

        static KeySpec blocks(String field) {
            return new KeySpec(List.of(), List.of(field), List.of(), Optional.empty());
        }

        static final KeySpec NONE = new KeySpec(List.of(), List.of(), List.of(), Optional.empty());
    }

    /** Every type in TFC's data and its key fields. A type missing here is indexed with {@link KeySpec#NONE}. */
    public static final Map<String, KeySpec> SPECS = Map.ofEntries(
            Map.entry(CRAFTING_SHAPED, KeySpec.items("key")),
            Map.entry(CRAFTING_SHAPELESS, KeySpec.items("ingredients")),
            Map.entry(ADVANCED_SHAPED, KeySpec.items("key")),
            Map.entry(ADVANCED_SHAPELESS, KeySpec.items("ingredients")),
            Map.entry(FOOD_COMBINING, KeySpec.NONE),
            Map.entry(CASTING_CRAFTING, KeySpec.NONE),
            Map.entry(HEATING, KeySpec.items("ingredient")),
            Map.entry(COLLAPSE, KeySpec.blocks("ingredient")),
            Map.entry(LANDSLIDE, KeySpec.blocks("ingredient")),
            Map.entry(CHISEL, KeySpec.blocks("ingredient")),
            Map.entry(ANVIL, KeySpec.items("ingredient")),
            Map.entry(WELDING, KeySpec.items("first_input", "second_input")),
            Map.entry(BARREL_SEALED, KeySpec.items("input_item").fluids("input_fluid")),
            Map.entry(BARREL_INSTANT, KeySpec.items("input_item").fluids("input_fluid")),
            Map.entry(BARREL_INSTANT_FLUID, KeySpec.NONE.fluids("primary_fluid")),
            Map.entry(POT, KeySpec.items("ingredients").fluids("fluid_ingredient")),
            Map.entry(POT_SOUP, KeySpec.items("ingredients").fluids("fluid_ingredient")),
            Map.entry(POT_JAM, KeySpec.items("ingredients").fluids("fluid_ingredient")),
            Map.entry(CASTING, KeySpec.items("mold").fluids("fluid")),
            Map.entry(KNAPPING, KeySpec.items("ingredient").key("knapping_type")),
            Map.entry(QUERN, KeySpec.items("ingredient")),
            Map.entry(GLASSWORKING, KeySpec.items("batch")),
            Map.entry(SEWING, KeySpec.NONE),
            Map.entry(ALLOY, KeySpec.NONE.fluids("contents")),
            Map.entry(SCRAPING, KeySpec.items("ingredient")),
            Map.entry(LOOM, KeySpec.items("ingredient")),
            Map.entry(BLOOMERY, KeySpec.items("catalyst").fluids("fluid")),
            Map.entry(BLAST_FURNACE, KeySpec.items("catalyst").fluids("fluid")));

    public static KeySpec spec(String type) {
        return SPECS.getOrDefault(type, KeySpec.NONE);
    }
}
