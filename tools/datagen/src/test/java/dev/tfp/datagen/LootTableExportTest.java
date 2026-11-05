/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import dev.tfp.core.animal.CarrierTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-21: TFC loot tables shipped for the Paper loot runtime. */
class LootTableExportTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;

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

    /** Pinned checkout: one table per spawned TFC entity (83), renamed, equal to TFC's after the rename. */
    @Test
    void exportsTheEntityTablesOfEverySpawnedEntityRenamed() {
        TfcCheckout tfc = pinned();
        OutputTree out = new OutputTree();
        int[] counts = LootTableExport.run(tfc, out);
        assertThat(counts[0]).isZero(); // no block generator ran
        assertThat(CarrierTable.get().ids()).hasSize(83);
        assertThat(counts[1]).isEqualTo(83);
        SortedMap<String, byte[]> files = out.files();
        assertThat(files.keySet()).allMatch(p -> p.startsWith(LootTableExport.TARGET + "entities/")).hasSize(83);
        String cow = text(files, LootTableExport.TARGET + "entities/cow.json");
        assertThat(cow).contains("\"tfp:food/beef\"", "\"tfp:large_raw_hide\"", "\"tfp:not_predated\"",
            "\"tfp:animal_yield\"", "\"minecraft:bone\"").doesNotContain("tfc:", "__comment__");
        for (String type : LootTableExport.entityTypes()) {
            String src = LootTableExport.ENTITY_SOURCE + type + ".json";
            JsonElement expected = Json.rename(Json.parse(tfc.readString(src), src));
            assertThat(text(files, LootTableExport.TARGET + "entities/" + type + ".json")).isEqualTo(Json.write(expected));
        }
    }

    /**
     * Pinned checkout: every TFC block and entity table (4,502; the deposit tables are not block or entity tables),
     * renamed, passes the export check (no {@code tfc:} left, only TFC's own {@code tfp:} types, which
     * {@code LootTableParser} maps back to {@code tfc:}).
     */
    @Test
    void everyRenamedTfcTablePassesTheExportCheck() {
        TfcCheckout tfc = pinned();
        int n = 0;
        List<String> types = new ArrayList<>();
        for (String dir : List.of(LootTableExport.BLOCK_SOURCE, LootTableExport.ENTITY_SOURCE)) {
            for (String f : tfc.list(dir)) {
                JsonElement table = Json.rename(Json.parse(tfc.readString(f), f));
                LootTableExport.check(f, table);
                LootTableExport.types(table, t -> {
                    if (t.startsWith("tfp:") && !types.contains(t)) {
                        types.add(t);
                    }
                });
                n++;
            }
        }
        assertThat(n).isEqualTo(4502);
        assertThat(types).contains("tfp:is_isolated", "tfp:crop_yield_uniform", "tfp:animal_yield", "tfp:not_predated");
    }

    /** Pinned checkout: the raw rock table (the shape of the follow-up delegation) carries TFC's is_isolated, renamed. */
    @Test
    void blockTableIsRenamed() {
        TfcCheckout tfc = pinned();
        String raw = Json.write(LootTableExport.blockTable(tfc, "rock/raw/granite"));
        assertThat(raw).contains("\"tfp:is_isolated\"", "\"tfp:rock/raw/granite\"", "\"tfp:rock/loose/granite\"")
            .doesNotContain("tfc:");
        assertThat(LootTableExport.blockTable(tfc, "no/such/block")).isNull();
    }

    /**
     * Fixture: a block whose TFC table has no template shape (here state-dependent counts and explosion decay, as TFC's
     * loose rocks) is delegated: its table ships renamed, its CE entry has no {@code loot:}, the items it names exist,
     * and the blocks with template shapes keep their CE loot.
     */
    @Test
    void aBlockWithNoTemplateShapeIsDelegated() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), PIN);
        Fixture.write(root, Fixture.M + "tfc/loot_table/blocks/dirt/alfisol.json", """
            {"__comment__": "x", "type": "minecraft:block", "pools": [{"name": "loot_pool", "rolls": 1, "entries": [
              {"type": "minecraft:item", "name": "tfc:rock/loose/granite", "functions": [
                {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
                  "block": "tfc:dirt/alfisol", "properties": {"count": "2"}}]},
                {"function": "minecraft:explosion_decay"}]},
              {"type": "minecraft:item", "name": "tfc:dirt/alfisol", "conditions": [{"condition": "tfc:is_isolated"}]}],
              "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
            """);
        Datagen.Result r = Datagen.generate(root, PIN);
        SortedMap<String, byte[]> files = r.tree().files();
        String table = text(files, LootTableExport.TARGET + "blocks/dirt/alfisol.json");
        assertThat(table).contains("\"tfp:rock/loose/granite\"", "\"block\": \"tfp:dirt/alfisol\"", "\"tfp:is_isolated\"",
            "\"minecraft:explosion_decay\"").doesNotContain("tfc:", "__comment__");
        assertThat(files.keySet().stream().filter(p -> p.startsWith(LootTableExport.TARGET))).containsExactly(
            LootTableExport.TARGET + "blocks/dirt/alfisol.json");
        assertThat(r.report().count("loot_delegated", 0)).isEqualTo(1);

        String soil = text(files, AssetPipeline.CONFIG + "soil/alfisol.yml");
        assertThat(blockEntry(soil, "tfp:dirt/alfisol")).contains("settings:").doesNotContain("loot:");
        assertThat(blockEntry(soil, "tfp:clay/alfisol")).contains("loot:", "loot_item: minecraft:clay_ball");
        // the loose rock the table names is a CE item; the block itself is not listed twice
        String drops = text(files, AssetPipeline.CONFIG + "drops.yml");
        assertThat(drops).contains("tfp:rock/loose/granite:").doesNotContain("tfp:dirt/alfisol:");
        assertThat(text(files, AssetPipeline.CONFIG + "rock/granite.yml")).contains("template: tfp:loot_table/rock_to_loose");
    }

    /** The lines of block {@code id}'s entry in a generated config (its head line and the deeper-indented lines below). */
    private static String blockEntry(String yaml, String id) {
        String[] lines = yaml.substring(yaml.indexOf("\nblocks:\n")).split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].equals("  " + id + ":")) {
                for (int j = i + 1; j < lines.length && lines[j].startsWith("    "); j++) {
                    sb.append(lines[j]).append('\n');
                }
                return sb.toString();
            }
        }
        throw new AssertionError(id + " not in the blocks section");
    }

    @Test
    void aDelegatedBlockWithCraftEngineLootFails() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(AssetPipeline.CONFIG + "x.yml", """
            # header
            blocks:
              tfp:a:
                settings:
                  template: tfp:settings/rock
              tfp:b:
                loot:
                  template: tfp:loot_table/self
            items:
              tfp:a:
                loot: not-a-block-entry
            """.getBytes(StandardCharsets.UTF_8));
        LootTableExport.requireNoCeLoot(files, "tfp:a");
        assertThatThrownBy(() -> LootTableExport.requireNoCeLoot(files, "tfp:b"))
            .isInstanceOf(DatagenException.class).hasMessageContaining("drop twice");
    }

    @Test
    void theCheckRejectsLeftoverTfcIdsAndUnknownTypes() {
        String ok = "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\","
            + "\"name\":\"tfp:x\",\"conditions\":[{\"condition\":\"tfp:is_isolated\"}]}]}]}";
        LootTableExport.check("ok", Json.parse(ok, "ok"));
        assertThatThrownBy(() -> LootTableExport.check("a", Json.parse(ok.replace("tfp:x", "tfc:x"), "a")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("tfc: id");
        assertThatThrownBy(() -> LootTableExport.check("b", Json.parse(ok.replace("tfp:is_isolated", "tfp:made_up"), "b")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("tfp:made_up");
        assertThatThrownBy(() -> LootTableExport.check("c", Json.parse(ok.replace("minecraft:block", "minecraft:chest"), "c")))
            .isInstanceOf(DatagenException.class).hasMessageContaining("minecraft:chest");
    }
}
