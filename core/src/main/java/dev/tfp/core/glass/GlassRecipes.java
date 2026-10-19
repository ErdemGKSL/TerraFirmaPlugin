/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/glassworking/ @ c3b4d9536 (the 60 glassworking recipes)
 */
package dev.tfp.core.glass;

import dev.tfp.core.craft.CraftOutput;
import java.util.List;

/**
 * The 60 glassworking recipes, transcribed from TFC's recipe JSON at c3b4d9536. Recipe ids are {@code glassworking/} plus the
 * TFC file name. The table is checked row by row against {@code tfc-glass-recipes.tsv} in {@code GlassGoldenTest}; the
 * {@code batch} strings are as written in the JSON (an item id or a {@code #tag}).
 */
public final class GlassRecipes {

    private GlassRecipes() {}

    /** Every glassworking recipe, in TFC file-name order. */
    public static final List<GlassRecipe> ALL = List.of(
        new GlassRecipe("glassworking/black_poured_glass", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:table_pour"), new CraftOutput("tfc:black_poured_glass", 1)),
        new GlassRecipe("glassworking/black_stained_glass", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:basin_pour"), new CraftOutput("minecraft:black_stained_glass", 1)),
        new GlassRecipe("glassworking/blue_poured_glass", "tfc:silica_glass_batch", List.of("tfc:copper", "tfc:table_pour"), new CraftOutput("tfc:blue_poured_glass", 1)),
        new GlassRecipe("glassworking/blue_poured_glass_default", "tfc:volcanic_glass_batch", List.of("tfc:table_pour"), new CraftOutput("tfc:blue_poured_glass", 1)),
        new GlassRecipe("glassworking/blue_stained_glass", "tfc:silica_glass_batch", List.of("tfc:copper", "tfc:basin_pour"), new CraftOutput("minecraft:blue_stained_glass", 1)),
        new GlassRecipe("glassworking/blue_stained_glass_default", "tfc:volcanic_glass_batch", List.of("tfc:basin_pour"), new CraftOutput("minecraft:blue_stained_glass", 1)),
        new GlassRecipe("glassworking/brown_poured_glass", "#tfc:glass_batches", List.of("tfc:nickel", "tfc:table_pour"), new CraftOutput("tfc:brown_poured_glass", 1)),
        new GlassRecipe("glassworking/brown_stained_glass", "#tfc:glass_batches", List.of("tfc:nickel", "tfc:basin_pour"), new CraftOutput("minecraft:brown_stained_glass", 1)),
        new GlassRecipe("glassworking/cyan_poured_glass", "#tfc:glass_batches_tier_3", List.of("tfc:copper", "tfc:sapphire", "tfc:table_pour"), new CraftOutput("tfc:cyan_poured_glass", 1)),
        new GlassRecipe("glassworking/cyan_poured_glass_2", "#tfc:glass_batches_tier_3", List.of("tfc:sapphire", "tfc:copper", "tfc:table_pour"), new CraftOutput("tfc:cyan_poured_glass", 1)),
        new GlassRecipe("glassworking/cyan_stained_glass", "#tfc:glass_batches_tier_3", List.of("tfc:copper", "tfc:sapphire", "tfc:basin_pour"), new CraftOutput("minecraft:cyan_stained_glass", 1)),
        new GlassRecipe("glassworking/cyan_stained_glass_2", "#tfc:glass_batches_tier_3", List.of("tfc:sapphire", "tfc:copper", "tfc:basin_pour"), new CraftOutput("minecraft:cyan_stained_glass", 1)),
        new GlassRecipe("glassworking/empty_jar", "#tfc:glass_batches_tier_2", List.of("tfc:blow", "tfc:pinch", "tfc:roll", "tfc:saw"), new CraftOutput("tfc:empty_jar", 1)),
        new GlassRecipe("glassworking/glass", "tfc:silica_glass_batch", List.of("tfc:basin_pour"), new CraftOutput("minecraft:glass", 1)),
        new GlassRecipe("glassworking/gray_poured_glass", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:soda_ash", "tfc:table_pour"), new CraftOutput("tfc:gray_poured_glass", 1)),
        new GlassRecipe("glassworking/gray_poured_glass_2", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:graphite", "tfc:table_pour"), new CraftOutput("tfc:gray_poured_glass", 1)),
        new GlassRecipe("glassworking/gray_stained_glass", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:soda_ash", "tfc:basin_pour"), new CraftOutput("minecraft:gray_stained_glass", 1)),
        new GlassRecipe("glassworking/gray_stained_glass_2", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:graphite", "tfc:basin_pour"), new CraftOutput("minecraft:gray_stained_glass", 1)),
        new GlassRecipe("glassworking/green_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:iron", "tfc:table_pour"), new CraftOutput("tfc:green_poured_glass", 1)),
        new GlassRecipe("glassworking/green_poured_glass_default", "tfc:olivine_glass_batch", List.of("tfc:table_pour"), new CraftOutput("tfc:green_poured_glass", 1)),
        new GlassRecipe("glassworking/green_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:iron", "tfc:basin_pour"), new CraftOutput("minecraft:green_stained_glass", 1)),
        new GlassRecipe("glassworking/green_stained_glass_default", "tfc:olivine_glass_batch", List.of("tfc:basin_pour"), new CraftOutput("minecraft:green_stained_glass", 1)),
        new GlassRecipe("glassworking/hematitic_glass_bottle", "tfc:hematitic_glass_batch", List.of("tfc:blow", "tfc:pinch", "tfc:saw"), new CraftOutput("tfc:hematitic_glass_bottle", 1)),
        new GlassRecipe("glassworking/lamp_glass", "#tfc:glass_batches", List.of("tfc:blow", "tfc:pinch", "tfc:flatten", "tfc:blow", "tfc:saw"), new CraftOutput("tfc:lamp_glass", 1)),
        new GlassRecipe("glassworking/lens", "tfc:silica_glass_batch", List.of("tfc:blow", "tfc:stretch", "tfc:roll", "tfc:saw"), new CraftOutput("tfc:lens", 1)),
        new GlassRecipe("glassworking/light_blue_poured_glass", "tfc:silica_glass_batch", List.of("tfc:lapis_lazuli", "tfc:table_pour"), new CraftOutput("tfc:light_blue_poured_glass", 1)),
        new GlassRecipe("glassworking/light_blue_stained_glass", "tfc:silica_glass_batch", List.of("tfc:lapis_lazuli", "tfc:basin_pour"), new CraftOutput("minecraft:light_blue_stained_glass", 1)),
        new GlassRecipe("glassworking/light_gray_poured_glass", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:soda_ash", "tfc:graphite", "tfc:table_pour"), new CraftOutput("tfc:light_gray_poured_glass", 1)),
        new GlassRecipe("glassworking/light_gray_poured_glass_2", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:graphite", "tfc:soda_ash", "tfc:table_pour"), new CraftOutput("tfc:light_gray_poured_glass", 1)),
        new GlassRecipe("glassworking/light_gray_poured_glass_3", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:soda_ash", "tfc:soda_ash", "tfc:table_pour"), new CraftOutput("tfc:light_gray_poured_glass", 1)),
        new GlassRecipe("glassworking/light_gray_stained_glass", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:soda_ash", "tfc:graphite", "tfc:basin_pour"), new CraftOutput("minecraft:light_gray_stained_glass", 1)),
        new GlassRecipe("glassworking/light_gray_stained_glass_2", "#tfc:glass_batches", List.of("tfc:soda_ash", "tfc:graphite", "tfc:soda_ash", "tfc:basin_pour"), new CraftOutput("minecraft:light_gray_stained_glass", 1)),
        new GlassRecipe("glassworking/light_gray_stained_glass_3", "#tfc:glass_batches", List.of("tfc:graphite", "tfc:soda_ash", "tfc:soda_ash", "tfc:basin_pour"), new CraftOutput("minecraft:light_gray_stained_glass", 1)),
        new GlassRecipe("glassworking/lime_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:iron", "tfc:soda_ash", "tfc:table_pour"), new CraftOutput("tfc:lime_poured_glass", 1)),
        new GlassRecipe("glassworking/lime_poured_glass_2", "#tfc:glass_batches_tier_2", List.of("tfc:soda_ash", "tfc:iron", "tfc:table_pour"), new CraftOutput("tfc:lime_poured_glass", 1)),
        new GlassRecipe("glassworking/lime_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:iron", "tfc:soda_ash", "tfc:basin_pour"), new CraftOutput("minecraft:lime_stained_glass", 1)),
        new GlassRecipe("glassworking/lime_stained_glass_2", "#tfc:glass_batches_tier_2", List.of("tfc:soda_ash", "tfc:iron", "tfc:basin_pour"), new CraftOutput("minecraft:lime_stained_glass", 1)),
        new GlassRecipe("glassworking/magenta_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:ruby", "tfc:table_pour"), new CraftOutput("tfc:magenta_poured_glass", 1)),
        new GlassRecipe("glassworking/magenta_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:ruby", "tfc:basin_pour"), new CraftOutput("minecraft:magenta_stained_glass", 1)),
        new GlassRecipe("glassworking/olivine_glass_bottle", "tfc:olivine_glass_batch", List.of("tfc:blow", "tfc:pinch", "tfc:saw"), new CraftOutput("tfc:olivine_glass_bottle", 1)),
        new GlassRecipe("glassworking/orange_poured_glass", "tfc:silica_glass_batch", List.of("tfc:pyrite", "tfc:table_pour"), new CraftOutput("tfc:orange_poured_glass", 1)),
        new GlassRecipe("glassworking/orange_poured_glass_default", "tfc:hematitic_glass_batch", List.of("tfc:table_pour"), new CraftOutput("tfc:orange_poured_glass", 1)),
        new GlassRecipe("glassworking/orange_stained_glass", "tfc:silica_glass_batch", List.of("tfc:pyrite", "tfc:basin_pour"), new CraftOutput("minecraft:orange_stained_glass", 1)),
        new GlassRecipe("glassworking/orange_stained_glass_default", "tfc:hematitic_glass_batch", List.of("tfc:basin_pour"), new CraftOutput("minecraft:orange_stained_glass", 1)),
        new GlassRecipe("glassworking/pink_poured_glass", "tfc:silica_glass_batch", List.of("tfc:gold", "tfc:table_pour"), new CraftOutput("tfc:pink_poured_glass", 1)),
        new GlassRecipe("glassworking/pink_stained_glass", "tfc:silica_glass_batch", List.of("tfc:gold", "tfc:basin_pour"), new CraftOutput("minecraft:pink_stained_glass", 1)),
        new GlassRecipe("glassworking/poured_glass", "tfc:silica_glass_batch", List.of("tfc:table_pour"), new CraftOutput("tfc:poured_glass", 1)),
        new GlassRecipe("glassworking/purple_poured_glass", "#tfc:glass_batches", List.of("tfc:iron", "tfc:copper", "tfc:table_pour"), new CraftOutput("tfc:purple_poured_glass", 1)),
        new GlassRecipe("glassworking/purple_poured_glass_2", "#tfc:glass_batches", List.of("tfc:copper", "tfc:iron", "tfc:table_pour"), new CraftOutput("tfc:purple_poured_glass", 1)),
        new GlassRecipe("glassworking/purple_stained_glass", "#tfc:glass_batches", List.of("tfc:iron", "tfc:copper", "tfc:basin_pour"), new CraftOutput("minecraft:purple_stained_glass", 1)),
        new GlassRecipe("glassworking/purple_stained_glass_2", "#tfc:glass_batches", List.of("tfc:copper", "tfc:iron", "tfc:basin_pour"), new CraftOutput("minecraft:purple_stained_glass", 1)),
        new GlassRecipe("glassworking/red_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:tin", "tfc:table_pour"), new CraftOutput("tfc:red_poured_glass", 1)),
        new GlassRecipe("glassworking/red_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:tin", "tfc:basin_pour"), new CraftOutput("minecraft:red_stained_glass", 1)),
        new GlassRecipe("glassworking/silica_glass_bottle", "tfc:silica_glass_batch", List.of("tfc:blow", "tfc:pinch", "tfc:saw"), new CraftOutput("tfc:silica_glass_bottle", 1)),
        new GlassRecipe("glassworking/tinted_glass", "#tfc:glass_batches_not_tier_1", List.of("tfc:amethyst", "tfc:basin_pour"), new CraftOutput("minecraft:tinted_glass", 1)),
        new GlassRecipe("glassworking/volcanic_glass_bottle", "tfc:volcanic_glass_batch", List.of("tfc:blow", "tfc:pinch", "tfc:saw"), new CraftOutput("tfc:volcanic_glass_bottle", 1)),
        new GlassRecipe("glassworking/white_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:soda_ash", "tfc:table_pour"), new CraftOutput("tfc:white_poured_glass", 1)),
        new GlassRecipe("glassworking/white_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:soda_ash", "tfc:basin_pour"), new CraftOutput("minecraft:white_stained_glass", 1)),
        new GlassRecipe("glassworking/yellow_poured_glass", "#tfc:glass_batches_tier_2", List.of("tfc:silver", "tfc:table_pour"), new CraftOutput("tfc:yellow_poured_glass", 1)),
        new GlassRecipe("glassworking/yellow_stained_glass", "#tfc:glass_batches_tier_2", List.of("tfc:silver", "tfc:basin_pour"), new CraftOutput("minecraft:yellow_stained_glass", 1))
    );
}
