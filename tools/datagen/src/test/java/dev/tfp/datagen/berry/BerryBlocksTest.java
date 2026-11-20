/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.berry;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
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

/** PAR-FARM-14: the stationary berry bush and dead bush entries on the real checkout. */
class BerryBlocksTest {
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
        assertThat(BerryBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = BerryBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String file, String id) {
        Map<String, Object> root = new Yaml().load(new String(out.get(BerryBlocks.OUTPUT_DIR + file + ".yml"), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(id);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sixBushesAndTheDeadBushHaveTfcsStates() {
        assertThat(BerryBlocks.species(tfc)).isEqualTo(HitboxDemands.BERRY_SPECIES);
        assertThat(states).isEqualTo(6 * 12 + 3);
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) block("snowberry", "tfp:berry_bush/snowberry")
            .get("states")).get("properties");
        assertThat(props.keySet()).containsExactly("lifecycle", "stage");
        Map<String, Object> variants = (Map<String, Object>) ((Map<String, Object>) block("snowberry", "tfp:berry_bush/snowberry")
            .get("states")).get("variants");
        assertThat(variants).hasSize(12);
        assertThat(((Map<String, Object>) variants.get("lifecycle=fruiting,stage=2")).get("appearance")).isEqualTo("fruiting_2");
        assertThat(((Map<String, Object>) block("dead", "tfp:berry_bush/dead").get("states")).get("variants")).isInstanceOf(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyStateDrawsTfcsModelOnItsSharedHitboxOnlyCarrier() {
        for (String sp : HitboxDemands.BERRY_SPECIES) {
            Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) block(sp, HitboxDemands.berryId(sp)).get("states"))
                .get("appearances");
            assertThat(apps).hasSize(12);
            var want = HitboxAllocation.agreed(HitboxDemands.berryId(sp), HitboxDemands.BERRY_PATTERN);
            for (Object a : apps.values()) {
                assertThat(((Map<String, Object>) a).get("state")).isEqualTo(want.state());
                assertThat(((Map<String, Object>) a).get("transparent")).isEqualTo(true);
            }
            assertThat(VanillaCandidates.load().get(want.carrier().worstVersion(), want.state()).collision().isEmpty()).isTrue();
        }
        String fruiting = new String(out.get(BerryBlocks.RP + "models/block/plant/fruiting_snowberry_bush_2.json"), StandardCharsets.UTF_8);
        assertThat(fruiting).contains("tfp:block/plant/stationary_bush_2").contains("tfp:block/berry_bush/fruiting_snowberry_bush");
    }

    @Test
    void theOutlinesAreTfcs() {
        Map<String, TfcShapes.Row> rows = TfcShapes.load();
        assertThat(rows.get(TfcShapes.BERRY_BUSH_KEY).selection()).isEqualTo(Shape.box(2, 0, 2, 14, 8, 14));
        assertThat(rows.get(TfcShapes.BERRY_BUSH_KEY).collision().isEmpty()).isTrue();
        // the dead bush (12 x 16 x 12 / full cube, no collision) has no row: it is served on the bush's
        assertThat(rows.keySet()).noneMatch(k -> k.contains("dead_berry_bush"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void settingsLootItemsAndNames() {
        Map<String, Object> b = block("snowberry", "tfp:berry_bush/snowberry");
        Map<String, Object> settings = (Map<String, Object>) b.get("settings");
        assertThat(settings.get("burn_chance")).isEqualTo(30);
        assertThat(settings.get("fire_spread_chance")).isEqualTo(60);
        assertThat(settings.get("push_reaction")).isEqualTo("destroy");
        Map<String, Object> dead = (Map<String, Object>) block("dead", "tfp:berry_bush/dead").get("settings");
        assertThat(dead.get("burn_chance")).isEqualTo(90);
        assertThat(dead.get("fire_spread_chance")).isEqualTo(120);
        assertThat(b.get("template")).isEqualTo("tfp:block/device_rendered");
        assertThat(new String(out.get("data/tfp/loot_table/blocks/berry_bush/snowberry.json"), StandardCharsets.UTF_8))
            .contains("tfp:berry_bush/snowberry").doesNotContain("tfc:");
        assertThat(new String(out.get("data/tfp/loot_table/blocks/berry_bush/dead.json"), StandardCharsets.UTF_8))
            .contains("minecraft:stick");
        assertThat(out.get(BerryBlocks.RP + "models/item/berry_bush/snowberry.json")).isNotNull();
        String en = new String(out.get(BerryBlocks.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(en).contains("item.tfp.berry_bush.snowberry").contains("Snowberry Bush");
        assertThat(List.of(en.split("\n")).stream().filter(l -> l.contains("item.tfp")).count()).isEqualTo(6);
    }
}
