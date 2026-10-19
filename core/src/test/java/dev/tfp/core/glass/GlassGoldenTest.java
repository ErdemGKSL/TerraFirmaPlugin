/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.glass;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.craft.CraftOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * M6-GLASS-01 golden values. {@code tfc-glass-recipes.tsv} was exported from TFC's glassworking recipe JSON and item tags at
 * c3b4d9536 by a scratch script (kept outside the repo). Each row is one recipe; the last column is the batch members with the
 * tags expanded, so the tag resolution is checked too.
 */
class GlassGoldenTest {

    static final String TSV = "/dev/tfp/core/glass/tfc-glass-recipes.tsv";

    /** The TSV rows (tab separated, comments skipped). */
    static final List<String[]> ROWS = new ArrayList<>();

    /** The file count recorded in the TSV header ({@code # source-files glassworking N}). */
    static final int SOURCE_FILES;

    static {
        int files = -1;
        try (InputStream in = GlassGoldenTest.class.getResourceAsStream(TSV)) {
            assert in != null : "missing " + TSV;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                if (line.startsWith("# source-files\t")) {
                    files = Integer.parseInt(line.split("\t")[2]);
                } else if (!line.startsWith("#") && !line.isEmpty()) {
                    ROWS.add(line.split("\t", -1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        SOURCE_FILES = files;
    }

    /** A blowpipe state with the given steps (TFC ids) and batch. */
    static GlassState state(List<String> ops, String batch) {
        final List<GlassOperation> steps = ops.stream().map(id -> GlassOperation.byId(id).orElseThrow()).toList();
        return new GlassState(steps, batch);
    }

    @Test
    void rowCountIsTheFileCountOfTheFolder() {
        assertThat(SOURCE_FILES).isEqualTo(60);
        assertThat(ROWS).hasSize(SOURCE_FILES);
        assertThat(GlassRecipes.ALL).hasSize(SOURCE_FILES);
    }

    @Test
    void everyRowIsTheJavaRecipe() {
        for (int i = 0; i < ROWS.size(); i++) {
            final String[] r = ROWS.get(i);
            final GlassRecipe recipe = GlassRecipes.ALL.get(i);
            assertThat(recipe.id()).as(r[0]).isEqualTo("glassworking/" + r[0]);
            assertThat(recipe.batch()).as(r[0]).isEqualTo(r[1]);
            assertThat(String.join(",", recipe.operations())).as(r[0]).isEqualTo(r[2]);
            assertThat(recipe.result()).as(r[0]).isEqualTo(new CraftOutput(r[3], Integer.parseInt(r[4])));
            assertThat(String.join(",", GlassBatches.members(r[1]))).as(r[0]).isEqualTo(r[5]);
        }
    }

    @Test
    void everyRowDecidesItsOwnOutput() {
        for (String[] r : ROWS) {
            final List<String> ops = List.of(r[2].split(","));
            final CraftOutput expected = new CraftOutput(r[3], Integer.parseInt(r[4]));
            for (String member : r[5].split(",")) {
                final Optional<GlassRecipe> match = GlassRules.match(GlassRecipes.ALL, state(ops, member));
                assertThat(match).as(r[0] + " with " + member).isPresent();
                assertThat(match.get().id()).as(r[0] + " with " + member).isEqualTo("glassworking/" + r[0]);
                assertThat(GlassRules.output(GlassRecipes.ALL, state(ops, member))).as(r[0] + " with " + member).contains(expected);
            }
        }
    }

    @Test
    void unrelatedBatchMatchesNothing() {
        for (String[] r : ROWS) {
            final List<String> ops = List.of(r[2].split(","));
            assertThat(GlassRules.match(GlassRecipes.ALL, state(ops, "minecraft:sand"))).as(r[0]).isEmpty();
        }
    }
}
