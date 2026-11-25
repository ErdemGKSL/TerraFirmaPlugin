/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.coral;

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

/** PAR-WORLD-50: the 30 corals and the sea pickle on their vanilla twins, items where TFC has them, loot delegated. */
class CoralBlockGenTest {
    private static OutputTree out;
    private static int appearances;

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
        assertThat(CoralBlockGen.available(tfc)).isTrue();
        out = new OutputTree();
        appearances = CoralBlockGen.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o, String key) {
        return (Map<String, Object>) ((Map<String, Object>) o).get(key);
    }

    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(CoralBlockGen.OUTPUT), StandardCharsets.UTF_8));
    }

    @Test
    void thirtyCoralsAndTheSeaPickleWithTfcsIds() {
        Map<String, Object> blocks = map(root(), "blocks");
        assertThat(blocks).hasSize(31).containsKeys("tfp:coral/tube_coral", "tfp:coral/horn_dead_coral_wall_fan",
            "tfp:sea_pickle");
        // 20 plants and fans x 2 fluid states + 10 wall fans x 4 facings x 2 + 8 sea pickle states
        assertThat(appearances).isEqualTo(20 * 2 + 10 * 8 + 8);
    }

    @Test
    void everyAppearanceIsTheVanillaTwinsStateOfTheSameProperties() {
        Map<String, Object> blocks = map(root(), "blocks");
        assertThat(HitboxDemands.twin("tfp:coral/tube_dead_coral_wall_fan")).isEqualTo("minecraft:dead_tube_coral_wall_fan");
        assertThat(HitboxDemands.twin("tfp:coral/fire_coral")).isEqualTo("minecraft:fire_coral");
        for (Map.Entry<String, Object> b : blocks.entrySet()) {
            Map<String, Object> apps = map(map(b.getValue(), "states"), "appearances");
            for (Map.Entry<String, Object> a : apps.entrySet()) {
                assertThat(map(apps, a.getKey())).as(b.getKey() + "#" + a.getKey())
                    .containsOnlyKeys("state").containsEntry("state", HitboxDemands.twin(b.getKey()) + "[" + a.getKey() + "]");
                assertThat(HitboxAllocation.agreed(b.getKey(), a.getKey()).mode()).isEqualTo(HitboxAllocation.Mode.VANILLA);
            }
            Map<String, Object> variants = map(map(b.getValue(), "states"), "variants");
            for (Map.Entry<String, Object> v : variants.entrySet()) {
                boolean wet = v.getKey().contains("waterlogged=true");
                Map<String, Object> s = map(v.getValue(), "settings");
                assertThat(s != null && "water".equals(s.get("fluid_state"))).as(b.getKey() + " " + v.getKey()).isEqualTo(wet);
            }
        }
    }

    @Test
    void settingsFollowTfc() {
        Map<String, Object> blocks = map(root(), "blocks");
        Map<String, Object> dead = map(blocks.get("tfp:coral/tube_dead_coral"), "settings");
        assertThat(dead.get("hardness")).isEqualTo(0);
        assertThat(dead.get("correct_tools")).isNotNull();
        assertThat(map(dead, "sounds").get("break")).isEqualTo("minecraft:block.stone.break");
        assertThat(dead.get("tags")).isEqualTo(List.of("minecraft:mineable/pickaxe"));
        Map<String, Object> live = map(blocks.get("tfp:coral/tube_coral_fan"), "settings");
        assertThat(live).doesNotContainKey("correct_tools").doesNotContainKey("item");
        assertThat(map(live, "sounds").get("step")).isEqualTo("minecraft:block.wet_grass.step");
        Map<String, Object> pickle = map(blocks.get("tfp:sea_pickle"), "settings");
        assertThat(map(pickle, "sounds").get("place")).isEqualTo("minecraft:block.slime_block.place");
        Map<String, Object> variants = map(map(blocks.get("tfp:sea_pickle"), "states"), "variants");
        assertThat(map(map(variants, "pickles=3,waterlogged=true"), "settings").get("luminance")).isEqualTo(12);
        assertThat(map(map(variants, "pickles=3,waterlogged=false"), "settings").get("luminance")).isEqualTo(0);
    }

    @Test
    void itemsOnlyWhereTfcRegistersThem() {
        Map<String, Object> items = map(root(), "items");
        assertThat(items).hasSize(11).containsKeys("tfp:coral/brain_coral", "tfp:coral/brain_dead_coral", "tfp:sea_pickle")
            .doesNotContainKey("tfp:coral/brain_coral_fan");
        assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/coral/brain_coral.json")).isNotNull();
    }

    @Test
    void lootIsTfcsAndNamesAreTfcs() {
        assertThat(out.files().keySet()).anyMatch(k -> k.endsWith("blocks/coral/tube_dead_coral_wall_fan.json"));
        assertThat(out.files().keySet()).anyMatch(k -> k.endsWith("blocks/sea_pickle.json"));
        String lang = new String(out.get(CoralBlockGen.LANG_RP + "en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("\"block.tfp.coral.tube_dead_coral_wall_fan\": \"Dead Tube Coral Wall Fan\"")
            .contains("\"block.tfp.sea_pickle\": \"Sea Pickle\"");
    }
}
