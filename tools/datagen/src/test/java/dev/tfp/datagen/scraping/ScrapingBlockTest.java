/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.scraping;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.hitbox.HitboxAllocation;
import dev.tfp.datagen.hitbox.HitboxDemands;
import dev.tfp.datagen.hitbox.Shape;
import dev.tfp.datagen.hitbox.TfcShapes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-29: the tfp:scraping entry on the real checkout, its shape row and its carrier. */
class ScrapingBlockTest {
    private static OutputTree out;
    private static int states;

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
        assertThat(ScrapingBlock.available(tfc)).isTrue();
        out = new OutputTree();
        states = ScrapingBlock.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> root() {
        return new Yaml().load(new String(out.get(ScrapingBlock.OUTPUT), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block() {
        return (Map<String, Object>) ((Map<String, Object>) root().get("blocks")).get(ScrapingBlock.ID);
    }

    @Test
    void shapeRowIsTfcsOnePixelPlate() {
        TfcShapes.Row row = TfcShapes.load().get("tfc:scraping");
        assertThat(row).isNotNull();
        // ScrapingBlock.SHAPE = box(0, 0, 0, 16, 1, 16); no getCollisionShape override.
        assertThat(row.selection()).isEqualTo(Shape.box(0, 0, 0, 16, 1, 16));
        assertThat(row.collision()).isEqualTo(Shape.box(0, 0, 0, 16, 1, 16));
        assertThat(row.kind()).isEqualTo(TfcShapes.Kind.OBJECT);
    }

    @Test
    void carrierComesFromTheSelectorAndIsDrawnEmpty() {
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ScrapingBlock.ID, ScrapingBlock.APPEARANCE);
        assertThat(a.mode()).isEqualTo(HitboxAllocation.Mode.HITBOX_ONLY);
        assertThat(a.demand().tfcKey()).isEqualTo("tfc:scraping");
        assertThat(a.demand().look()).isEqualTo(HitboxAllocation.TRANSPARENT);
        assertThat(HitboxDemands.all()).filteredOn(d -> d.blockId().equals(ScrapingBlock.ID)).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsSettingsNoItemNoLootAndOneTransparentAppearance() {
        assertThat(states).isEqualTo(1);
        Map<String, Object> root = root();
        assertThat(root).doesNotContainKey("items");
        Map<String, Object> b = block();
        assertThat(b).doesNotContainKeys("loot", "template", "state");
        assertThat(b.get("behavior")).isEqualTo(Map.of("type", "tfp:device"));
        Map<String, Object> s = (Map<String, Object>) b.get("settings");
        assertThat(s.get("hardness")).isEqualTo(0.2);
        assertThat(s.get("resistance")).isEqualTo(0.2);
        assertThat(s).doesNotContainKey("tags");
        assertThat(s).containsEntry("can_occlude", false).containsEntry("is_suffocating", false)
            .containsEntry("is_view_blocking", false);
        Map<String, Object> sounds = (Map<String, Object>) s.get("sounds");
        for (String k : List.of("break", "step", "place", "hit", "fall")) {
            assertThat(sounds.get(k)).isEqualTo("minecraft:block.stem." + k);
        }
        Map<String, Object> st = (Map<String, Object>) b.get("states");
        assertThat(st).containsOnlyKeys("appearances");
        Map<String, Object> apps = (Map<String, Object>) st.get("appearances");
        assertThat(apps).containsOnlyKeys(ScrapingBlock.APPEARANCE);
        Map<String, Object> app = (Map<String, Object>) apps.get(ScrapingBlock.APPEARANCE);
        HitboxAllocation.Assignment a = HitboxAllocation.agreed(ScrapingBlock.ID, ScrapingBlock.APPEARANCE);
        assertThat(app).containsEntry("state", a.state()).containsEntry("transparent", true).doesNotContainKey("model");
        if (a.hideTarget() == null) {
            assertThat(root).doesNotContainKey("block_state_mappings");
        } else {
            assertThat(root.get("block_state_mappings")).isEqualTo(Map.of(a.state(), a.hideTarget()));
        }
    }
}
