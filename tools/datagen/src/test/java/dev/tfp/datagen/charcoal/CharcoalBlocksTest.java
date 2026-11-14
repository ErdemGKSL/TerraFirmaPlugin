/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.charcoal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.hitbox.VanillaCandidates;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-37: the tfp:log_pile, tfp:burning_log_pile and tfp:charcoal_pile entries on the real checkout. */
class CharcoalBlocksTest {
    private static OutputTree out;
    private static int states;
    private static TfcCheckout tfc;

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
        assertThat(CharcoalBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = CharcoalBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String file) {
        return new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        return (Map<String, Object>) ((Map<String, Object>) root(file).get("blocks")).get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> states(String file, String id) {
        return (Map<String, Object>) block(file, id).get("states");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearance(String file, String id, String name) {
        return (Map<String, Object>) ((Map<String, Object>) states(file, id).get("appearances")).get(name);
    }

    @Test
    @SuppressWarnings("unchecked")
    void propertiesAreTfcsAxisCountAndLayers() {
        assertThat(states).isEqualTo(32 + 32 + 8);
        for (String[] f : new String[][] {{CharcoalBlocks.LOG_PILE_OUTPUT, CharcoalBlocks.LOG_PILE},
                {CharcoalBlocks.BURNING_OUTPUT, CharcoalBlocks.BURNING}}) {
            Map<String, Object> props = (Map<String, Object>) states(f[0], f[1]).get("properties");
            assertThat(props.keySet()).containsExactly(CharcoalBlocks.AXIS, CharcoalBlocks.COUNT); // builder.add(AXIS).add(COUNT)
            assertThat((Map<String, Object>) props.get("axis")).containsEntry("type", "string")
                .containsEntry("values", List.of("x", "z")).containsEntry("default", "x");
            assertThat((Map<String, Object>) props.get("count")).containsEntry("type", "int").containsEntry("range", "1~16")
                .containsEntry("default", 1);
            Map<String, Object> variants = (Map<String, Object>) states(f[0], f[1]).get("variants");
            assertThat(variants).hasSize(32).containsEntry("axis=x,count=7", Map.of("appearance", "x_7"));
        }
        Map<String, Object> props = (Map<String, Object>) states(CharcoalBlocks.CHARCOAL_OUTPUT, CharcoalBlocks.CHARCOAL_PILE)
            .get("properties");
        assertThat((Map<String, Object>) props.get("layers")).containsEntry("type", "int").containsEntry("range", "1~8")
            .containsEntry("default", 1);
        // The plugin writes these names (paper CharcoalPitDevice.AXIS / COUNT / LAYERS).
        assertThat(List.of(CharcoalBlocks.AXIS, CharcoalBlocks.COUNT, CharcoalBlocks.LAYERS)).containsExactly("axis", "count", "layers");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyAppearanceTakesTheSelectorsCarrierWithTfcsModel() {
        for (boolean burning : new boolean[] {false, true}) {
            String file = burning ? CharcoalBlocks.BURNING_OUTPUT : CharcoalBlocks.LOG_PILE_OUTPUT;
            String id = burning ? CharcoalBlocks.BURNING : CharcoalBlocks.LOG_PILE;
            for (String axis : TfcShapes.LOG_PILE_AXES) {
                for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
                    String name = HitboxDemands.logPileAppearance(axis, count);
                    Map<String, Object> app = appearance(file, id, name);
                    String model = HitboxDemands.logPileModel(count, burning);
                    String pool = HitboxDemands.charcoalPitPool(id, name);
                    if (pool != null) {
                        assertThat(app).as(name).containsEntry("auto_state", pool);
                        assertModel(app.get("model"), model, axis);
                        continue;
                    }
                    HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, name);
                    switch (a.mode()) {
                        case MODEL -> {
                            assertThat(app).as(name).containsEntry("state", a.state());
                            assertModel(app.get("model"), model, axis);
                        }
                        case POOL -> {
                            assertThat(app).as(name).containsEntry("auto_state", VanillaCandidates.poolName(a.state()));
                            assertModel(app.get("model"), model, axis);
                        }
                        case HITBOX_ONLY -> {
                            assertThat(app).as(name).containsEntry("state", a.state()).containsEntry("transparent", true);
                            List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                            assertThat(r).hasSize(1);
                            assertThat(r.getFirst()).containsEntry("item", CharcoalBlocks.renderItem(model))
                                .containsEntry("rotation", axis.equals("x") ? "0,90,0" : "0,180,0");
                            Map<String, Object> items = (Map<String, Object>) root(file).get("items");
                            assertThat(items).containsKey(CharcoalBlocks.renderItem(model));
                        }
                        default -> throw new AssertionError(name + " " + a.mode());
                    }
                }
            }
        }
        // TFC's burning pile is noOcclusion: every state on the see-through leaves pool.
        assertThat(appearance(CharcoalBlocks.BURNING_OUTPUT, CharcoalBlocks.BURNING, "z_9")).containsEntry("auto_state", "leaves");
        for (int layers = 1; layers <= HitboxDemands.CHARCOAL_PILE_MAX; layers++) {
            String name = HitboxDemands.charcoalPileAppearance(layers);
            Map<String, Object> app = appearance(CharcoalBlocks.CHARCOAL_OUTPUT, CharcoalBlocks.CHARCOAL_PILE, name);
            String model = HitboxDemands.charcoalPileModel(layers);
            if (layers == HitboxDemands.CHARCOAL_PILE_MAX) {
                assertThat(app).containsEntry("auto_state", "solid").containsEntry("model", model);
                continue;
            }
            HitboxAllocation.Assignment a = HitboxAllocation.agreed(CharcoalBlocks.CHARCOAL_PILE, name);
            assertThat(app).containsEntry("state", a.state());
            if (a.mode() == HitboxAllocation.Mode.HITBOX_ONLY) {
                List<Map<String, Object>> r = (List<Map<String, Object>>) app.get("entity_renderer");
                assertThat(r.getFirst()).containsEntry("item", CharcoalBlocks.renderItem(model));
            } else {
                assertThat(app).as(name).containsEntry("model", model);
            }
        }
        assertThat(HitboxDemands.charcoalPileModel(1)).isEqualTo("tfp:block/charcoal_pile/charcoal_height2");
        assertThat(HitboxDemands.charcoalPileModel(8)).isEqualTo("tfp:block/charcoal_pile/charcoal_block");
    }

    @SuppressWarnings("unchecked")
    private static void assertModel(Object model, String path, String axis) {
        if (axis.equals("x")) {
            assertThat((Map<String, Object>) model).containsEntry("path", path).containsEntry("y", 90);
        } else {
            assertThat(model).isEqualTo(path);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsLootAndBehaviourAreTfcs() {
        for (String[] f : new String[][] {{CharcoalBlocks.LOG_PILE_OUTPUT, CharcoalBlocks.LOG_PILE, "30", "60"},
                {CharcoalBlocks.BURNING_OUTPUT, CharcoalBlocks.BURNING, "5", "5"}}) {
            Map<String, Object> b = block(f[0], f[1]);
            // The log pile draws its one- and two-log states as displays (device_rendered); the burning pile has none.
            assertThat(b).containsEntry("template", f[1].equals(CharcoalBlocks.LOG_PILE) ? "tfp:block/device_rendered"
                : "tfp:block/device").doesNotContainKey("loot");
            Map<String, Object> args = (Map<String, Object>) b.get("arguments");
            assertThat(args.get("device_hardness").toString()).isEqualTo("0.6");
            assertThat(args).containsEntry("device_sound_break", "minecraft:block.wood.break")
                .containsEntry("device_tool_tag", "minecraft:mineable/axe");
            Map<String, Object> settings = (Map<String, Object>) b.get("settings");
            assertThat(settings.containsKey("can_occlude")).isEqualTo(f[1].equals(CharcoalBlocks.BURNING)); // noOcclusion
            assertThat(settings).containsEntry("burnable", true).containsEntry("burn_chance", Integer.parseInt(f[2]))
                .containsEntry("fire_spread_chance", Integer.parseInt(f[3]));
            // registerNoItem: no block item.
            Map<String, Object> items = (Map<String, Object>) root(f[0]).get("items");
            if (items != null) {
                assertThat(items).doesNotContainKey(f[1]);
            }
            Map<String, Object> variants = (Map<String, Object>) states(f[0], f[1]).get("variants");
            variants.values().forEach(v -> assertThat((Map<String, Object>) v).doesNotContainKey("light"));
        }
        Map<String, Object> c = block(CharcoalBlocks.CHARCOAL_OUTPUT, CharcoalBlocks.CHARCOAL_PILE);
        assertThat(c).doesNotContainKey("behavior").doesNotContainKey("template");
        Map<String, Object> settings = (Map<String, Object>) c.get("settings");
        assertThat(settings.get("hardness").toString()).isEqualTo("0.2");
        assertThat(settings).containsEntry("push_reaction", "popped").containsEntry("tags", List.of("minecraft:mineable/shovel"));
        assertThat(((Map<String, Object>) settings.get("sounds")).values().toString()).contains("tfp:block.charcoal.break");
        assertThat(c.get("loot").toString()).contains("minecraft:charcoal");
        // registerNoItem: no block item; only display items of hitbox-only layers.
        Map<String, Object> items = (Map<String, Object>) root(CharcoalBlocks.CHARCOAL_OUTPUT).get("items");
        if (items != null) {
            assertThat(items.keySet()).allMatch(k -> k.startsWith("tfp:render/charcoal_pile/"));
        }
    }

    @Test
    void tfcsModelsAndTexturesAreCopied() {
        String rp = "craftengine/tfp/resourcepack/assets/tfp/";
        for (int count = 1; count <= TfcShapes.LOG_PILE_MAX; count++) {
            assertThat(out.contains(rp + "models/block/log_pile/log_pile_" + count + ".json")).isTrue();
            String burning = new String(out.get(rp + "models/block/log_pile/log_pile_" + count + "_burning.json"),
                StandardCharsets.UTF_8);
            assertThat(burning).contains("\"tfp:block/log_pile/log_pile_" + count + "\"").contains("tfp:block/molten_lit");
        }
        for (int layers = 1; layers <= HitboxDemands.CHARCOAL_PILE_MAX; layers++) {
            String model = HitboxDemands.charcoalPileModel(layers);
            String json = new String(out.get(rp + "models/" + model.substring("tfp:".length()) + ".json"), StandardCharsets.UTF_8);
            assertThat(json).contains("tfp:block/charcoal_pile");
        }
        for (String t : List.of("log_pile_front", "log_pile_side", "molten_lit", "charcoal_pile")) {
            assertThat(out.get(rp + "textures/block/" + t + ".png")).isEqualTo(tfc.read(TfcCheckout.ASSETS + "textures/block/" + t + ".png"));
        }
        String one = new String(out.get(rp + "models/block/log_pile/log_pile_1.json"), StandardCharsets.UTF_8);
        assertThat(one).contains("tfp:block/log_pile_front").doesNotContain("tfc:");
    }
}
