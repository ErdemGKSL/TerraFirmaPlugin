/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.datagen.visuals;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.tfp.datagen.Datagen;
import dev.tfp.datagen.TfcCheckout;
import dev.tfp.datagen.crafting.CraftingRecipes;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * PAR-PROC-30: a vanilla block that a TFC workbench recipe makes is player-made in a TFC world, so no visual profile may
 * repurpose it (the runtime suppression clears any crafting result that is a repurposed block). Checked on the real
 * pinned checkout against every emitted {@code configuration/crafting/*_recipes.yml} and every profile's
 * {@code suppressed_blocks.json}.
 */
class CraftedResultsNotRepurposedTest {
    private static SortedMap<String, byte[]> files;

    @BeforeAll
    static void generate() {
        Path path = Path.of(System.getProperty("tfc.checkout", "/opt/tfc"));
        Assumptions.assumeTrue(Files.isDirectory(path.resolve(".git")), "no TFC checkout at " + path);
        try {
            TfcCheckout.open(path, TfcCheckout.PINNED_COMMIT);
        } catch (RuntimeException e) {
            Assumptions.abort("TFC checkout is not at the pinned commit: " + e.getMessage());
        }
        files = Datagen.generate(path, TfcCheckout.PINNED_COMMIT).tree().files();
    }

    @Test
    void noWorkbenchResultIsASuppressedBlockInAnyProfile() {
        Map<String, TreeSet<String>> results = recipeResults();
        assertThat(results).as("emitted workbench recipe results").containsKey("minecraft:bricks");
        Map<String, TreeSet<String>> suppressed = suppressedPerProfile();
        assertThat(suppressed).as("visual profiles").hasSize(12);
        for (var profile : suppressed.entrySet()) {
            for (var result : results.entrySet()) {
                assertThat(profile.getValue())
                    .as("%s repurposes %s, the result of %s", profile.getKey(), result.getKey(), result.getValue())
                    .doesNotContain(result.getKey());
            }
        }
    }

    @Test
    void bricksAreNeverACandidateState() {
        VanillaSupply supply = VanillaSupply.load();
        for (VersionFamily f : VersionFamily.values()) {
            assertThat(supply.get(f, "minecraft:bricks").source()).isEqualTo(VanillaSupply.Source.TFC_PRESENT);
            for (boolean netherEnd : List.of(true, false)) {
                assertThat(supply.candidates(f, netherEnd, HideTargets.EXCLUDED))
                    .noneMatch(s -> s.state().equals("minecraft:bricks"));
            }
        }
    }

    /** Result id → the recipe ids producing it. */
    @SuppressWarnings("unchecked")
    private static Map<String, TreeSet<String>> recipeResults() {
        Map<String, TreeSet<String>> out = new TreeMap<>();
        for (var e : files.entrySet()) {
            if (!e.getKey().startsWith(CraftingRecipes.DIR) || !e.getKey().endsWith("_recipes.yml")) {
                continue;
            }
            Map<String, Object> root = new Yaml().load(new String(e.getValue(), StandardCharsets.UTF_8));
            Map<String, Map<String, Object>> recipes = (Map<String, Map<String, Object>>) root.get("recipes");
            recipes.forEach((id, recipe) -> {
                Object result = recipe.get("result");
                String item = result instanceof Map<?, ?> m ? String.valueOf(m.get("id")) : String.valueOf(result);
                out.computeIfAbsent(item, k -> new TreeSet<>()).add(id);
            });
        }
        assertThat(out).as("emitted recipes under " + CraftingRecipes.DIR).isNotEmpty();
        return out;
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
