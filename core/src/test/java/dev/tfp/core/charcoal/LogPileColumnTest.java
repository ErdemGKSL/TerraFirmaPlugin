/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (insertAndPushUp, extractFromTop),
 *       src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (suckLogsFromAbove,
 *       checkDoubleClick),
 *       src/main/java/net/dries007/tfc/common/blocks/CharcoalPileBlock.java @ c3b4d9536 (onDestroyedByPlayer, updateShape)
 */
package dev.tfp.core.charcoal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The log pile column moves ({@code insertAndPushUp}, {@code suckLogsFromAbove}, {@code extractFromTop}), the click memory,
 * the burning pile state and the charcoal pile layer rules, from TFC's branches written out by hand.
 */
class LogPileColumnTest {
    static List<String> pile(String... logs) {
        List<String> p = new ArrayList<>(Arrays.asList(new String[LogPile.SLOTS]));
        for (int i = 0; i < logs.length; i++) {
            p.set(i, logs[i]);
        }
        return p;
    }

    static List<String> full(String log) {
        String[] all = new String[LogPile.SLOTS];
        Arrays.fill(all, log);
        return pile(all);
    }

    static int count(List<String> p) {
        return LogPileColumn.count(p);
    }

    @Test
    void oneLogGoesIntoTheFirstEmptySlotOfTheClickedPile() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(pile("oak", null, "ash")), true, "pine", 5, false);
        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.newPileOnTop()).isFalse();
        assertThat(r.piles().get(0).subList(0, 3)).containsExactly("oak", "pine", "ash");
    }

    @Test
    void aFullPilePushesTheLogIntoANewPileAboveWhenThereIsAir() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(full("oak")), true, "pine", 3, false);
        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.newPileOnTop()).isTrue();
        assertThat(count(r.piles().get(0))).isEqualTo(16);
        assertThat(count(r.piles().get(1))).isEqualTo(1);
        assertThat(r.piles().get(1).get(0)).isEqualTo("pine");
    }

    @Test
    void aFullPileWithABlockAboveTakesNothing() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(full("oak")), false, "pine", 3, false);
        assertThat(r.inserted()).isZero();
        assertThat(r.newPileOnTop()).isFalse();
        assertThat(r.piles()).hasSize(1);
    }

    @Test
    void fullPilesPassTheLogUpTheColumn() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(full("oak"), full("oak"), pile("ash")), false, "pine",
            1, false);
        assertThat(r.inserted()).isEqualTo(1);
        assertThat(r.piles().get(2).subList(0, 2)).containsExactly("ash", "pine");
    }

    @Test
    void doubleClickFillsThePileThenStartsANewOne() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(pile("oak", "oak", "oak", "oak")), true, "pine", 20,
            true);
        // 12 fill the pile, the rest (8) goes into a new pile above
        assertThat(r.inserted()).isEqualTo(20);
        assertThat(count(r.piles().get(0))).isEqualTo(16);
        assertThat(r.newPileOnTop()).isTrue();
        assertThat(count(r.piles().get(1))).isEqualTo(8);
    }

    @Test
    void doubleClickStopsWhenTheStackRunsOut() {
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(pile("oak")), true, "pine", 6, true);
        assertThat(r.inserted()).isEqualTo(6);
        assertThat(count(r.piles().get(0))).isEqualTo(7);
        assertThat(r.newPileOnTop()).isFalse();
    }

    @Test
    void anInsertPullsTheLogsOfThePileAboveIntoTheEmptySlots() {
        // suckLogsFromAbove after setAndUpdateSlots: the clicked pile has 14 empty slots after the insert, the pile above
        // has 3 logs: all 3 move down and the pile above becomes air (all-empty)
        LogPileColumn.Insert<String> r = LogPileColumn.insert(List.of(pile("oak"), pile("ash", "ash", "birch")), false,
            "pine", 1, false);
        assertThat(r.piles().get(0).subList(0, 5)).containsExactly("oak", "pine", "ash", "ash", "birch");
        assertThat(count(r.piles().get(1))).isZero();
    }

    @Test
    void extractionTakesTheFirstLogOrAll() {
        LogPileColumn.Extract<String> one = LogPileColumn.extractFromTop(pile(null, "oak", "ash"), false);
        assertThat(one.extracted()).containsExactly("oak");
        assertThat(count(one.top())).isEqualTo(1);
        LogPileColumn.Extract<String> all = LogPileColumn.extractFromTop(pile("oak", "ash"), true);
        assertThat(all.extracted()).containsExactly("oak", "ash");
        assertThat(count(all.top())).isZero();
    }

    @Test
    void columnShapesAreChecked() {
        assertThatThrownBy(() -> LogPileColumn.insert(List.of(), true, "oak", 1, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogPileColumn.insert(List.of(List.of("oak")), true, "oak", 1, false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogPileColumn.insert(List.of(pile()), true, "oak", 0, false))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void clickMemoryFollowsTfcsWindows() {
        LogPileData placed = LogPileData.placedAt(100);
        assertThat(placed.placementDoubleClick(105)).isTrue(); // < 6
        assertThat(placed.placementDoubleClick(106)).isFalse();
        assertThat(placed.extractionDoubleClick(101)).isFalse(); // last click was a placement
        LogPileData plain = placed.clicked(200, false);
        assertThat(plain.extractionDoubleClick(210)).isTrue(); // <= 10
        assertThat(plain.extractionDoubleClick(211)).isFalse();
        assertThat(plain.placementDoubleClick(201)).isFalse();
        assertThat(LogPileData.FRESH.placementDoubleClick(0)).isFalse();
        assertThat(LogPileData.CODEC.decode(LogPileData.CODEC.encode(plain))).isEqualTo(plain);
    }

    @Test
    void burningPileConvertsAfterCharcoalTicks() {
        BurningPileData d = BurningPileData.lit(12, 1000);
        assertThat(d.converted(19000, CharcoalPit.DEFAULT_CHARCOAL_TICKS)).isFalse();
        assertThat(d.converted(19001, CharcoalPit.DEFAULT_CHARCOAL_TICKS)).isTrue();
        assertThat(d.convertTick(CharcoalPit.DEFAULT_CHARCOAL_TICKS)).isEqualTo(19001);
        assertThat(d.converted(500, CharcoalPit.DEFAULT_CHARCOAL_TICKS)).isFalse();
        assertThat(BurningPileData.FRESH.converted(Long.MAX_VALUE / 2, 0)).isFalse();
        assertThat(BurningPileData.CODEC.decode(BurningPileData.CODEC.encode(d))).isEqualTo(d);
        assertThatThrownBy(() -> new BurningPileData(17, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void charcoalPileLayers() {
        assertThat(CharcoalPile.afterPlayerBreak(3, false)).isEqualTo(2);
        assertThat(CharcoalPile.afterPlayerBreak(1, false)).isZero();
        assertThat(CharcoalPile.afterPlayerBreak(5, true)).isZero();
        assertThat(CharcoalPile.canAddLayer(7)).isTrue();
        assertThat(CharcoalPile.canAddLayer(8)).isFalse();
        assertThat(CharcoalPile.mergeDown(3, 4)).isEqualTo(new CharcoalPile.Merge(7, 0));
        assertThat(CharcoalPile.mergeDown(6, 4)).isEqualTo(new CharcoalPile.Merge(8, 2));
        assertThat(CharcoalPile.mergeDown(6, 8)).isNull();
        assertThat(CharcoalPile.canSurvive(true, 0)).isTrue();
        assertThat(CharcoalPile.canSurvive(false, 8)).isTrue();
        assertThat(CharcoalPile.canSurvive(false, 7)).isFalse();
    }

    @Test
    void randomTicksDrawOncePerPositionAtVanillasRate() {
        AtomicInteger draws = new AtomicInteger();
        int hits = CharcoalSpread.randomTicks(20, 3, bound -> {
            assertThat(bound).isEqualTo(4096);
            return draws.incrementAndGet() % 10 == 0 ? 0 : 1;
        });
        assertThat(draws.get()).isEqualTo(60);
        assertThat(hits).isEqualTo(6);
        assertThat(CharcoalSpread.randomTicks(20, 0, b -> 0)).isZero();
    }
}
