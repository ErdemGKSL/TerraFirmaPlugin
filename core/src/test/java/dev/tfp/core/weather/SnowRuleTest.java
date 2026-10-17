/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.weather;

import static dev.tfp.core.weather.SnowRule.Cover;
import static dev.tfp.core.weather.SnowRule.SnowChange;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Golden values for {@link SnowRule#decide}, from TFC's {@code WeatherHelpers.onTickChunk} and the placement and removal
 * helpers it calls. TFC has no unit tests for these. The thresholds are the strict comparisons in lines 248, 252, 283,
 * 285 and 293 of {@code WeatherHelpers}: accumulation below -2, melting above 2. Boundaries use the float neighbours of
 * each threshold.
 */
class SnowRuleTest {
    private static final float COLD = -10f;
    private static final float WARM = 10f;
    private static final PrecipitationType SNOWING = PrecipitationType.SNOW;
    private static final PrecipitationType DRY = PrecipitationType.NONE;

    /** A column that accumulates when nothing blocks it: cold, snowing, sky visible, ground top. */
    private static SnowRule.Column coldGround() {
        return column(Cover.GROUND, 0, false, false, true, COLD, SNOWING);
    }

    private static SnowRule.Column column(Cover top, int layers, boolean supported, boolean waterEdge, boolean sky,
            float temperature, PrecipitationType precipitation) {
        return new SnowRule.Column(top, layers, supported, waterEdge, sky, temperature, precipitation);
    }

    @Test
    void freezingThresholdIsStrictlyBelowMinusTwo() {
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, false, true, -2f, SNOWING))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, false, true, Math.nextUp(-2f), SNOWING))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, false, true, Math.nextDown(-2f), SNOWING)))
                .isEqualTo(SnowChange.ADD_SNOW_LAYER);
    }

    @Test
    void meltingThresholdIsStrictlyAboveTwo() {
        assertThat(SnowRule.decide(column(Cover.SNOW, 3, false, false, true, 2f, DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.SNOW, 3, false, false, true, Math.nextDown(2f), DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.SNOW, 3, false, false, true, Math.nextUp(2f), DRY)))
                .isEqualTo(SnowChange.REMOVE_SNOW_LAYER);
    }

    @Test
    void betweenTheThresholdsNothingChanges() {
        float[] temps = {Math.nextUp(-2f), -1f, 0f, -0f, 1f, Math.nextDown(2f)};
        for (float t : temps) {
            for (Cover top : Cover.values()) {
                int layers = top == Cover.SNOW ? 4 : 0;
                for (PrecipitationType p : PrecipitationType.values()) {
                    assertThat(SnowRule.decide(column(top, layers, true, true, true, t, p)))
                            .as("top %s at %s, %s", top, t, p)
                            .isEqualTo(SnowChange.NONE);
                }
            }
        }
    }

    @Test
    void noPrecipitationNoAccumulation() {
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, false, true, COLD, DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.WATER, 0, false, true, true, COLD, DRY))).isEqualTo(SnowChange.NONE);
    }

    @Test
    void snowLayerGoesOnGroundWithSky() {
        assertThat(SnowRule.decide(coldGround())).isEqualTo(SnowChange.ADD_SNOW_LAYER);
        assertThat(SnowRule.decide(coldGround()).placedAboveTop()).isTrue();
    }

    @Test
    void noSkyNoSnowLayer() {
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, false, false, COLD, SNOWING))).isEqualTo(SnowChange.NONE);
    }

    @Test
    void fullSnowTopCarriesALayerButPartialSnowDoesNot() {
        // Vanilla SnowLayerBlock.canSurvive: a layer rests on an eight-layer snow block, not on a shorter one
        assertThat(SnowRule.decide(column(Cover.SNOW, 8, false, false, true, COLD, SNOWING))).isEqualTo(SnowChange.ADD_SNOW_LAYER);
        for (int layers = 1; layers <= 7; layers++) {
            assertThat(SnowRule.decide(column(Cover.SNOW, layers, false, false, true, COLD, SNOWING)))
                    .as("layers %d", layers)
                    .isEqualTo(SnowChange.NONE);
        }
    }

    @Test
    void notRestingTopsGetNoSnowLayer() {
        assertThat(SnowRule.decide(column(Cover.ICE, 0, false, false, true, COLD, SNOWING))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.OTHER, 0, false, false, true, COLD, SNOWING))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.AIR, 0, false, false, true, COLD, SNOWING))).isEqualTo(SnowChange.NONE);
    }

    @Test
    void waterFreezesWhenANeighbourIsNotWater() {
        assertThat(SnowRule.decide(column(Cover.WATER, 0, false, true, true, COLD, SNOWING))).isEqualTo(SnowChange.FREEZE_WATER);
        assertThat(SnowRule.decide(column(Cover.WATER, 0, false, false, true, COLD, SNOWING))).isEqualTo(SnowChange.NONE);
    }

    @Test
    void waterFreezesUnderAnOverhangWithoutSky() {
        // TFC's ice path is not behind the sky gate: only snow placement checks canSeeSky
        assertThat(SnowRule.decide(column(Cover.WATER, 0, false, true, false, COLD, SNOWING))).isEqualTo(SnowChange.FREEZE_WATER);
        assertThat(SnowRule.decide(column(Cover.WATER, 0, false, true, false, COLD, SNOWING)).placedAboveTop()).isFalse();
    }

    @Test
    void groundTopIsNeverFrozenEvenWithAWaterEdge() {
        // Ice only forms on water: a ground top with a water-side neighbour gets snow (TFC tries the snow placement first)
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, false, true, true, COLD, SNOWING))).isEqualTo(SnowChange.ADD_SNOW_LAYER);
    }

    @Test
    void meltingRemovesOneSnowLayerAtEveryCount() {
        for (int layers = 1; layers <= 8; layers++) {
            assertThat(SnowRule.decide(column(Cover.SNOW, layers, false, false, true, WARM, DRY)))
                    .as("layers %d", layers)
                    .isEqualTo(SnowChange.REMOVE_SNOW_LAYER);
        }
    }

    @Test
    void meltingIgnoresSkyAndPrecipitation() {
        assertThat(SnowRule.decide(column(Cover.SNOW, 2, true, true, false, WARM, SNOWING))).isEqualTo(SnowChange.REMOVE_SNOW_LAYER);
    }

    @Test
    void layersAfterRemoveCountsDownToZero() {
        assertThat(SnowRule.layersAfterRemove(8)).isEqualTo(7);
        assertThat(SnowRule.layersAfterRemove(2)).isEqualTo(1);
        // A single layer goes to air (TFC removeBlock), which is 0 here
        assertThat(SnowRule.layersAfterRemove(1)).isZero();
        assertThatThrownBy(() -> SnowRule.layersAfterRemove(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SnowRule.layersAfterRemove(9)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void iceMeltsToWaterOnlyWhenHeldFromBelow() {
        assertThat(SnowRule.decide(column(Cover.ICE, 0, true, false, true, WARM, DRY))).isEqualTo(SnowChange.MELT_ICE_TO_WATER);
        assertThat(SnowRule.decide(column(Cover.ICE, 0, false, false, true, WARM, DRY))).isEqualTo(SnowChange.MELT_ICE_TO_AIR);
    }

    @Test
    void nothingMeltsWhenTheTopCannotMelt() {
        assertThat(SnowRule.decide(column(Cover.WATER, 0, true, true, true, WARM, DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.GROUND, 0, true, true, true, WARM, DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.OTHER, 0, true, true, true, WARM, DRY))).isEqualTo(SnowChange.NONE);
        assertThat(SnowRule.decide(column(Cover.AIR, 0, true, true, true, WARM, DRY))).isEqualTo(SnowChange.NONE);
    }

    @Test
    void placedAboveTopOnlyForAddedLayers() {
        for (SnowChange change : SnowChange.values()) {
            assertThat(change.placedAboveTop()).isEqualTo(change == SnowChange.ADD_SNOW_LAYER);
        }
    }

    @Test
    void invalidColumnsAreRejected() {
        assertThatThrownBy(() -> column(Cover.SNOW, 0, false, false, true, COLD, SNOWING)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> column(Cover.SNOW, 9, false, false, true, COLD, SNOWING)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> column(Cover.GROUND, 3, false, false, true, COLD, SNOWING)).isInstanceOf(IllegalArgumentException.class);
    }
}
