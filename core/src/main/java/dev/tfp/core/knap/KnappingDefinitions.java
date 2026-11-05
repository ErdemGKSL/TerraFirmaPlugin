/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/KnappingType.java @ c3b4d9536 (the registry and the amount_to_consume default)
 */
package dev.tfp.core.knap;

import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.RecipeSchemas;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.registry.DefinitionKinds;
import dev.tfp.core.registry.Registry;
import dev.tfp.core.registry.RegistrySnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every knapping type and knapping recipe, loaded from a registry snapshot (M3 knapping, TFC {@code knapping_type} definitions
 * and {@code tfc:knapping} recipes) into the rules' own records. Immutable.
 *
 * <p>Knapping data only uses item and item-tag ingredients; any other ingredient is a data error and fails the load.
 */
public final class KnappingDefinitions {
    public static final KnappingDefinitions EMPTY = new KnappingDefinitions(List.of(), List.of());

    private final Map<String, KnappingType> types;
    private final List<KnappingRecipe> recipes;
    private final Map<String, ItemStackTemplate> results;

    public KnappingDefinitions(List<KnappingType> types, List<KnappingRecipe> recipes) {
        this(types, recipes, Map.of());
    }

    /** @param results the result stacks as the data writes them, by recipe id (components kept as JSON) */
    public KnappingDefinitions(List<KnappingType> types, List<KnappingRecipe> recipes, Map<String, ItemStackTemplate> results) {
        Map<String, KnappingType> byId = new LinkedHashMap<>();
        for (KnappingType type : types) {
            byId.put(type.id(), type);
        }
        this.types = java.util.Collections.unmodifiableMap(byId);
        this.recipes = List.copyOf(recipes);
        this.results = Map.copyOf(results);
    }

    /** Builds the types from the {@code knapping_type} definitions and the recipes from the knapping entries of {@code recipe}. */
    public static KnappingDefinitions fromRegistry(RegistrySnapshot snapshot) {
        Registry<SchemaRecord> typeData = snapshot.get(DefinitionKinds.key(RecipeSchemas.KNAPPING_TYPE));
        List<KnappingType> types = typeData.asMap().entrySet().stream()
                .map(e -> type(e.getKey(), e.getValue())).toList();
        List<KnappingRecipe> recipes = new java.util.ArrayList<>();
        Map<String, ItemStackTemplate> results = new LinkedHashMap<>();
        for (Map.Entry<String, SchemaRecord> e : snapshot.get(DefinitionKinds.RECIPE).asMap().entrySet()) {
            if (e.getValue().has("knapping_type") && !snapshot.disabledRecipes().contains(e.getKey())) {
                recipes.add(recipe(e.getKey(), e.getValue()));
                results.put(e.getKey(), e.getValue().require("result", ItemStackTemplate.class));
            }
        }
        return new KnappingDefinitions(types, recipes, results);
    }

    private static KnappingType type(String id, SchemaRecord r) {
        SizedIngredient input = r.require("input", SizedIngredient.class);
        // TFC: amount_to_consume defaults to the input count
        int consume = r.get("amount_to_consume", Integer.class).orElse(input.count());
        return new KnappingType(id, ingredient(id, input.ingredient()), input.count(), consume,
                r.require("consume_after_complete", Boolean.class), r.require("has_off_texture", Boolean.class),
                r.require("spawns_particles", Boolean.class), r.require("click_sound", String.class),
                r.require("icon", ItemStackTemplate.class).id());
    }

    @SuppressWarnings("unchecked")
    private static KnappingRecipe recipe(String id, SchemaRecord r) {
        KnappingPattern pattern = KnappingPattern.of(r.require("pattern", List.class), r.get("default_on", Boolean.class));
        KnappingIngredient ingredient = r.get("ingredient", Ingredient.class).map(i -> ingredient(id, i)).orElse(null);
        ItemStackTemplate result = r.require("result", ItemStackTemplate.class);
        Map<String, String> components = new LinkedHashMap<>();
        result.components().ifPresent(c -> c.entrySet().forEach(e -> components.put(e.getKey(),
                e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : e.getValue().toString())));
        return new KnappingRecipe(id, r.require("knapping_type", String.class), pattern, ingredient,
                new KnappingResult(result.id(), result.count(), components));
    }

    private static KnappingIngredient ingredient(String owner, Ingredient ingredient) {
        return switch (ingredient) {
            case Ingredient.Item item -> new KnappingIngredient(item.item(), false);
            case Ingredient.Tag tag -> new KnappingIngredient(tag.tag(), true);
            default -> throw new IllegalArgumentException("knapping data " + owner + " only takes item and tag ingredients");
        };
    }

    public Optional<KnappingType> type(String typeId) {
        return Optional.ofNullable(types.get(typeId));
    }

    public Map<String, KnappingType> types() {
        return types;
    }

    /**
     * The result stack of a recipe as its data wrote it (TFC {@code assemble}: a copy of the result). For definitions built
     * without the data, the template is rebuilt from the recipe's result; a component value that is a JSON object or
     * array is parsed back, any other is a string.
     */
    public ItemStackTemplate resultTemplate(KnappingRecipe recipe) {
        ItemStackTemplate t = results.get(recipe.id());
        if (t != null) {
            return t;
        }
        KnappingResult r = recipe.result();
        if (r.components().isEmpty()) {
            return new ItemStackTemplate(r.item(), r.count(), Optional.empty());
        }
        com.google.gson.JsonObject c = new com.google.gson.JsonObject();
        r.components().forEach((k, v) -> c.add(k, v.startsWith("{") || v.startsWith("[")
                ? com.google.gson.JsonParser.parseString(v) : new com.google.gson.JsonPrimitive(v)));
        return new ItemStackTemplate(r.item(), r.count(), Optional.of(c));
    }

    /** Every recipe, in registry order. */
    public List<KnappingRecipe> recipes() {
        return recipes;
    }

    /** The recipes of one knapping type, in registry order. */
    public List<KnappingRecipe> recipesOf(String typeId) {
        return recipes.stream().filter(r -> r.typeId().equals(typeId)).toList();
    }
}
