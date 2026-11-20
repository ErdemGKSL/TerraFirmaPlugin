/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.crop.wild;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.berry.SpreadRules.Side;
import dev.tfp.core.calendar.CalendarTicks;
import dev.tfp.core.calendar.Month;
import dev.tfp.core.crop.CropType;
import dev.tfp.core.food.FoodDecay;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** PAR-FARM-05: the maturity window, the spreading crop's fruit decision, the fruit rot and the block ids. */
class WildRulesTest {
    @Test
    void maturityWindowIsJuneToOctober() {
        for (Month m : Month.values()) {
            boolean expected = m.ordinal() >= Month.JUNE.ordinal() && m.ordinal() <= Month.OCTOBER.ordinal();
            assertThat(WildRules.isMature(m)).as(m.name()).isEqualTo(expected);
        }
    }

    @Test
    void maturityWindowWrapsOverTheNewYear() {
        assertThat(WildRules.isMature(Month.DECEMBER, Month.NOVEMBER, Month.FEBRUARY)).isTrue();
        assertThat(WildRules.isMature(Month.JANUARY, Month.NOVEMBER, Month.FEBRUARY)).isTrue();
        assertThat(WildRules.isMature(Month.FEBRUARY, Month.NOVEMBER, Month.FEBRUARY)).isTrue();
        assertThat(WildRules.isMature(Month.MARCH, Month.NOVEMBER, Month.FEBRUARY)).isFalse();
        assertThat(WildRules.isMature(Month.OCTOBER, Month.NOVEMBER, Month.FEBRUARY)).isFalse();
        assertThat(WildRules.isMature(Month.JULY, Month.JULY, Month.JULY)).isTrue();
        assertThat(WildRules.isMature(Month.AUGUST, Month.JULY, Month.JULY)).isFalse();
    }

    @Test
    void crowdedCropsAreCappedAtNinetyPercent() {
        assertThat(WildRules.growthLimit(2, 1f)).isEqualTo(1f);
        assertThat(WildRules.growthLimit(3, 1f)).isEqualTo(0.9f);
        assertThat(WildRules.growthLimit(4, 1f)).isEqualTo(0.9f);
    }

    @Test
    void spreadBelowFullGrowthDrawsNothing() {
        Random r = new Random(7);
        long before = new Random(7).nextLong();
        assertThat(WildRules.spread(0.99f, r, s -> true).place()).isFalse();
        assertThat(r.nextLong()).isEqualTo(before);
    }

    @Test
    void spreadDrawsTheSideThenTheGrowthInTfcOrder() {
        Random expected = new Random(11);
        Side side = Side.values()[expected.nextInt(4)];
        float growth = 0.6f + expected.nextFloat() * (0.72f - 0.6f);
        WildRules.Spread s = WildRules.spread(1f, new Random(11), x -> true);
        assertThat(s.place()).isTrue();
        assertThat(s.side()).isEqualTo(side);
        assertThat(s.growth()).isEqualTo(growth).isBetween(0.6f, 0.72f);
    }

    @Test
    void blockedSideKeepsTheGrowthAndDrawsNoSecondFloat() {
        Random r = new Random(3);
        Random shadow = new Random(3);
        shadow.nextInt(4);
        assertThat(WildRules.spread(1.2f, r, s -> false)).isEqualTo(WildRules.Spread.NONE);
        assertThat(r.nextLong()).isEqualTo(shadow.nextLong());
    }

    @Test
    void aFruitRotsOnTheFoodClockAndTheWildTraitDelaysIt() {
        long life = (long) (1f * FoodDecay.DEFAULT_DECAY_TICKS);
        long created = 1_000L;
        assertThat(WildRules.rotten(created, false, 1f, 1f, created + life - 1)).isFalse();
        assertThat(WildRules.rotten(created, false, 1f, 1f, created + life)).isTrue();
        assertThat(WildRules.rottenAt(created, false, 1f, 1f)).isEqualTo(created + life);
        // the wild trait halves the decay rate: twice the life time
        assertThat(WildRules.rotten(created, true, 1f, 1f, created + life + 1)).isFalse();
        assertThat(WildRules.rotten(created, true, 1f, 1f, created + 2 * life)).isTrue();
        assertThat(WildRules.rottenAt(created, true, 1f, 1f)).isEqualTo(created + 2 * life);
    }

    @Test
    void flagsNeverOrAlwaysRot() {
        assertThat(WildRules.rotten(FoodDecay.ROTTEN_FLAG, false, 1f, 1f, 0L)).isTrue();
        assertThat(WildRules.rottenAt(FoodDecay.ROTTEN_FLAG, false, 1f, 1f)).isEqualTo(Long.MIN_VALUE);
        assertThat(WildRules.rotten(FoodDecay.NEVER_DECAY_FLAG, false, 1f, 1f, Long.MAX_VALUE / 2)).isFalse();
        assertThat(WildRules.rottenAt(FoodDecay.NEVER_DECAY_FLAG, false, 1f, 1f)).isEqualTo(Long.MAX_VALUE);
        assertThat(WildRules.rotten(5L, false, 0f, 1f, Long.MAX_VALUE / 2)).isFalse(); // a zero modifier never rots
    }

    @Test
    void lazyRotMatchesSteppedChecks() {
        long created = 24_000L;
        long at = WildRules.rottenAt(created, false, 1f, 1f);
        long firstRotten = -1;
        for (long t = created; t < created + 2L * FoodDecay.DEFAULT_DECAY_TICKS; t += 1_000) {
            if (WildRules.rotten(created, false, 1f, 1f, t)) {
                firstRotten = t;
                break;
            }
        }
        assertThat(firstRotten).isGreaterThanOrEqualTo(at).isLessThan(at + 1_000);
        assertThat(CalendarTicks.CALENDAR_TICKS_IN_DAY).isPositive();
    }

    @Test
    void blockIdsAndRecordsRoundTrip() {
        assertThat(FruitDevice.type("tfp:fruit/pumpkin")).contains(CropType.PUMPKIN);
        assertThat(FruitDevice.type("tfp:fruit/rotten_melon")).contains(CropType.MELON);
        assertThat(FruitDevice.type("tfp:fruit/wheat")).isEmpty();
        assertThat(FruitDevice.isRottenBlock("tfp:fruit/rotten_pumpkin")).isTrue();
        assertThat(FruitDevice.isRottenBlock("tfp:fruit/pumpkin")).isFalse();
        assertThat(FruitDevice.rottenId(CropType.PUMPKIN)).isEqualTo("tfp:fruit/rotten_pumpkin");
        assertThat(WildCropDevice.type("tfp:wild_crop/melon")).contains(CropType.MELON);
        assertThat(WildCropDevice.type("tfp:crop/melon")).isEmpty();
        assertThat(WildCropDevice.fruitOf(CropType.PUMPKIN)).isEqualTo("tfp:fruit/pumpkin");
        assertThat(WildCropDevice.fruitOf(CropType.WHEAT)).isNull();
        FruitDevice.FruitState f = new FruitDevice.FruitState(123_456L, true);
        assertThat(FruitDevice.CODEC.decode(FruitDevice.CODEC.encode(f))).isEqualTo(f);
        assertThat(WildCropDevice.CODEC.decode(WildCropDevice.CODEC.encode(99L))).isEqualTo(99L);
    }
}
