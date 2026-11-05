/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-30: TFC's overrides of vanilla loot tables ship in the bootstrap datapack under their minecraft: ids. */
class VanillaLootOverridesTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    private static final String BLOCKS = VanillaLootOverrides.TARGET + "blocks/";
    private static final String ENTITIES = VanillaLootOverrides.TARGET + "entities/";

    @TempDir
    Path tmp;

    private static TfcCheckout pinned() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        return TfcCheckout.open(checkout, PIN);
    }

    private static String text(Map<String, byte[]> files, String path) {
        byte[] b = files.get(path);
        assertThat(b).as(path).isNotNull();
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Pinned checkout: 39 block and 2 entity tables, 39 shipped (chest and trapped chest skipped), equal to TFC's renamed. */
    @Test
    void pinnedCheckoutShips37BlockAnd2EntityTables() {
        TfcCheckout tfc = pinned();
        OutputTree out = new OutputTree();
        VanillaLootOverrides.Counts counts = VanillaLootOverrides.run(tfc, out);
        assertThat(counts.blocks()).isEqualTo(39);
        assertThat(counts.entities()).isEqualTo(2);
        assertThat(counts.shipped()).isEqualTo(39);
        SortedMap<String, byte[]> files = out.files();
        assertThat(files.keySet()).hasSize(39).allMatch(p -> p.startsWith(VanillaLootOverrides.TARGET));
        assertThat(files.keySet().stream().filter(p -> p.startsWith(BLOCKS))).hasSize(37);
        assertThat(files.keySet().stream().filter(p -> p.startsWith(ENTITIES)))
            .containsExactly(ENTITIES + "drowned.json", ENTITIES + "zombie.json");
        assertThat(files).doesNotContainKeys(BLOCKS + "chest.json", BLOCKS + "trapped_chest.json");
        for (String path : files.keySet()) {
            assertThat(text(files, path)).as(path).doesNotContain("tfc:", "tfp:", "__comment__");
        }
        // glass and panes drop themselves with no tool condition; snow only with silk touch
        for (String block : List.of("glass", "tinted_glass", "red_stained_glass", "glass_pane", "black_stained_glass_pane")) {
            assertThat(text(files, BLOCKS + block + ".json")).contains("\"minecraft:" + block + "\"")
                .doesNotContain("silk_touch", "match_tool");
        }
        assertThat(text(files, BLOCKS + "snow.json")).contains("\"minecraft:snow\"", "minecraft:silk_touch");
        assertThat(text(files, BLOCKS + "snow_block.json")).contains("\"minecraft:snow_block\"", "minecraft:silk_touch");
        assertThat(text(files, ENTITIES + "zombie.json")).contains("\"minecraft:rotten_flesh\"", "\"max\": 2.0")
            .doesNotContain("iron_ingot", "carrot", "potato", "enchanted_count_increase");
        // equal to TFC's after the rename
        for (String dir : List.of(VanillaLootOverrides.BLOCK_SOURCE, VanillaLootOverrides.ENTITY_SOURCE)) {
            String kind = dir.equals(VanillaLootOverrides.BLOCK_SOURCE) ? BLOCKS : ENTITIES;
            for (String src : tfc.list(dir)) {
                String name = src.substring(dir.length());
                if (kind.equals(BLOCKS) && (name.equals("chest.json") || name.equals("trapped_chest.json"))) {
                    continue;
                }
                JsonElement expected = Json.rename(Json.parse(tfc.readString(src), src));
                assertThat(text(files, kind + name)).isEqualTo(Json.write(expected));
            }
        }
    }

    /** Pinned checkout: the skipped chest tables are exactly the ones naming a non-vanilla item. */
    @Test
    void onlyTheChestTablesNameATfcItem() {
        TfcCheckout tfc = pinned();
        for (String src : tfc.list(VanillaLootOverrides.BLOCK_SOURCE)) {
            String rel = "blocks/" + src.substring(VanillaLootOverrides.BLOCK_SOURCE.length(), src.length() - 5);
            JsonElement table = Json.rename(Json.parse(tfc.readString(src), src));
            if (VanillaLootOverrides.SKIPPED.contains(rel)) {
                assertThat(Json.write(table)).contains(rel.equals("blocks/chest") ? "\"tfp:wood/chest/oak\"" : "\"tfp:wood/trapped_chest/oak\"",
                    "minecraft:custom_name");
                assertThatThrownBy(() -> VanillaLootOverrides.check(rel, table)).isInstanceOf(DatagenException.class);
            } else {
                VanillaLootOverrides.check(rel, table);
            }
        }
    }

    /** Fixture through the whole generator: renamed, in the bootstrap datapack, chest skipped. */
    @Test
    void fixtureShipsRenamedTablesInTheBootstrapDatapack() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), PIN);
        String block = "{\"__comment__\": \"x\", \"type\": \"minecraft:block\", \"pools\": [{\"rolls\": 1, \"entries\": "
            + "[{\"type\": \"minecraft:item\", \"name\": \"%s\"}]}]}";
        Fixture.write(root, VanillaLootOverrides.BLOCK_SOURCE + "glass.json", block.formatted("minecraft:glass"));
        Fixture.write(root, VanillaLootOverrides.BLOCK_SOURCE + "chest.json", block.formatted("tfc:wood/chest/oak"));
        Fixture.write(root, VanillaLootOverrides.ENTITY_SOURCE + "zombie.json", "{\"type\": \"minecraft:entity\", "
            + "\"pools\": [{\"rolls\": 1.0, \"entries\": [{\"type\": \"minecraft:item\", \"name\": \"minecraft:rotten_flesh\"}]}], "
            + "\"random_sequence\": \"minecraft:entities/zombie\"}");
        SortedMap<String, byte[]> files = Datagen.generate(root, PIN).tree().files();
        assertThat(files.keySet().stream().filter(p -> p.startsWith(VanillaLootOverrides.TARGET)))
            .containsExactly(BLOCKS + "glass.json", ENTITIES + "zombie.json");
        assertThat(VanillaLootOverrides.TARGET).startsWith(SeasonalBiomes.OUT);
        assertThat(files).containsKey(SeasonalBiomes.OUT + "pack.mcmeta");
        assertThat(text(files, BLOCKS + "glass.json")).contains("\"minecraft:glass\"").doesNotContain("__comment__");
        assertThat(text(files, ENTITIES + "zombie.json")).contains("minecraft:entities/zombie");
    }

    /** A shipped table that still names a TFC id, or has the wrong type, fails datagen. */
    @Test
    void rejectsModIdsAndWrongTypes() {
        JsonElement tfp = Json.parse("{\"type\": \"minecraft:block\", \"pools\": [{\"entries\": [{\"type\": \"minecraft:item\", "
            + "\"name\": \"tfp:wood/chest/oak\"}]}]}", "x");
        assertThatThrownBy(() -> VanillaLootOverrides.check("blocks/x", tfp)).hasMessageContaining("tfp:wood/chest/oak");
        JsonElement tfc = Json.parse("{\"type\": \"minecraft:block\", \"pools\": [{\"conditions\": [{\"condition\": "
            + "\"tfc:is_isolated\"}]}]}", "x");
        assertThatThrownBy(() -> VanillaLootOverrides.check("blocks/x", tfc)).hasMessageContaining("tfc:is_isolated");
        JsonElement wrong = Json.parse("{\"type\": \"minecraft:entity\", \"pools\": []}", "x");
        assertThatThrownBy(() -> VanillaLootOverrides.check("blocks/x", wrong)).hasMessageContaining("expected minecraft:block");
        VanillaLootOverrides.check("entities/x", Json.parse("{\"type\": \"minecraft:entity\", \"random_sequence\": "
            + "\"minecraft:entities/x\", \"pools\": []}", "x"));
    }
}
