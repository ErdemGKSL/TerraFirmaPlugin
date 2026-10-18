/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.food;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** M3-SURV-01: TFC's creation-date rounding and food stacking rule (default window 7200 ticks). */
class FoodStackingTest {
    private static final FoodConfig CFG = FoodConfig.DEFAULT;
    private static final float BEEF = 2.0f;

    @Test
    void foodsCreatedInTheSameWindowGetTheSameDateAndStack() {
        // TFC rounds up to the next multiple of 7200: 7201..14400 -> 14400
        FoodState a = FoodState.createdAt(7_201, CFG);
        FoodState b = FoodState.createdAt(14_400, CFG);
        assertThat(a).isEqualTo(b);
        assertThat(a.creationDate()).isEqualTo(14_400);
        assertThat(a.stacksWith(b, BEEF, 14_400, CFG)).isTrue();

        FoodState c = FoodState.createdAt(1_234_000, CFG);
        FoodState d = FoodState.createdAt(1_238_400, CFG);
        assertThat(c.creationDate()).isEqualTo(d.creationDate()).isEqualTo(1_238_400);
    }

    @Test
    void adjacentWindowsStillStackWithinTheToleranceButNotFurther() {
        // 7200 and 7201 round to 7200 and 14400: 7200 apart, inside TFC's |a - b| <= foodDecayStackTicks
        FoodState a = FoodState.createdAt(7_200, CFG);
        FoodState b = FoodState.createdAt(7_201, CFG);
        assertThat(a).isNotEqualTo(b);
        assertThat(a.stacksWith(b, BEEF, 20_000, CFG)).isTrue();
        // 7200 and 21600 are two windows apart
        FoodState c = FoodState.createdAt(14_401, CFG);
        assertThat(a.stacksWith(c, BEEF, 20_000, CFG)).isFalse();
        assertThat(FoodDecay.creationDatesStack(1_000, 8_200, 7_200)).isTrue();
        assertThat(FoodDecay.creationDatesStack(1_000, 8_201, 7_200)).isFalse();
    }

    @Test
    void mergingKeepsTheEarlierCreationDate() {
        FoodState a = new FoodState(14_400, List.of());
        FoodState b = new FoodState(7_200, List.of());
        assertThat(a.mergedWith(b).creationDate()).isEqualTo(7_200);
        assertThat(b.mergedWith(a).creationDate()).isEqualTo(7_200);
    }

    @Test
    void differentTraitsNeverStack() {
        FoodState plain = new FoodState(14_400, List.of());
        FoodState salted = new FoodState(14_400, List.of(FoodTrait.SALTED));
        assertThat(plain.stacksWith(salted, BEEF, 14_400, CFG)).isFalse();
        // order matters, as in TFC's List.equals
        FoodState sp = new FoodState(14_400, List.of(FoodTrait.SALTED, FoodTrait.PRESERVED));
        FoodState ps = new FoodState(14_400, List.of(FoodTrait.PRESERVED, FoodTrait.SALTED));
        assertThat(sp.stacksWith(ps, BEEF, 14_400, CFG)).isFalse();
    }

    @Test
    void flagsOnlyStackWithTheSameFlag() {
        FoodState never = new FoodState(FoodDecay.NEVER_DECAY_FLAG, List.of());
        FoodState invisible = new FoodState(FoodDecay.INVISIBLE_NEVER_DECAY_FLAG, List.of());
        FoodState dated = new FoodState(0, List.of());
        assertThat(never.stacksWith(never, BEEF, 0, CFG)).isTrue();
        assertThat(never.stacksWith(invisible, BEEF, 0, CFG)).isFalse();
        assertThat(never.stacksWith(dated, BEEF, 0, CFG)).isFalse();
    }

    @Test
    void foodsThatHaveBothRottenStackThroughTheRottenFlag() {
        // beef lives 264 000 ticks; created a window apart, both rotten later -> both sanitize to ROTTEN_FLAG
        FoodState a = new FoodState(7_200, List.of());
        FoodState b = new FoodState(100_800, List.of());
        long fresh = 200_000;
        assertThat(a.stacksWith(b, BEEF, fresh, CFG)).isFalse();
        long later = 400_000;
        assertThat(a.sanitize(BEEF, later, CFG).creationDate()).isEqualTo(FoodDecay.ROTTEN_FLAG);
        assertThat(a.stacksWith(b, BEEF, later, CFG)).isTrue();
        // a fresh and a rotten food do not stack
        FoodState c = new FoodState(394_000, List.of());
        assertThat(a.stacksWith(c, BEEF, later, CFG)).isFalse();
    }
}
