/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/data/DataManagers.java @ c3b4d9536
 */
package dev.tfp.core.registry;

import static dev.tfp.core.json.ObjectSchema.optional;
import static dev.tfp.core.json.ObjectSchema.required;

import dev.tfp.core.json.Codec;
import dev.tfp.core.json.Codecs;
import dev.tfp.core.json.ObjectSchema;
import dev.tfp.core.json.SchemaRecord;
import dev.tfp.core.recipe.RecipeSchemas;
import dev.tfp.core.recipe.block.BlockIngredient;
import dev.tfp.core.recipe.fluid.FluidIngredient;
import dev.tfp.core.recipe.food.FoodData;
import dev.tfp.core.recipe.ingredient.Ingredient;
import dev.tfp.core.recipe.ingredient.SizedIngredient;
import dev.tfp.core.recipe.output.ItemStackTemplate;
import dev.tfp.core.tag.TagType;
import java.util.ArrayList;
import java.util.List;

/**
 * The standard data kinds: recipes plus every TFC definition manager ({@code data/tfc/tfc/<kind>}),
 * with strict schemas taken from each class's {@code CODEC} (HeatDefinition, FoodDefinition, Fuel,
 * ItemSizeDefinition, ClimateRange, Deposit, Fertilizer, Drinkable, EntityDamageResistance,
 * ItemDamageResistance, FluidHeat, KnappingType, LampFuel, Support, Fauna, RockSettings). Feature
 * tasks swap a {@link SchemaRecord} kind for a typed record when they port it.
 */
public final class DefinitionKinds {
    private DefinitionKinds() {
    }

    public static final RegistryKey<SchemaRecord> RECIPE = RegistryKey.of("recipe", SchemaRecord.class);

    private static final ObjectSchema.Field[] PHYSICAL_DAMAGE = {
        optional("piercing", Codecs.FLOAT), optional("slashing", Codecs.FLOAT), optional("crushing", Codecs.FLOAT)};

    /** TFC {@code ForestType} serialized names. */
    private static final Codec<String> FOREST_TYPE = Codecs.oneOf(
            "grassland", "clearing", "shrubland", "sparse", "savanna_monoculture", "savanna_diverse",
            "savanna_alternate", "savanna_shrub_monoculture", "savanna_shrub_diverse",
            "savanna_shrub_alternate", "primary_monoculture", "primary_diverse", "primary_alternate",
            "secondary_monoculture", "secondary_monoculture_tall", "secondary_diverse", "secondary_bamboo",
            "secondary_diverse_tall", "secondary_dense", "secondary_dense_tall", "secondary_alternate",
            "edge_monoculture", "edge_diverse", "edge_alternate", "edge_bamboo", "dead_monoculture",
            "dead_diverse", "dead_alternate", "dead_bamboo");

    private static final ObjectSchema.Field[] CLIMATE_PLACEMENT = {
        optional("min_temperature", Codecs.FLOAT), optional("max_temperature", Codecs.FLOAT),
        optional("min_groundwater", Codecs.FLOAT), optional("max_groundwater", Codecs.FLOAT),
        optional("min_rain_variance", Codecs.FLOAT), optional("max_rain_variance", Codecs.FLOAT),
        optional("rain_variance_absolute", Codecs.BOOL),
        optional("min_forest", Codecs.NON_NEGATIVE_INT), optional("max_forest", Codecs.NON_NEGATIVE_INT),
        optional("forest_types", FOREST_TYPE.listOf()),
        optional("min_elevation", Codecs.INT), optional("max_elevation", Codecs.INT),
        optional("fuzzy", Codecs.BOOL), optional("ignore_rivers", Codecs.BOOL)};

    private static final String BLOCK = TagType.BLOCK.directory();

    private static final List<ObjectSchema> SCHEMAS = List.of(
            ObjectSchema.of("item_heat",
                    required("ingredient", Ingredient.CODEC),
                    required("heat_capacity", Codecs.FLOAT),
                    optional("forging_temperature", Codecs.FLOAT),
                    optional("welding_temperature", Codecs.FLOAT)),
            ObjectSchema.of("food", required("ingredient", Ingredient.CODEC))
                    .with("food", FoodData.FIELDS)
                    .with("food", optional("edible", Codecs.BOOL)),
            ObjectSchema.of("fuel",
                    required("ingredient", Ingredient.CODEC),
                    required("duration", Codecs.INT),
                    required("temperature", Codecs.FLOAT),
                    optional("purity", Codecs.FLOAT)),
            ObjectSchema.of("item_size",
                    required("ingredient", Ingredient.CODEC),
                    required("size", Codecs.oneOf("tiny", "very_small", "small", "normal", "large", "very_large", "huge")),
                    required("weight", Codecs.oneOf("very_light", "light", "medium", "heavy", "very_heavy"))),
            ObjectSchema.of("climate_range",
                    optional("min_hydration", Codecs.INT), optional("max_hydration", Codecs.INT),
                    optional("hydration_wiggle_range", Codecs.INT),
                    optional("min_temperature", Codecs.FLOAT), optional("max_temperature", Codecs.FLOAT),
                    optional("temperature_wiggle_range", Codecs.FLOAT)),
            ObjectSchema.of("deposit",
                    required("ingredient", Ingredient.CODEC),
                    required("loot_table", Codecs.ID),
                    optional("model_stages", Codecs.ID.listOf())),
            ObjectSchema.of("fertilizer",
                    required("ingredient", Ingredient.CODEC),
                    optional("nitrogen", Codecs.FLOAT), optional("phosphorus", Codecs.FLOAT), optional("potassium", Codecs.FLOAT)),
            ObjectSchema.of("drinkable",
                    required("ingredient", FluidIngredient.CODEC),
                    optional("consume_chance", Codecs.FLOAT),
                    optional("may_drink_when_full", Codecs.BOOL),
                    optional("effects", ObjectSchema.of("drinkable_effect",
                            required("effect", Codecs.ID), required("duration", Codecs.INT),
                            required("amplifier", Codecs.INT), required("chance", Codecs.FLOAT)).codec().listOf()))
                    .with("drinkable", FoodData.FIELDS),
            ObjectSchema.of("entity_damage_resistance", required("entity", Codecs.tagRef(TagType.ENTITY_TYPE.directory())))
                    .with("entity_damage_resistance", PHYSICAL_DAMAGE),
            ObjectSchema.of("item_damage_resistance", required("ingredient", Ingredient.CODEC))
                    .with("item_damage_resistance", PHYSICAL_DAMAGE),
            ObjectSchema.of("fluid_heat",
                    required("fluid", Codecs.ref(TagType.FLUID.directory())),
                    required("melt_temperature", Codecs.FLOAT),
                    required("specific_heat_capacity", Codecs.FLOAT)),
            ObjectSchema.of(RecipeSchemas.KNAPPING_TYPE,
                    required("input", SizedIngredient.CODEC),
                    optional("amount_to_consume", Codecs.INT),
                    required("click_sound", Codecs.ID),
                    required("consume_after_complete", Codecs.BOOL),
                    required("has_off_texture", Codecs.BOOL),
                    required("spawns_particles", Codecs.BOOL),
                    required("icon", ItemStackTemplate.CODEC)),
            ObjectSchema.of("lamp_fuel",
                    required("fluid", FluidIngredient.CODEC),
                    required("lamps", BlockIngredient.CODEC),
                    required("burn_rate", Codecs.INT)),
            ObjectSchema.of("support",
                    required("ingredient", BlockIngredient.CODEC),
                    required("support_up", Codecs.INT),
                    required("support_down", Codecs.INT),
                    required("support_horizontal", Codecs.INT)),
            ObjectSchema.of("fauna",
                    optional("chance", Codecs.INT),
                    optional("distance_below_sea_level", Codecs.INT))
                    .with("fauna", CLIMATE_PLACEMENT)
                    .with("fauna",
                            optional("solid_ground", Codecs.BOOL),
                            optional("max_brightness", Codecs.INT),
                            optional("months", Codecs.oneOf("january", "february", "march", "april", "may", "june", "july",
                                    "august", "september", "october", "november", "december").listOf())),
            ObjectSchema.of("worldgen/rock_settings",
                    required("raw", Codecs.ref(BLOCK)), required("hardened", Codecs.ref(BLOCK)),
                    required("gravel", Codecs.ref(BLOCK)), required("cobble", Codecs.ref(BLOCK)),
                    required("sand", Codecs.ref(BLOCK)), required("sandstone", Codecs.ref(BLOCK)),
                    optional("spike", Codecs.ref(BLOCK)), optional("loose", Codecs.ref(BLOCK)),
                    optional("mossy_loose", Codecs.ref(BLOCK)),
                    optional("karst", Codecs.BOOL), optional("mafic", Codecs.BOOL)));

    /** Registry key of a definition kind, e.g. {@code key("item_heat")}. */
    public static RegistryKey<SchemaRecord> key(String kind) {
        return RegistryKey.of(kind, SchemaRecord.class);
    }

    /** Recipes + every definition kind. */
    public static List<DataKind<?>> standard() {
        List<DataKind<?>> kinds = new ArrayList<>();
        kinds.add(new DataKind<>(RECIPE, "recipe", false, RecipeSchemas.CODEC));
        for (ObjectSchema s : SCHEMAS) {
            kinds.add(new DataKind<>(key(s.name()), s.name(), true, s.codec()));
        }
        return List.copyOf(kinds);
    }
}
