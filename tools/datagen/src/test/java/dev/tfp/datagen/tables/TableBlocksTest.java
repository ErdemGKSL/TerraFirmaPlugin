/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.tables;

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

/** PAR-PROC-49: the tfp:wood/loom/&lt;wood&gt; entries on the real checkout. */
class TableBlocksTest {
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
        assertThat(TableBlocks.available(tfc)).isTrue();
        out = new OutputTree();
        states = TableBlocks.run(tfc, out);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> block(String kind, String wood) {
        Map<String, Object> root = new Yaml().load(new String(out.get(TableBlocks.OUTPUT), StandardCharsets.UTF_8));
        return (Map<String, Object>) ((Map<String, Object>) root.get("blocks")).get(TableBlocks.id(kind, wood));
    }

    @Test
    void oneBlockPerWoodAndKindWithFourStates() {
        assertThat(states).isEqualTo(2 * 20 * 4);
        for (String wood : HitboxDemands.BARREL_WOODS) {
            assertThat(block("scribing_table", wood)).as(wood).isNotNull();
            assertThat(block("sewing_table", wood)).as(wood).isNotNull();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void entriesHaveTfcsStateSettingsAndHitboxOnlyCarriers() {
        for (String kind : List.of("scribing_table", "sewing_table")) {
            Map<String, Object> b = block(kind, "oak");
            // the tables are not devices (no tfp:device behaviour: PAR-PROC-15 uses an anvil screen), so the entry is
            // written literally: settings and loot in the entry, no device template
            assertThat(b).doesNotContainKey("template").doesNotContainKey("behavior");
            Map<String, Object> settings = (Map<String, Object>) b.get("settings");
            assertThat(settings.get("hardness")).isEqualTo(2.5);
            assertThat(settings.toString()).contains("minecraft:mineable/axe");
            assertThat(b.get("loot")).isNotNull();
            Map<String, Object> apps = (Map<String, Object>) ((Map<String, Object>) b.get("states")).get("appearances");
            assertThat(apps).hasSize(4);
            for (String facing : List.of("north", "east", "south", "west")) {
                Map<String, Object> app = (Map<String, Object>) apps.get(facing);
                String demand = kind.equals("scribing_table") ? facing : HitboxDemands.SEWING_APPEARANCE;
                assertThat(app.get("state")).isEqualTo(HitboxAllocation.agreed(TableBlocks.id(kind, "oak"), demand).state());
                assertThat(app).containsEntry("transparent", true);
                assertThat(((List<Map<String, Object>>) app.get("entity_renderer")).getFirst().get("item"))
                    .isEqualTo("tfp:render/wood/" + kind + "/oak");
            }
        }
    }

    @Test
    void modelsTexturesAndNamesAreCopied() {
        for (String kind : List.of("scribing_table", "sewing_table")) {
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/" + kind + ".json")).isNotNull();
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/block/wood/" + kind + "/oak.json")).isNotNull();
            assertThat(out.get("craftengine/tfp/resourcepack/assets/tfp/models/item/wood/" + kind + "/oak.json")).isNotNull();
        }
        String lang = new String(out.get("craftengine/tfp/resourcepack/assets/tfp_tables/lang/en_us.json"), StandardCharsets.UTF_8);
        assertThat(lang).contains("block.tfp.wood.scribing_table.oak").contains("block.tfp.wood.sewing_table.oak");
    }

    @Test
    void displayTurnFollowsTheBlockstateY() {
        assertThat(TableBlocks.rotation(0)).isEqualTo("0,180,0");
        assertThat(TableBlocks.rotation(90)).isEqualTo("0,90,0");
        assertThat(TableBlocks.rotation(270)).isEqualTo("0,270,0");
    }
}
