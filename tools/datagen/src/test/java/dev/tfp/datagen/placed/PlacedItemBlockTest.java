/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.placed;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.Yaml;
import dev.tfp.datagen.device.DeviceYaml;
import dev.tfp.datagen.hitbox.HitboxCarrier;
import dev.tfp.datagen.hitbox.TfcShapes;
import dev.tfp.datagen.visuals.Profile;
import dev.tfp.datagen.visuals.VersionFamily;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PAR-POT-01: the tfp:placed_item entry against the real checkout and the owner's pressure-plate hitbox rule. */
class PlacedItemBlockTest {
    static final String PLATE = "minecraft:heavy_weighted_pressure_plate[power=0]";
    static final String PRESSED = "minecraft:heavy_weighted_pressure_plate[power=1]";
    private static TfcCheckout tfc;

    @BeforeAll
    static void open() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
    }

    /** The 15 non-empty occupancies of TFC's ITEM_0..3. */
    static List<String> occupancyKeys() {
        List<String> keys = new ArrayList<>();
        for (int mask = 1; mask < 16; mask++) {
            List<String> p = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                p.add("item_" + i + "=" + ((mask & (1 << i)) != 0));
            }
            keys.add("tfc:placed_item[" + String.join(",", p) + "]");
        }
        return keys;
    }

    @Test
    void tfcsSourceStillMatches() {
        assertThat(PlacedItemBlock.available(tfc)).isTrue();
        PlacedItemBlock.checkSource(tfc); // throws on drift
    }

    @Test
    void everyOccupancyTakesThePressurePlateHitbox() {
        // PAR-INFRA-13 owner rule (class-overrides.json, class placed_item): whatever the item or count
        assertThat(TfcShapes.load()).containsKey(PlacedItemBlock.DEMAND_KEY);
        for (VersionFamily f : VersionFamily.values()) {
            for (String key : occupancyKeys()) {
                HitboxCarrier.Choice c = HitboxCarrier.select(HitboxCarrier.TfcState.of(key), Profile.defaultFor(f), f);
                assertThat(c.chosen().carrier()).as(f.id() + " " + key).isEqualTo(PLATE);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entryHasTfcsSettingsNoItemNoLootAndOneTransparentAppearance() {
        OutputTree out = new OutputTree();
        out.put(PlacedItemBlock.OUTPUT, Yaml.write(DeviceYaml.HEADER, PlacedItemBlock.yaml(PlacedItemBlock.spec(PLATE, PRESSED))));
        Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(new String(out.get(PlacedItemBlock.OUTPUT),
            StandardCharsets.UTF_8));
        assertThat(root).doesNotContainKey("items");
        Map<String, Object> b = (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(PlacedItemBlock.ID);
        assertThat(b).doesNotContainKeys("loot", "template", "state");
        assertThat(b.get("behavior")).isEqualTo(Map.of("type", "tfp:device"));
        Map<String, Object> s = (Map<String, Object>) b.get("settings");
        assertThat(((Number) s.get("hardness")).doubleValue()).as("instabreak").isZero();
        assertThat(((Number) s.get("resistance")).doubleValue()).isZero();
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
        assertThat(apps).containsOnlyKeys(PlacedItemBlock.APPEARANCE);
        Map<String, Object> app = (Map<String, Object>) apps.get(PlacedItemBlock.APPEARANCE);
        assertThat(app).containsEntry("state", PLATE).containsEntry("transparent", true).doesNotContainKey("model");
        assertThat(root.get("block_state_mappings")).isEqualTo(Map.of(PLATE, PRESSED));
        assertThat(PlacedItemBlock.spec(PLATE, PRESSED).stateCount()).isEqualTo(1);
    }
}
