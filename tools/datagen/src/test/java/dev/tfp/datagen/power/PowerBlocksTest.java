/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.power;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.rendered.RenderedTemplates;
import dev.tfp.datagen.rotation.RotationBlocks;
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

/** PAR-MECH-18: the water wheel and windmill block families, the blade items and the baked TFC models on the real checkout. */
class PowerBlocksTest {
    private static final String RP = "craftengine/tfp/resourcepack/assets/tfp/";
    private static OutputTree out;
    private static long states;

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
        assertThat(PowerBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = PowerBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String file) {
        return new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        return (Map<String, Object>) ((Map<String, Object>) yaml(file).get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states(Map<String, Object> block) {
        return (Map<String, Object>) block.get("states");
    }

    private static JsonObject model(String path) {
        return Json.parse(new String(out.get(RP + "models/" + path + ".json"), StandardCharsets.UTF_8), path).getAsJsonObject();
    }

    @Test
    void stateCountsAreTfcsOnCraftEnginesAxis() {
        // 20 woods x (wheel: axis x|y|z; windmill: axis x|y|z x count 1..5); y is CraftEngine's axis type, never used
        assertThat(states).isEqualTo(20L * (3 + 3 * 5));
        assertThat((Map<?, ?>) yaml(PowerBlocks.WHEEL_FILE).get("blocks")).hasSize(20);
        assertThat((Map<?, ?>) yaml(PowerBlocks.WINDMILL_FILE).get("blocks")).hasSize(20);
    }

    /** Wood.BlockType.WATER_WHEEL: strength 9, wood sounds, the axe, the wheel drops itself; a barrier drawn by the model. */
    @Test
    @SuppressWarnings("unchecked")
    void theWheelIsABarrierCubeDrawnByTfcsModel() {
        Map<String, Object> wheel = block(PowerBlocks.WHEEL_FILE, "tfp:wood/water_wheel/oak");
        Map<String, Object> s = (Map<String, Object>) wheel.get("settings");
        assertThat(s.get("hardness")).isEqualTo(9.0);
        assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/axe"));
        assertThat(wheel.get("behavior")).isEqualTo(Map.of("type", "tfp:device"));
        assertThat(wheel.get("loot").toString()).contains("tfp:loot_table/self");
        Map<String, Object> props = (Map<String, Object>) states(wheel).get("properties");
        assertThat(props.get("axis")).isEqualTo(Map.of("type", "axis", "default", "x"));
        Map<String, Object> x = (Map<String, Object>) ((Map<String, Object>) states(wheel).get("appearances")).get("x");
        assertThat(x.get("state")).isEqualTo(RenderedTemplates.CARRIER);
        Map<String, Object> r = ((List<Map<String, Object>>) x.get("entity_renderer")).getFirst();
        assertThat(r.get("item")).isEqualTo("tfp:render/power/water_wheel/oak");
        assertThat(r.get("scale")).isEqualTo(4.0);
        assertThat(r.get("rotation")).isEqualTo(RotationBlocks.rotation(0, 0));
        Map<String, Object> variants = (Map<String, Object>) states(wheel).get("variants");
        assertThat(variants.get("axis=y")).isEqualTo(Map.of("appearance", "x"));
        assertThat(variants.get("axis=z")).isEqualTo(Map.of("appearance", "z"));
        Map<String, Object> items = (Map<String, Object>) yaml(PowerBlocks.WHEEL_FILE).get("items");
        assertThat(items).containsKeys("tfp:wood/water_wheel/oak", "tfp:render/power/water_wheel/oak");
        assertThat(model("item/wood/water_wheel/oak").getAsJsonObject("textures").get("layer0").getAsString())
            .isEqualTo("tfp:item/wood/water_wheel_oak");
        assertThat(out.contains(RP + "textures/item/wood/water_wheel_oak.png")).isTrue();
        assertThat(out.contains(RP + "textures/block/power/water_wheel/oak.png")).isTrue();
    }

    /** WindmillBlock: the axle's outline and loot, count 1..5, the shaft and count evenly spaced default blades. */
    @Test
    @SuppressWarnings("unchecked")
    void theWindmillDrawsItsShaftAndBlades() {
        Map<String, Object> mill = block(PowerBlocks.WINDMILL_FILE, "tfp:wood/windmill/oak");
        assertThat(mill.get("loot").toString()).contains("tfp:wood/axle/oak").doesNotContain("windmill_blade");
        Map<String, Object> props = (Map<String, Object>) states(mill).get("properties");
        assertThat(props.get("count")).isEqualTo(Map.of("type", "int", "range", "1~5", "default", 1));
        Map<String, Object> appearances = (Map<String, Object>) states(mill).get("appearances");
        assertThat(appearances).hasSize(10);
        Map<String, Object> z3 = (Map<String, Object>) appearances.get("z_3");
        assertThat(z3.get("state")).isEqualTo(RotationBlocks.axleCarrier("z").state());
        List<Map<String, Object>> r = (List<Map<String, Object>>) z3.get("entity_renderer");
        assertThat(r).hasSize(4);
        assertThat(r.getFirst().get("item")).isEqualTo("tfp:render/power/windmill_axle/oak");
        assertThat(r.get(1).get("item")).isEqualTo(PowerBlocks.BLADE_RENDER);
        assertThat(r.stream().skip(1).map(m -> m.get("rotation")).distinct()).hasSize(3);
        Map<String, Object> variants = (Map<String, Object>) states(mill).get("variants");
        assertThat(variants.get("axis=y,count=5")).isEqualTo(Map.of("appearance", "x_5"));
        Map<String, Object> items = (Map<String, Object>) yaml(PowerBlocks.WINDMILL_FILE).get("items");
        assertThat(items).doesNotContainKey("tfp:wood/windmill/oak"); // TFC registers no windmill item
        assertThat(items).containsKeys("tfp:render/power/windmill_axle/oak", PowerBlocks.BLADE_RENDER, PowerBlocks.LATTICE_RENDER);
    }

    /** TFC's renderer: blade i at -(2 pi / count) i about x; the display's half turn about y undone last. */
    @Test
    void bladeRotationsSpaceTheBladesEvenly() {
        assertThat(PowerBlocks.bladeRotation("x", 0, 1)).isEqualTo("0,1,0,0");
        assertThat(PowerBlocks.bladeRotation("x", 1, 2)).isEqualTo(RotationBlocks.rotation(180, 0));
        assertThat(PowerBlocks.bladeRotation("z", 0, 4)).isEqualTo(RotationBlocks.rotation(0, 90));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bladeItemsAreTfcs() {
        Map<String, Object> items = (Map<String, Object>) yaml(PowerBlocks.BLADE_FILE).get("items");
        assertThat(items).hasSize(18).containsKeys("tfp:windmill_blade/white", "tfp:windmill_blade/black", PowerBlocks.LATTICE,
            PowerBlocks.RUSTIC);
        assertThat(items.get("tfp:windmill_blade/red").toString()).contains("tfp:item/simple", "item.tfp.windmill_blade.red");
        assertThat(model("item/windmill_blade/red").getAsJsonObject("textures").get("layer0").getAsString())
            .isEqualTo("tfp:item/windmill_blade/red");
        JsonObject en = Json.parse(new String(out.get("craftengine/tfp/resourcepack/assets/tfp_power/lang/en_us.json"),
            StandardCharsets.UTF_8), "en").getAsJsonObject();
        assertThat(en.get("item.tfp.windmill_blade.light_blue").getAsString()).isEqualTo("Light Blue Windmill Blade");
        assertThat(en.get("item.tfp.lattice_windmill_blade").getAsString()).isNotBlank();
        assertThat(en.get("block.tfp.wood.water_wheel.oak").getAsString()).isEqualTo("Oak Water Wheel");
        assertThat(en.get("block.tfp.wood.windmill.oak").getAsString()).isEqualTo("Oak Windmill");
    }

    /** WaterWheelModel: the hub's 7 cubes and 8 arms of 10, every cube in the element range, turns of 22.5 degree steps. */
    @Test
    void theWheelModelIsTfcsCubes() {
        JsonArray elements = model("block/power/water_wheel").getAsJsonArray("elements");
        assertThat(elements).hasSize(7 + 8 * 10);
        Set<Double> angles = Set.of(-45.0, -22.5, 22.5, 45.0);
        for (JsonElement e : elements) {
            JsonObject o = e.getAsJsonObject();
            for (String k : List.of("from", "to")) {
                for (JsonElement c : o.getAsJsonArray(k)) {
                    assertThat(c.getAsDouble()).isBetween(-16.0, 32.0);
                }
            }
            if (o.has("rotation")) {
                assertThat(angles).contains(o.getAsJsonObject("rotation").get("angle").getAsDouble());
                assertThat(o.getAsJsonObject("rotation").get("axis").getAsString()).isEqualTo("x");
            }
            assertThat(o.getAsJsonObject("faces").keySet()).hasSize(6);
        }
        assertThat(model("block/power/water_wheel/oak").get("parent").getAsString()).isEqualTo("tfp:block/power/water_wheel");
    }

    /** WindmillBladeModel at rest: the spar and the sail hang down from the centre along the frame's y. */
    @Test
    void theBladeModelHangsFromTheCentre() {
        JsonArray elements = model("block/power/windmill_blade").getAsJsonArray("elements");
        assertThat(elements).hasSize(2);
        JsonObject main = elements.get(1).getAsJsonObject(); // main: box(-2, -1.5, -94.5, 4, 3, 96) 24 px up
        // XN 90 turns -z to -y: y from 8 - 94.5 / 4 to 8 + 1.5 / 4; x 8 -+ 2 / 4; z 8 -+ 1.5 / 4
        assertThat(main.getAsJsonArray("from").toString()).isEqualTo("[7.5,-15.625,7.625]");
        assertThat(main.getAsJsonArray("to").toString()).isEqualTo("[8.5,8.375,8.375]");
        assertThat(model("block/power/windmill_blade_lattice").getAsJsonArray("elements")).hasSize(5);
    }

    /** ModelPart.Cube's box layout on a vanilla element: a plain 2x2x2 cube at texOffs(0, 0) on a 16 px texture. */
    @Test
    void boxUvsLandOnTheirCorners() {
        String src = "PartDefinition a = partdefinition.addOrReplaceChild(\"a\", CubeListBuilder.create().texOffs(0, 0)"
            + ".addBox(0.0F, 0.0F, 0.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));"
            + " LayerDefinition.create(meshdefinition, 16, 16)";
        JsonObject faces = EntityModelBaker.read("test", src).bake(List.of("a"), EntityModelBaker.Affine.IDENTITY, 1, "#t")
            .getAsJsonArray("elements").get(0).getAsJsonObject().getAsJsonObject("faces");
        // f4..f9 = 0, 2, 4, 6, 6, 8; f10..f12 = 0, 2, 4. North (u f5..f6, v f11..f12): u runs -x, v runs +y, as vanilla's
        // north (u = 16 - x) with v flipped (vanilla v = 16 - y)
        assertThat(faces.getAsJsonObject("north").getAsJsonArray("uv").toString()).isEqualTo("[4.0,4.0,2.0,2.0]");
        assertThat(faces.getAsJsonObject("south").getAsJsonArray("uv").toString()).isEqualTo("[8.0,4.0,6.0,2.0]");
        assertThat(faces.getAsJsonObject("down").getAsJsonArray("uv").toString()).isEqualTo("[2.0,0.0,4.0,2.0]");
        assertThat(faces.getAsJsonObject("up").getAsJsonArray("uv").toString()).isEqualTo("[4.0,2.0,6.0,0.0]");
        assertThat(faces.getAsJsonObject("west").getAsJsonArray("uv").toString()).isEqualTo("[2.0,4.0,0.0,2.0]");
        assertThat(faces.getAsJsonObject("east").getAsJsonArray("uv").toString()).isEqualTo("[6.0,4.0,4.0,2.0]");
    }
}
