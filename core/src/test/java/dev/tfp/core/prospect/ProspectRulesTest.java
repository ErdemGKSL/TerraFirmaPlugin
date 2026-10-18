/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prospect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProspectRulesTest {
    private static final String COPPER = "tfc:ore/normal_copper/granite";
    private static final String COPPER_POOR = "tfc:ore/poor_copper/granite";
    private static final String COPPER_RICH = "tfc:ore/rich_copper/granite";
    private static final String GYPSUM = "tfc:ore/gypsum/granite";
    private static final String CLICK = "tfc:stone/granite";

    private static List<ScanFact> copies(int count, String block) {
        List<ScanFact> out = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new ScanFact(i % 25 - 12, (i / 25) % 25 - 12, (i / 625) % 25 - 12, block));
        }
        return out;
    }

    @Test
    void ratingBoundariesAreTfcs() {
        assertThat(ProspectRules.rate(1)).isEqualTo(ProspectResult.TRACES);
        assertThat(ProspectRules.rate(9)).isEqualTo(ProspectResult.TRACES);
        assertThat(ProspectRules.rate(10)).isEqualTo(ProspectResult.SMALL);
        assertThat(ProspectRules.rate(19)).isEqualTo(ProspectResult.SMALL);
        assertThat(ProspectRules.rate(20)).isEqualTo(ProspectResult.MEDIUM);
        assertThat(ProspectRules.rate(39)).isEqualTo(ProspectResult.MEDIUM);
        assertThat(ProspectRules.rate(40)).isEqualTo(ProspectResult.LARGE);
        assertThat(ProspectRules.rate(79)).isEqualTo(ProspectResult.LARGE);
        assertThat(ProspectRules.rate(80)).isEqualTo(ProspectResult.VERY_LARGE);
    }

    @Test
    void resultOrdinalsMatchTfcPacketOrder() {
        assertThat(ProspectResult.values()).containsExactly(
            ProspectResult.VERY_LARGE, ProspectResult.LARGE, ProspectResult.MEDIUM, ProspectResult.SMALL,
            ProspectResult.TRACES, ProspectResult.FOUND, ProspectResult.NOTHING);
    }

    @Test
    void falseNegativeChanceClampsToTheToolLevelRange() {
        assertThat(ProspectRules.falseNegativeChance(-3)).isEqualTo(ProspectRules.falseNegativeChance(0));
        assertThat(ProspectRules.falseNegativeChance(0)).isEqualTo(0.3f);
        assertThat(ProspectRules.falseNegativeChance(5)).isEqualTo(0f);
        assertThat(ProspectRules.falseNegativeChance(9)).isEqualTo(0f);
    }

    @Test
    void representativeGroupsGradesOnlyForGradedOres() {
        assertThat(ProspectRules.representative(COPPER_POOR)).isEqualTo(COPPER);
        assertThat(ProspectRules.representative(COPPER_RICH)).isEqualTo(COPPER);
        assertThat(ProspectRules.representative(COPPER)).isEqualTo(COPPER);
        assertThat(ProspectRules.representative(GYPSUM)).isEqualTo(GYPSUM);
        assertThat(ProspectRules.representative("tfc:halite")).isEqualTo("tfc:halite");
        assertThat(ProspectRules.representative("tfc:ore/poor_native_copper/marble"))
            .isEqualTo("tfc:ore/normal_native_copper/marble");
    }

    @Test
    void directHitIsFoundWithoutAScan() {
        ProspectOutcome out = ProspectRules.decide(new ProspectRequest(0, 64, 0, COPPER, true, 0, List.of()));
        assertThat(out).isEqualTo(new ProspectOutcome(ProspectResult.FOUND, COPPER));
    }

    @Test
    void emptyCubeIsNothing() {
        ProspectOutcome out = ProspectRules.decide(new ProspectRequest(0, 64, 0, CLICK, false, 5, List.of()));
        assertThat(out).isEqualTo(new ProspectOutcome(ProspectResult.NOTHING, CLICK));
    }

    @Test
    void factsOutsideTheRadiusAreIgnored() {
        List<ScanFact> cube = List.of(new ScanFact(13, 0, 0, COPPER), new ScanFact(0, -13, 0, COPPER), new ScanFact(0, 0, 13, COPPER));
        ProspectOutcome out = ProspectRules.decide(new ProspectRequest(0, 64, 0, CLICK, false, 5, cube));
        assertThat(out.result()).isEqualTo(ProspectResult.NOTHING);
    }

    @Test
    void factsOnTheRadiusBoundAreCounted() {
        List<ScanFact> cube = List.of(new ScanFact(12, -12, 12, COPPER));
        ProspectOutcome out = ProspectRules.decide(new ProspectRequest(0, 64, 0, CLICK, false, 5, cube));
        assertThat(out).isEqualTo(new ProspectOutcome(ProspectResult.TRACES, COPPER));
    }

    @Test
    void poorAndRichOresCountWithTheNormalRepresentative() {
        List<ScanFact> cube = new java.util.ArrayList<>(copies(5, COPPER));
        cube.addAll(copies(6, COPPER_POOR));
        cube.addAll(copies(3, COPPER_RICH));
        ProspectOutcome out = ProspectRules.decide(new ProspectRequest(0, 64, 0, CLICK, false, 5, cube));
        assertThat(out).isEqualTo(new ProspectOutcome(ProspectResult.SMALL, COPPER));
    }

    @Test
    void theChosenRepresentativeDoesNotDependOnFactOrder() {
        List<ScanFact> forward = new java.util.ArrayList<>(copies(12, COPPER));
        forward.addAll(copies(12, GYPSUM));
        List<ScanFact> backward = new java.util.ArrayList<>(forward);
        java.util.Collections.reverse(backward);
        ProspectRequest a = new ProspectRequest(7, 70, -3, CLICK, false, 2, forward);
        ProspectRequest b = new ProspectRequest(7, 70, -3, CLICK, false, 2, backward);
        assertThat(ProspectRules.decide(a)).isEqualTo(ProspectRules.decide(b));
    }

    @Test
    void decisionIsDeterministicForAPosition() {
        List<ScanFact> cube = copies(30, COPPER);
        ProspectRequest request = new ProspectRequest(1, 70, -3, CLICK, false, 1, cube);
        assertThat(ProspectRules.decide(request)).isEqualTo(ProspectRules.decide(request));
    }

    @Test
    void inRadiusIsAClosedCube() {
        assertThat(ProspectRules.inRadius(12, -12, 0)).isTrue();
        assertThat(ProspectRules.inRadius(13, 0, 0)).isFalse();
    }

    @Test
    void requestRejectsNullInputs() {
        assertThatThrownBy(() -> new ProspectRequest(0, 0, 0, null, false, 0, List.of()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanFact(0, 0, 0, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
