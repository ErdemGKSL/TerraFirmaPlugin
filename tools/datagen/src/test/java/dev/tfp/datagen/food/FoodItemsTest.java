/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.food;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-SURV-01: every TFC food item from the real pinned checkout. */
class FoodItemsTest {
    private static TfcCheckout tfc;
    private static List<FoodItems.FoodItem> items;
    private static OutputTree out;
    private static Map<String, Object> config;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            tfc = TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        assertThat(FoodItems.available(tfc)).isTrue();
        items = FoodItems.items(tfc);
        out = new OutputTree();
        assertThat(FoodItems.run(tfc, out)).isEqualTo(items.size());
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        String text = new String(out.get(FoodItems.OUTPUT), StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("\t");
        Map<String, Object> root = new Yaml(opts).load(text);
        assertThat(root.keySet()).containsExactly("items");
        config = (Map<String, Object>) root.get("items");
    }

    /** TFC's Food enum (182, 22 with jam) and Nutrient enum (5): foods + jars + unsealed jars + jams + soups + salads. */
    @Test
    void countIsTfcsFoodItemCount() {
        assertThat(FoodItems.foods(tfc.readString(FoodItems.FOOD_SOURCE))).hasSize(182)
            .filteredOn(FoodItems.Food::hasJam).hasSize(22);
        assertThat(FoodItems.nutrients(tfc.readString(FoodItems.NUTRIENT_SOURCE)))
            .containsExactly("grain", "fruit", "vegetables", "protein", "dairy");
        assertThat(items).hasSize(182 + 3 * 22 + 2 * 5);
        assertThat(config).hasSize(items.size());
        // every TFC item model of a food item is covered: models/item/food/ holds the foods, soups and salads
        long foodModels = tfc.list(TfcCheckout.ASSETS + "models/item/food/").stream().filter(f -> f.endsWith(".json")).count();
        assertThat(items.stream().filter(i -> i.id().startsWith("food/")).count()).isEqualTo(foodModels);
    }

    /** Every food item of TFC's registry is generated (the id map of DataExport lists TFC's item ids). */
    @Test
    void idsAreStableAndCoverEveryFoodModel() {
        Set<String> ids = new HashSet<>();
        items.forEach(i -> ids.add(i.id()));
        assertThat(ids).contains("food/mutton", "food/wheat_bread", "food/grain_soup", "food/dairy_salad",
            "jar/blackberry", "jar/blackberry_unsealed", "blackberry_jam", "jar/peanut", "peanut_jam");
        for (String f : tfc.list(TfcCheckout.ASSETS + "models/item/jar/")) {
            String id = f.substring((TfcCheckout.ASSETS + "models/item/").length(), f.length() - ".json".length());
            assertThat(ids).as("jar model " + id).contains(id);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyItemHasAFoodDefinitionANameAModelAndTheStamp() {
        JsonObject lang = JsonParser.parseString(tfc.readString(FoodItems.LANG)).getAsJsonObject();
        for (FoodItems.FoodItem item : items) {
            assertThat(item.definition()).as(item.id()).startsWith("tfc:");
            assertThat(lang.has(item.langKey().replace("item.tfp.", "item.tfc."))).as(item.id()).isTrue();
            assertThat(out.contains(FoodItems.RP + "models/item/" + item.id() + ".json")).as(item.id()).isTrue();
            Map<String, Object> entry = (Map<String, Object>) config.get("tfp:" + item.id());
            assertThat(entry).as(item.id()).isNotNull();
            Map<String, Object> args = (Map<String, Object>) entry.get("arguments");
            assertThat(args.get("item_lang_key")).isEqualTo(item.langKey());
            Map<String, Object> data = (Map<String, Object>) entry.get("data");
            assertThat(data).as(item.id()).containsEntry("tfp:food_stamp", Map.of())
                .containsEntry("max_stack_size", item.stackSize());
            if (item.edible()) {
                assertThat(entry.get("template")).isEqualTo("tfp:item/food");
                assertThat(args.get("nutrition")).isEqualTo(item.hunger());
                assertThat((Map<String, Object>) data.get("consumable")).containsEntry("consume_seconds", 1.6);
            } else {
                assertThat(entry.get("template")).isEqualTo("tfp:item/simple");
                assertThat(data).doesNotContainKey("consumable");
            }
        }
    }

    /** Golden rows read from TFC's JSON by hand (food/*.json, item_size/*.json, the tags). */
    @Test
    void goldenRows() {
        record Row(String id, String def, int hunger, boolean edible, int stack) {
        }
        List<Row> rows = List.of(
            new Row("food/mutton", "tfc:mutton", 4, true, 32),
            new Row("food/wheat_bread", "tfc:wheat_bread", 4, true, 32),
            new Row("food/sugarcane", "tfc:sugarcane", 4, true, 32),
            // soups and salads match foods (light) before soups/salads (medium) in TFC's load order
            new Row("food/grain_soup", "tfc:soup", 4, true, 32),
            new Row("food/dairy_salad", "tfc:salad", 4, true, 32),
            new Row("blackberry_jam", "tfc:jam", 4, true, 32),
            new Row("peanut_jam", "tfc:peanut_jam", 4, true, 64),
            new Row("jar/blackberry", "tfc:sealed_preserves", 4, false, 4),
            new Row("jar/blackberry_unsealed", "tfc:preserves", 4, false, 4),
            // the peanut jar is removed from the preserves tags; filled_jars names only its unsealed jar
            new Row("jar/peanut", "tfc:jar/peanut", 4, false, 64),
            new Row("jar/peanut_unsealed", "tfc:jar/peanut_unsealed", 4, false, 4));
        for (Row r : rows) {
            FoodItems.FoodItem item = FoodItems.find(items, r.id()).orElseThrow();
            assertThat(item.definition()).as(r.id()).isEqualTo(r.def());
            assertThat(item.hunger()).as(r.id()).isEqualTo(r.hunger());
            assertThat(item.edible()).as(r.id()).isEqualTo(r.edible());
            assertThat(item.stackSize()).as(r.id()).isEqualTo(r.stack());
        }
    }

    /**
     * No food item matches two food definitions, so TFC's load-order rule and the runtime's item-then-tag rule
     * ({@code core/food/FoodDefinitions}) pick the same one.
     */
    @Test
    void foodDefinitionsAreUnambiguous() {
        TfcItemData data = new TfcItemData(tfc);
        List<TfcItemData.Definition> defs = data.definitions("food");
        for (FoodItems.FoodItem item : items) {
            assertThat(data.all(defs, "tfc:" + item.id())).as(item.id()).hasSize(1);
        }
    }

    /** The load order is java.util.HashMap's over ResourceLocation-style hashes (checked on TFC's item_size ids). */
    @Test
    void loadOrderPutsFoodsBeforeSoupsAndSalads() {
        List<String> order = TfcItemData.loadOrder("tfc", new TreeMap<>(Map.of(
            "foods", 1, "soups", 1, "salads", 1)).keySet());
        assertThat(order).containsExactlyInAnyOrder("foods", "soups", "salads");
        List<String> full = new TfcItemData(tfc).definitions("item_size").stream().map(TfcItemData.Definition::id).toList();
        assertThat(full.indexOf("tfc:foods")).isLessThan(full.indexOf("tfc:soups")).isLessThan(full.indexOf("tfc:salads"));
    }

    /** The full datagen run (the real checkout) holds the same food.yml and the English pack names of jars and jams. */
    @Test
    void fullRunHasTheFoodConfigAndNames() {
        Assumptions.assumeTrue(TfcCheckout.PINNED_COMMIT.equals(tfc.head()));
        Datagen.Result r = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT);
        assertThat(r.tree().get(FoodItems.OUTPUT)).isEqualTo(out.get(FoodItems.OUTPUT));
        JsonObject lang = JsonParser.parseString(new String(
            r.tree().get(FoodItems.RP + "lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        for (FoodItems.FoodItem item : items) {
            assertThat(lang.has(item.langKey())).as(item.langKey()).isTrue();
        }
        assertThat(lang.get("item.tfp.jar.blackberry").getAsString()).isEqualTo("Jar Of Blackberry Jam");
    }
}
