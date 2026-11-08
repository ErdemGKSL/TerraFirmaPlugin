/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crafting.CraftingRecipes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-PROC-30/31: a vanilla block that any TFC recipe makes is player-made in a TFC world, so no visual profile may
 * repurpose it (the runtime suppression clears any recipe result that is a repurposed block, and a repurposed state
 * would make a real dyed wool, glazed terracotta, piston, ... look like a TFC block). Checked on the real pinned
 * checkout over the result ids of <em>every</em> recipe type:
 * <ul>
 *   <li>every recipe JSON under the checkout's {@code src/generated/resources/data/<ns>/recipe/} (tfc: barrel, loom,
 *       heating, glassworking, crafting, ...; and TFC's overrides of vanilla recipes in {@code data/minecraft});</li>
 *   <li>every recipe JSON that datagen emits under {@code data/<ns>/recipe/} and every emitted CE workbench recipe
 *       ({@code configuration/crafting/*_recipes.yml}), so a device port cannot bring back a repurposed result;</li>
 *   <li>blocks that vanilla physics makes from a result: placed concrete powder hardens to concrete next to water.</li>
 * </ul>
 * Recipes disabled by a {@code neoforge:false} condition make nothing; a result that is one of the recipe's own
 * ingredients or inputs is not made by it (tfc:landslide and tfc:collapse move a block that is already there). The
 * same rules give {@code TFC_CRAFTED} in {@code tools/datagen/scripts/export_fullcube_states.py}.
 */
class CraftedResultsNotRepurposedTest {
    private static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private static SortedMap<String, byte[]> files;
    private static Path checkout;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        checkout = path;
        files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
    }

    @Test
    void noRecipeResultIsASuppressedBlockInAnyProfile() {
        Map<String, TreeSet<String>> results = recipeResults();
        Map<String, TreeSet<String>> suppressed = suppressedPerProfile();
        assertThat(suppressed).as("visual profiles").hasSize(12);
        for (var profile : suppressed.entrySet()) {
            for (var result : results.entrySet()) {
                assertThat(profile.getValue())
                    .as("%s repurposes %s, made by %s", profile.getKey(), result.getKey(), result.getValue())
                    .doesNotContain(result.getKey());
            }
        }
    }

    /**
     * Stronger than the profile check (which only sees the states today's demand claims): every non-identity full-cube
     * state of a recipe-made block is {@code tfc_present} in the supply, so no future demand can claim it.
     */
    @Test
    void everyRecipeResultIsTfcPresentInTheSupply() {
        Set<String> results = recipeResults().keySet();
        VanillaSupply supply = VanillaSupply.load();
        for (VersionFamily f : VersionFamily.values()) {
            Set<String> listed = new HashSet<>();
            for (VanillaSupply.State s : supply.all(f).values()) {
                if (results.contains(s.block()) && !s.source().identity()) {
                    listed.add(s.block());
                    assertThat(s.source()).as("%s on %s is made by a TFC recipe", s.state(), f.id())
                        .isEqualTo(VanillaSupply.Source.TFC_PRESENT);
                }
            }
            for (boolean netherEnd : List.of(true, false)) {
                assertThat(supply.candidates(f, netherEnd, HideTargets.EXCLUDED))
                    .as("candidates on %s", f.id())
                    .noneMatch(s -> results.contains(s.block()) && !s.source().identity());
            }
            // the families PAR-PROC-30/31 found: workbench, loom, barrel, heating, glassworking, vanilla overrides
            assertThat(listed).as("recipe-made full cubes on %s", f.id()).contains("minecraft:bricks",
                "minecraft:white_wool", "minecraft:red_wool", "minecraft:terracotta", "minecraft:blue_terracotta",
                "minecraft:black_glazed_terracotta", "minecraft:white_glazed_terracotta", "minecraft:lime_concrete_powder",
                "minecraft:lime_concrete", "minecraft:glass", "minecraft:tinted_glass", "minecraft:cyan_stained_glass",
                "minecraft:hay_block", "minecraft:piston", "minecraft:sticky_piston", "minecraft:observer",
                "minecraft:smithing_table", "minecraft:slime_block");
        }
    }

    @Test
    void everyRecipeTypeIsRead() {
        Map<String, TreeSet<String>> results = recipeResults();
        Set<String> sources = new TreeSet<>();
        results.values().forEach(sources::addAll);
        for (String dir : List.of("barrel/", "loom/", "heating/", "glassworking/", "crafting/")) {
            assertThat(sources).as("TFC recipes under " + dir).anyMatch(s -> s.startsWith("tfc:tfc/recipe/" + dir));
            assertThat(sources).as("emitted recipes under " + dir).anyMatch(s -> s.startsWith("emitted:data/tfp/recipe/" + dir));
        }
        assertThat(sources).anyMatch(s -> s.startsWith("tfc:minecraft/recipe/"));
        assertThat(sources).anyMatch(s -> s.startsWith("emitted:" + CraftingRecipes.DIR));
        assertThat(results.get("minecraft:white_wool")).contains("tfc:tfc/recipe/loom/white_wool.json",
            "tfc:tfc/recipe/barrel/bleach_wool.json");
        assertThat(results.get("minecraft:white_glazed_terracotta")).contains("tfc:tfc/recipe/heating/white_glazed_terracotta.json");
        assertThat(results.get("minecraft:piston")).contains("tfc:minecraft/recipe/piston.json");
        // landslides only move a block that already exists; the removed vanilla recipes make nothing
        assertThat(results).doesNotContainKeys("minecraft:sand", "minecraft:gravel", "minecraft:cobblestone");
        assertThat(results).doesNotContainKey("minecraft:stone_bricks");
    }

    /** Result id → where it is made ({@code tfc:<path>}, {@code emitted:<path>} or {@code vanilla:<from>}). */
    @SuppressWarnings("unchecked")
    private static Map<String, TreeSet<String>> recipeResults() {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (var e : files.entrySet()) {
            String path = e.getKey();
            String text = new String(e.getValue(), StandardCharsets.UTF_8);
            if (path.startsWith(CraftingRecipes.DIR) && path.endsWith("_recipes.yml")) {
                Map<String, Object> root = new Yaml().load(text);
                Map<String, Map<String, Object>> recipes = (Map<String, Map<String, Object>>) root.get("recipes");
                recipes.forEach((id, recipe) -> {
                    Object result = recipe.get("result");
                    String item = result instanceof Map<?, ?> m ? String.valueOf(m.get("id")) : String.valueOf(result);
                    out.computeIfAbsent(item, k -> new TreeSet<>()).add("emitted:" + path + "#" + id);
                });
            } else if (path.matches("data/[a-z0-9_]+/recipe/.+\\.json")) {
                addJsonRecipe(out, "emitted:" + path, JsonParser.parseString(text));
            }
        }
        Path data = checkout.resolve("src/generated/resources/data");
        try (Stream<Path> walk = Files.walk(data)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String rel = data.relativize(p).toString().replace('\\', '/');
                if (rel.matches("[a-z0-9_]+/recipe/.+")) {
                    addJsonRecipe(out, "tfc:" + rel, JsonParser.parseString(Files.readString(p)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (String c : COLORS) {
            if (out.containsKey("minecraft:" + c + "_concrete_powder")) {
                out.computeIfAbsent("minecraft:" + c + "_concrete", k -> new TreeSet<>())
                    .add("vanilla:minecraft:" + c + "_concrete_powder hardens next to water");
            }
        }
        out.keySet().removeIf(id -> !id.startsWith("minecraft:"));
        assertThat(out).as("recipe results").isNotEmpty();
        return out;
    }

    private static void addJsonRecipe(Map<String, TreeSet<String>> out, String source, JsonElement json) {
        if (!json.isJsonObject()) {
            return;
        }
        JsonObject o = json.getAsJsonObject();
        if (o.has("neoforge:conditions")) {
            for (JsonElement c : o.getAsJsonArray("neoforge:conditions")) {
                if (c.isJsonObject() && "neoforge:false".equals(c.getAsJsonObject().get("type").getAsString())) {
                    return;
                }
            }
        }
        Set<String> made = ids(o, k -> k.contains("result") || k.contains("output"));
        made.removeAll(ids(o, k -> k.contains("ingredient") || k.contains("input") || k.equals("key")));
        made.forEach(id -> out.computeIfAbsent(id, k -> new TreeSet<>()).add(source));
    }

    /** Every {@code minecraft:} id string below a key matching {@code key}. */
    private static Set<String> ids(JsonElement e, Predicate<String> key) {
        Set<String> out = new TreeSet<>();
        collect(e, key, false, out);
        return out;
    }

    private static void collect(JsonElement e, Predicate<String> key, boolean inside, Set<String> out) {
        if (e.isJsonObject()) {
            e.getAsJsonObject().entrySet().forEach(en -> collect(en.getValue(), key, inside || key.test(en.getKey()), out));
        } else if (e.isJsonArray()) {
            e.getAsJsonArray().forEach(v -> collect(v, key, inside, out));
        } else if (inside && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            String s = e.getAsString();
            if (s.matches("minecraft:[a-z0-9_/]+")) {
                out.add(s);
            }
        }
    }

    /** Profile path → the suppressed (repurposed) vanilla blocks of that allocation. */
    private static Map<String, TreeSet<String>> suppressedPerProfile() {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith("visuals/") || !e.getKey().endsWith("/" + VisualOutputs.SUPPRESSED_FILE)) {
                continue;
            }
            TreeSet<String> blocks = new TreeSet<>();
            JsonElement json = JsonParser.parseString(new String(e.getValue(), StandardCharsets.UTF_8));
            json.getAsJsonObject().getAsJsonArray("suppress")
                .forEach(s -> blocks.add(s.getAsJsonObject().get("block").getAsString()));
            out.put(e.getKey(), blocks);
        }
        return out;
    }
}
