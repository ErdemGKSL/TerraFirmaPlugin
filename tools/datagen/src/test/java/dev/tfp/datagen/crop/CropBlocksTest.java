/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crop;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-FARM-03: the crop, dead crop and wild crop entries and the seeds on the real checkout. */
class CropBlocksTest {
    private static OutputTree out;
    private static int states;
    private static TfcCheckout tfc;
    private static List<CropBlocks.CropDef> crops;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(CropBlocks.available(tfc)).isTrue();
        crops = CropBlocks.crops(tfc);
        out = new OutputTree();
        states = CropBlocks.run(tfc, out);
    }

    private static CropBlocks.CropDef crop(String name) {
        return crops.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String crop) {
        return new Yaml().load(new String(out.get(CropBlocks.OUTPUT_DIR + crop + ".yml"), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String kind, String crop) {
        return (Map<String, Object>) ((Map<String, Object>) root(crop).get("blocks")).get(HitboxDemands.cropId(kind, crop));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearances(String kind, String crop) {
        return (Map<String, Object>) ((Map<String, Object>) block(kind, crop).get("states")).get("appearances");
    }

    @Test
    void theCropsAreTfcsTwentyNineWithTheirStageCounts() {
        assertThat(crops.stream().map(CropBlocks.CropDef::name).toList()).isEqualTo(HitboxDemands.CROPS).hasSize(29);
        assertThat(crop("wheat")).isEqualTo(new CropBlocks.CropDef("wheat", CropBlocks.Shape.SINGLE, 8, 0, false));
        assertThat(crop("rice")).isEqualTo(new CropBlocks.CropDef("rice", CropBlocks.Shape.SINGLE, 8, 0, false));
        assertThat(crop("red_bell_pepper")).isEqualTo(new CropBlocks.CropDef("red_bell_pepper", CropBlocks.Shape.SINGLE, 7, 0, false));
        assertThat(crop("pumpkin")).isEqualTo(new CropBlocks.CropDef("pumpkin", CropBlocks.Shape.SPREADING, 8, 0, false));
        assertThat(crop("melon").shape()).isEqualTo(CropBlocks.Shape.SPREADING);
        assertThat(crop("green_bean")).isEqualTo(new CropBlocks.CropDef("green_bean", CropBlocks.Shape.DOUBLE, 4, 4, true));
        assertThat(crop("tomato")).isEqualTo(new CropBlocks.CropDef("tomato", CropBlocks.Shape.DOUBLE, 4, 4, true));
        assertThat(crop("maize")).isEqualTo(new CropBlocks.CropDef("maize", CropBlocks.Shape.DOUBLE, 3, 3, false));
        assertThat(crop("sugarcane")).isEqualTo(new CropBlocks.CropDef("sugarcane", CropBlocks.Shape.DOUBLE, 4, 4, false));
        assertThat(crops.stream().filter(c -> c.shape() == CropBlocks.Shape.DOUBLE).count()).isEqualTo(6);
        // every crop's highest age is the highest age TFC's blockstate draws
        for (CropBlocks.CropDef c : crops) {
            String src = CropBlocks.BLOCKSTATES + "crop/" + c.name() + ".json";
            String text = tfc.readString(src);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"age\\\\?=?\\\"?:?\\s*\\\"?(\\d+)|age=(\\d+)").matcher(text);
            int max = 0;
            while (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2)));
            }
            assertThat(c.maxAge()).as(c.name()).isEqualTo(max);
        }
    }

    @Test
    void everyCropHasThreeBlocksWithTfcsPropertiesAndStateCounts() {
        int total = 0;
        for (CropBlocks.CropDef c : crops) {
            for (String kind : HitboxDemands.CROP_KINDS) {
                assertThat(block(kind, c.name())).as(kind + "/" + c.name()).isNotNull();
            }
            total += CropBlocks.spec(tfc, "crop", c).states() + CropBlocks.spec(tfc, "dead_crop", c).states()
                + CropBlocks.spec(tfc, "wild_crop", c).states();
        }
        assertThat(states).isEqualTo(total);
        assertThat(CropBlocks.spec(tfc, "crop", crop("wheat")).states()).isEqualTo(8);
        assertThat(CropBlocks.spec(tfc, "crop", crop("green_bean")).states()).isEqualTo(8 * 2 * 2);
        assertThat(CropBlocks.spec(tfc, "crop", crop("maize")).states()).isEqualTo(6 * 2);
        assertThat(CropBlocks.spec(tfc, "crop", crop("pumpkin")).states()).isEqualTo(8 * 16);
        assertThat(CropBlocks.spec(tfc, "dead_crop", crop("wheat")).states()).isEqualTo(2);
        assertThat(CropBlocks.spec(tfc, "dead_crop", crop("tomato")).states()).isEqualTo(8);
        assertThat(CropBlocks.spec(tfc, "dead_crop", crop("pumpkin")).states()).isEqualTo(2);
        assertThat(CropBlocks.spec(tfc, "wild_crop", crop("pumpkin")).states()).isEqualTo(2 * 16);
        assertThat(CropBlocks.spec(tfc, "wild_crop", crop("green_bean")).states()).isEqualTo(4);
        assertThat(CropBlocks.spec(tfc, "wild_crop", crop("rice")).states()).isEqualTo(2);
    }

    @Test
    void theOutlineFollowsTfcsGetShape() {
        Map<String, String> s = new LinkedHashMap<>();
        // CropBlock.getShape: growth = age / maxAge: <= 0.25 quarter, <= 0.5 half, else full (wheat: 7)
        String[] wheat = {"quarter", "quarter", "half", "half", "full", "full", "full", "full"};
        for (int age = 0; age < 8; age++) {
            s.put("age", Integer.toString(age));
            assertThat(CropBlocks.outline("crop", crop("wheat"), s)).as("wheat age " + age).isEqualTo(wheat[age]);
        }
        // DoubleCropBlock: the top half is HALF, the bottom grows with age / maxSingleAge (maize: 2)
        s.put("age", "3");
        s.put("part", "top");
        assertThat(CropBlocks.outline("crop", crop("maize"), s)).isEqualTo("half");
        s.put("part", "bottom");
        assertThat(CropBlocks.outline("crop", crop("maize"), s)).isEqualTo("full");
        s.put("age", "1");
        assertThat(CropBlocks.outline("crop", crop("maize"), s)).isEqualTo("half");
        // DeadCropBlock: mature FULL, young QUARTER; DeadDoubleCropBlock: bottom FULL, top HALF; WildCropBlock: FULL
        Map<String, String> dead = new LinkedHashMap<>();
        dead.put("mature", "false");
        assertThat(CropBlocks.outline("dead_crop", crop("wheat"), dead)).isEqualTo("quarter");
        dead.put("mature", "true");
        assertThat(CropBlocks.outline("dead_crop", crop("wheat"), dead)).isEqualTo("full");
        dead.put("part", "top");
        assertThat(CropBlocks.outline("dead_crop", crop("maize"), dead)).isEqualTo("half");
        assertThat(CropBlocks.outline("wild_crop", crop("maize"), dead)).isEqualTo("half");
        assertThat(CropBlocks.outline("wild_crop", crop("wheat"), Map.of("mature", "false"))).isEqualTo("full");
    }

    @Test
    void theOutlineRowsAreTfcsBoxesWithNoCollision() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.cropKey("quarter")).selection()).isEqualTo(Shape.box(2, 0, 2, 14, 4, 14));
        assertThat(rows.get(TfcShapes.cropKey("half")).selection()).isEqualTo(Shape.box(2, 0, 2, 14, 8, 14));
        for (String shape : TfcShapes.CROP_ROWS) {
            assertThat(rows.get(TfcShapes.cropKey(shape)).collision().isEmpty()).isTrue();
        }
        // the full outline has no row (no tall carrier without collision): it is served on the half row
        assertThat(rows).doesNotContainKey(TfcShapes.cropKey("full"));
        assertThat(HitboxDemands.cropDemandKey("full")).isEqualTo(TfcShapes.cropKey("half"));
        for (String shape : TfcShapes.CROP_SHAPES) {
            var a = HitboxAllocation.agreed("tfp:crop/wheat", HitboxDemands.cropAppearancePattern(shape));
            assertThat(dev.tfp.datagen.hitbox.VanillaCandidates.load().get(a.carrier().worstVersion(), a.state()).collision()
                .isEmpty()).as(shape + " carrier " + a.state() + " has no collision").isTrue();
        }
    }

    @Test
    void everyAppearanceSitsOnTheSharedHitboxOnlyCarrierOfItsOutline() {
        for (CropBlocks.CropDef c : crops) {
            for (String kind : HitboxDemands.CROP_KINDS) {
                String id = HitboxDemands.cropId(kind, c.name());
                for (Map.Entry<String, Object> a : appearances(kind, c.name()).entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> app = (Map<String, Object>) a.getValue();
                    String shape = a.getKey().substring(0, a.getKey().indexOf('_'));
                    assertThat(TfcShapes.CROP_SHAPES).contains(shape);
                    HitboxAllocation.Assignment want = HitboxAllocation.agreed(id, HitboxDemands.cropAppearancePattern(shape));
                    assertThat(want.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
                    assertThat(app.get("state")).as(id + "#" + a.getKey()).isEqualTo(want.state());
                    assertThat(app.get("transparent")).isEqualTo(true);
                    assertThat((List<?>) app.get("entity_renderer")).isNotEmpty();
                }
            }
        }
        // one carrier per outline, shared by all 87 blocks
        for (String shape : TfcShapes.CROP_SHAPES) {
            java.util.Set<String> carriers = new java.util.TreeSet<>();
            for (String kind : HitboxDemands.CROP_KINDS) {
                for (String crop : HitboxDemands.CROPS) {
                    carriers.add(HitboxAllocation.agreed(HitboxDemands.cropId(kind, crop),
                        HitboxDemands.cropAppearancePattern(shape)).state());
                }
            }
            assertThat(carriers).as(shape).hasSize(1);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSpreadingCropDrawsItsAgeModelAndOneTurnedSideModelPerFruitSide() {
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) block("crop", "pumpkin").get("states")).get("variants");
        Map<String, Object> variant = (Map<String, Object>) variants.get("age=3,east=true,north=false,south=false,west=true");
        String name = (String) variant.get("appearance");
        assertThat(name).isEqualTo("half_age_3__side_y90__side_y270");
        Map<String, Object> app = (Map<String, Object>) appearances("crop", "pumpkin").get(name);
        List<Map<String, Object>> elements = (List<Map<String, Object>>) app.get("entity_renderer");
        assertThat(elements).extracting(e -> e.get("item")).containsExactly("tfp:render/crop/pumpkin_age_3",
            "tfp:render/crop/pumpkin_side", "tfp:render/crop/pumpkin_side");
        // a blockstate y 90 is -90 about +y for the display, after the half turn every block model needs
        assertThat(elements).extracting(e -> e.get("rotation")).containsExactly("0,180,0", "0,90,0", "0,270,0");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aClimbingCropWithAStickDrawsTfcsStickModelOnItsTopHalf() {
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) block("crop", "tomato").get("states")).get("variants");
        assertThat(((Map<String, Object>) variants.get("age=1,part=top,stick=true")).get("appearance")).isEqualTo("half_stick");
        assertThat(((Map<String, Object>) variants.get("age=1,part=bottom,stick=true")).get("appearance"))
            .isEqualTo("half_age_1_stick");
        assertThat(((Map<String, Object>) variants.get("age=6,part=top,stick=false")).get("appearance")).isEqualTo("half_age_6_top");
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsAreTfcsCropSettings() {
        Map<String, Object> settings = (Map<String, Object>) block("crop", "wheat").get("settings");
        assertThat(settings.get("hardness")).isEqualTo(new java.math.BigDecimal("0.4").doubleValue());
        assertThat(settings.get("push_reaction")).isEqualTo("destroy");
        assertThat(settings.get("burn_chance")).isEqualTo(30);
        assertThat(settings.get("fire_spread_chance")).isEqualTo(60);
        assertThat(((Map<String, Object>) settings.get("sounds")).get("break")).isEqualTo("minecraft:block.crop.break");
        assertThat((List<String>) settings.get("tags")).containsExactly("minecraft:mineable/hoe");
        assertThat(block("crop", "wheat")).doesNotContainKey("loot");
        for (String kind : HitboxDemands.CROP_KINDS) {
            assertThat(out.get("data/tfp/loot_table/blocks/" + kind + "/wheat.json")).as(kind).isNotNull();
        }
        assertThat(new String(out.get("data/tfp/loot_table/blocks/crop/wheat.json"), StandardCharsets.UTF_8))
            .contains("tfp:food/wheat").contains("tfp:seeds/wheat").doesNotContain("tfc:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everySeedPlacesItsLivingBlockAndHasModelTextureAndName() {
        for (String crop : HitboxDemands.CROPS) {
            Map<String, Object> items = (Map<String, Object>) root(crop).get("items");
            Map<String, Object> seed = (Map<String, Object>) items.get("tfp:seeds/" + crop);
            assertThat(((Map<String, Object>) seed.get("behavior")).get("block")).isEqualTo("tfp:crop/" + crop);
            assertThat(seed.get("model")).isEqualTo("tfp:item/seeds/" + crop);
            assertThat(out.get(CropBlocks.RP + "models/item/seeds/" + crop + ".json")).isNotNull();
            assertThat(out.get(CropBlocks.RP + "textures/item/seeds/" + crop + ".png")).isNotNull();
        }
        JsonObject en = Json.parse(new String(out.get(CropBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8), "lang").getAsJsonObject();
        assertThat(en.get("item.tfp.seeds.green_bean").getAsString()).isEqualTo("Green Bean Seeds");
        assertThat(en.size()).isEqualTo(29);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyDrawnModelAndTextureShipsAndTheWildGrassOverlayIsDropped() {
        for (CropBlocks.CropDef c : crops) {
            for (String kind : HitboxDemands.CROP_KINDS) {
                for (Object e : ((Map<String, Object>) root(c.name()).get("items")).values()) {
                    Map<String, Object> item = (Map<String, Object>) e;
                    if (item.get("model") instanceof String m && m.startsWith("tfp:block/")) {
                        String path = m.substring("tfp:".length());
                        assertThat(out.get(CropBlocks.RP + "models/" + path + ".json")).as(m).isNotNull();
                    }
                }
            }
        }
        String wild = new String(out.get(CropBlocks.RP + "models/block/wild_crop/crop.json"), StandardCharsets.UTF_8);
        assertThat(wild).doesNotContain("tintindex").doesNotContain("wild_crop_grass");
        assertThat(new String(out.get(CropBlocks.RP + "models/block/wild_crop/wheat.json"), StandardCharsets.UTF_8))
            .contains("tfp:block/wild_crop/crop").contains("tfp:block/crop/wheat_wild");
        assertThat(out.get(CropBlocks.RP + "textures/block/crop/wheat_3.png")).isNotNull();
    }
}
