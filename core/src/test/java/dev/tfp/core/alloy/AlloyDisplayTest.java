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
import org.junit.jupiter.api.Test;

/** The alloy display and storage codec, against TFC's recipe table (the same export as {@link FluidAlloyTest}). */
class AlloyDisplayTest {
    private static final int CAPACITY = 3000;

    private static List<AlloyRecipe> recipes() throws IOException {
        List<AlloyRecipe> out = new ArrayList<>();
        try (InputStream in = AlloyDisplayTest.class.getResourceAsStream("alloy-recipes.tsv")) {
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

    private static FluidAlloy mix(String a, int ma, String b, int mb) {
        return FluidAlloy.empty().fill(a, ma, CAPACITY).alloy().fill(b, mb, CAPACITY).alloy();
    }

    @Test
    void ninetyTenCopperTinIsBronze() throws IOException {
        AlloyDisplay.View view = AlloyDisplay.of(mix("tfc:metal/copper", 900, "tfc:metal/tin", 100), recipes());
        assertThat(view.result()).isEqualTo("tfc:metal/bronze");
        assertThat(view.amount()).isEqualTo(1000);
        assertThat(view.entries()).extracting(AlloyDisplay.Entry::fluid).containsExactly("tfc:metal/copper", "tfc:metal/tin");
        assertThat(view.entries()).extracting(AlloyDisplay.Entry::percent).containsExactly("90.0", "10.0");
        assertThat(view.unknown()).isFalse();
    }

    @Test
    void anUnmatchedMixIsUnknownInOurNamespace() throws IOException {
        AlloyDisplay.View view = AlloyDisplay.of(mix("tfc:metal/copper", 500, "tfc:metal/iron", 500), recipes());
        assertThat(view.result()).isEqualTo(AlloyDisplay.UNKNOWN);
        assertThat(view.unknown()).isTrue();
    }

    @Test
    void aSingleMetalIsItself() throws IOException {
        FluidAlloy alloy = FluidAlloy.empty().fill("tfc:metal/tin", 250, CAPACITY).alloy();
        AlloyDisplay.View view = AlloyDisplay.of(alloy, recipes());
        assertThat(view.result()).isEqualTo("tfc:metal/tin");
        assertThat(view.entries()).extracting(AlloyDisplay.Entry::percent).containsExactly("100.0");
    }

    @Test
    void emptyAlloyHasNoView() throws IOException {
        AlloyDisplay.View view = AlloyDisplay.of(FluidAlloy.empty(), recipes());
        assertThat(view.result()).isEmpty();
        assertThat(view.entries()).isEmpty();
    }

    @Test
    void percentRoundsToATenthLikeTfc() {
        assertThat(AlloyDisplay.percent(1, 3)).isEqualTo("33.3");
        assertThat(AlloyDisplay.percent(2, 3)).isEqualTo("66.7");
        assertThat(AlloyDisplay.percent(1, 1)).isEqualTo("100.0");
        assertThat(AlloyDisplay.percent(0.05, 1000)).isEqualTo("0.0");
    }

    @Test
    void codecRoundTripsAPartiallyDrainedAlloy() throws IOException {
        FluidAlloy drained = mix("tfc:metal/copper", 900, "tfc:metal/tin", 100).drain(333).alloy();
        FluidAlloy back = AlloyCodec.decode(drained.amount(), AlloyCodec.encode(drained));
        assertThat(back.amount()).isEqualTo(drained.amount());
        assertThat(back.contents()).isEqualTo(drained.contents());
        assertThat(back.result(recipes())).isEqualTo("tfc:metal/bronze");
    }

    @Test
    void codecSkipsMalformedLines() {
        FluidAlloy back = AlloyCodec.decode(100, List.of("nonsense", "a=notanumber", "tfp:metal/tin=-5", "tfp:metal/tin=100.0"));
        assertThat(back.contents()).containsOnlyKeys("tfp:metal/tin");
        assertThat(AlloyCodec.decode(0, List.of("tfp:metal/tin=100.0")).isEmpty()).isTrue();
        assertThat(AlloyCodec.decode(100, List.of()).isEmpty()).isTrue();
    }
}
