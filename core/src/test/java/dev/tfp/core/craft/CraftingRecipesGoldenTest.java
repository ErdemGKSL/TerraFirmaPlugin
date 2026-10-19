/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/generated/resources/data/tfc/recipe/crafting/ @ c3b4d9536 (golden values only)
 */
package dev.tfp.core.craft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * M3-GUI-02 golden table: every TFC crafting recipe at c3b4d9536, one row per file in {@code recipe/crafting/}. The rows are in
 * {@code crafting-recipes.tsv}, exported by a scratch script (not in the repo). This test checks the table's shape and counts, not
 * the recipe logic: the row count against the file count recorded in the table, the count per recipe type, and the well-formedness
 * of every recipe id, ingredient ref and result.
 */
class CraftingRecipesGoldenTest {

    static final String TSV = "/dev/tfp/core/craft/crafting-recipes.tsv";

    /** Matches a resource id: {@code namespace:path}. */
    static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    static final Pattern RECIPE_ID = Pattern.compile("crafting/[a-z0-9_./-]+");
    static final Pattern FLUID_AMOUNT = Pattern.compile("fluid:[a-z0-9_.-]+:[a-z0-9_./-]+@[0-9]+");
    static final Pattern TRAIT = Pattern.compile("trait:[a-z0-9_.-]+:[a-z0-9_./-]+");

    /** The file count the table was exported from, and the expected count per recipe type. */
    static final int EXPECTED_FILES = 2912;
    static final Map<String, Integer> EXPECTED_BY_TYPE = Map.of(
        "minecraft:crafting_shaped", 1686,
        "minecraft:crafting_shapeless", 569,
        "tfc:advanced_shaped_crafting", 372,
        "tfc:advanced_shapeless_crafting", 283,
        "tfc:casting_crafting", 1,
        "tfc:food_combining", 1);

    /** The data rows, with the 8 columns; comment lines and the header are skipped. */
    static final List<String[]> ROWS;
    /** The {@code files=N} value from the table's comment header. */
    static final int FILES;

    static {
        final List<String> lines;
        try (InputStream in = CraftingRecipesGoldenTest.class.getResourceAsStream(TSV)) {
            assert in != null : "missing " + TSV;
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        FILES = lines.stream()
            .filter(l -> l.startsWith("# files="))
            .findFirst()
            .map(l -> Integer.parseInt(l.substring("# files=".length())))
            .orElseThrow();
        ROWS = lines.stream()
            .filter(l -> !l.startsWith("#") && !l.isBlank())
            .skip(1) // the column header
            .map(l -> l.split("\t", -1))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Test
    void rowCountMatchesFileCount() {
        assertThat(FILES).isEqualTo(EXPECTED_FILES);
        assertThat(ROWS).hasSize(FILES);
        assertThat(ROWS.stream().map(r -> r[1]).distinct()).hasSize(FILES);
    }

    @Test
    void countsPerTypeMatchTheGoldenTable() {
        final Map<String, Integer> counts = new TreeMap<>();
        for (String[] r : ROWS) {
            counts.merge(r[0], 1, Integer::sum);
        }
        assertThat(counts).isEqualTo(new TreeMap<>(EXPECTED_BY_TYPE));
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(FILES);
    }

    @Test
    void everyRowHasAllColumnsAndANonEmptyResult() {
        for (String[] r : ROWS) {
            assertThat(r).as("columns of %s", r[1]).hasSize(8);
            assertThat(r[4]).as("result of %s", r[1]).isNotEmpty();
        }
    }

    @Test
    void recipeIdsAndResultsAreWellFormed() {
        for (String[] r : ROWS) {
            assertThat(RECIPE_ID.matcher(r[1]).matches()).as("recipe id %s", r[1]).isTrue();
            assertThat(ID.matcher(r[0]).matches()).as("type of %s", r[1]).isTrue();
            final String result = r[4];
            if (result.startsWith("item:")) {
                assertThat(ID.matcher(result.substring(5)).matches()).as("result of %s", r[1]).isTrue();
                assertThat(Integer.parseInt(r[5])).as("count of %s", r[1]).isGreaterThanOrEqualTo(1);
            } else if (result.equals("modifiers")) {
                assertThat(r[6]).as("modifiers of %s", r[1]).isNotEmpty();
            } else {
                assertThat(result).as("result of %s", r[1]).startsWith("special:");
            }
        }
    }

    @Test
    void ingredientRefsAreWellFormed() {
        for (String[] r : ROWS) {
            if (!r[2].isEmpty()) {
                assertThat(isWellFormedRef(r[2])).as("primary of %s", r[1]).isTrue();
            }
            if (!r[3].isEmpty()) {
                for (String ref : r[3].split(";", -1)) {
                    assertThat(isWellFormedRef(ref)).as("ingredient %s of %s", ref, r[1]).isTrue();
                }
            }
        }
    }

    @Test
    void advancedShapelessRowsHavePrimaryAmongTheirIngredients() {
        for (String[] r : ROWS) {
            if (r[0].equals("tfc:advanced_shapeless_crafting")) {
                assertThat(r[2]).as("primary of %s", r[1]).isNotEmpty();
                assertThat(List.of(r[3].split(";"))).as("ingredients of %s", r[1]).contains(r[2]);
            }
        }
    }

    /** A ref is {@code item:<id>}, {@code tag:<id>}, {@code pred:<type>} with an optional {@code [...]} body, or {@code any[...]}. */
    static boolean isWellFormedRef(String ref) {
        if (ref.startsWith("item:") || ref.startsWith("tag:")) {
            return ID.matcher(ref.substring(ref.indexOf(':') + 1)).matches();
        }
        if (ref.startsWith("any[") && ref.endsWith("]")) {
            return allWellFormed(ref.substring(4, ref.length() - 1));
        }
        if (ref.startsWith("pred:")) {
            final int open = ref.indexOf('[');
            if (open < 0) {
                return ID.matcher(ref.substring(5)).matches();
            }
            return ID.matcher(ref.substring(5, open)).matches()
                && ref.endsWith("]")
                && allWellFormed(ref.substring(open + 1, ref.length() - 1));
        }
        if (FLUID_AMOUNT.matcher(ref).matches() || TRAIT.matcher(ref).matches()) {
            return true;
        }
        return false;
    }

    /** Splits {@code inner} on {@code +} at bracket depth 0 and checks each part. */
    static boolean allWellFormed(String inner) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            final char c = inner.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '+' && depth == 0) {
                parts.add(inner.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(inner.substring(start));
        if (depth != 0) return false;
        for (String p : parts) {
            if (!isWellFormedRef(p)) return false;
        }
        return true;
    }
}
