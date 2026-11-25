/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.pile;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import dev.tfp.core.pile.PileKind;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-METAL-16: the tfp:ingot_pile and tfp:double_ingot_pile entries and display models on the real checkout. */
class PileBlocksTest {
    private static OutputTree out;
    private static List<String> metals;
    private static int states;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        TfcCheckout tfc;
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
            return;
        }
        assertThat(PileBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = PileBlocks.run(tfc, out);
        metals = PileBlocks.metals(tfc);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bothPilesHaveTfcsCountStatesSettingsAndNoLootOrItem() {
        assertThat(states).isEqualTo(64 + 36);
        for (Object[] c : new Object[][] {{PileBlocks.INGOT_OUTPUT, PileBlocks.INGOT_ID, 64}, {PileBlocks.DOUBLE_OUTPUT, PileBlocks.DOUBLE_ID, 36}}) {
            Map<String, Object> b = block((String) c[0], (String) c[1]);
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            assertThat(args.get("device_hardness")).isEqualTo(4.0);
            assertThat(args.get("device_resistance")).isEqualTo(60.0);
            assertThat(args.get("device_tool_tag")).isEqualTo("minecraft:mineable/pickaxe");
            assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
            assertThat(b).doesNotContainKey("loot");
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            assertThat(((Map<String, Object>) st.get("properties")).keySet()).containsExactly("count");
            assertThat((Map<String, Object>) st.get("variants")).hasSize((int) c[2]);
            Map<String, Object> root = new Yaml().load(new String(out.get((String) c[0]), StandardCharsets.UTF_8));
            assertThat(root).doesNotContainKey("items");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCountSitsOnItsLayersCarrierAndTfcsOutline() {
        for (PileKind kind : PileKind.values()) {
            String file = kind == PileKind.INGOT ? PileBlocks.INGOT_OUTPUT : PileBlocks.DOUBLE_OUTPUT;
            Map<String, Object> b = block(file, PileBlocks.id(kind));
            Map<String, Object> st = (Map<String, Object>) b.get("states");
            Map<String, Object> variants = (Map<String, Object>) st.get("variants");
            Map<String, Object> appearances = (Map<String, Object>) st.get("appearances");
            assertThat(appearances).hasSize(kind.layers());
            for (int count = 1; count <= kind.capacity(); count++) {
                String layer = HitboxDemands.ingotPileAppearance(kind.shapeIndex(count));
                assertThat(((Map<String, Object>) variants.get("count=" + count)).get("appearance")).as("count " + count).isEqualTo(layer);
            }
            for (int layer = 0; layer < kind.layers(); layer++) {
                Map<String, Object> a = (Map<String, Object>) appearances.get(HitboxDemands.ingotPileAppearance(layer));
                assertThat(a.get("state")).isEqualTo(HitboxAllocation.agreed(PileBlocks.id(kind), HitboxDemands.ingotPileAppearance(layer)).state());
                assertThat(a.get("transparent")).isEqualTo(true);
                assertThat(a).doesNotContainKey("entity_renderer");
            }
        }
    }

    @Test
    void theOutlinesAreTfcsShapesOfEachLayer() {
        for (int layer = 0; layer < 8; layer++) {
            assertThat(TfcShapes.load().get(TfcShapes.ingotPileKey(layer)).selection())
                .isEqualTo(Shape.box(0.25, 0, 0.25, 15.75, 2 * (layer + 1), 15.75));
        }
        for (int layer = 0; layer < 6; layer++) {
            float top = layer == 5 ? 16f : (layer + 1) / 6f * 16;
            assertThat(TfcShapes.load().get(TfcShapes.doubleIngotPileKey(layer)).selection())
                .isEqualTo(Shape.box(0.25, 0, 0.25, 15.75, top, 15.75));
            assertThat(TfcShapes.load().get(TfcShapes.doubleIngotPileKey(layer)).collision())
                .isEqualTo(TfcShapes.load().get(TfcShapes.doubleIngotPileKey(layer)).selection());
        }
    }

    @Test
    void everyMetalHasATextureAndAModelPerKindAndVariant() {
        assertThat(metals).contains("copper", "bronze", "wrought_iron", "unknown");
        for (String metal : metals) {
            assertThat(out.get(PileBlocks.RP + "textures/block/pile/" + metal + ".png")).isNotNull();
            for (PileKind kind : PileKind.values()) {
                for (String v : List.of("single", "layer")) {
                    assertThat(out.get(PileBlocks.RP + "models/item/render/pile/" + PileBlocks.name(kind) + "_" + v + "/" + metal + ".json"))
                        .isNotNull();
                }
            }
        }
    }

    @Test
    void theItemDefinitionSelectsTheModelByTheMetalAndFallsBackToTheUnknownOne() {
        String text = new String(out.get(PileBlocks.RP + "items/render/pile/ingot_single.json"), StandardCharsets.UTF_8);
        JsonObject model = Json.parse(text, "items").getAsJsonObject().getAsJsonObject("model");
        assertThat(model.get("type").getAsString()).isEqualTo("minecraft:select");
        assertThat(model.get("property").getAsString()).isEqualTo("minecraft:custom_model_data");
        assertThat(model.getAsJsonArray("cases")).hasSize(metals.size());
        assertThat(model.getAsJsonObject("fallback").get("model").getAsString())
            .isEqualTo("tfp:item/render/pile/ingot_single/unknown");
    }

    @Test
    void aSingleModelHoldsOneIngotAndALayerModelAllOfTheLayersIngots() {
        for (PileKind kind : PileKind.values()) {
            JsonObject single = PileBlocks.model(kind, PileBlocks.Variant.SINGLE, "copper");
            JsonObject layer = PileBlocks.model(kind, PileBlocks.Variant.LAYER, "copper");
            assertThat(single.getAsJsonArray("elements")).hasSize(2);
            assertThat(layer.getAsJsonArray("elements")).hasSize(2 * kind.perLayer());
            assertThat(single.getAsJsonObject("textures").get("0").getAsString()).isEqualTo("tfp:block/pile/copper");
        }
    }

    @Test
    void theFirstSingleIngotLiesWhereTfcsTrapezoidDoes() {
        // IngotPileBlockModel: minX = 1/64, maxX = (1/64 + 7) / 32, maxZ = (1/64 + 15) / 32, height 4 / 32 (all in blocks)
        List<double[]> boxes = PileBlocks.ingotBoxes(PileKind.INGOT, 0, 0);
        assertThat(boxes.get(0)).containsExactly(0.25, 0.0, 0.25, 3.508, 1.0, 7.508);
        assertThat(boxes.get(1)).containsExactly(0.75, 1.0, 0.75, 3.008, 2.0, 7.008);
        List<double[]> dbl = PileBlocks.ingotBoxes(PileKind.DOUBLE_INGOT, 0, 0);
        assertThat(dbl.get(1)[4]).isEqualTo(2.5);
        assertThat(dbl.get(0)[3]).isEqualTo(5.008);
    }
}
