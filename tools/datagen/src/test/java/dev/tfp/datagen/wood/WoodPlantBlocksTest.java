/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.wood;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-WORLD-44: the wood plant blocks on the real checkout, and the tripwire pool over every generated file. */
class WoodPlantBlocksTest {
    private static OutputTree out;
    private static WoodPlantBlocks.Result result;
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
        assertThat(WoodPlantBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        result = WoodPlantBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String wood) {
        return new Yaml().load(new String(out.get(WoodPlantBlocks.OUTPUT_DIR + wood + ".yml"), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String wood, String kind) {
        return (Map<String, Object>) ((Map<String, Object>) root(wood).get("blocks")).get(HitboxDemands.woodPlantId(kind, wood));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> appearances(String wood, String kind) {
        return (Map<String, Object>) ((Map<String, Object>) block(wood, kind).get("states")).get("appearances");
    }

    /** Wood.java: 20 woods x 4 kinds; the fallen leaves have layers 1..8, the others one state. */
    @Test
    @SuppressWarnings("unchecked")
    void countsAreWoodJavas() {
        assertThat(result.woods()).hasSize(20).containsAll(HitboxDemands.BARREL_WOODS);
        assertThat(result.blocks()).isEqualTo(80);
        assertThat(result.states()).isEqualTo(20 * (1 + 1 + 8 + 1));
        Map<String, Object> layers = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) block("oak",
            HitboxDemands.FALLEN_LEAVES).get("states")).get("properties")).get("layers");
        assertThat(layers).containsEntry("type", "int").containsEntry("range", "1~8").containsEntry("default", 1);
        for (String kind : List.of(HitboxDemands.SAPLING, HitboxDemands.POTTED_SAPLING, HitboxDemands.TWIG)) {
            assertThat((Map<String, Object>) block("oak", kind).get("states")).doesNotContainKey("properties");
        }
    }

    /** LeafDecay places {@code tfp:wood/fallen_leaves/<wood>} and {@code tfp:wood/twig/<wood>}: every one exists. */
    @Test
    void everyLeafDecayIdExists() {
        Set<String> ids = new HashSet<>(result.blockIds());
        for (String wood : HitboxDemands.BARREL_WOODS) {
            assertThat(ids).contains("tfp:wood/fallen_leaves/" + wood, "tfp:wood/twig/" + wood, "tfp:wood/sapling/" + wood,
                "tfp:wood/potted_sapling/" + wood);
        }
    }

    /** TFC's settings: strengths, sounds, tools; the items of the three kinds that have one. */
    @Test
    @SuppressWarnings("unchecked")
    void settingsAndItemsAreTfcs() {
        Map<String, Object> sapling = (Map<String, Object>) block("ash", HitboxDemands.SAPLING).get("settings");
        assertThat(Double.parseDouble(sapling.get("hardness").toString())).isZero();
        assertThat((List<String>) sapling.get("tags")).containsExactly("minecraft:mineable/hoe");
        assertThat(((Map<String, Object>) sapling.get("sounds")).get("break")).isEqualTo("minecraft:block.grass.break");
        Map<String, Object> leaves = (Map<String, Object>) block("ash", HitboxDemands.FALLEN_LEAVES).get("settings");
        assertThat(Double.parseDouble(leaves.get("hardness").toString())).isEqualTo(0.05);
        assertThat(((Map<String, Object>) leaves.get("sounds")).get("place")).isEqualTo("minecraft:item.crop.plant");
        Map<String, Object> twig = (Map<String, Object>) block("ash", HitboxDemands.TWIG).get("settings");
        assertThat((List<String>) twig.get("tags")).containsExactly("minecraft:mineable/axe");
        assertThat((Map<String, Object>) block("ash", HitboxDemands.POTTED_SAPLING).get("settings")).doesNotContainKey("tags");
        Map<String, Object> items = (Map<String, Object>) root("ash").get("items");
        assertThat(items).containsKeys("tfp:wood/sapling/ash", "tfp:wood/fallen_leaves/ash", "tfp:wood/twig/ash")
            .doesNotContainKey("tfp:wood/potted_sapling/ash");
        assertThat(out.contains("data/tfp/loot_table/blocks/wood/potted_sapling/ash.json")).isTrue();
        assertThat(out.contains(WoodPlantBlocks.RP + "models/item/wood/fallen_leaves/ash.json")).isTrue();
    }

    /** PAR-WORLD-53 option a: saplings, potted saplings and fallen leaves are item displays; twigs share one tripwire state. */
    @Test
    @SuppressWarnings("unchecked")
    void visualsFollowTheBudgetDecision() {
        for (String wood : HitboxDemands.BARREL_WOODS) {
            for (String kind : List.of(HitboxDemands.SAPLING, HitboxDemands.POTTED_SAPLING, HitboxDemands.FALLEN_LEAVES)) {
                for (Object a : appearances(wood, kind).values()) {
                    Map<String, Object> app = (Map<String, Object>) a;
                    assertThat(app).as("%s %s", kind, wood).containsKey("state").containsKey("entity_renderer")
                        .doesNotContainKey("auto_state");
                }
            }
            assertThat(appearances(wood, HitboxDemands.FALLEN_LEAVES)).hasSize(8);
            Map<String, Object> twig = (Map<String, Object>) appearances(wood, HitboxDemands.TWIG).get("base");
            assertThat((Map<String, Object>) twig.get("auto_state")).containsEntry("type", "tripwire")
                .containsEntry("id", HitboxDemands.TWIG_SHARE_KEY);
            assertThat(twig.get("model").toString()).contains("tfp:block/wood/twig/oak");
        }
    }

    /** The conifers' needles are TFC's 0xcf7d13; the others the foliage colormap at TFC's default climate. */
    @Test
    void fallenLeavesColourIsTfcs() {
        assertThat(WoodPlantBlocks.FOLIAGE_X).isEqualTo(102);
        assertThat(WoodPlantBlocks.FOLIAGE_Y).isEqualTo(128);
        assertThat(WoodPlantBlocks.conifer(tfc, "pine")).isTrue();
        assertThat(WoodPlantBlocks.conifer(tfc, "oak")).isFalse();
        assertThat(WoodPlantBlocks.foliageColor(tfc)).isEqualTo(0x668f23);
    }

    /**
     * CraftEngine's tripwire pool has 126 states on 1.21.x and 26.x; every generator's tripwire looks together must fit
     * (PAR-WORLD-53). Counted over the generated configuration: one state per distinct {@code auto_state} share id, one per
     * appearance without one.
     */
    @Test
    @SuppressWarnings("unchecked")
    void tripwireDemandFitsThePool() throws IOException {
        Path config = Path.of(System.getProperty("tfp.repoRoot", "../..")).resolve("content/generated/content/craftengine/tfp/configuration");
        Assumptions.assumeTrue(Files.isDirectory(config.resolve("wood/plants")), "no generated wood plants at " + config);
        Set<String> shared = new HashSet<>();
        int plain = 0;
        try (Stream<Path> files = Files.walk(config)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".yml")).sorted().toList()) {
                String text = Files.readString(p);
                if (!text.contains("tripwire")) {
                    continue;
                }
                Object root = new Yaml().load(text);
                if (!(root instanceof Map<?, ?> top)) {
                    continue;
                }
                for (Map.Entry<?, ?> section : top.entrySet()) {
                    if (!String.valueOf(section.getKey()).split("#")[0].equals("blocks") || !(section.getValue() instanceof Map<?, ?> blocks)) {
                        continue;
                    }
                    for (Object b : blocks.values()) {
                        if (!(b instanceof Map<?, ?> block) || !(block.get("states") instanceof Map<?, ?> st)
                                || !(st.get("appearances") instanceof Map<?, ?> apps)) {
                            continue;
                        }
                        for (Object a : apps.values()) {
                            Object auto = a instanceof Map<?, ?> app ? app.get("auto_state") : null;
                            if ("tripwire".equals(auto)) {
                                plain++;
                            } else if (auto instanceof Map<?, ?> m && "tripwire".equals(m.get("type"))) {
                                if (m.get("id") == null) {
                                    plain++;
                                } else {
                                    shared.add(String.valueOf(m.get("id")));
                                }
                            }
                        }
                    }
                }
            }
        }
        assertThat(shared).contains(HitboxDemands.TWIG_SHARE_KEY);
        assertThat(shared.size() + plain).as("tripwire states used").isLessThanOrEqualTo(126);
    }
}
