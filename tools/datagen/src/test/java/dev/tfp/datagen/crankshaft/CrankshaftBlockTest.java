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

/** PAR-MECH-21: the tfp:crankshaft entry on the real checkout. */
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
            "tfp:render/crankshaft/shaft");
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

    /** The base draws TFC's base and wheel models turned as the blockstate (north 0, east 90, south 180, west 270). */
    @Test
    @SuppressWarnings("unchecked")
    void appearancesTurnWithTheFacing() {
        Map<String, Object> appearances = (Map<String, Object>) states().get("appearances");
        assertThat(appearances).hasSize(8);
        for (Map.Entry<String, Object> e : appearances.entrySet()) {
            Map<String, Object> a = (Map<String, Object>) e.getValue();
            assertThat(a.get("state")).isEqualTo("minecraft:barrier");
            List<Map<String, Object>> r = (List<Map<String, Object>>) a.get("entity_renderer");
            assertThat(r).hasSize(e.getKey().startsWith("base_") ? 2 : 1);
        }
        List<Map<String, Object>> north = (List<Map<String, Object>>) ((Map<String, Object>) appearances.get("base_north"))
            .get("entity_renderer");
        assertThat(north.get(0).get("item")).isEqualTo("tfp:render/crankshaft/base");
        assertThat(north.get(1).get("item")).isEqualTo("tfp:render/crankshaft/wheel");
        List<Map<String, Object>> east = (List<Map<String, Object>>) ((Map<String, Object>) appearances.get("shaft_east"))
            .get("entity_renderer");
        assertThat(east.get(0).get("item")).isEqualTo("tfp:render/crankshaft/shaft");
        assertThat(east.get(0).get("rotation")).isEqualTo(dev.tfp.datagen.rotation.RotationBlocks.rotation(0, 90));
    }

    /** The shaft at rest: TFC's three cuboids, lengthEH 1, no raise; the steel rod texture. */
    @Test
    void shaftModelIsTheRendererAtRest() {
        JsonObject model = Json.parse(new String(out.get(CrankshaftBlock.RP + "models/" + CrankshaftBlock.SHAFT_MODEL + ".json"),
            StandardCharsets.UTF_8), "shaft").getAsJsonObject();
        assertThat(model.getAsJsonObject("textures").get("rod").getAsString()).isEqualTo("tfp:block/metal/block/steel");
        JsonArray elements = model.getAsJsonArray("elements");
        assertThat(elements).hasSize(3);
        // the connecting box: centre (9, 8, 16) px, radius 2
        assertThat(elements.get(0).getAsJsonObject().get("from").toString()).isEqualTo("[7,6,14]");
        assertThat(elements.get(0).getAsJsonObject().get("to").toString()).isEqualTo("[11,10,18]");
        // the piston: PISTON_LENGTH + 1/32 = 16.5 px toward the front
        assertThat(elements.get(1).getAsJsonObject().get("from").toString()).isEqualTo("[8,7,-0.5]");
        assertThat(elements.get(1).getAsJsonObject().get("to").toString()).isEqualTo("[10,9,17]");
        // the arm: ARM_LENGTH + armRadius back to the wheel
        assertThat(elements.get(2).getAsJsonObject().get("to").toString()).isEqualTo("[10,9,29]");
        assertThat(out.contains(CrankshaftBlock.RP + "textures/block/metal/block/steel.png")).isTrue();
        assertThat(out.contains(CrankshaftBlock.RP + "models/block/crankshaft/crankshaft.json")).isTrue();
        assertThat(out.contains(CrankshaftBlock.RP + "models/block/crankshaft/crankshaft_wheel.json")).isTrue();
    }
}
