/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.alloy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Golden tests for the nine TFC alloys: the recipe table is exported from TFC's {@code recipe/alloy/} at c3b4d9536. */
class FluidAlloyTest {
    private static final int CAPACITY = 16_000;

    private static List<AlloyRecipe> recipes() throws IOException {
        List<AlloyRecipe> out = new ArrayList<>();
        try (InputStream in = FluidAlloyTest.class.getResourceAsStream("alloy-recipes.tsv")) {
            assertThat(in).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String[] cols = line.split("\t");
                List<AlloyRange> ranges = new ArrayList<>();
                for (int i = 1; i < cols.length; i++) {
                    String[] p = cols[i].split("\\|");
                    ranges.add(new AlloyRange(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                }
                out.add(new AlloyRecipe(cols[0], ranges));
            }
        }
        return out;
    }

    /** An alloy whose fractions are the midpoints of each range, normalised to the given total. */
    private static FluidAlloy midpoint(AlloyRecipe recipe, int total) {
        double sum = 0;
        for (AlloyRange range : recipe.contents()) {
            sum += (range.min() + range.max()) / 2;
        }
        FluidAlloy alloy = FluidAlloy.empty();
        for (AlloyRange range : recipe.contents()) {
            double fraction = (range.min() + range.max()) / 2 / sum;
            alloy = alloy.fill(range.fluid(), (int) Math.round(fraction * total), CAPACITY).alloy();
        }
        return alloy;
    }

    @Test
    void nineAlloysAreGolden() throws IOException {
        List<AlloyRecipe> recipes = recipes();
        assertThat(recipes).hasSize(9);
        for (AlloyRecipe recipe : recipes) {
            FluidAlloy alloy = midpoint(recipe, 1000);
            assertThat(alloy.matches(recipe)).as(recipe.result()).isTrue();
            assertThat(alloy.result(recipes)).as(recipe.result()).isEqualTo(recipe.result());
        }
    }

    @Test
    void rangeEdgesAreInside() throws IOException {
        for (AlloyRecipe recipe : recipes()) {
            for (AlloyRange range : recipe.contents()) {
                assertThat(range.isIn(range.min())).as(recipe.result()).isTrue();
                assertThat(range.isIn(range.max())).as(recipe.result()).isTrue();
                assertThat(range.isIn(range.max() + 0.01)).as(recipe.result()).isFalse();
            }
        }
    }

    @Test
    void singleFluidIsItsOwnResult() throws IOException {
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/copper", 500, CAPACITY).alloy();
        assertThat(alloy.result(recipes())).isEqualTo("tfc:metal/copper");
    }

    @Test
    void unknownMixGivesUnknownResult() throws IOException {
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/copper", 500, CAPACITY).alloy()
            .fill("tfc:metal/iron", 500, CAPACITY).alloy();
        assertThat(alloy.result(recipes())).isEqualTo(FluidAlloy.UNKNOWN);
    }

    @Test
    void fillStopsAtCapacity() {
        FluidAlloy.Fill first = FluidAlloy.empty().fill("tfc:metal/tin", 15_000, CAPACITY);
        assertThat(first.accepted()).isEqualTo(15_000);
        FluidAlloy.Fill second = first.alloy().fill("tfc:metal/tin", 5_000, CAPACITY);
        assertThat(second.accepted()).isEqualTo(1_000);
        assertThat(second.alloy().amount()).isEqualTo(CAPACITY);
        assertThat(second.alloy().fill("tfc:metal/tin", 1, CAPACITY).accepted()).isZero();
    }

    @Test
    void partialDrainKeepsTheRatio() {
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/copper", 600, CAPACITY).alloy()
            .fill("tfc:metal/tin", 400, CAPACITY).alloy();
        FluidAlloy.Drain drain = alloy.drain(500);
        assertThat(drain.drained()).isEqualTo(500);
        assertThat(drain.alloy().amount()).isEqualTo(500);
        Map<String, Double> left = drain.alloy().contents();
        assertThat(left.get("tfc:metal/copper")).isCloseTo(300, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(left.get("tfc:metal/tin")).isCloseTo(200, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void drainOfAllOrMoreEmptiesTheAlloy() {
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/copper", 300, CAPACITY).alloy();
        FluidAlloy.Drain drain = alloy.drain(1_000);
        assertThat(drain.drained()).isEqualTo(300);
        assertThat(drain.alloy().isEmpty()).isTrue();
    }
}
