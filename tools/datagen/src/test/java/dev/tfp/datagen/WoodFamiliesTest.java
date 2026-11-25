/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WoodFamiliesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    /** The 21 kinds of a wood that TFC registers once, and the 2 kinds that TFC registers per metal. */
    private static final Set<String> ONCE = Set.of("stairs", "slab", "fence", "log_fence", "fence_gate", "door",
        "trapdoor", "button", "pressure_plate", "sign", "wall_sign", "chest", "trapped_chest", "bookshelf", "lectern",
        "tool_rack", "shelf", "scribing_table", "sewing_table", "loom", "sluice");
    private static final Set<String> PER_METAL = Set.of("hanging_sign", "wall_hanging_sign");

    @TempDir
    Path tmp;

    /** Real checkout at the pinned commit (-Dtfc.checkout, $TFC_CHECKOUT, /opt/tfc); skipped when absent. */
    @Test
    void realCheckoutHasTwentyWoodsWithTheSameBlockKinds() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");

        List<WoodFamilies.Family> families = WoodFamilies.families(TfcCheckout.open(checkout, PIN));
        assertThat(families).hasSize(20);
        assertThat(families.stream().map(WoodFamilies.Family::wood)).contains("acacia", "white_cedar", "willow");
        for (WoodFamilies.Family f : families) {
            assertThat(WoodFamilies.kinds(f)).as(f.wood()).isEqualTo(expectedKinds());
            assertThat(f.blocks()).as(f.wood()).hasSize(21 + 2 * 9);
            assertThat(f.blocks().stream().filter(b -> b.metal() != null).map(WoodFamilies.Block::metal).distinct())
                .as(f.wood()).hasSize(9);
        }
        assertThat(families.stream().mapToInt(f -> f.blocks().size()).sum()).isEqualTo(20 * 39);
    }

    /**
     * PAR-DECO-01: the stairs, slabs, fences and log fences of every wood are CraftEngine blocks with TFC's properties,
     * strengths, tags, drops and names; stairs and slabs carry TFC's models on their own vanilla twin, the double slab draws
     * the planks' visual, fences are drawn by item displays on the shared oak fence carrier.
     */
    @Test
    @SuppressWarnings("unchecked")
    void realCheckoutWritesTheStairsSlabsAndFencesAsCraftEngineBlocks() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);
        List<String> woods = WoodFamilies.families(tfc).stream().map(WoodFamilies.Family::wood).toList();
        OutputTree out = new OutputTree();
        assertThat(WoodFamilies.writeBlocks(tfc, out, woods)).isEqualTo(20 * (80 + 6 + 32 + 32 + 32 + 64 + 64 + 24 + 2));
        Set<String> modelCarriers = new java.util.HashSet<>();
        for (String wood : woods) {
            String file = WoodFamilies.CONFIG_DIR + wood + ".yml";
            Map<String, Object> root = (Map<String, Object>) CeYamlCheck.parse(
                new String(out.get(file), StandardCharsets.UTF_8), file);
            Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
            String stairs = "tfp:wood/planks/" + wood + "_stairs";
            String slab = "tfp:wood/planks/" + wood + "_slab";
            assertThat(blocks.keySet()).containsExactly(stairs, slab, "tfp:wood/fence/" + wood, "tfp:wood/log_fence/" + wood,
                "tfp:wood/fence_gate/" + wood, "tfp:wood/door/" + wood, "tfp:wood/trapdoor/" + wood, "tfp:wood/button/" + wood,
                "tfp:wood/pressure_plate/" + wood);
            for (Map.Entry<String, Object> b : blocks.entrySet()) {
                if (WoodFamilies.LOGIC_KINDS.stream().anyMatch(k -> b.getKey().startsWith("tfp:wood/" + k + "/"))) {
                    continue; // PAR-DECO-02: checked by woodLogicBlocksUseTheBuiltInBehavioursOnSharedHitboxCarriers
                }
                Map<String, Object> block = (Map<String, Object>) b.getValue();
                Map<String, Object> settings = (Map<String, Object>) block.get("settings");
                boolean fence = b.getKey().contains("fence");
                assertThat(settings.get("hardness")).as(b.getKey()).isEqualTo(fence ? 2.0 : 1.5);
                assertThat(settings.get("resistance")).as(b.getKey()).isEqualTo(3.0);
                assertThat((List<String>) settings.get("tags")).as(b.getKey()).contains("minecraft:mineable/axe",
                    fence ? "minecraft:wooden_fences" : b.getKey().endsWith("_slab") ? "minecraft:wooden_slabs" : "minecraft:wooden_stairs");
                Map<String, Object> states = (Map<String, Object>) block.get("states");
                Map<String, Object> apps = (Map<String, Object>) states.get("appearances");
                Map<String, Object> variants = (Map<String, Object>) states.get("variants");
                assertThat(apps.keySet()).as(b.getKey()).isEqualTo(variants.keySet());
                assertThat(apps).hasSize(fence ? 32 : b.getKey().equals(slab) ? 6 : 80);
                String twin = dev.tfp.datagen.hitbox.HitboxDemands.twin(b.getKey());
                for (Map.Entry<String, Object> a : apps.entrySet()) {
                    Map<String, Object> app = (Map<String, Object>) a.getValue();
                    Map<String, Object> variant = (Map<String, Object>) variants.get(a.getKey());
                    boolean wet = a.getKey().contains("waterlogged=true") && !a.getKey().contains("type=double");
                    assertThat(variant.containsKey("settings")).as(b.getKey() + "#" + a.getKey()).isEqualTo(wet);
                    if (a.getKey().startsWith("type=double")) {
                        assertThat(app).isEqualTo(Map.of("template", "tfp:visual/wood/planks/" + wood));
                        continue;
                    }
                    assertThat((String) app.get("state")).isEqualTo(twin + "[" + a.getKey() + "]");
                    if (fence) {
                        assertThat(app.get("transparent")).isEqualTo(true);
                        long sides = java.util.Arrays.stream(a.getKey().split(",")).filter(kv -> kv.endsWith("=true")
                            && !kv.startsWith("waterlogged")).count();
                        assertThat((List<Object>) app.get("entity_renderer")).hasSize((int) sides + 1);
                    } else {
                        assertThat(modelCarriers.add((String) app.get("state"))).as("shared " + app.get("state")).isTrue();
                    }
                }
            }
            Map<String, Object> items = (Map<String, Object>) root.get("items");
            // 4 block items + 4 fence parts (DECO-01), 5 block items + 4 gate, 8 door, 3 trapdoor, 2 button, 2 plate models
            assertThat(items).hasSize(4 + 4 + 5 + 4 + 8 + 3 + 2 + 2);
            for (String id : items.keySet()) {
                if (!id.startsWith("tfp:render/")) {
                    assertThat(out.contains(AssetPipeline.RP + "models/item/" + id.substring(4) + ".json")).as(id).isTrue();
                }
            }
        }
        assertThat(modelCarriers).hasSize(20 * (80 + 4));
        // TFC's multipart turns the east side y 90: the display turns 180 - 90 about +y.
        String acacia = new String(out.get(WoodFamilies.CONFIG_DIR + "acacia.yml"), StandardCharsets.UTF_8);
        assertThat(acacia).contains("        'east=true,north=false,south=false,waterlogged=false,west=false':\n"
            + "          state: 'minecraft:oak_fence[east=true,north=false,south=false,waterlogged=false,west=false]'\n"
            + "          transparent: true\n          entity_renderer:\n"
            + "            - item: tfp:render/wood/fence/acacia_post\n              rotation: '0,180,0'\n"
            + "            - item: tfp:render/wood/fence/acacia_side\n              rotation: '0,90,0'\n");
        // The slab drops two when double (TFC's set_count under block_state_property type=double).
        assertThat(acacia).contains("                    - type: match_block_property\n                      properties:\n"
            + "                        type: double\n");
        String lang = new String(out.get(WoodFamilies.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.wood.planks.acacia_stairs\": \"Acacia Stairs\"",
            "\"block.tfp.wood.log_fence.willow\": \"Willow Log Fence\"");
        assertThat(lang.lines().filter(l -> l.contains("\"block.tfp.")).count()).isEqualTo(20 * 9);
    }

    /**
     * PAR-DECO-02: the fence gates, doors, trapdoors, buttons and pressure plates of every wood are CraftEngine blocks on
     * CraftEngine's built-in behaviours, hitbox-only on one shared vanilla twin per kind, each state drawing TFC's model as
     * one item display.
     */
    @Test
    @SuppressWarnings("unchecked")
    void woodLogicBlocksUseTheBuiltInBehavioursOnSharedHitboxCarriers() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        TfcCheckout tfc = TfcCheckout.open(checkout, PIN);
        List<String> woods = WoodFamilies.families(tfc).stream().map(WoodFamilies.Family::wood).toList();
        OutputTree out = new OutputTree();
        WoodFamilies.writeBlocks(tfc, out, woods);
        Map<String, String> behaviour = Map.of("fence_gate", "fence_gate_block", "door", "door_block", "trapdoor",
            "trapdoor_block", "button", "button_block", "pressure_plate", "pressure_plate_block");
        Map<String, Integer> states = Map.of("fence_gate", 32, "door", 64, "trapdoor", 64, "button", 24, "pressure_plate", 2);
        Map<String, String> twins = Map.of("fence_gate", "minecraft:oak_fence_gate", "door", "minecraft:oak_door",
            "trapdoor", "minecraft:oak_trapdoor", "button", "minecraft:spruce_button", "pressure_plate",
            "minecraft:oak_pressure_plate");
        Map<String, double[]> strength = Map.of("fence_gate", new double[] {2, 3}, "door", new double[] {3, 3}, "trapdoor",
            new double[] {3, 3}, "button", new double[] {0.5, 0.5}, "pressure_plate", new double[] {0.5, 0.5});
        Map<String, String> tag = Map.of("fence_gate", "minecraft:fence_gates", "door", "minecraft:wooden_doors", "trapdoor",
            "minecraft:wooden_trapdoors", "button", "minecraft:wooden_buttons", "pressure_plate",
            "minecraft:wooden_pressure_plates");
        int total = 0;
        for (String wood : woods) {
            String file = WoodFamilies.CONFIG_DIR + wood + ".yml";
            Map<String, Object> root = (Map<String, Object>) CeYamlCheck.parse(
                new String(out.get(file), StandardCharsets.UTF_8), file);
            Map<String, Object> blocks = (Map<String, Object>) root.get("blocks");
            for (String kind : WoodFamilies.LOGIC_KINDS) {
                String id = "tfp:wood/" + kind + "/" + wood;
                Map<String, Object> block = (Map<String, Object>) blocks.get(id);
                List<Map<String, Object>> behaviors = (List<Map<String, Object>>) block.get("behavior");
                assertThat(behaviors.stream().map(b -> b.get("type"))).as(id).contains(behaviour.get(kind));
                if (kind.equals("button")) {
                    assertThat(behaviors.get(0).get("type")).isEqualTo("face_attached_horizontal_directional_block");
                    assertThat(behaviors.get(1).get("ticks_to_stay_pressed")).isEqualTo(30);
                }
                Map<String, Object> settings = (Map<String, Object>) block.get("settings");
                assertThat(((Number) settings.get("hardness")).doubleValue()).as(id).isEqualTo(strength.get(kind)[0]);
                assertThat(((Number) settings.get("resistance")).doubleValue()).as(id).isEqualTo(strength.get(kind)[1]);
                assertThat((List<String>) settings.get("tags")).as(id).contains("minecraft:mineable/axe", tag.get(kind));
                Map<String, Object> st = (Map<String, Object>) block.get("states");
                Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
                Map<String, Object> variants = (Map<String, Object>) st.get("variants");
                assertThat(apps).as(id).hasSize(states.get(kind));
                assertThat(apps.keySet()).isEqualTo(variants.keySet());
                assertThat(((Map<String, Object>) st.get("properties")).keySet()).as(id)
                    .containsExactlyElementsOf(WoodLogicBlocks.propertyNames(kind));
                for (Map.Entry<String, Object> a : apps.entrySet()) {
                    Map<String, Object> app = (Map<String, Object>) a.getValue();
                    assertThat(app.get("state")).as(id + "#" + a.getKey()).isEqualTo(twins.get(kind) + "[" + a.getKey() + "]");
                    assertThat(app.get("transparent")).isEqualTo(true);
                    assertThat((List<Object>) app.get("entity_renderer")).hasSize(1);
                    boolean wet = a.getKey().contains("waterlogged=true");
                    assertThat(((Map<String, Object>) variants.get(a.getKey())).containsKey("settings")).isEqualTo(wet);
                }
                total += apps.size();
            }
        }
        assertThat(total).isEqualTo(20 * (32 + 64 + 64 + 24 + 2));
        String acacia = new String(out.get(WoodFamilies.CONFIG_DIR + "acacia.yml"), StandardCharsets.UTF_8);
        // TFC: facing=north, bottom, hinge left, closed = oak_bottom_left turned 270: the display turns 180 - 270 = 270.
        assertThat(acacia).contains("        'facing=north,half=lower,hinge=left,open=false,powered=false':\n"
            + "          state: 'minecraft:oak_door[facing=north,half=lower,hinge=left,open=false,powered=false]'\n"
            + "          transparent: true\n          entity_renderer:\n"
            + "            - item: tfp:render/wood/door/acacia_bottom_left\n              rotation: '0,270,0'\n");
        // A door drops once (the lower half), through double_high_block_item.
        assertThat(acacia).contains("type: double_high_block_item");
        assertThat(acacia).contains("half: lower\n");
        // The wall button (x 90) and the ceiling button (x 180) turn about x first: quaternions, not Euler strings.
        assertThat(WoodLogicBlocks.rotation(0, 90)).isEqualTo("0,90,0");
        assertThat(WoodLogicBlocks.rotation(180, 180)).isEqualTo("-1,0,0,0"); // Ry(0) * Rx(-180): half turn about x
    }

    /** PAR-DECO-01: the ported vanilla outlines in tfc-shapes.json equal the vanilla dump rows of every twin state. */
    @Test
    void portedStairsSlabAndFenceShapesEqualTheVanillaDump() {
        Map<String, dev.tfp.datagen.hitbox.TfcShapes.Row> rows = dev.tfp.datagen.hitbox.TfcShapes.load();
        dev.tfp.datagen.hitbox.VanillaCandidates v = dev.tfp.datagen.hitbox.VanillaCandidates.load();
        int checked = 0;
        for (dev.tfp.datagen.hitbox.HitboxAllocation.Demand d : dev.tfp.datagen.hitbox.HitboxDemands.all()) {
            String twin = dev.tfp.datagen.hitbox.HitboxDemands.twin(d.blockId());
            if (twin == null || !d.blockId().startsWith("tfp:wood/")) {
                continue;
            }
            dev.tfp.datagen.hitbox.TfcShapes.Row row = rows.get(d.tfcKey());
            for (String version : v.versions()) {
                var s = v.get(version, twin + "[" + d.appearance() + "]");
                assertThat(s.selection()).as(d.id() + " " + version).isEqualTo(row.selection());
                assertThat(s.collision()).as(d.id() + " " + version).isEqualTo(row.collision());
            }
            checked++;
        }
        assertThat(checked).isEqualTo(20 * (80 + 4 + 2 * 32 + 32 + 64 + 64 + 24 + 2));
    }

    @Test
    void fixtureFamiliesHaveEveryKindAndMetalAndTheYamlMatches() throws IOException {
        Path root = fixture(List.of("acacia", "oak"), List.of("bronze", "steel"));
        OutputTree out = new OutputTree();
        int blocks = WoodFamilies.run(TfcCheckout.open(root, PIN), out);

        assertThat(blocks).isEqualTo(2 * (21 + 2 * 2));
        String text = new String(out.get(WoodFamilies.OUTPUT), StandardCharsets.UTF_8);
        Map<?, ?> families = (Map<?, ?>) ((Map<?, ?>) CeYamlCheck.parse(text, WoodFamilies.OUTPUT)).get("families");
        assertThat(families.keySet().stream().map(String::valueOf).toList()).containsExactly("acacia", "oak");
        for (Object wood : families.keySet()) {
            Map<?, ?> family = (Map<?, ?>) families.get(wood);
            List<?> entries = (List<?>) family.get("blocks");
            assertThat(entries).hasSize(21 + 2 * 2);
            Set<String> kinds = new TreeSet<>();
            for (Object e : entries) {
                Map<?, ?> m = (Map<?, ?>) e;
                kinds.add((String) m.get("kind"));
                assertThat((String) m.get("id")).startsWith("tfp:wood/");
                assertThat((String) m.get("tfc")).isEqualTo("tfc:" + ((String) m.get("id")).substring("tfp:".length()));
                assertThat(m.containsKey("metal")).isEqualTo(PER_METAL.contains((String) m.get("kind")));
            }
            assertThat(kinds).isEqualTo(expectedKinds());
        }
        assertThat(text).startsWith("# Generated by tools/datagen");
        assertThat(text).contains("      - kind: stairs\n        id: tfp:wood/planks/oak_stairs\n        tfc: tfc:wood/planks/oak_stairs\n");
        assertThat(text).contains("      - kind: hanging_sign\n        metal: steel\n        id: tfp:wood/hanging_sign/steel/oak\n");
    }

    @Test
    void missingBlockstateOfAFamilyIsRefused() throws IOException {
        Path root = fixture(List.of("acacia", "oak"), List.of("bronze"));
        Files.delete(root.resolve(TfcCheckout.ASSETS + "blockstates/wood/loom/oak.json"));
        assertThatThrownBy(() -> WoodFamilies.families(TfcCheckout.open(root, PIN)))
            .isInstanceOf(DatagenException.class).hasMessageContaining("wood/loom/oak");
    }

    private static Set<String> expectedKinds() {
        Set<String> all = new TreeSet<>(ONCE);
        all.addAll(PER_METAL);
        return all;
    }

    /** A TFC-shaped checkout at the pinned commit with a Wood enum of {@code woods}, a BlockType and every blockstate. */
    private Path fixture(List<String> woods, List<String> metals) throws IOException {
        Path root = tmp.resolve("tfc");
        write(root, ".git/HEAD", PIN + "\n");
        StringBuilder enumBody = new StringBuilder();
        for (int i = 0; i < woods.size(); i++) {
            enumBody.append("    ").append(woods.get(i).toUpperCase(java.util.Locale.ROOT))
                .append("(false, 0f, MapColor.WOOD, MapColor.WOOD, 7, 0)")
                .append(i == woods.size() - 1 ? ";" : ",").append('\n');
        }
        StringBuilder types = new StringBuilder();
        List<String> names = new java.util.ArrayList<>(WoodFamilies.KINDS.stream()
            .map(WoodFamilies.Kind::blockType).filter(java.util.Objects::nonNull).toList());
        names.add("LOG");
        for (int i = 0; i < names.size(); i++) {
            types.append("        ").append(names.get(i)).append("(wood -> null)")
                .append(i == names.size() - 1 ? ";" : ",").append('\n');
        }
        write(root, WoodFamilies.SOURCE, "public enum Wood implements RegistryWood\n{\n" + enumBody + "\n"
            + "    public enum BlockType\n    {\n" + types + "    }\n}\n");
        for (String wood : woods) {
            for (WoodFamilies.Kind k : WoodFamilies.KINDS) {
                for (String metal : k.perMetal() ? metals : List.of("")) {
                    write(root, TfcCheckout.ASSETS + "blockstates/" + k.path(wood, metal.isEmpty() ? null : metal) + ".json", "{}");
                }
            }
        }
        return root;
    }

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }
}
