/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.bloomery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.material.MaterialItems;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-14: the tfp:bloomery, tfp:bloom and tfp:molten entries on the real checkout. */
class BloomeryBlocksTest {
    private static TfcCheckout tfc;
    private static OutputTree out;
    private static int states;

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
        assertThat(BloomeryBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = BloomeryBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @Test
    void thereAreSixteenPlusEightPlusEightServerStates() {
        assertThat(states).isEqualTo(16 + 8 + 8);
    }

    @Test
    void bloomeryHasTfcsThreePropertiesLightAndSettings() {
        Map<String, Object> b = block(BloomeryBlocks.BLOOMERY_OUTPUT, BloomeryBlocks.BLOOMERY_ID);
        Map<String, Object> settings = map(b.get("settings"));
        assertThat(settings.get("hardness")).isEqualTo(3.0);
        assertThat(settings.get("tags")).isEqualTo(List.of("minecraft:mineable/pickaxe"));
        assertThat(map(b.get("behavior")).get("type")).isEqualTo("tfp:device");
        assertThat(b.get("loot")).isNotNull();
        Map<String, Object> st = map(b.get("states"));
        assertThat(map(st.get("properties")).keySet()).containsExactly("facing", "lit", "open");
        Map<String, Object> variants = map(st.get("variants"));
        assertThat(variants).hasSize(16);
        Map<String, Object> lit = map(variants.get("facing=east,lit=true,open=false"));
        assertThat(map(lit.get("settings")).get("luminance")).isEqualTo(BloomeryBlocks.LIT_LIGHT);
        assertThat(lit.get("appearance")).isEqualTo("east_closed_on");
        Map<String, Object> dark = map(variants.get("facing=west,lit=false,open=true"));
        assertThat(map(dark.get("settings")).get("luminance")).isEqualTo(0);
        assertThat(dark.get("appearance")).isEqualTo("west_open_off");
    }

    @Test
    void everyBloomeryAppearanceSitsOnTheCarrierOfItsOutlineAndDrawsTfcsModelTurnedByTheFacing() {
        Map<String, Object> b = block(BloomeryBlocks.BLOOMERY_OUTPUT, BloomeryBlocks.BLOOMERY_ID);
        Map<String, Object> appearances = map(map(b.get("states")).get("appearances"));
        assertThat(appearances).hasSize(16);
        for (String f : List.of("north", "east", "south", "west")) {
            for (boolean open : new boolean[] {false, true}) {
                String carrier = HitboxAllocation.agreed(BloomeryBlocks.BLOOMERY_ID, HitboxDemands.bloomeryOutline(f, open))
                    .state();
                for (boolean lit : new boolean[] {false, true}) {
                    String name = BloomeryBlocks.bloomeryAppearance(f, open, lit);
                    Map<String, Object> a = map(appearances.get(name));
                    assertThat(a.get("state")).as(name).isEqualTo(carrier);
                    List<?> renderers = (List<?>) a.get("entity_renderer");
                    assertThat(renderers).as(name).hasSize(1);
                    assertThat(map(renderers.getFirst()).get("item")).isEqualTo("tfp:render/bloomery/" + (open ? "open" : "closed")
                        + (lit ? "_on" : "_off"));
                }
            }
        }
    }

    @Test
    void theOutlinesAreTfcsGetShape() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.bloomeryKey("north", false)).selection()).isEqualTo(Shape.box(0, 0, 0, 16, 16, 2));
        assertThat(rows.get(TfcShapes.bloomeryKey("south", false)).selection()).isEqualTo(Shape.box(0, 0, 14, 16, 16, 16));
        assertThat(rows.get(TfcShapes.bloomeryKey("west", false)).selection()).isEqualTo(Shape.box(0, 0, 0, 2, 16, 16));
        assertThat(rows.get(TfcShapes.bloomeryKey("east", false)).selection()).isEqualTo(Shape.box(14, 0, 0, 16, 16, 16));
        Shape open = rows.get(TfcShapes.bloomeryKey("north", true)).selection();
        assertThat(open.height()).isEqualTo(16);
        assertThat(open.volume()).as("lintel + sill + two posts + two walls").isPositive().isLessThan(16 * 16 * 8);
        assertThat(rows.get(TfcShapes.bloomeryKey("east", true)).selection())
            .isEqualTo(rows.get(TfcShapes.bloomeryKey("north", true)).selection().rotateY(1));
        for (String f : List.of("north", "east", "south", "west")) {
            for (boolean o : new boolean[] {false, true}) {
                assertThat(rows.get(TfcShapes.bloomeryKey(f, o)).collision()).isEqualTo(rows.get(TfcShapes.bloomeryKey(f, o)).selection());
            }
        }
        for (int layers = 1; layers <= 8; layers++) {
            assertThat(rows.get(TfcShapes.bloomKey(layers)).selection()).isEqualTo(Shape.box(0, 0, 0, 16, 2 * layers, 16));
        }
    }

    @Test
    void bloomHasEightLayersOnTfcsStrengthWithoutLootAndDrawsTheHeightModels() {
        Map<String, Object> b = block(BloomeryBlocks.BLOOM_OUTPUT, BloomeryBlocks.BLOOM_ID);
        Map<String, Object> settings = map(b.get("settings"));
        assertThat(settings.get("hardness")).isEqualTo(3.0);
        assertThat(settings.get("resistance")).isEqualTo(6.0);
        assertThat(b.containsKey("loot")).as("the plugin drops one item a break").isFalse();
        assertThat(map(b.get("behavior")).get("type")).isEqualTo("tfp:device");
        Map<String, Object> st = map(b.get("states"));
        assertThat(map(st.get("properties")).keySet()).containsExactly("layers");
        assertThat(map(st.get("variants"))).hasSize(8);
        Map<String, Object> appearances = map(st.get("appearances"));
        for (int layers = 1; layers <= 8; layers++) {
            Map<String, Object> a = map(appearances.get(HitboxDemands.bloomLayers(layers)));
            assertThat(a.get("state")).isEqualTo(HitboxAllocation.agreed(BloomeryBlocks.BLOOM_ID, HitboxDemands.bloomLayers(layers)).state());
            assertThat(map(((List<?>) a.get("entity_renderer")).getFirst()).get("item"))
                .isEqualTo("tfp:render/" + BloomeryBlocks.BLOOM_MODELS.get(layers - 1));
        }
    }

    @Test
    void moltenIsAPlainFourLayerLitBlock() {
        Map<String, Object> b = block(BloomeryBlocks.MOLTEN_OUTPUT, BloomeryBlocks.MOLTEN_ID);
        assertThat(b.containsKey("behavior")).isFalse();
        assertThat(b.containsKey("loot")).isFalse();
        Map<String, Object> st = map(b.get("states"));
        assertThat(map(st.get("properties")).keySet()).containsExactly("layers", "lit");
        Map<String, Object> variants = map(st.get("variants"));
        assertThat(variants).hasSize(8);
        assertThat(map(map(variants.get("layers=3,lit=true")).get("settings")).get("luminance")).isEqualTo(15);
        assertThat(map(map(variants.get("layers=3,lit=false")).get("settings")).get("luminance")).isEqualTo(0);
        assertThat(map(variants.get("layers=4,lit=true")).get("appearance")).isEqualTo("layers_4_on");
    }

    @Test
    void theModelsTexturesAndNamesAreCopied() {
        for (String m : List.of("bloomery/closed_off", "bloomery/closed_on", "bloomery/open_off", "bloomery/open_on",
            "bloom/bloom_block", "bloom/bloom_height2", "molten/molten_block", "molten/molten_lit_height4")) {
            assertThat(out.get(BloomeryBlocks.RP + "models/block/" + m + ".json")).as(m).isNotNull();
        }
        assertThat(out.get(BloomeryBlocks.RP + "models/item/bloomery.json")).isNotNull();
        String en = new String(out.get(BloomeryBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("block.tfp.bloomery").contains("Bloomery");
    }

    @Test
    void theBloomItemsAreGeneratedAsPlainMaterialItems() {
        var items = MaterialItems.items(tfc, Set.of());
        assertThat(items.stream().map(MaterialItems.MaterialItem::id)).contains("raw_iron_bloom", "refined_iron_bloom");
        assertThat(items.stream().filter(i -> i.id().endsWith("_iron_bloom")).map(MaterialItems.MaterialItem::group))
            .containsOnly("misc");
    }
}
