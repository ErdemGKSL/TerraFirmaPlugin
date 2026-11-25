/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.pile.PileVisual.Variant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PAR-METAL-16: where TFC's client model puts ingot {@code i} ({@code IngotPileBlockModel.render}, {@code layer = (i + 8) / 8},
 * {@code x = (i % 4) * 0.25}, {@code y = (layer - 1) * 0.125}, {@code z = i % 8 >= 4 ? 0.5 : 0}, odd layers turned) and the display
 * plan built on it.
 */
class PileVisualTest {

    @Test
    void singleIngotsLieInRowsOfFourInTwoRowsAndEightLayers() {
        assertThat(PileLayout.of(PileKind.INGOT, 0)).isEqualTo(new PileLayout.Placement(0, 0, 0, true));
        assertThat(PileLayout.of(PileKind.INGOT, 3)).isEqualTo(new PileLayout.Placement(0.75, 0, 0, true));
        assertThat(PileLayout.of(PileKind.INGOT, 4)).isEqualTo(new PileLayout.Placement(0, 0, 0.5, true));
        assertThat(PileLayout.of(PileKind.INGOT, 7)).isEqualTo(new PileLayout.Placement(0.75, 0, 0.5, true));
        // the ninth ingot starts layer 2 (even: not turned) one eighth up
        assertThat(PileLayout.of(PileKind.INGOT, 8)).isEqualTo(new PileLayout.Placement(0, 0.125, 0, false));
        assertThat(PileLayout.of(PileKind.INGOT, 63)).isEqualTo(new PileLayout.Placement(0.75, 0.875, 0.5, false));
        assertThat(PileLayout.of(PileKind.INGOT, 56).rotated()).isFalse(); // layer 8
        assertThat(PileLayout.of(PileKind.INGOT, 48).rotated()).isTrue(); // layer 7
    }

    @Test
    void doubleIngotsLieInRowsOfThreeInTwoRowsAndSixLayers() {
        assertThat(PileLayout.of(PileKind.DOUBLE_INGOT, 2)).isEqualTo(new PileLayout.Placement(0.66, 0, 0, true));
        assertThat(PileLayout.of(PileKind.DOUBLE_INGOT, 3)).isEqualTo(new PileLayout.Placement(0, 0, 0.5, true));
        assertThat(PileLayout.of(PileKind.DOUBLE_INGOT, 5)).isEqualTo(new PileLayout.Placement(0.66, 0, 0.5, true));
        PileLayout.Placement second = PileLayout.of(PileKind.DOUBLE_INGOT, 6);
        assertThat(second.y()).isEqualTo(1.0 / 6);
        assertThat(second.rotated()).isFalse();
        assertThat(PileLayout.of(PileKind.DOUBLE_INGOT, 35).y()).isCloseTo(5.0 / 6, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void aLayerPlacementIsTheLayersFirstIngotOffsetsWithoutTheRows() {
        assertThat(PileLayout.ofLayer(PileKind.INGOT, 1)).isEqualTo(new PileLayout.Placement(0, 0, 0, true));
        assertThat(PileLayout.ofLayer(PileKind.INGOT, 8)).isEqualTo(new PileLayout.Placement(0, 0.875, 0, false));
        assertThatThrownBy(() -> PileLayout.ofLayer(PileKind.INGOT, 9)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PileLayout.of(PileKind.DOUBLE_INGOT, 36)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theMetalIsReadFromTheIngotItemIds() {
        assertThat(PileVisual.metalOf("tfp:metal/ingot/copper")).isEqualTo("copper");
        assertThat(PileVisual.metalOf("tfp:metal/double_ingot/bismuth_bronze")).isEqualTo("bismuth_bronze");
        assertThat(PileVisual.metalOf("minecraft:iron_ingot")).isEqualTo(PileVisual.UNKNOWN);
        assertThat(PileVisual.metalOf("tfp:metal/sheet/copper")).isEqualTo(PileVisual.UNKNOWN);
        assertThat(PileVisual.metalOf(null)).isEqualTo(PileVisual.UNKNOWN);
    }

    @Test
    void aFullLayerOfOneMetalIsOneDisplayAndTheRestAreOnePerIngot() {
        List<String> metals = new java.util.ArrayList<>(Collections.nCopies(8, "copper"));
        metals.addAll(List.of("copper", "bronze", "copper"));
        List<PileVisual.Part> parts = PileVisual.plan(PileKind.INGOT, metals);
        assertThat(parts).hasSize(1 + 3);
        assertThat(parts.get(0).variant()).isEqualTo(Variant.LAYER);
        assertThat(parts.get(0).at()).isEqualTo(PileLayout.ofLayer(PileKind.INGOT, 1));
        assertThat(parts.get(1).variant()).isEqualTo(Variant.SINGLE);
        assertThat(parts.get(1).signature()).isEqualTo("S:8:copper");
        assertThat(parts.get(2).metal()).isEqualTo("bronze");
        assertThat(parts.get(3).at()).isEqualTo(PileLayout.of(PileKind.INGOT, 10));
    }

    @Test
    void aMixedFullLayerAndAPartialLayerOfOneMetalAreDrawnPerIngot() {
        List<String> mixed = List.of("a", "a", "a", "a", "a", "a", "a", "b");
        assertThat(PileVisual.plan(PileKind.INGOT, mixed)).hasSize(8).allMatch(p -> p.variant() == Variant.SINGLE);
        assertThat(PileVisual.plan(PileKind.INGOT, Collections.nCopies(7, "a"))).hasSize(7);
    }

    @Test
    void aFullPileOfOneMetalNeedsOnlyEightDisplaysAndMixedAtMostSixtyFour() {
        assertThat(PileVisual.plan(PileKind.INGOT, Collections.nCopies(64, "tin"))).hasSize(8);
        assertThat(PileVisual.plan(PileKind.DOUBLE_INGOT, Collections.nCopies(36, "tin"))).hasSize(6);
        List<String> mixed = new java.util.ArrayList<>();
        for (int i = 0; i < 64; i++) {
            mixed.add(i % 2 == 0 ? "copper" : "tin");
        }
        assertThat(PileVisual.plan(PileKind.INGOT, mixed)).hasSize(64);
    }

    @Test
    void signaturesAreUniquePerPart() {
        List<String> mixed = new java.util.ArrayList<>();
        for (int i = 0; i < 64; i++) {
            mixed.add(i % 3 == 0 ? "copper" : "tin");
        }
        assertThat(PileVisual.plan(PileKind.INGOT, mixed).stream().map(PileVisual.Part::signature).distinct().count())
            .isEqualTo(64);
    }

    @Test
    void anEmptyOrOversizedPileIsRejected() {
        assertThatThrownBy(() -> PileVisual.plan(PileKind.INGOT, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PileVisual.plan(PileKind.DOUBLE_INGOT, Collections.nCopies(37, "a")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
