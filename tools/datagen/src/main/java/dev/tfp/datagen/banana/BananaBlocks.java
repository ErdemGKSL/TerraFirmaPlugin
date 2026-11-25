/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/plant/fruit/FruitBlocks.java @ c3b4d9536 (createBananaSapling, createBananaPlant),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/BananaPlantBlock.java @ c3b4d9536 (getShape, getCollisionShape),
 *       src/main/java/net/dries007/tfc/common/blocks/plant/fruit/DeadBananaPlantBlock.java @ c3b4d9536,
 *       src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java @ c3b4d9536 (DEAD_BANANA_PLANT),
 *       src/main/resources/assets/tfc/blockstates/plant/{banana_sapling,banana_plant,dead_banana_plant}.json, models/block/plant/banana_*,
 *       models/item/plant/banana_sapling.json, textures @ c3b4d9536 (copied),
 *       src/main/resources/data/tfc/loot_table/blocks/plant/{banana_sapling,banana_plant,dead_banana_plant}.json @ c3b4d9536 (the drops)
 */
package dev.tfp.datagen.banana;

import dev.tfp.datagen.DatagenException;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crop.CropBlocks;
import dev.tfp.datagen.device.DeviceProperty;
import dev.tfp.datagen.fruittree.FruitTreeBlocks;
import dev.tfp.datagen.fruittree.FruitTreeBlocks.Built;
import dev.tfp.datagen.fruittree.FruitTreeBlocks.Look;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * PAR-FARM-17: the banana as CraftEngine blocks ({@code craftengine/tfp/configuration/banana/banana.yml}):
 * {@code tfp:plant/banana_sapling}, {@code tfp:plant/banana_plant} ({@code stage} 0..2, {@code lifecycle}) and
 * {@code tfp:plant/dead_banana_plant} ({@code stage}). The device kinds of PAR-FARM-09 run them. Every state draws TFC's model
 * as an item display on a hitbox-only carrier: the sapling on the bush outline, the stalks on TFC's TRUNK_0 and TRUNK_1 (with
 * collision) and PLANT (stage 2, none). Drops are TFC's tables; the sapling has a block item.
 */
public final class BananaBlocks {
    private BananaBlocks() {
    }

    public static boolean available(TfcCheckout tfc) {
        return FruitTreeBlocks.available(tfc) && tfc.exists(FruitTreeBlocks.BLOCKSTATES + "banana_plant.json");
    }

    static Look plantLook(Map<String, String> s) {
        int stage = Integer.parseInt(s.get("stage"));
        String lc = s.getOrDefault("lifecycle", "healthy");
        String name = "stage_" + stage + (stage == 2 && (lc.equals("flowering") || lc.equals("fruiting")) ? "_" + lc : "");
        return new Look(name, HitboxDemands.bananaPattern(stage));
    }

    /** Writes the entry, models, textures, loot and name. Returns the number of server states. */
    public static int run(TfcCheckout tfc, OutputTree out) {
        checkSources(tfc);
        List<Built> built = new ArrayList<>();
        built.add(FruitTreeBlocks.block(tfc, HitboxDemands.BANANA_SAPLING_ID, "banana_sapling", List.of(),
            s -> new Look(HitboxDemands.BANANA_SAPLING_APPEARANCE, HitboxDemands.BANANA_SAPLING_APPEARANCE),
            FruitTreeBlocks.settings("0", "hoe"), FruitTreeBlocks.langKey("banana_sapling")));
        built.add(FruitTreeBlocks.block(tfc, HitboxDemands.BANANA_PLANT_ID, "banana_plant",
            List.of(DeviceProperty.range("stage", 0, 2, 0), DeviceProperty.names("lifecycle", FruitTreeBlocks.LIFECYCLES, "healthy")),
            BananaBlocks::plantLook, FruitTreeBlocks.settings("0.5", "axe"), null));
        built.add(FruitTreeBlocks.block(tfc, HitboxDemands.BANANA_DEAD_ID, "dead_banana_plant",
            List.of(DeviceProperty.range("stage", 0, 2, 0)), BananaBlocks::plantLook, FruitTreeBlocks.settings("0.6", "axe"), null));
        List<Map<String, Object>> roots = List.of(
            FruitTreeBlocks.yaml(built.get(0).spec(), FruitTreeBlocks.GRASS, FruitTreeBlocks.LEAVES_FIRE, "hoe", false),
            FruitTreeBlocks.yaml(built.get(1).spec(), FruitTreeBlocks.GRASS, FruitTreeBlocks.LEAVES_FIRE, "axe", false),
            FruitTreeBlocks.yaml(built.get(2).spec(), FruitTreeBlocks.SWEET_BERRY_BUSH, new int[] {120, 90}, "axe", false));
        int states = FruitTreeBlocks.write(out, FruitTreeBlocks.OUTPUT_DIR.replace("fruittree", "banana") + "banana.yml", roots, built);
        Set<String> models = new TreeSet<>();
        built.forEach(b -> models.addAll(b.models()));
        for (String n : List.of("banana_sapling", "banana_plant", "dead_banana_plant")) {
            FruitTreeBlocks.loot(tfc, out, n);
        }
        FruitTreeBlocks.itemModel(tfc, out, "banana_sapling", models);
        Set<String> copied = new TreeSet<>();
        for (String m : models) {
            CropBlocks.copyModel(tfc, out, m, copied);
        }
        FruitTreeBlocks.lang(tfc, out, List.of("banana_sapling"), FruitTreeBlocks.LANG_RP.replace("tfp_fruit_tree", "tfp_banana"));
        return states;
    }

    static void checkSources(TfcCheckout tfc) {
        String fruit = tfc.readString(FruitTreeBlocks.FRUIT_BLOCKS).replaceAll("\\s+", " ");
        String plant = "return new BananaPlantBlock(ExtendedProperties.of(MapColor.PLANT).strength(0.5F).sound(SoundType.GRASS).randomTicks()"
            + ".noOcclusion().forceSolidOn().blockEntity(TFCBlockEntities.BERRY_BUSH).flammableLikeLeaves()";
        if (!fruit.contains(plant)) {
            throw new DatagenException(FruitTreeBlocks.FRUIT_BLOCKS + " no longer registers the banana plant with " + plant);
        }
        String blocks = tfc.readString("src/main/java/net/dries007/tfc/common/blocks/TFCBlocks.java").replaceAll("\\s+", " ");
        String dead = "registerNoItem(\"plant/dead_banana_plant\", () -> new DeadBananaPlantBlock(ExtendedProperties.of(MapColor.PLANT)"
            + ".strength(0.6f).noOcclusion().sound(SoundType.SWEET_BERRY_BUSH).blockEntity(TFCBlockEntities.TICK_COUNTER).flammable(120, 90)));";
        if (!blocks.contains(dead)) {
            throw new DatagenException("TFCBlocks no longer registers the dead banana plant as " + dead);
        }
    }
}
