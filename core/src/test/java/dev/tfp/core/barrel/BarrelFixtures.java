/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/recipes/SealedBarrelRecipe.java @ c3b4d9536 (test fixtures for the barrel
 * recipe table, which is transcribed in tfc-barrel-recipes.tsv)
 */
package dev.tfp.core.barrel;

import dev.tfp.core.vessel.FluidAmount;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Shared loading of the TFC transcriptions and the synthetic chain used by the barrel tests. */
final class BarrelFixtures {

    static final String RECIPES_TSV = "/dev/tfp/core/barrel/tfc-barrel-recipes.tsv";
    static final String GOLDEN_TSV = "/dev/tfp/core/barrel/tfc-barrel-golden.tsv";

    /** The seal tick used by the golden file. */
    static final long BASE = 1000L;

    /** The six TFC recipes used by the golden progress rows (ids as in TFC's recipe JSON). */
    static final List<String> TFC_GRID = List.of("tfc:black_bed", "tfc:alabaster/raw", "tfc:large_prepared_hide",
        "tfc:food/cheese", "tfc:curdled_milk", "tfc:candle/black");

    /**
     * A synthetic sequential chain on one fluid, modelled on TFC's brined, pickled and preserved_in_vinegar. TFC's traits are
     * replaced by renamed items, and the last stage is infinite. The Python scratch script uses the same definitions.
     */
    static final List<BarrelRecipe> CHAIN = List.of(
        new BarrelRecipe("chain:brine", 3600, "tfc:brine", 125, id -> id.equals("food:maize"), 1,
            "food:brined", 1, "", 0),
        new BarrelRecipe("chain:pickle", 3600, "tfc:brine", 125, id -> id.equals("food:brined"), 1,
            "food:pickled", 1, "", 0),
        new BarrelRecipe("chain:preserve", -1, "tfc:brine", 125, id -> id.equals("food:pickled"), 1,
            "", 0, "", 0));

    private BarrelFixtures() {
    }

    /** The TFC recipe table rows, keyed by recipe id, in file order. */
    static Map<String, String[]> tfcTable() {
        final List<String[]> rows = read(RECIPES_TSV);
        return rows.stream().skip(1).collect(Collectors.toMap(r -> r[0], r -> r, (a, b) -> {
            throw new IllegalStateException("duplicate recipe " + a[0]);
        }, java.util.LinkedHashMap::new));
    }

    /** The recipe for a table row with a fluid input and at most one plain item input and output. */
    static BarrelRecipe fromRow(String[] r) {
        final int duration = Integer.parseInt(r[1]);
        final String inFluid = r[2];
        final int inFluidAmount = Integer.parseInt(r[3]);
        final String inKind = r[4];
        final boolean noItem = inKind.equals("none");
        if (!noItem && !inKind.equals("item")) {
            throw new IllegalArgumentException("not a plain row: " + r[0]);
        }
        final String inItem = noItem ? null : r[5];
        final int inItemCount = noItem ? 0 : Integer.parseInt(r[6]);
        final String outKind = r[7];
        final String outItem = switch (outKind) {
            case "none" -> "";
            case "fixed" -> r[8];
            case "copy_input" -> BarrelRecipe.COPY_INPUT;
            default -> throw new IllegalArgumentException("not a plain output: " + r[0]);
        };
        final int outItemCount = outKind.equals("none") ? 0 : Integer.parseInt(r[9]);
        final String outFluid = r[10].equals("-") ? "" : r[10];
        final int outFluidAmount = Integer.parseInt(r[11]);
        return new BarrelRecipe(r[0], duration, inFluid, inFluidAmount,
            inItem == null ? null : (id -> id.equals(inItem)), inItemCount, outItem, outItemCount, outFluid, outFluidAmount);
    }

    /** The six TFC grid recipes, in the golden file's order of lookup. */
    static List<BarrelRecipe> tfcGrid() {
        final Map<String, String[]> table = tfcTable();
        return TFC_GRID.stream().map(id -> fromRow(table.get(id))).toList();
    }

    /** A fluid from a golden cell: {@code -} or zero is empty. */
    static FluidAmount fluid(String id, int amount) {
        return "-".equals(id) || amount == 0 ? FluidAmount.EMPTY : new FluidAmount(id, amount);
    }

    /** A stack from a golden cell: {@code -} or zero is empty. */
    static BarrelStack stack(String id, int count) {
        return "-".equals(id) || count == 0 ? BarrelStack.EMPTY : new BarrelStack(id, count);
    }

    /** The golden progress rows: each entry is one cell line, split on tabs. */
    static List<String[]> goldenProgress() {
        return read(GOLDEN_TSV).stream().filter(r -> r[0].equals("progress")).toList();
    }

    /** The golden fill and drain rows. */
    static List<String[]> goldenFillDrain() {
        return read(GOLDEN_TSV).stream().filter(r -> !r[0].equals("progress")).toList();
    }

    /** The plain item input of a TFC grid recipe, or {@code null} for a fluid-only recipe. */
    static String itemOf(String recipeId) {
        final String[] row = tfcTable().get(recipeId);
        return row[4].equals("item") ? row[5] : null;
    }

    /** The recipe list a golden progress row runs against. */
    static List<BarrelRecipe> recipesFor(String recipeId) {
        return recipeId.startsWith("chain:") ? CHAIN : tfcGrid();
    }

    /** A sealed barrel at {@link #BASE} holding the row's start contents, as in the Python scratch script. */
    static BarrelState startOf(String[] row, List<BarrelRecipe> recipes) {
        final BarrelState unsealed = BarrelState.unsealed(fluid(row[2], Integer.parseInt(row[3])),
            stack(row[4], Integer.parseInt(row[5])));
        return unsealed.seal(BASE, recipes);
    }

    static long elapsed(String[] row) {
        return Long.parseLong(row[6]);
    }

    /** The row's expected state at {@code BASE + elapsed}. */
    static BarrelState expectedState(String[] row, long asOf) {
        final long recipeTick = Long.parseLong(row[13]);
        return new BarrelState(fluid(row[7], Integer.parseInt(row[8])), stack(row[9], Integer.parseInt(row[10])),
            stack(row[11], Integer.parseInt(row[12])), true, BASE, recipeTick, asOf);
    }

    private static List<String[]> read(String path) {
        try (InputStream in = BarrelFixtures.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            final List<String[]> rows = new ArrayList<>();
            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(l -> !l.startsWith("#") && !l.isBlank())
                .map(l -> l.split("\t", -1))
                .forEach(rows::add);
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
