/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crankshaft;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.tfp.datagen.Json;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-MECH-21 / PAR-MECH-26: the tfp:crankshaft entry on the real checkout. */
class CrankshaftBlockTest {
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
        assertThat(CrankshaftBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = CrankshaftBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(CrankshaftBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get("tfp:crankshaft");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states() {
        return (Map<String, Object>) block().get("states");
    }

    /** TFC CrankshaftBlock: facing (4) x part (2), default north / base. */
    @Test
    @SuppressWarnings("unchecked")
    void eightStates() {
        assertThat(states).isEqualTo(8);
        Map<String, Object> props = (Map<String, Object>) states().get("properties");
        assertThat(((Map<String, Object>) props.get("facing")).get("values")).isEqualTo(List.of("north", "east", "south", "west"));
        assertThat(((Map<String, Object>) props.get("facing")).get("default")).isEqualTo("north");
        assertThat(((Map<String, Object>) props.get("part")).get("values")).isEqualTo(List.of("base", "shaft"));
        assertThat(((Map<String, Object>) props.get("part")).get("default")).isEqualTo("base");
        Map<String, Object> variants = (Map<String, Object>) states().get("variants");
        assertThat(variants).hasSize(8);
        assertThat(((Map<String, Object>) variants.get("facing=east,part=shaft")).get("appearance")).isEqualTo("shaft_east");
        assertThat(((Map<String, Object>) variants.get("facing=west,part=base")).get("appearance")).isEqualTo("base_west");
    }

    /** TFCBlocks: strength(3f), SoundType.METAL, the pickaxe; the device behaviour; an item. */
    @Test
    @SuppressWarnings("unchecked")
    void settingsBehaviourAndItem() {
        Map<String, Object> s = (Map<String, Object>) block().get("settings");
        assertThat(s.get("hardness")).isEqualTo(3.0);
        assertThat(s.get("resistance")).isEqualTo(3.0);
        assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/pickaxe"));
        assertThat(((Map<String, Object>) s.get("sounds")).toString()).contains("minecraft:block.metal.break");
        assertThat(((Map<String, Object>) block().get("behavior")).get("type")).isEqualTo("tfp:device");
        Map<String, Object> items = (Map<String, Object>) root().get("items");
        assertThat(items).containsKeys("tfp:crankshaft", "tfp:render/crankshaft/base", "tfp:render/crankshaft/wheel",
            "tfp:render/crankshaft/slider", "tfp:render/crankshaft/arm", "tfp:render/crankshaft/pump");
        assertThat(items).doesNotContainKey("tfp:render/crankshaft/shaft");
        assertThat(out.contains(CrankshaftBlock.RP + "models/item/crankshaft.json")).isTrue();
        JsonObject item = Json.parse(new String(out.get(CrankshaftBlock.RP + "models/item/crankshaft.json"),
            StandardCharsets.UTF_8), "item").getAsJsonObject();
        assertThat(item.get("parent").getAsString()).isEqualTo("tfp:block/crankshaft/crankshaft_with_wheel");
        assertThat(new String(out.get(CrankshaftBlock.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("\"block.tfp.crankshaft\": \"Crankshaft\"");
    }

    /** TFC's loot: the base drops the crankshaft, the shaft the steel rod. */
    @Test
    @SuppressWarnings("unchecked")
    void lootPerPart() {
        List<Map<String, Object>> pools = (List<Map<String, Object>>) ((Map<String, Object>) block().get("loot")).get("pools");
        assertThat(pools).hasSize(2);
        assertThat(pools.toString()).contains("item=tfp:crankshaft", "item=tfp:metal/rod/steel", "type=survives_explosion");
        Map<String, Object> base = ((List<Map<String, Object>>) pools.get(0).get("entries")).getFirst();
        assertThat(base.get("item")).isEqualTo("tfp:crankshaft");
        assertThat(base.get("conditions")).isEqualTo(List.of(Map.of("type", "match_block_property", "properties",
            Map.of("part", "base"))));
        Map<String, Object> shaft = ((List<Map<String, Object>>) pools.get(1).get("entries")).getFirst();
        assertThat(shaft.get("conditions").toString()).contains("part=shaft");
    }

    /**
     * PAR-MECH-26: the carrier of every state (barrier until TFC's outlines get shape rows, PAR-MECH-28); the base draws only
     * TFC's base model turned as the blockstate (north 0, east 90, south 180, west 270); the wheel and the shaft are the
     * spinning visuals' packet displays, so the shaft draws nothing of its own.
     */
    @Test
    @SuppressWarnings("unchecked")
    void carriersPerStateAndOnlyTheStaticBase() {
        Map<String, Object> appearances = (Map<String, Object>) states().get("appearances");
        assertThat(appearances).hasSize(8);
        Map<String, Object> variants = (Map<String, Object>) states().get("variants");
        for (String facing : CrankshaftBlock.FACINGS) {
            for (String part : CrankshaftBlock.PARTS) {
                String name = ((Map<String, Object>) variants.get("facing=" + facing + ",part=" + part)).get("appearance").toString();
                Map<String, Object> a = (Map<String, Object>) appearances.get(name);
                assertThat(a.get("state")).as(name).isEqualTo("minecraft:barrier");
                List<Map<String, Object>> r = (List<Map<String, Object>>) a.get("entity_renderer");
                if (part.equals("base")) {
                    assertThat(r).as(name).singleElement().satisfies(e -> {
                        assertThat(e.get("item")).isEqualTo("tfp:render/crankshaft/base");
                        assertThat(e.get("rotation")).isEqualTo(dev.tfp.datagen.rotation.RotationBlocks.rotation(0,
                            List.of(0, 90, 180, 270).get(CrankshaftBlock.FACINGS.indexOf(facing))));
                    });
                } else {
                    assertThat(r).as(name).isNull();
                }
            }
        }
    }

    /** PAR-MECH-26: the renderer's cuboids about the connecting point (the model centre): box and piston, and the arm. */
    @Test
    void sliderAndArmAreTheRenderersCuboidsAboutTheConnectingPoint() {
        JsonObject slider = model(CrankshaftBlock.SLIDER_MODEL);
        assertThat(slider.getAsJsonObject("textures").get("rod").getAsString()).isEqualTo("tfp:block/metal/block/steel");
        JsonArray s = slider.getAsJsonArray("elements");
        assertThat(s).hasSize(2);
        // the connecting box: boxRadius 2 px about the centre
        assertThat(s.get(0).getAsJsonObject().get("from").toString()).isEqualTo("[6,6,6]");
        assertThat(s.get(0).getAsJsonObject().get("to").toString()).isEqualTo("[10,10,10]");
        // the piston: PISTON_LENGTH + 1/32 = 16.5 px toward the front, armRadius behind
        assertThat(s.get(1).getAsJsonObject().get("from").toString()).isEqualTo("[7,7,-8.5]");
        assertThat(s.get(1).getAsJsonObject().get("to").toString()).isEqualTo("[9,9,9]");
        JsonArray a = model(CrankshaftBlock.ARM_MODEL).getAsJsonArray("elements");
        assertThat(a).hasSize(1);
        // the arm: -armRadius to ARM_LENGTH + armRadius back to the wheel
        assertThat(a.get(0).getAsJsonObject().get("from").toString()).isEqualTo("[7,7,7]");
        assertThat(a.get(0).getAsJsonObject().get("to").toString()).isEqualTo("[9,9,21]");
        assertThat(out.contains(CrankshaftBlock.RP + "textures/block/metal/block/steel.png")).isTrue();
        assertThat(out.contains(CrankshaftBlock.RP + "models/block/crankshaft/crankshaft.json")).isTrue();
        assertThat(out.contains(CrankshaftBlock.RP + "models/block/crankshaft/crankshaft_wheel.json")).isTrue();
        assertThat(out.contains(CrankshaftBlock.RP + "models/block/crankshaft/shaft.json")).isFalse();
        // the pump attachment: 2 px about the piston's axis, -pistonLength - 3/32 to -pistonLength + 5/32
        JsonObject pump = model(CrankshaftBlock.PUMP_MODEL);
        assertThat(pump.getAsJsonObject("textures").get("rod").getAsString()).isEqualTo("tfp:block/metal/smooth/brass");
        assertThat(pump.getAsJsonArray("elements").get(0).getAsJsonObject().get("from").toString()).isEqualTo("[6,6,-10]");
        assertThat(pump.getAsJsonArray("elements").get(0).getAsJsonObject().get("to").toString()).isEqualTo("[10,10,-6]");
        assertThat(out.contains(CrankshaftBlock.RP + "textures/block/metal/smooth/brass.png")).isTrue();
    }

    private static JsonObject model(String path) {
        return Json.parse(new String(out.get(CrankshaftBlock.RP + "models/" + path + ".json"), StandardCharsets.UTF_8), path)
            .getAsJsonObject();
    }
}
