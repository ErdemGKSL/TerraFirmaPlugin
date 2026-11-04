/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PAR-INFRA-20: TFC's overrides of vanilla recipes ({@code data/minecraft/recipe}) are exported. */
class DataExportTest {
    private static final String PIN = TfcCheckout.PINNED_COMMIT;
    private static final String RECIPES = "data/minecraft/recipe/";

    @TempDir
    Path tmp;

    private static void write(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text);
    }

    private static String text(SortedMap<String, byte[]> files, String path) {
        assertThat(files).containsKey(path);
        return new String(files.get(path), StandardCharsets.UTF_8);
    }

    @Test
    void exportsVanillaRecipeOverridesAndDisabledRecipesWithRenamedIds() throws IOException {
        Path root = Fixture.create(tmp.resolve("tfc"), PIN);
        String dir = Fixture.D + "minecraft/recipe/";
        write(root, dir + "compass.json", "{\"type\": \"minecraft:crafting_shaped\", \"category\": \"misc\", "
            + "\"key\": {\"L\": {\"item\": \"tfc:lens\"}, \"B\": {\"tag\": \"c:bowls\"}}, \"pattern\": [\"L\", \"B\"], "
            + "\"result\": {\"count\": 1, \"id\": \"minecraft:compass\"}}");
        write(root, dir + "golden_shovel.json", "{\"neoforge:conditions\": [{\"type\": \"neoforge:false\"}]}");
        write(root, Fixture.D + "minecraft/loot_table/blocks/grass.json", "{}");
        SortedMap<String, byte[]> files = Datagen.generate(root, PIN).tree().files();

        assertThat(text(files, RECIPES + "compass.json"))
            .contains("\"item\": \"tfp:lens\"", "\"tag\": \"c:bowls\"", "\"id\": \"minecraft:compass\"")
            .doesNotContain("tfc:");
        // kept as-is so core's loader records it in RegistrySnapshot.disabledRecipes
        assertThat(text(files, RECIPES + "golden_shovel.json")).contains("\"neoforge:conditions\"", "\"neoforge:false\"");
        // other vanilla folders TFC overrides (loot tables) are not part of this export
        assertThat(files.keySet()).noneMatch(p -> p.startsWith("data/minecraft/loot_table"));
        // the new references reach the side tables: minecraft:compass is external, c:bowls is an undefined c: tag
        JsonObject idMap = JsonParser.parseString(text(files, "data/id_map.json")).getAsJsonObject();
        assertThat(idMap.getAsJsonObject("external_ids").getAsJsonArray("item").toString()).contains("\"minecraft:compass\"");
        assertThat(text(files, "data/external_tags.json")).contains("\"c:bowls\"");
    }

    /** On the real pinned checkout: the 66 overrides (48 shaped, 18 shapeless) and the 195 disabled recipes. */
    @Test
    void pinnedCheckoutExportsAllVanillaOverridesAndTheDisabledSet() {
        Path checkout = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(checkout.resolve(".git")), "no TFC checkout at " + checkout);
        Assumptions.assumeTrue(PIN.equals(TfcCheckout.resolveHead(checkout)), "TFC checkout is not at the pinned commit");
        OutputTree out = new OutputTree();
        Map<String, Integer> counts = DataExport.run(TfcCheckout.open(checkout, PIN), out);
        assertThat(counts).containsEntry("minecraft/recipe", 261);

        Map<String, Integer> types = new TreeMap<>();
        int disabled = 0;
        for (Map.Entry<String, byte[]> e : out.files().entrySet()) {
            if (!e.getKey().startsWith(RECIPES)) {
                continue;
            }
            String json = new String(e.getValue(), StandardCharsets.UTF_8);
            assertThat(json).as(e.getKey()).doesNotContain("\"tfc:").doesNotContain("#tfc:");
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("neoforge:conditions")) {
                assertThat(o.toString()).as(e.getKey()).isEqualTo("{\"neoforge:conditions\":[{\"type\":\"neoforge:false\"}]}");
                disabled++;
            } else {
                types.merge(o.get("type").getAsString(), 1, Integer::sum);
            }
        }
        assertThat(types).isEqualTo(Map.of("minecraft:crafting_shaped", 48, "minecraft:crafting_shapeless", 18));
        assertThat(disabled).isEqualTo(195);
        assertThat(new String(out.get(RECIPES + "compass.json"), StandardCharsets.UTF_8))
            .contains("\"item\": \"tfp:lens\"", "\"item\": \"tfp:ore/normal_magnetite\"");
    }
}
