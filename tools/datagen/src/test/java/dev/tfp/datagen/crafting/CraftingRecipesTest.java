/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.OutputTree;
import dev.tfp.datagen.TfcCheckout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/** PAR-PROC-02: TFC's plain workbench recipes as CE recipe YAML, on the real pinned checkout. */
class CraftingRecipesTest {
    private static TfcCheckout tfc;
    private static OutputTree tree;
    private static List<CraftingRecipes.Outcome> outcomes;
    private static Map<String, Map<String, Object>> recipes;
    private static JsonObject report;

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
        assertThat(CraftingRecipes.available(tfc)).isTrue();
        tree = Datagen.generate(tfc.root(), TfcCheckout.PINNED_COMMIT).tree();
        // A second run over the finished tree sees the same CE items (its own folder is skipped) and must agree.
        outcomes = CraftingRecipes.run(tfc, copyOf(tree));
        recipes = new TreeMap<>();
        LoaderOptions opts = new LoaderOptions();
        opts.setAllowDuplicateKeys(false);
        for (var e : tree.files().entrySet()) {
            if (e.getKey().startsWith(CraftingRecipes.DIR)) {
                String text = new String(e.getValue(), StandardCharsets.UTF_8);
                assertThat(text).as(e.getKey()).doesNotContain("\t");
                Map<String, Object> root = new Yaml(opts).load(text);
                assertThat(root.keySet()).as(e.getKey()).containsExactly("recipes");
                for (var r : ((Map<String, Map<String, Object>>) root.get("recipes")).entrySet()) {
                    assertThat(recipes.put(r.getKey(), r.getValue())).as("duplicate " + r.getKey()).isNull();
                }
            }
        }
        report = JsonParser.parseString(new String(tree.get(CraftingRecipes.REPORT), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static OutputTree copyOf(OutputTree t) {
        OutputTree c = new OutputTree();
        t.files().forEach(c::put);
        return c;
    }

    /** The item's test: every one of TFC's 1686 shaped + 569 shapeless recipes is emitted, routed or pending. */
    @Test
    void everyPlainRecipeIsAccountedFor() {
        long shaped = 0;
        long shapeless = 0;
        for (String f : tfc.list(CraftingRecipes.SOURCE)) {
            String type = JsonParser.parseString(tfc.readString(f)).getAsJsonObject().get("type").getAsString();
            shaped += type.equals(CraftingRecipes.SHAPED) ? 1 : 0;
            shapeless += type.equals(CraftingRecipes.SHAPELESS) ? 1 : 0;
        }
        assertThat(shaped).isEqualTo(1686);
        assertThat(shapeless).isEqualTo(569);
        assertThat(outcomes).hasSize(2255);
        int emitted = report.get("emitted").getAsInt();
        int routed = report.get("routed").getAsInt();
        int pending = report.get("pending").getAsInt();
        assertThat(report.get("total").getAsInt()).isEqualTo(2255);
        assertThat(emitted + routed + pending).isEqualTo(2255);
        assertThat(recipes).hasSize(emitted);
        assertThat(outcomes.stream().filter(o -> o.bucket() == CraftingRecipes.Bucket.EMITTED).map(CraftingRecipes.Outcome::id))
            .containsExactlyInAnyOrderElementsOf(recipes.keySet());
        assertThat(report.getAsJsonObject("pending_recipes").size()).isEqualTo(pending);
        assertThat(report.getAsJsonObject("routed_recipes").size()).isEqualTo(routed);
    }

    /** Only TFC's custom ingredient types go to PAR-PROC-03 (counts hand-checked against the checkout). */
    @Test
    void routedRecipesAreTheCustomIngredients() {
        JsonObject reasons = report.getAsJsonObject("routed_by_reason");
        assertThat(reasons.keySet()).containsExactlyInAnyOrder("ingredient tfc:fluid_content", "ingredient tfc:and");
        assertThat(reasons.get("ingredient tfc:fluid_content").getAsInt()).isEqualTo(137);
        assertThat(reasons.get("ingredient tfc:and").getAsInt()).isEqualTo(46);
        assertThat(report.getAsJsonObject("routed_recipes").get("tfp:crafting/clay_ball_2").getAsString())
            .isEqualTo("PAR-PROC-03: ingredient tfc:fluid_content");
        assertThat(report.getAsJsonObject("routed_recipes").get("tfp:crafting/cranberry_jam").getAsString())
            .isEqualTo("PAR-PROC-03: ingredient tfc:and");
    }

    /** CE fails a recipe naming an unknown item or an empty tag: every element must be a CE item of the tree or vanilla. */
    @Test
    @SuppressWarnings("unchecked")
    void emittedRecipesNameOnlyKnownItems() {
        Set<String> ce = CraftingRecipes.ceItems(tree);
        assertThat(recipes).isNotEmpty();
        for (var r : recipes.entrySet()) {
            Map<String, Object> recipe = r.getValue();
            assertThat(recipe.get("type")).as(r.getKey()).isIn("shaped", "shapeless");
            assertThat(recipe.get("category")).as(r.getKey()).isIn("building", "redstone", "equipment", "misc");
            Map<String, Object> result = (Map<String, Object>) recipe.get("result");
            assertThat(result.keySet()).containsExactly("id", "count");
            assertKnown((String) result.get("id"), ce, r.getKey());
            List<Object> slots = new ArrayList<>();
            if (recipe.get("type").equals("shaped")) {
                Map<String, Object> key = (Map<String, Object>) recipe.get("ingredients");
                List<String> pattern = (List<String>) recipe.get("pattern");
                assertThat(pattern).isNotEmpty().hasSizeLessThanOrEqualTo(3);
                StringBuilder used = new StringBuilder();
                pattern.forEach(p -> {
                    assertThat(p.length()).as(r.getKey()).isEqualTo(pattern.getFirst().length()).isBetween(1, 3);
                    used.append(p.replace(" ", ""));
                });
                assertThat(key.keySet()).as(r.getKey())
                    .containsExactlyInAnyOrderElementsOf(used.chars().distinct().mapToObj(c -> String.valueOf((char) c)).toList());
                slots.addAll(key.values());
            } else {
                slots.addAll((List<Object>) recipe.get("ingredients"));
                assertThat(slots).as(r.getKey()).hasSizeBetween(1, 9);
            }
            for (Object slot : slots) {
                List<String> elements = slot instanceof List<?> l ? (List<String>) l : List.of((String) slot);
                assertThat(elements).as(r.getKey()).isNotEmpty().doesNotHaveDuplicates();
                elements.forEach(e -> assertKnown(e, ce, r.getKey()));
            }
        }
    }

    private static void assertKnown(String id, Set<String> ce, String where) {
        if (id.startsWith("#")) {
            assertThat(id).as(where).startsWith("#minecraft:");
        } else if (id.startsWith("tfp:")) {
            assertThat(ce).as(where).contains(id);
        } else {
            assertThat(id).as(where).startsWith("minecraft:").isNotEqualTo("minecraft:air");
        }
    }

    /** Golden conversions against the TFC JSON. */
    @Test
    @SuppressWarnings("unchecked")
    void goldenRecipes() {
        // tfc:crafting/activator_rail: c:rods/steel (TFC's steel rod), c:rods/wooden (NeoForge: minecraft:stick), 12 rails.
        Map<String, Object> rail = recipes.get("tfp:crafting/activator_rail");
        assertThat(rail).containsEntry("type", "shaped").containsEntry("category", "misc")
            .containsEntry("pattern", List.of("ISI", "ITI", "ISI"));
        assertThat((Map<String, Object>) rail.get("ingredients")).containsExactlyInAnyOrderEntriesOf(Map.of(
            "I", "tfp:metal/rod/steel", "S", "minecraft:stick", "T", "minecraft:redstone_torch"));
        assertThat(rail.get("result")).isEqualTo(Map.of("id", "minecraft:activator_rail", "count", 12));
        // tfc:crafting/black_dye: a shapeless list ingredient becomes one slot with both alternatives.
        Map<String, Object> dye = recipes.get("tfp:crafting/black_dye");
        assertThat(dye).containsEntry("type", "shapeless")
            .containsEntry("ingredients", List.of(List.of("tfp:powder/charcoal", "tfp:powder/coke")));
        // tfc:crafting/firestarter: its result item is a CE item since PAR-HEAT-05, so the recipe is emitted.
        assertThat(((Map<?, ?>) recipes.get("tfp:crafting/firestarter").get("result")).get("id"))
            .isEqualTo("tfp:firestarter");
        // tfc:crafting/gem_saw: its CE result has a tool rule on tfp:mineable/glass_saw, a server tag since PAR-PROC-24
        // (bootstrap datapack), so CE can build it and the recipe is emitted.
        assertThat(recipes).containsKey("tfp:crafting/gem_saw");
        Set<String> unbuildable = CraftingRecipes.unbuildable(tree).keySet();
        assertThat(unbuildable).isEmpty();
        recipes.forEach((id, r) -> assertThat(((Map<?, ?>) r.get("result")).get("id")).as(id).isNotIn(unbuildable));
        // tfc:crafting/detector_rail: c:stones/pressure_plate is TFC's rock plates (family blocks, not CE yet) plus an
        // optional #minecraft:stone_pressure_plates, which vanilla has only as a block tag: left out, the recipe waits.
        assertThat(report.getAsJsonObject("pending_recipes").getAsJsonArray("tfp:crafting/detector_rail").toString())
            .contains("tfc:rock/pressure_plate/granite");
        // A wood family recipe waits for the family blocks (the stairs are a family block that is not a CE block yet).
        assertThat(report.getAsJsonObject("pending_recipes").getAsJsonArray("tfp:crafting/wood/planks/oak_stairs").toString())
            .contains("family block of families/wood.yml");
    }

    /** The tag layers: NeoForge's c: members, TFC's additions, vanilla tags kept as server tags. */
    @Test
    void tagLayers() {
        Map<String, com.google.gson.JsonArray> neoforge = new HashMap<>();
        byte[] common = tree.get("data/neoforge_common_tags.json");
        Assumptions.assumeTrue(common != null, "generated without the NeoForge jar");
        JsonParser.parseString(new String(common, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("item")
            .entrySet().forEach(e -> neoforge.put(e.getKey(), e.getValue().getAsJsonArray()));
        ItemTags tags = new ItemTags(tfc, neoforge);
        assertThat(tags.resolve("c:rods/wooden").items()).contains("minecraft:stick");
        assertThat(tags.resolve("c:rods/steel").items()).contains("tfc:metal/rod/steel");
        assertThat(tags.resolve("minecraft:dirt").vanillaTags()).containsExactly("#minecraft:dirt");
        assertThat(tags.resolve("c:stones/pressure_plate").vanillaTags()).isEmpty(); // optional vanilla reference left out
        assertThat(tags.resolve("c:stones/pressure_plate").items()).contains("tfc:rock/pressure_plate/granite");
        assertThat(tags.resolve("tfc:lumber").items()).contains("tfc:wood/lumber/oak").hasSize(20); // TFC's 20 woods
        assertThat(new ItemTags(tfc, Map.of()).resolve("c:rods/wooden").items()).doesNotContain("minecraft:stick");
    }

    @Test
    void fileGrouping() {
        assertThat(CraftingRecipes.fileOf("wood/planks/oak")).isEqualTo("wood_recipes");
        assertThat(CraftingRecipes.fileOf("bellows")).isEqualTo("general_recipes");
    }
}
