/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.plantable;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.banana.BananaRules;
import dev.tfp.core.berry.BushSpecies;
import dev.tfp.core.berry.FruitTree;
import dev.tfp.core.berry.Lifecycle;
import dev.tfp.core.berry.SpreadingSpecies;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.climate.ClimateTemperatureStyle;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.plantable.PlantLines.Kind;
import dev.tfp.core.plantable.PlantLines.Line;
import dev.tfp.core.worldgen.tree.Wood;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** PAR-FARM-13: the plant info and fertilizer line model against TFC's {@code PlantableInfo} (values read from the pinned sources). */
class PlantLinesTest {
    private static List<Line> lines(String id) {
        return PlantLines.plantable(PlantInfos.of(id).orElseThrow(), ClimateTemperatureStyle.CELSIUS);
    }

    private static List<Kind> kinds(List<Line> lines) {
        return lines.stream().map(Line::kind).toList();
    }

    @Test
    void everyPlantableItemIsListed() {
        // 29 seeds, 20 wood saplings, 8 fruit tree saplings, banana, 7 stationary bushes (cranberry included), 4 spreading bushes
        assertThat(PlantInfos.all()).hasSize(29 + 20 + 8 + 1 + 7 + 4);
    }

    @Test
    void barleySeedsShowClimateAndNutrients() {
        List<Line> l = lines("tfp:seeds/barley");
        assertThat(kinds(l)).containsExactly(Kind.CLIMATE_HEADER, Kind.TEMPERATURE_RANGE, Kind.HYDRATION_RANGE, Kind.BLANK,
            Kind.NUTRIENTS_HEADER, Kind.NITROGEN, Kind.PHOSPHORUS, Kind.POTASSIUM);
        assertThat(l.get(1).a()).isEqualTo("-7.0°C");
        assertThat(l.get(1).b()).isEqualTo("23.0°C");
        assertThat(l.get(2).a()).isEqualTo("10");
        assertThat(l.get(2).b()).isEqualTo("70");
        // barley takes 0.75 nitrogen and gives phosphorus and potassium back (0.2 each): "+" and 100 percent of the value
        assertThat(l.get(5).a()).isEqualTo("75");
        assertThat(l.get(6).a()).isEqualTo("+20");
        assertThat(l.get(7).a()).isEqualTo("+20");
    }

    @Test
    void fahrenheitStyleChangesTheUnit() {
        List<Line> l = PlantLines.plantable(PlantInfos.of("tfp:seeds/barley").orElseThrow(), ClimateTemperatureStyle.FAHRENHEIT);
        assertThat(l.get(1).a()).isEqualTo("19.4°F");
        assertThat(l.get(1).b()).isEqualTo("73.4°F");
    }

    @Test
    void aSoilRestoringCropThatTakesNothingShowsThirtyPercent() {
        assertThat(PlantLines.nutrientAmount(-0.8f, false)).isEqualTo("+24");
        assertThat(PlantLines.nutrientAmount(-0.8f, true)).isEqualTo("+80");
        assertThat(PlantLines.nutrientAmount(0f, true)).isEqualTo("0");
    }

    @Test
    void soybeansTakePhosphorusAndPotassiumAndGiveNitrogen() {
        List<Line> l = lines("tfp:seeds/soybean");
        assertThat(l.get(5).a()).isEqualTo("+80");
        assertThat(l.get(6).a()).isEqualTo("60");
        assertThat(l.get(7).a()).isEqualTo("30");
    }

    @Test
    void woodSaplingsShowOnlyTheGrowthTime() {
        List<Line> l = lines("tfp:wood/sapling/oak");
        assertThat(kinds(l)).containsExactly(Kind.LIFECYCLE_HEADER, Kind.GROWTH_SPEED);
        assertThat(l.get(1).ticks()).isEqualTo(240_000L);
        assertThat(lines("tfp:wood/sapling/sequoia").get(1).ticks()).isEqualTo(432_000L);
    }

    @Test
    void fruitTreeSaplingsShowClimateGrowthAndMonths() {
        List<Line> l = lines("tfp:plant/cherry_sapling");
        assertThat(kinds(l)).containsExactly(Kind.CLIMATE_HEADER, Kind.TEMPERATURE_RANGE, Kind.HYDRATION_RANGE, Kind.BLANK,
            Kind.LIFECYCLE_HEADER, Kind.GROWTH_SPEED, Kind.CAN_PLANT, Kind.STARTS_FRUITING);
        assertThat(l.get(5).ticks()).isEqualTo(8L * 24_000);
        // cherry: HEALTHY in December, January, February, March? the table is H H H F F Fr D D D D D H
        assertThat(l.get(6).month()).isEqualTo(Month.DECEMBER);
        assertThat(l.get(7).month()).isEqualTo(Month.JUNE);
    }

    @Test
    void theJanuaryEntryIsSkippedLikeTfcsLoop() {
        // strawberry: F F Fr D D D D D D H H H. TFC's loop stops at index 1, so January is never looked at
        List<Line> l = lines("tfp:berry_bush/strawberry");
        assertThat(l.stream().filter(x -> x.kind() == Kind.CAN_PLANT).findFirst().orElseThrow().month()).isEqualTo(Month.OCTOBER);
        assertThat(l.stream().filter(x -> x.kind() == Kind.STARTS_FRUITING).findFirst().orElseThrow().month()).isEqualTo(Month.MARCH);
        assertThat(kinds(l)).doesNotContain(Kind.GROWTH_SPEED, Kind.NUTRIENTS_HEADER);
    }

    @Test
    void bananaAndBushesAreCovered() {
        assertThat(PlantInfos.of(PlantInfos.BANANA_SAPLING).orElseThrow().growthTicks()).isEqualTo((int) BananaRules.SAPLING_GROWTH_TICKS);
        for (BushSpecies b : BushSpecies.values()) {
            assertThat(PlantInfos.of(PlantInfos.bushId(b))).as(b.name()).isPresent();
        }
        for (SpreadingSpecies b : SpreadingSpecies.values()) {
            assertThat(PlantInfos.of(PlantInfos.spreadingBushId(b))).as(b.name()).isPresent();
        }
        for (FruitTree t : FruitTree.values()) {
            assertThat(PlantInfos.of(PlantInfos.fruitSaplingId(t))).as(t.name()).isPresent();
        }
        for (CropType c : CropType.values()) {
            assertThat(PlantInfos.of(PlantInfos.seedId(c))).as(c.name()).isPresent();
        }
        assertThat(PlantInfos.of("tfp:plant/allium")).isEmpty();
    }

    @Test
    void startingMonthFollowsTheRisingEdge() {
        // two healthy months in a row: the earlier one wins; a gap keeps the first found
        Month found = PlantLines.startingMonth(Lifecycle.HEALTHY, Lifecycle.HEALTHY, null, Month.MARCH);
        assertThat(found).isEqualTo(Month.MARCH);
        assertThat(PlantLines.startingMonth(Lifecycle.HEALTHY, Lifecycle.HEALTHY, found, Month.FEBRUARY)).isEqualTo(Month.FEBRUARY);
        assertThat(PlantLines.startingMonth(Lifecycle.HEALTHY, Lifecycle.HEALTHY, found, Month.JANUARY)).isEqualTo(Month.MARCH);
        assertThat(PlantLines.startingMonth(Lifecycle.HEALTHY, Lifecycle.DORMANT, found, Month.FEBRUARY)).isEqualTo(Month.MARCH);
    }

    @Test
    void fertilizerLinesShowTheNonZeroNutrientsWithOneDecimal() {
        assertThat(PlantLines.fertilizer(0.4f, 0.5f, 0.1f).stream().map(Line::a).toList()).containsExactly("40.0", "50.0", "10.0");
        List<Line> compost = PlantLines.fertilizer(0.2f, 0f, 0.2f);
        assertThat(kinds(compost)).containsExactly(Kind.NITROGEN, Kind.POTASSIUM);
        assertThat(compost.get(0).a()).isEqualTo("20.0");
        assertThat(PlantLines.fertilizer(0f, 0f, 0f)).isEmpty();
    }

    @Test
    void fertilizerLookupResolvesItemsThenTags() {
        FertilizerInfo info = FertilizerInfo.of(Map.of("tfp:compost", new FertilizerInfo.Values(0.2f, 0f, 0.2f)),
            Map.of("c:bones", new FertilizerInfo.Values(0f, 0.1f, 0f)));
        dev.tfp.core.tag.TagLookup tags = new dev.tfp.core.tag.TagLookup() {
            @Override
            public boolean contains(dev.tfp.core.tag.TagType type, String tag, String id) {
                return tag.equals("c:bones") && id.equals("minecraft:bone");
            }

            @Override
            public boolean exists(dev.tfp.core.tag.TagType type, String tag) {
                return true;
            }
        };
        assertThat(info.get("tfp:compost", tags)).map(FertilizerInfo.Values::nitrogen).contains(0.2f);
        assertThat(info.get("minecraft:bone", tags)).map(FertilizerInfo.Values::phosphorus).contains(0.1f);
        assertThat(info.get("minecraft:stick", tags)).isEmpty();
        assertThat(Optional.ofNullable(Wood.OAK)).isPresent();
    }
}
