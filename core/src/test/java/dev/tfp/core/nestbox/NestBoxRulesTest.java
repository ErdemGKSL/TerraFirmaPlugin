/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.nestbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.livestock.products.NestRules;
import dev.tfp.core.persist.DataMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * PAR-FAUNA-07 core: the box's rules, traced from TFC's {@code NestBoxBlockEntity} at c3b4d9536: {@code SLOTS = 4},
 * {@code getSlotStackLimit = 1}, {@code isItemValid = has(EGG) && !isRotten}, {@code getGameTime() % 30 == 0}, the
 * {@code insertOne} order and {@code egg.canHatch}. The 1 in 7 draw order itself is {@code NestLayGoldenTest}.
 */
class NestBoxRulesTest {
    private static final long DAY = 24_000L;
    private static final NestBoxRules.Slot EMPTY = new NestBoxRules.Empty();

    private static NestBoxRules.Egg egg(boolean fertilized, long hatchDay, boolean rotten) {
        return new NestBoxRules.Egg(fertilized, hatchDay, rotten);
    }

    @Test
    void tfcConstants() {
        assertThat(NestBoxRules.SLOTS).isEqualTo(4);
        assertThat(NestBoxRules.SLOT_LIMIT).isEqualTo(1);
        assertThat(NestBoxRules.CHECK_INTERVAL).isEqualTo(30L);
        assertThat(NestRules.LAY_ROLL_BOUND).isEqualTo(7);
    }

    @Test
    void acceptsOnlyAFreshEggRecord() {
        assertThat(NestBoxRules.accepts(true, false)).isTrue();
        assertThat(NestBoxRules.accepts(true, true)).as("rotten").isFalse();
        assertThat(NestBoxRules.accepts(false, false)).as("no egg component").isFalse();
        assertThat(NestBoxRules.accepts(false, true)).isFalse();
    }

    @Test
    void eggsGoIntoTheFirstEmptySlot() {
        assertThat(NestBoxRules.firstFree(List.of(EMPTY, EMPTY, EMPTY, EMPTY))).isEqualTo(OptionalInt.of(0));
        assertThat(NestBoxRules.firstFree(List.of(egg(false, 0, false), new NestBoxRules.Other(), EMPTY, EMPTY)))
            .isEqualTo(OptionalInt.of(2));
        assertThat(NestBoxRules.firstFree(List.of(egg(true, 5, false), egg(true, 5, false), egg(true, 5, false),
            egg(true, 5, false)))).isEmpty();
        assertThat(NestBoxRules.hasOpenSlot(List.of(egg(true, 5, false), EMPTY))).isTrue();
        assertThat(NestBoxRules.hasOpenSlot(List.of(new NestBoxRules.Other()))).isFalse();
    }

    @Test
    void hatchRuleIsFertilizedAndDayReachedAndFresh() {
        long day10 = 10 * DAY;
        assertThat(NestBoxRules.hatches(egg(true, 10, false), day10)).isTrue();
        assertThat(NestBoxRules.hatches(egg(true, 10, false), day10 + DAY - 1)).isTrue();
        assertThat(NestBoxRules.hatches(egg(true, 10, false), day10 - 1)).as("day 9").isFalse();
        assertThat(NestBoxRules.hatches(egg(true, 10, false), 400 * DAY)).as("long overdue").isTrue();
        assertThat(NestBoxRules.hatches(egg(true, 10, true), day10)).as("rotten never hatches").isFalse();
        assertThat(NestBoxRules.hatches(egg(false, 10, false), day10)).as("unfertilized").isFalse();
        assertThat(NestBoxRules.hatches(EMPTY, day10)).isFalse();
        assertThat(NestBoxRules.hatches(new NestBoxRules.Other(), day10)).isFalse();
    }

    @Test
    void hatchingListsTheSlotsInOrder() {
        List<NestBoxRules.Slot> slots = List.of(egg(true, 3, false), EMPTY, egg(true, 4, false), egg(true, 2, false));
        assertThat(NestBoxRules.hatching(slots, 3 * DAY)).containsExactly(0, 3);
        assertThat(NestBoxRules.hatching(slots, 4 * DAY)).containsExactly(0, 2, 3);
        assertThat(NestBoxRules.hatching(slots, DAY)).isEmpty();
    }

    @Test
    void delayToCheckLandsOnTheThirtyTickGrid() {
        assertThat(NestBoxRules.delayToCheck(0, 0)).isZero();
        assertThat(NestBoxRules.delayToCheck(1, 0)).isEqualTo(29);
        assertThat(NestBoxRules.delayToCheck(30, 0)).isZero();
        assertThat(NestBoxRules.delayToCheck(31, 0)).isEqualTo(29);
        assertThat(NestBoxRules.delayToCheck(100, 50)).isEqualTo(50); // 150 is a grid point
        assertThat(NestBoxRules.delayToCheck(100, 51)).isEqualTo(80); // 151 -> 180
        assertThat(NestBoxRules.delayToCheck(100, -5)).as("a negative wait is none").isEqualTo(20);
    }

    @Test
    void delayToHatchPicksTheEarliestFreshFertilizedEgg() {
        long now = 1_000;
        long cal = 9 * DAY + 100; // day 9 and a bit
        List<NestBoxRules.Slot> slots = List.of(egg(false, 9, false), egg(true, 12, false), egg(true, 10, false),
            egg(true, 8, true));
        // the day 10 egg: wait DAY - 100 ticks, then the next grid point
        long wait = DAY - 100;
        assertThat(NestBoxRules.delayToHatch(slots, now, cal)).isEqualTo(NestBoxRules.delayToCheck(now, wait));
        // an egg already due waits for the next check
        assertThat(NestBoxRules.delayToHatch(List.of(egg(true, 9, false)), now, cal))
            .isEqualTo(NestBoxRules.delayToCheck(now, 0));
        assertThat(NestBoxRules.delayToHatch(List.of(egg(false, 1, false), egg(true, 1, true), EMPTY), now, cal)).isEqualTo(-1);
        assertThat(NestBoxRules.delayToHatch(List.of(), now, cal)).isEqualTo(-1);
    }

    /** The first check at which an egg hatches is the first grid point at or after its day, whatever the start tick. */
    @Test
    void hatchCheckIsTheFirstGridPointAtOrAfterTheDay() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            long now = r.nextInt(1_000_000);
            long cal = now * 3 + r.nextInt(50_000);
            long hatchDay = cal / DAY + r.nextInt(4);
            List<NestBoxRules.Slot> slots = List.of(egg(true, hatchDay, false));
            long delay = NestBoxRules.delayToHatch(slots, now, cal);
            assertThat((now + delay) % NestBoxRules.CHECK_INTERVAL).isZero();
            assertThat(delay).isBetween(0L, 4 * DAY + 30);
            // stepping the checks one by one finds the same first hatch
            long t = NestRules.firstCheckAtOrAfter(now);
            while (!NestBoxRules.hatches(slots.get(0), cal + (t - now))) {
                t += NestBoxRules.CHECK_INTERVAL;
            }
            assertThat(t - now).as("now=%d cal=%d day=%d", now, cal, hatchDay).isEqualTo(delay);
        }
    }

    @Test
    void stateRoundTripsAndRejectsHalfSeated() {
        NestBoxState seated = NestBoxState.FRESH.seat("0f8f7ad4-7e5e-4b6e-a0d7-1ab0c5b3b1de", 12_345L);
        assertThat(seated.occupied()).isTrue();
        DataMap map = NestBoxState.CODEC.encode(seated);
        assertThat(NestBoxState.CODEC.decode(map)).isEqualTo(seated);
        assertThat(NestBoxState.CODEC.decode(NestBoxState.CODEC.encode(NestBoxState.FRESH))).isEqualTo(NestBoxState.FRESH);
        assertThat(seated.vacated()).isEqualTo(NestBoxState.FRESH);
        assertThatThrownBy(() -> new NestBoxState("x", NestBoxState.NOT_SEATED)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NestBoxState("", 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NestBoxState.FRESH.seat("", 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkedThroughNeverRollsACheckTwice() {
        NestBoxState seated = NestBoxState.FRESH.seat("hen", 100);
        // checks at 120 and 150 are rolled by a callback at 155; the next unrolled one is 180
        NestBoxState after = seated.checkedThrough(155);
        assertThat(NestRules.firstCheckAtOrAfter(after.seatedTick())).isEqualTo(180);
        // a callback exactly on a grid point has rolled that check
        assertThat(NestRules.firstCheckAtOrAfter(seated.checkedThrough(180).seatedTick())).isEqualTo(210);
        // it never moves back
        assertThat(after.checkedThrough(100).seatedTick()).isEqualTo(after.seatedTick());
        assertThat(NestBoxState.FRESH.checkedThrough(500)).isEqualTo(NestBoxState.FRESH);
    }

    @Test
    void screenSlotsSitOnTheChestGridInTfcOrder() {
        assertThat(NestBoxScreenLayout.CHEST_SLOTS).containsExactly(3, 4, 12, 13);
        assertThat(NestBoxScreenLayout.DX_SLOTS).isEqualTo(-9);
        assertThat(NestBoxScreenLayout.DY_SLOTS).isEqualTo(-5);
        assertThat(NestBoxScreenLayout.tfcSlot(12)).isEqualTo(2);
        assertThat(NestBoxScreenLayout.tfcSlot(0)).isEqualTo(-1);
        assertThat(NestBoxScreenLayout.DY_PLAYER).isEqualTo(1);
    }
}
