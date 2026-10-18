/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/tags/block/can_landslide.json @ c3b4d9536 (the fallback tag),
 * file: src/generated/resources/data/tfc/tags/block/supports_landslide.json @ c3b4d9536 (with the farmlands and paths tags it refers to),
 * file: src/generated/resources/data/tfc/recipe/landslide/ @ c3b4d9536 (the landslide recipes)
 */
package dev.tfp.core.landslide;

import java.util.List;

/**
 * The TFC landslide data at c3b4d9536: the {@code can_landslide} fallback tag, the {@code supports_landslide} tag and the landslide
 * recipes (ingredient kinds to result kind). Transcribed from the JSON files; the golden tests check the counts and the oracle
 * rows. The soils that slide are these kinds: TFC has no separate gravity flag on soils.
 */
public final class LandslideSoils {
    /** The {@code can_landslide} tag. A kind here with no recipe slides into itself. */
    public static final List<String> CAN_LANDSLIDE = List.of(
        "tfc:rock/cobble/granite",
        "tfc:rock/cobble/diorite",
        "tfc:rock/cobble/gabbro",
        "tfc:rock/cobble/shale",
        "tfc:rock/cobble/claystone",
        "tfc:rock/cobble/limestone",
        "tfc:rock/cobble/conglomerate",
        "tfc:rock/cobble/dolomite",
        "tfc:rock/cobble/chert",
        "tfc:rock/cobble/chalk",
        "tfc:rock/cobble/tuff",
        "tfc:rock/cobble/rhyolite",
        "tfc:rock/cobble/basalt",
        "tfc:rock/cobble/andesite",
        "tfc:rock/cobble/dacite",
        "tfc:rock/cobble/quartzite",
        "tfc:rock/cobble/slate",
        "tfc:rock/cobble/phyllite",
        "tfc:rock/cobble/schist",
        "tfc:rock/cobble/gneiss",
        "tfc:rock/cobble/marble",
        "tfc:rock/mossy_cobble/granite",
        "tfc:rock/mossy_cobble/diorite",
        "tfc:rock/mossy_cobble/gabbro",
        "tfc:rock/mossy_cobble/shale",
        "tfc:rock/mossy_cobble/claystone",
        "tfc:rock/mossy_cobble/limestone",
        "tfc:rock/mossy_cobble/conglomerate",
        "tfc:rock/mossy_cobble/dolomite",
        "tfc:rock/mossy_cobble/chert",
        "tfc:rock/mossy_cobble/chalk",
        "tfc:rock/mossy_cobble/tuff",
        "tfc:rock/mossy_cobble/rhyolite",
        "tfc:rock/mossy_cobble/basalt",
        "tfc:rock/mossy_cobble/andesite",
        "tfc:rock/mossy_cobble/dacite",
        "tfc:rock/mossy_cobble/quartzite",
        "tfc:rock/mossy_cobble/slate",
        "tfc:rock/mossy_cobble/phyllite",
        "tfc:rock/mossy_cobble/schist",
        "tfc:rock/mossy_cobble/gneiss",
        "tfc:rock/mossy_cobble/marble",
        "tfc:rock/gravel/granite",
        "tfc:rock/gravel/diorite",
        "tfc:rock/gravel/gabbro",
        "tfc:rock/gravel/shale",
        "tfc:rock/gravel/claystone",
        "tfc:rock/gravel/limestone",
        "tfc:rock/gravel/conglomerate",
        "tfc:rock/gravel/dolomite",
        "tfc:rock/gravel/chert",
        "tfc:rock/gravel/chalk",
        "tfc:rock/gravel/tuff",
        "tfc:rock/gravel/rhyolite",
        "tfc:rock/gravel/basalt",
        "tfc:rock/gravel/andesite",
        "tfc:rock/gravel/dacite",
        "tfc:rock/gravel/quartzite",
        "tfc:rock/gravel/slate",
        "tfc:rock/gravel/phyllite",
        "tfc:rock/gravel/schist",
        "tfc:rock/gravel/gneiss",
        "tfc:rock/gravel/marble",
        "tfc:sand/brown",
        "tfc:sand/white",
        "tfc:sand/black",
        "tfc:sand/red",
        "tfc:sand/yellow",
        "tfc:sand/green",
        "tfc:sand/pink",
        "tfc:deposit/native_copper/granite",
        "tfc:deposit/native_silver/granite",
        "tfc:deposit/native_gold/granite",
        "tfc:deposit/cassiterite/granite",
        "tfc:deposit/native_copper/diorite",
        "tfc:deposit/native_silver/diorite",
        "tfc:deposit/native_gold/diorite",
        "tfc:deposit/cassiterite/diorite",
        "tfc:deposit/native_copper/gabbro",
        "tfc:deposit/native_silver/gabbro",
        "tfc:deposit/native_gold/gabbro",
        "tfc:deposit/cassiterite/gabbro",
        "tfc:deposit/native_copper/shale",
        "tfc:deposit/native_silver/shale",
        "tfc:deposit/native_gold/shale",
        "tfc:deposit/cassiterite/shale",
        "tfc:deposit/native_copper/claystone",
        "tfc:deposit/native_silver/claystone",
        "tfc:deposit/native_gold/claystone",
        "tfc:deposit/cassiterite/claystone",
        "tfc:deposit/native_copper/limestone",
        "tfc:deposit/native_silver/limestone",
        "tfc:deposit/native_gold/limestone",
        "tfc:deposit/cassiterite/limestone",
        "tfc:deposit/native_copper/conglomerate",
        "tfc:deposit/native_silver/conglomerate",
        "tfc:deposit/native_gold/conglomerate",
        "tfc:deposit/cassiterite/conglomerate",
        "tfc:deposit/native_copper/dolomite",
        "tfc:deposit/native_silver/dolomite",
        "tfc:deposit/native_gold/dolomite",
        "tfc:deposit/cassiterite/dolomite",
        "tfc:deposit/native_copper/chert",
        "tfc:deposit/native_silver/chert",
        "tfc:deposit/native_gold/chert",
        "tfc:deposit/cassiterite/chert",
        "tfc:deposit/native_copper/chalk",
        "tfc:deposit/native_silver/chalk",
        "tfc:deposit/native_gold/chalk",
        "tfc:deposit/cassiterite/chalk",
        "tfc:deposit/native_copper/tuff",
        "tfc:deposit/native_silver/tuff",
        "tfc:deposit/native_gold/tuff",
        "tfc:deposit/cassiterite/tuff",
        "tfc:deposit/native_copper/rhyolite",
        "tfc:deposit/native_silver/rhyolite",
        "tfc:deposit/native_gold/rhyolite",
        "tfc:deposit/cassiterite/rhyolite",
        "tfc:deposit/native_copper/basalt",
        "tfc:deposit/native_silver/basalt",
        "tfc:deposit/native_gold/basalt",
        "tfc:deposit/cassiterite/basalt",
        "tfc:deposit/native_copper/andesite",
        "tfc:deposit/native_silver/andesite",
        "tfc:deposit/native_gold/andesite",
        "tfc:deposit/cassiterite/andesite",
        "tfc:deposit/native_copper/dacite",
        "tfc:deposit/native_silver/dacite",
        "tfc:deposit/native_gold/dacite",
        "tfc:deposit/cassiterite/dacite",
        "tfc:deposit/native_copper/quartzite",
        "tfc:deposit/native_silver/quartzite",
        "tfc:deposit/native_gold/quartzite",
        "tfc:deposit/cassiterite/quartzite",
        "tfc:deposit/native_copper/slate",
        "tfc:deposit/native_silver/slate",
        "tfc:deposit/native_gold/slate",
        "tfc:deposit/cassiterite/slate",
        "tfc:deposit/native_copper/phyllite",
        "tfc:deposit/native_silver/phyllite",
        "tfc:deposit/native_gold/phyllite",
        "tfc:deposit/cassiterite/phyllite",
        "tfc:deposit/native_copper/schist",
        "tfc:deposit/native_silver/schist",
        "tfc:deposit/native_gold/schist",
        "tfc:deposit/cassiterite/schist",
        "tfc:deposit/native_copper/gneiss",
        "tfc:deposit/native_silver/gneiss",
        "tfc:deposit/native_gold/gneiss",
        "tfc:deposit/cassiterite/gneiss",
        "tfc:deposit/native_copper/marble",
        "tfc:deposit/native_silver/marble",
        "tfc:deposit/native_gold/marble",
        "tfc:deposit/cassiterite/marble",
        "tfc:mud/entisol",
        "tfc:mud/aridisol",
        "tfc:mud/oxisol",
        "tfc:mud/fluvisol",
        "tfc:mud/andisol",
        "tfc:mud/podzol",
        "tfc:mud/alfisol",
        "tfc:mud/mollisol",
        "tfc:coarse_dirt/entisol",
        "tfc:coarse_dirt/aridisol",
        "tfc:coarse_dirt/oxisol",
        "tfc:coarse_dirt/fluvisol",
        "tfc:coarse_dirt/andisol",
        "tfc:coarse_dirt/podzol",
        "tfc:coarse_dirt/alfisol",
        "tfc:coarse_dirt/mollisol",
        "tfc:duff/entisol",
        "tfc:duff/aridisol",
        "tfc:duff/oxisol",
        "tfc:duff/fluvisol",
        "tfc:duff/andisol",
        "tfc:duff/podzol",
        "tfc:duff/alfisol",
        "tfc:duff/mollisol",
        "tfc:clay_duff/entisol",
        "tfc:clay_duff/aridisol",
        "tfc:clay_duff/oxisol",
        "tfc:clay_duff/fluvisol",
        "tfc:clay_duff/andisol",
        "tfc:clay_duff/podzol",
        "tfc:clay_duff/alfisol",
        "tfc:clay_duff/mollisol",
        "tfc:white_kaolin_clay",
        "tfc:pink_kaolin_clay",
        "tfc:red_kaolin_clay"
    );

    /** The {@code supports_landslide} tag: a side block of one of these kinds holds a block up even when it is not sturdy. */
    public static final List<String> SUPPORTS_LANDSLIDE = List.of(
        "minecraft:farmland",
        "tfc:farmland/entisol",
        "tfc:farmland/aridisol",
        "tfc:farmland/oxisol",
        "tfc:farmland/fluvisol",
        "tfc:farmland/andisol",
        "tfc:farmland/podzol",
        "tfc:farmland/alfisol",
        "tfc:farmland/mollisol",
        "minecraft:dirt_path",
        "tfc:grass_path/entisol",
        "tfc:grass_path/aridisol",
        "tfc:grass_path/oxisol",
        "tfc:grass_path/fluvisol",
        "tfc:grass_path/andisol",
        "tfc:grass_path/podzol",
        "tfc:grass_path/alfisol",
        "tfc:grass_path/mollisol"
    );

    /** One landslide recipe: every ingredient kind slides into {@code result}. */
    public record Recipe(String result, List<String> ingredients) {}

    /** The landslide recipes, in the order of the data files (sorted by path). */
    public static final List<Recipe> RECIPES = List.of(
        new Recipe("tfc:clay/alfisol", List.of(
            "tfc:clay/alfisol",
            "tfc:clay_grass/alfisol",
            "tfc:clay_duff/alfisol"
        )),
        new Recipe("tfc:clay/andisol", List.of(
            "tfc:clay/andisol",
            "tfc:clay_grass/andisol",
            "tfc:clay_duff/andisol"
        )),
        new Recipe("tfc:clay/aridisol", List.of(
            "tfc:clay/aridisol",
            "tfc:clay_grass/aridisol",
            "tfc:clay_duff/aridisol"
        )),
        new Recipe("tfc:clay/entisol", List.of(
            "tfc:clay/entisol",
            "tfc:clay_grass/entisol",
            "tfc:clay_duff/entisol"
        )),
        new Recipe("tfc:clay/fluvisol", List.of(
            "tfc:clay/fluvisol",
            "tfc:clay_grass/fluvisol",
            "tfc:clay_duff/fluvisol"
        )),
        new Recipe("tfc:clay/mollisol", List.of(
            "tfc:clay/mollisol",
            "tfc:clay_grass/mollisol",
            "tfc:clay_duff/mollisol"
        )),
        new Recipe("tfc:clay/oxisol", List.of(
            "tfc:clay/oxisol",
            "tfc:clay_grass/oxisol",
            "tfc:clay_duff/oxisol"
        )),
        new Recipe("tfc:clay/podzol", List.of(
            "tfc:clay/podzol",
            "tfc:clay_grass/podzol",
            "tfc:clay_duff/podzol"
        )),
        new Recipe("minecraft:cobblestone", List.of(
            "minecraft:cobblestone"
        )),
        new Recipe("minecraft:dirt", List.of(
            "minecraft:dirt"
        )),
        new Recipe("tfc:dirt/alfisol", List.of(
            "tfc:dirt/alfisol",
            "tfc:grass/alfisol",
            "tfc:duff/alfisol",
            "tfc:grass_path/alfisol",
            "tfc:farmland/alfisol",
            "tfc:rooted_dirt/alfisol"
        )),
        new Recipe("tfc:dirt/andisol", List.of(
            "tfc:dirt/andisol",
            "tfc:grass/andisol",
            "tfc:duff/andisol",
            "tfc:grass_path/andisol",
            "tfc:farmland/andisol",
            "tfc:rooted_dirt/andisol"
        )),
        new Recipe("tfc:dirt/aridisol", List.of(
            "tfc:dirt/aridisol",
            "tfc:grass/aridisol",
            "tfc:duff/aridisol",
            "tfc:grass_path/aridisol",
            "tfc:farmland/aridisol",
            "tfc:rooted_dirt/aridisol"
        )),
        new Recipe("tfc:dirt/entisol", List.of(
            "tfc:dirt/entisol",
            "tfc:grass/entisol",
            "tfc:duff/entisol",
            "tfc:grass_path/entisol",
            "tfc:farmland/entisol",
            "tfc:rooted_dirt/entisol"
        )),
        new Recipe("tfc:dirt/fluvisol", List.of(
            "tfc:dirt/fluvisol",
            "tfc:grass/fluvisol",
            "tfc:duff/fluvisol",
            "tfc:grass_path/fluvisol",
            "tfc:farmland/fluvisol",
            "tfc:rooted_dirt/fluvisol"
        )),
        new Recipe("tfc:dirt/mollisol", List.of(
            "tfc:dirt/mollisol",
            "tfc:grass/mollisol",
            "tfc:duff/mollisol",
            "tfc:grass_path/mollisol",
            "tfc:farmland/mollisol",
            "tfc:rooted_dirt/mollisol"
        )),
        new Recipe("tfc:dirt/oxisol", List.of(
            "tfc:dirt/oxisol",
            "tfc:grass/oxisol",
            "tfc:duff/oxisol",
            "tfc:grass_path/oxisol",
            "tfc:farmland/oxisol",
            "tfc:rooted_dirt/oxisol"
        )),
        new Recipe("tfc:dirt/podzol", List.of(
            "tfc:dirt/podzol",
            "tfc:grass/podzol",
            "tfc:duff/podzol",
            "tfc:grass_path/podzol",
            "tfc:farmland/podzol",
            "tfc:rooted_dirt/podzol"
        )),
        new Recipe("minecraft:gravel", List.of(
            "minecraft:gravel"
        )),
        new Recipe("tfc:red_kaolin_clay", List.of(
            "tfc:kaolin_clay_grass"
        )),
        new Recipe("minecraft:sand", List.of(
            "minecraft:sand"
        ))
    );

    private LandslideSoils() {}

    /** The landslide kinds of TFC, with the tag and the recipes above. */
    public static LandslideKinds tfc() {
        LandslideKinds.Builder b = LandslideKinds.builder();
        b.canLandslide(CAN_LANDSLIDE.toArray(String[]::new));
        b.supportsLandslide(SUPPORTS_LANDSLIDE.toArray(String[]::new));
        for (Recipe r : RECIPES) {
            b.slides(r.result(), r.ingredients().toArray(String[]::new));
        }
        return b.build();
    }
}
