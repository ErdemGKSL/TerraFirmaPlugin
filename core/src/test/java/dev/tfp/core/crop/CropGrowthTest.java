/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/crop/CropHelpers.java @ c3b4d9536 (growthTick, growthTickStep)
 */
package dev.tfp.core.crop;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.farm.NutrientGrowth;
import dev.tfp.core.farm.Nutrients;
import org.junit.jupiter.api.Test;

/** The crop catch-up with the soil: nutrient use, the order of the steps, lazy equals split, death. */
class CropGrowthTest {
    private static final long DAY = CalendarTicks.CALENDAR_TICKS_IN_DAY;
    private static final long START = 5 * DAY;
    private static final CropRules.ClimateSource GOOD = t -> new CropRules.Sample(50, 10f);
    private static final CropRules.ClimateSource DRY = t -> new CropRules.Sample(0, 10f);
    private static final Nutrients RICH = new Nutrients(1f, 1f, 1f);

    private static CropGrowth.Result run(CropType type, CropPlant plant, Nutrients soil, CropRules.ClimateSource climate, long days) {
        return CropGrowth.advance(type, plant, soil, 1f, type.spec(1f, 1f, 1f), climate, CropRules.Jitter.seeded(9L), START + days * DAY);
    }

    @Test
    void withoutFarmlandItEqualsTheBareRules() {
        for (CropType type : CropType.values()) {
            CropRules.NutrientFeed feed = (from, to) -> {
                NutrientGrowth.Consumption c = NutrientGrowth.consumeForGrowthStep(Nutrients.EMPTY,
                    new NutrientGrowth.Demand(type.nitrogen(), type.phosphorus(), type.potassium()),
                    new NutrientGrowth.Demand(0f, 0f, 0f), to - from);
                return new CropRules.NutrientStep(0f, c.forGrowth(), c.required(), 0f);
            };
            CropRules.Progress bare = CropRules.advance(CropState.planted(START), type.spec(1f, 1f, 1f), GOOD, feed,
                CropRules.Jitter.seeded(9L), START + 30 * DAY);
            CropGrowth.Result got = run(type, CropPlant.planted(START), null, GOOD, 30);
            if (bare instanceof CropRules.Alive alive) {
                assertThat(got.dead()).as(type.name()).isFalse();
                assertThat(got.plant().state()).as(type.name()).isEqualTo(alive.state());
            } else {
                assertThat(got.dead()).as(type.name()).isTrue();
                assertThat(got.diedAt()).isEqualTo(((CropRules.Dead) bare).diedAt());
            }
        }
    }

    @Test
    void richSoilGrowsFasterAndIsConsumed() {
        CropGrowth.Result poor = run(CropType.WHEAT, CropPlant.planted(START), Nutrients.EMPTY, GOOD, 12);
        CropGrowth.Result rich = run(CropType.WHEAT, CropPlant.planted(START), RICH, GOOD, 12);
        assertThat(rich.plant().state().growth()).isGreaterThan(poor.plant().state().growth());
        assertThat(rich.plant().state().yield()).isGreaterThan(poor.plant().state().yield());
        assertThat(rich.soil().nitrogen()).isLessThan(1f);
        assertThat(rich.plant().absorbed().nitrogen()).isGreaterThan(0f);
        // wheat gives phosphorus and potassium back to the soil only above 0 growth; they are consumed as well
        assertThat(rich.soil().phosphorus()).isLessThanOrEqualTo(1f);
    }

    @Test
    void wheatInGoodClimateIsFullyGrownWithinTheSeason() {
        CropGrowth.Result r = run(CropType.WHEAT, CropPlant.planted(START), RICH, GOOD, 40);
        assertThat(r.dead()).isFalse();
        assertThat(r.plant().state().growth()).isEqualTo(1f);
        assertThat(CropType.WHEAT.stages().stage(r.plant().state().growth()).age()).isEqualTo(7);
    }

    @Test
    void aDryFieldKillsTheCropAtTheEndOfTheFirstStep() {
        CropGrowth.Result r = run(CropType.WHEAT, CropPlant.planted(START), RICH, DRY, 10);
        assertThat(r.dead()).isTrue();
        assertThat(r.plant()).isNull();
        assertThat(r.diedAt()).isEqualTo(START + CropRules.UPDATE_INTERVAL);
        assertThat(r.fullyGrown()).isFalse();
    }

    @Test
    void aCoverCropGivesNutrientsBack() {
        Nutrients start = new Nutrients(0.1f, 0.1f, 0.1f);
        CropGrowth.Result r = run(CropType.ALFALFA, CropPlant.planted(START), start, t -> new CropRules.Sample(50, 0f), 20);
        assertThat(r.dead()).isFalse();
        assertThat(r.soil().nitrogen()).isGreaterThan(0.1f);
    }

    @Test
    void splitAtAnIntervalEqualsOneCallIncludingTheSoil() {
        CropRules.Spec spec = CropType.POTATO.spec(1f, 1f, 1f);
        CropGrowth.Result whole = CropGrowth.advance(CropType.POTATO, CropPlant.planted(START), RICH, 1f, spec, GOOD,
            CropRules.Jitter.seeded(3L), START + 30 * DAY);
        CropGrowth.Result first = CropGrowth.advance(CropType.POTATO, CropPlant.planted(START), RICH, 1f, spec, GOOD,
            CropRules.Jitter.seeded(3L), START + 8 * CropRules.UPDATE_INTERVAL);
        CropGrowth.Result second = CropGrowth.advance(CropType.POTATO, first.plant(), first.soil(), 1f, spec, GOOD,
            CropRules.Jitter.seeded(3L), START + 30 * DAY);
        assertThat(second).isEqualTo(whole);
    }

    @Test
    void aCallAtOrBeforeTheLastTickChangesNothing() {
        CropPlant plant = CropPlant.planted(START);
        CropGrowth.Result r = CropGrowth.advance(CropType.WHEAT, plant, RICH, 1f, CropType.WHEAT.spec(1f, 1f, 1f), GOOD,
            CropRules.Jitter.MIDPOINT, START);
        assertThat(r.plant()).isEqualTo(plant);
        assertThat(r.soil()).isEqualTo(RICH);
    }

    @Test
    void darknessTurnsGrowthIntoExpiryAndEventuallyDeath() {
        CropRules.Spec dark = CropType.WHEAT.spec(1f, 1f, 0f);
        CropGrowth.Result r = CropGrowth.advance(CropType.WHEAT, CropPlant.planted(START), RICH, 1f, dark, GOOD,
            CropRules.Jitter.MIDPOINT, START + 120 * DAY);
        assertThat(r.dead()).isTrue();
        assertThat(r.fullyGrown()).isFalse();
    }

    @Test
    void aPickKeepsExpiryAndResetsYield() {
        CropPlant p = new CropPlant(new CropState(1f, 0.3f, 0.8f, START), new NutrientGrowth.Demand(0.1f, 0.2f, 0.3f));
        CropPlant picked = p.picked(0.55f);
        assertThat(picked.state()).isEqualTo(new CropState(0.55f, 0.3f, 0f, START));
        assertThat(picked.absorbed()).isEqualTo(p.absorbed());
    }

    @Test
    void growthLimitPerShape() {
        assertThat(CropGrowth.growthLimit(CropType.WHEAT, true, false, false, false)).isEqualTo(1f);
        assertThat(CropGrowth.growthLimit(CropType.WHEAT, false, false, true, false)).isEqualTo(0f);
        assertThat(CropGrowth.growthLimit(CropType.RICE, true, false, true, false)).isEqualTo(0f);
        assertThat(CropGrowth.growthLimit(CropType.RICE, true, true, true, false)).isEqualTo(1f);
        // the limit of a two-block crop is maxSingleAge / maxAge
        assertThat(CropGrowth.growthLimit(CropType.JUTE, true, false, false, false)).isEqualTo(2f / 5f);
        assertThat(CropGrowth.growthLimit(CropType.JUTE, true, false, true, false)).isEqualTo(1f);
        assertThat(CropGrowth.growthLimit(CropType.TOMATO, true, false, true, false)).isEqualTo(3f / 7f);
        assertThat(CropGrowth.growthLimit(CropType.TOMATO, true, false, true, true)).isEqualTo(1f);
        assertThat(CropGrowth.growthLimit(CropType.TOMATO, false, false, true, true)).isEqualTo(0f);
    }

    @Test
    void pickableCropsHaveFruits() {
        assertThat(CropHarvest.fruits(CropType.TOMATO)).isEqualTo(new CropHarvest.Fruits(null, "tomato"));
        assertThat(CropHarvest.fruits(CropType.RED_BELL_PEPPER).first()).isEqualTo("green_bell_pepper");
        assertThat(CropHarvest.fruits(CropType.WHEAT)).isNull();
    }
}
