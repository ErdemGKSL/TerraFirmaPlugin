/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/scraping/ @ c3b4d9536 (the seven scraping recipes),
 * file: src/generated/resources/data/tfc/recipe/loom/ @ c3b4d9536 (the five loom recipes),
 * file: src/generated/resources/data/tfc/recipe/sewing/ @ c3b4d9536 (the twenty-five sewing recipes)
 */
package dev.tfp.core.craft;

import java.util.List;

/**
 * The scraping, loom and sewing recipe tables, transcribed from TFC's recipe JSON at c3b4d9536. Recipe ids are the TFC file
 * paths under {@code tfc:recipe/} (for example {@code scraping/paper}). The tables are checked against the golden TSV in
 * {@code CraftGoldenTest}.
 */
public final class CraftRecipes {

    private CraftRecipes() {}

    /** The scraping recipes: a knife scrapes a 4x4 grid of cells on the item; the result replaces it when the grid is full. */
    public static final List<ScrapingRecipe> SCRAPING = List.of(
        new ScrapingRecipe("scraping/large_raw_hide", "tfc:large_sheepskin_hide", "tfc:item/hide/large/sheepskin", "tfc:item/hide/large/raw", new CraftOutput("tfc:large_raw_hide", 1), new CraftOutput("tfc:wool", 1)),
        new ScrapingRecipe("scraping/large_scraped_hide", "tfc:large_soaked_hide", "tfc:item/hide/large/soaked", "tfc:item/hide/large/scraped", new CraftOutput("tfc:large_scraped_hide", 1), null),
        new ScrapingRecipe("scraping/medium_raw_hide", "tfc:medium_sheepskin_hide", "tfc:item/hide/medium/sheepskin", "tfc:item/hide/medium/raw", new CraftOutput("tfc:medium_raw_hide", 1), new CraftOutput("tfc:wool", 1)),
        new ScrapingRecipe("scraping/medium_scraped_hide", "tfc:medium_soaked_hide", "tfc:item/hide/medium/soaked", "tfc:item/hide/medium/scraped", new CraftOutput("tfc:medium_scraped_hide", 1), null),
        new ScrapingRecipe("scraping/paper", "tfc:unrefined_paper", "tfc:block/unrefined_paper", "tfc:block/paper", new CraftOutput("minecraft:paper", 1), null),
        new ScrapingRecipe("scraping/small_raw_hide", "tfc:small_sheepskin_hide", "tfc:item/hide/small/sheepskin", "tfc:item/hide/small/raw", new CraftOutput("tfc:small_raw_hide", 1), new CraftOutput("tfc:wool", 1)),
        new ScrapingRecipe("scraping/small_scraped_hide", "tfc:small_soaked_hide", "tfc:item/hide/small/soaked", "tfc:item/hide/small/scraped", new CraftOutput("tfc:small_scraped_hide", 1), null)
    );

    /** The loom recipes: a stack of the ingredient is pushed through a number of steps, then becomes the result. */
    public static final List<LoomRecipe> LOOM = List.of(
        new LoomRecipe("loom/burlap_cloth", "tfc:jute_fiber", 12, new CraftOutput("tfc:burlap_cloth", 1), 12, "tfc:block/burlap"),
        new LoomRecipe("loom/silk_cloth", "minecraft:string", 24, new CraftOutput("tfc:silk_cloth", 1), 24, "minecraft:block/white_wool"),
        new LoomRecipe("loom/unrefined_paper", "tfc:soaked_papyrus_strip", 4, new CraftOutput("tfc:unrefined_paper", 1), 8, "tfc:block/unrefined_paper"),
        new LoomRecipe("loom/white_wool", "tfc:wool_cloth", 4, new CraftOutput("minecraft:white_wool", 8), 4, "minecraft:block/white_wool"),
        new LoomRecipe("loom/wool_cloth", "tfc:wool_yarn", 16, new CraftOutput("tfc:wool_cloth", 1), 16, "minecraft:block/white_wool")
    );

    /** The sewing recipes: a grid of cloth squares (B burlap, W wool, space empty) and a grid of stitches (# placed). */
    public static final List<SewingRecipe> SEWING = List.of(
        new SewingRecipe("sewing/bolt_armor_trim_smithing_template", "BWBWBBBBBWBWBBWBBWBBBBWBBWWWWBWB",
            "            #   #     #      #            #  ", new CraftOutput("minecraft:bolt_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/coast_armor_trim_smithing_template", "BBBBBWWBBBBBWWBBBBBBWWBBBBBBBWWB",
            "     # #     #                 #         # # ", new CraftOutput("minecraft:coast_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/creeper_banner_pattern", "BBWBBWBBBBBBBBBBBBWWWWBBBBWBBWBB",
            "  ## ##    #   #    # # #    # # #    ## ##  ", new CraftOutput("minecraft:creeper_banner_pattern", 1)),
        new SewingRecipe("sewing/dune_armor_trim_smithing_template", "BBBBBBBBBBBWWBBBBBWWWWBBBWWWWWWB",
            "             #       # #     #   #   #     # ", new CraftOutput("minecraft:dune_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/eye_armor_trim_smithing_template", "BBBWWBBBBBWBBWBBBBWBBWBBBBBWWBBB",
            "   # #     #   #             #   #     # #   ", new CraftOutput("minecraft:eye_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/flow_armor_trim_smithing_template", "BBWWWWBBBBWBWWBBBBWBBBBBBBWWWWBB",
            "  #          ##        #      #  #           ", new CraftOutput("minecraft:flow_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/flower_banner_pattern", "BBBWWBBBBBWWWWBBBBWWWWBBBBBWWBBB",
            "   # #     ## ##             ## ##     # #   ", new CraftOutput("minecraft:flower_banner_pattern", 1)),
        new SewingRecipe("sewing/globe_banner_pattern", "BBWWWWBBBBWBBWBBBBWBBWBBBBWWWWBB",
            "  ## ##    ## ##             ## ##    ## ##  ", new CraftOutput("minecraft:globe_banner_pattern", 1)),
        new SewingRecipe("sewing/guster_banner_pattern", "BBBWWBBBBWWWWWWBBBWWWWBBBBBWWBBB",
            "            # #     #   #      #        #    ", new CraftOutput("minecraft:guster_banner_pattern", 1)),
        new SewingRecipe("sewing/host_armor_trim_smithing_template", "BBBBBBBBWWWWWWWWBBBBBWBBBBBBBWBB",
            "         # # # # #                       ##  ", new CraftOutput("minecraft:host_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/mojang_banner_pattern", "WWWWBBBBWBBBBBBWWBBBBBBWWWWWWWWW",
            "          # # #  #       #  # # # #          ", new CraftOutput("minecraft:mojang_banner_pattern", 1)),
        new SewingRecipe("sewing/piglin_banner_pattern", "WWWWWWWWWBWBBWBWWBWBBWBWWWWWWWWW",
            "          # # # #           # # # #          ", new CraftOutput("minecraft:piglin_banner_pattern", 1)),
        new SewingRecipe("sewing/raiser_armor_trim_smithing_template", "BBBBBWWWBBBWWWBBBWWWBBBBWWBBBBBB",
            "            #####          #####             ", new CraftOutput("minecraft:raiser_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/rib_armor_trim_smithing_template", "BBWBWBWBBBWBWBWBBWBWBWBBBWBWBWBB",
            "  # # #            # # #             # # #   ", new CraftOutput("minecraft:rib_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/sentry_armor_trim_smithing_template", "BBBWWBBBWWWBBWWWBBWBBWBBBBBWWBBB",
            "   # #            # ## ## #            # #   ", new CraftOutput("minecraft:sentry_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/shaper_armor_trim_smithing_template", "BBBBBBWBBBBWWWBBBBBWBBBBBBWBBBBB",
            "      #     #   #     #      #         #     ", new CraftOutput("minecraft:shaper_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/silence_armor_trim_smithing_template", "BBWBBBBBBBWWWBBBBBBBWBBBBBBBWWBB",
            "  #                 # ##                 ##  ", new CraftOutput("minecraft:silence_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/skull_banner_pattern", "BBWWWWBBBBWBBWBBBBWWWWBBBBWWWWBB",
            "  #   #     ###     ## ##             #   #  ", new CraftOutput("minecraft:skull_banner_pattern", 1)),
        new SewingRecipe("sewing/snout_armor_trim_smithing_template", "BBBBBBBBBWBBBBWBBWBBBBWBBBBBBBBB",
            " #     # # #   # #         # #   # # #     # ", new CraftOutput("minecraft:snout_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/spire_armor_trim_smithing_template", "BBWBBWBBBBWBBWBBBBWBBWBBBBWWWWBB",
            "           #   #             ## ##           ", new CraftOutput("minecraft:spire_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/tide_armor_trim_smithing_template", "BWWWBBBBBBBBWWWBBBBBWWWBBWWWBBBB",
            " # #         # #               # #   # #     ", new CraftOutput("minecraft:tide_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/vex_armor_trim_smithing_template", "BBBBBBBBWWWBBWBBWWWBWBBBWWWWWBBB",
            "         #### ##        #  ####              ", new CraftOutput("minecraft:vex_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/ward_armor_trim_smithing_template", "BBBBBWBBBWWBBWBBBWWBBWBBBBBBBWBB",
            "     ##   #        #   ##   #            ##  ", new CraftOutput("minecraft:ward_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/wayfinder_armor_trim_smithing_template", "BWBBBWWBBWWBBWBWBBWBWBWBBBWWBBBW",
            " #        #    ##       ##   # #           # ", new CraftOutput("minecraft:wayfinder_armor_trim_smithing_template", 1)),
        new SewingRecipe("sewing/wild_armor_trim_smithing_template", "WWBBBBWWBWWBBWWBBBBWWBBBBBBWWBBB",
            "         ##     ##   ###      # #      # #   ", new CraftOutput("minecraft:wild_armor_trim_smithing_template", 1))
    );
}
