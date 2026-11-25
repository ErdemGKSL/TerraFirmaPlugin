/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.groundcover;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-WORLD-31: the groundcover CE blocks against TFC's registries, on the real checkout. */
class GroundcoverCeBlocksTest {
    private static OutputTree out;
    private static GroundcoverCeBlocks.Counts counts;
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
        assertThat(GroundcoverCeBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        counts = GroundcoverCeBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root(String file) {
        return new Yaml().load(new String(out.get(file), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> blocks(String file) {
        return (Map<String, Object>) root(file).get("blocks");
    }

    @Test
    void countsMatchTfcsRegistries() {
        assertThat(GroundcoverCeBlocks.types(tfc)).isEqualTo(HitboxDemands.GROUNDCOVER_TYPES).hasSize(17);
        assertThat(blocks(GroundcoverCeBlocks.LOOSE_OUTPUT)).hasSize(42);
        assertThat(blocks(GroundcoverCeBlocks.SMALL_ORE_OUTPUT)).hasSize(12);
        assertThat(blocks(GroundcoverCeBlocks.GROUNDCOVER_OUTPUT)).hasSize(17);
        assertThat(counts.blocks()).isEqualTo(42 + 12 + 17);
        assertThat(counts.states()).isEqualTo(42 * 3 + 12 + 17);
        assertThat(counts.modelCarriers() + counts.hitboxOnly()).isEqualTo(42 * 3 + 12 + 17);
    }

    @Test
    @SuppressWarnings("unchecked")
    void looseRocksAreHitboxOnlyWithTfcsModelPerCount() {
        Map<String, Object> granite = (Map<String, Object>) blocks(GroundcoverCeBlocks.LOOSE_OUTPUT).get("tfp:rock/mossy_loose/granite");
        Map<String, Object> states = (Map<String, Object>) granite.get("states");
        assertThat(((Map<String, Object>) states.get("properties")).get("count")).isEqualTo(Map.of("type", "int", "range", "1~3",
            "default", 1));
        Map<String, Object> app = (Map<String, Object>) ((Map<String, Object>) states.get("appearances")).get("count_2");
        assertThat(app.get("transparent")).isEqualTo(true);
        assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed("tfp:rock/mossy_loose/granite", "count_2").state());
        assertThat((List<Map<String, Object>>) app.get("entity_renderer")).singleElement()
            .satisfies(r -> assertThat(r.get("item")).isEqualTo("tfp:render/rock/loose/granite_2_mossy"));
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/rock/loose/granite_2_mossy.json")).isNotNull();
        assertThat(((Map<String, Object>) states.get("variants")).keySet()).containsExactly("count=1", "count=2", "count=3");
        // the loose rock item stays the plain drops.yml item; the mossy one is a block item here
        Map<String, Object> items = (Map<String, Object>) root(GroundcoverCeBlocks.LOOSE_OUTPUT).get("items");
        assertThat(items).containsKey("tfp:rock/mossy_loose/granite").doesNotContainKey("tfp:rock/loose/granite");
    }

    @Test
    @SuppressWarnings("unchecked")
    void smallOresAndGroundcoverDrawTfcsModelsOnTheirCarrier() {
        for (String file : List.of(GroundcoverCeBlocks.SMALL_ORE_OUTPUT, GroundcoverCeBlocks.GROUNDCOVER_OUTPUT)) {
            blocks(file).forEach((id, entry) -> {
                Map<String, Object> state = (Map<String, Object>) ((Map<String, Object>) entry).get("state");
                HitboxAllocation.Assignment a = HitboxAllocation.agreed(id, HitboxDemands.GROUNDCOVER_APPEARANCE);
                assertThat(state.get("state")).as(id).isEqualTo(a.state());
                if (a.mode() == HitboxAllocation.Mode.MODEL) {
                    assertThat((List<Object>) state.get("models")).as(id).hasSizeGreaterThanOrEqualTo(4);
                } else {
                    assertThat(state.get("transparent")).as(id).isEqualTo(true);
                }
            });
        }
        Map<String, Object> sticks = (Map<String, Object>) blocks(GroundcoverCeBlocks.GROUNDCOVER_OUTPUT).get("tfp:groundcover/stick");
        Map<String, Object> settings = (Map<String, Object>) sticks.get("settings");
        assertThat(settings.get("hardness")).isEqualTo(0.05);
        assertThat(settings.get("push_reaction")).isEqualTo("destroy");
    }

    @Test
    void blockItemsLootAndNames() {
        Map<String, Object> items = root(GroundcoverCeBlocks.GROUNDCOVER_OUTPUT);
        assertThat(items.toString()).contains("tfp:groundcover/clam").doesNotContain("tfp:groundcover/stick:");
        assertThat(root(GroundcoverCeBlocks.SMALL_ORE_OUTPUT).toString()).contains("item.tfp.ore.small_native_copper");
        assertThat(out.get("loot_tables/blocks/rock/loose/granite.json") != null
            || out.files().keySet().stream().anyMatch(k -> k.endsWith("blocks/rock/loose/granite.json"))).isTrue();
        assertThat(out.files().keySet()).anyMatch(k -> k.endsWith("blocks/groundcover/bone.json"));
        assertThat(new String(out.get(GroundcoverCeBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8))
            .contains("\"block.tfp.groundcover.pinecone\"");
    }

    @Test
    void hitboxDemandsCoverEveryId() {
        long demands = HitboxDemands.all().stream().filter(d -> d.blockId().startsWith("tfp:rock/loose/")
            || d.blockId().startsWith("tfp:rock/mossy_loose/") || d.blockId().startsWith("tfp:ore/small_")
            || d.blockId().startsWith("tfp:groundcover/")).count();
        assertThat(demands).isEqualTo(42 * 3 + 12 + 17);
    }
}
