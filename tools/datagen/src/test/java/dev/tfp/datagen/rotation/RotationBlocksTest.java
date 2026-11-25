/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.rotation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.rendered.RenderedTemplates;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-MECH-01: the rotation network block entries on the real checkout. */
class RotationBlocksTest {
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
        assertThat(RotationBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = RotationBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String type) {
        return new Yaml().load(new String(out.get(RotationBlocks.OUTPUT_DIR + type + ".yml"), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String type, String id) {
        return (Map<String, Object>) ((Map<String, Object>) root(type).get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearance(Map<String, Object> block, String name) {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) block.get("states")).get("appearances")).get(name);
    }

    @Test
    void stateCountsAreTfcs() {
        // TFC's 20 woods x (axle 3 + encased 3 + clutch 6 + gearbox 64 + bladed axle 3) + motor 3
        assertThat(states).isEqualTo(20L * (3 + 3 + 6 + 64 + 3) + 3);
        assertThat(((Map<?, ?>) root("gear_box").get("blocks"))).hasSize(20);
    }

    /** Wood.BlockType: strength 2.5 (gearbox 2), SoundType.WOOD with vanilla's client multipliers, axe, loot self. */
    @Test
    @SuppressWarnings("unchecked")
    void cubeSettingsAreTheExpandedRenderedCube() {
        Map<String, Object> clutch = block("clutch", "tfp:wood/clutch/oak");
        Map<String, Object> s = (Map<String, Object>) clutch.get("settings");
        assertThat(s.get("hardness")).isEqualTo(2.5);
        assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/axe"));
        Map<String, Object> brk = (Map<String, Object>) ((Map<String, Object>) s.get("sounds")).get("break");
        assertThat(brk).containsEntry("id", "minecraft:block.wood.break").containsEntry("pitch", 0.8);
        assertThat(((Map<String, Object>) s.get("destroy_stages")).get("items")).asList().hasSize(RenderedTemplates.STAGES);
        assertThat(((Map<String, Object>) clutch.get("loot")).get("template")).isEqualTo("tfp:loot_table/self");
        assertThat(((Map<String, Object>) clutch.get("behavior")).get("type")).isEqualTo("tfp:device");
        assertThat(clutch).containsKey("events").doesNotContainKey("state");
        assertThat(((Map<String, Object>) block("gear_box", "tfp:wood/gear_box/oak").get("settings")).get("hardness")).isEqualTo(2.0);
        Map<String, Object> motor = block("creative_motor", RotationBlocks.MOTOR);
        assertThat(motor).doesNotContainKey("loot");
        Map<String, Object> ms = (Map<String, Object>) motor.get("settings");
        assertThat(ms.get("hardness")).isEqualTo(5.0);
        assertThat(ms.get("correct_tools")).isEqualTo(List.of("#minecraft:pickaxes", "#tfp:tools/pickaxe"));
    }

    /** The barrier carrier drawn by TFC's model, turned as TFC's blockstate turns it; the powered clutch uses its own model. */
    @Test
    @SuppressWarnings("unchecked")
    void cubesAreDrawnByTfcsVariantModels() {
        Map<String, Object> clutch = block("clutch", "tfp:wood/clutch/oak");
        Map<String, Object> xPowered = appearance(clutch, "x_powered");
        assertThat(xPowered.get("state")).isEqualTo(RenderedTemplates.CARRIER);
        assertThat(xPowered).doesNotContainKey("transparent");
        Map<String, Object> r = (Map<String, Object>) ((List<Object>) xPowered.get("entity_renderer")).get(0);
        assertThat(r.get("item")).isEqualTo("tfp:render/rotation/wood/clutch/oak_powered");
        assertThat(r.get("rotation")).isEqualTo(RotationBlocks.rotation(90, 90));
        assertThat(r.get("view_range")).isEqualTo(4.0);
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) clutch.get("states")).get("variants");
        assertThat(((Map<String, Object>) variants.get("axis=z,powered=true")).get("appearance")).isEqualTo("z_powered");
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/models/block/rotation/wood/clutch/oak_powered.json")).isTrue();
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/textures/block/axle_casing_powered.png")).isTrue();
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/models/item/wood/clutch/oak.json")).isTrue();
    }

    /** GearBoxBlock's multipart: each state draws the six faces, a port where the face is enabled. */
    @Test
    @SuppressWarnings("unchecked")
    void gearBoxDrawsSixFacesPerState() {
        Map<String, Object> box = block("gear_box", "tfp:wood/gear_box/oak");
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) box.get("states")).get("variants");
        assertThat(variants).hasSize(64);
        String name = "faces_001000"; // FACES order up, down, north, south, east, west: only north enabled
        String key = variants.entrySet().stream()
            .filter(e -> name.equals(((Map<String, Object>) e.getValue()).get("appearance"))).findFirst().orElseThrow().getKey();
        assertThat(key).contains("north=true");
        assertThat(key.split("true", -1)).hasSize(2);
        List<Object> faces = (List<Object>) appearance(box, name).get("entity_renderer");
        assertThat(faces).hasSize(6);
        assertThat(((Map<String, Object>) faces.get(2)).get("item")).isEqualTo("tfp:render/rotation/wood/gear_box_port/oak");
        assertThat(((Map<String, Object>) faces.get(3)).get("item")).isEqualTo("tfp:render/rotation/wood/gear_box_face/oak");
        // south: y 180 in TFC's multipart
        assertThat(((Map<String, Object>) faces.get(3)).get("rotation")).isEqualTo(RotationBlocks.rotation(0, 180));
    }

    /**
     * PAR-MECH-15: the axle carriers come from the hitbox allocation (the demands of TFC's shaft outline), hitbox-only and
     * transparent; the lightning rods that score 1.0 for the shaft keep their vanilla look (a crafted block), so none is used.
     */
    @Test
    @SuppressWarnings("unchecked")
    void axleCarriersComeFromTheAllocation() {
        Map<String, Shape> shapes = Map.of("x", Shape.box(0, 6, 6, 16, 10, 10), "y", Shape.box(6, 0, 6, 10, 16, 10),
            "z", Shape.box(6, 6, 0, 10, 10, 16));
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        for (String axis : List.of("x", "y", "z")) {
            TfcShapes.Row row = rows.get(TfcShapes.axleKey(axis));
            assertThat(row.selection()).as(axis).isEqualTo(shapes.get(axis));
            assertThat(row.collision()).as(axis).isEqualTo(shapes.get(axis));
            HitboxAllocation.Assignment a = RotationBlocks.axleCarrier(axis);
            assertThat(a.demand().blockId()).isEqualTo(HitboxDemands.AXLE_ID);
            assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
            assertThat(a.state()).doesNotStartWith("minecraft:lightning_rod");
            for (String wood : List.of("oak", "palm", "willow")) {
                Map<String, Object> app = appearance(block("axle", "tfp:wood/axle/" + wood), axis);
                assertThat(app.get("state")).isEqualTo(a.state());
                assertThat(app.get("transparent")).isEqualTo(true);
            }
        }
    }

    /** PAR-MECH-12: the axle draws no static element (the spinning display is the only drawing); its render item stays. */
    @Test
    @SuppressWarnings("unchecked")
    void axleAppearancesCarryNoStaticElement() {
        Map<String, Object> items = (Map<String, Object>) root("axle").get("items");
        for (String wood : List.of("oak", "acacia", "willow")) {
            Map<String, Object> block = block("axle", "tfp:wood/axle/" + wood);
            for (String axis : List.of("x", "y", "z")) {
                assertThat(appearance(block, axis)).as("%s %s", wood, axis).doesNotContainKey("entity_renderer");
            }
            assertThat(items).containsKey("tfp:render/rotation/wood/axle/" + wood);
        }
        // the cubes keep theirs
        assertThat(appearance(block("encased_axle", "tfp:wood/encased_axle/oak"), "y")).containsKey("entity_renderer");
    }

    /**
     * PAR-MECH-20: 20 bladed axles, each the axle's carriers (TFC's shaft outline) with no static element, TFC's settings
     * (strength 2.5, axe), its loot (itself) and TFC's shaft-and-blade model as its render item, with the steel texture.
     */
    @Test
    @SuppressWarnings("unchecked")
    void bladedAxlesAreAxleCarriersWithTheBladeModel() {
        Map<String, Object> blocks = (Map<String, Object>) root("bladed_axle").get("blocks");
        Map<String, Object> items = (Map<String, Object>) root("bladed_axle").get("items");
        assertThat(blocks).hasSize(20);
        for (String wood : List.of("oak", "acacia", "willow")) {
            Map<String, Object> bladed = block("bladed_axle", "tfp:wood/bladed_axle/" + wood);
            Map<String, Object> axle = block("axle", "tfp:wood/axle/" + wood);
            for (String axis : List.of("x", "y", "z")) {
                assertThat(appearance(bladed, axis)).as("%s %s", wood, axis).doesNotContainKey("entity_renderer")
                    .isEqualTo(appearance(axle, axis));
            }
            assertThat(items).containsKey("tfp:render/rotation/wood/bladed_axle/" + wood);
            Map<String, Object> s = (Map<String, Object>) bladed.get("settings");
            assertThat(s.get("hardness")).isEqualTo(2.5);
            assertThat(s.get("tags")).isEqualTo(List.of("minecraft:mineable/axe"));
            assertThat(String.valueOf(bladed.get("loot"))).contains("tfp:loot_table/self"); // TFC: drops itself
        }
        String model = new String(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/rotation/bladed_axle.json"),
            StandardCharsets.UTF_8);
        assertThat(model.replaceAll("\\s", "")).contains("\"from\":[7,10,6]").contains("tfp:block/metal/smooth/steel");
        assertThat(out.contains("craftengine/tfp/resourcepack/assets/tfp/textures/block/metal/smooth/steel.png")).isTrue();
        String en = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_rotation/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("\"block.tfp.wood.bladed_axle.oak\": \"Oak Bladed Axle\"");
    }

    /** Vanilla's rotateYXZ(-y, -x, 0) followed by the item display's half turn. */
    @Test
    void rotationQuaternions() {
        assertThat(RotationBlocks.rotation(0, 0)).isEqualTo("0,1,0,0");
        assertThat(RotationBlocks.rotation(0, 90)).isEqualTo("0,0.707107,0,0.707107");
        assertThat(RotationBlocks.rotation(0, 180)).isEqualTo("0,0,0,1");
    }

    @Test
    void namesComeFromTfc() {
        String en = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_rotation/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("\"block.tfp.wood.axle.oak\": \"Oak Axle\"").contains("\"block.tfp.creative_motor\": \"Creative Motor\"");
    }
}
