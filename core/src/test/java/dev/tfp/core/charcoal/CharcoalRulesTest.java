/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LogPileBlockEntity.java @ c3b4d9536 (SLOTS, getSlotStackLimit),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/LogPileBlock.java @ c3b4d9536 (getShapeByDirByCount),
 *       src/main/java/net/dries007/tfc/common/blocks/devices/BurningLogPileBlock.java @ c3b4d9536 (isValidCoverBlock, tryLightNearby)
 */
package dev.tfp.core.charcoal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Log pile counts and the burning pile's neighbour sweep, against TFC's branches. The sweep is a transcription of
 * {@code tryLightNearby}, so its cases are written out by hand from the TFC code, not from a golden file.
 */
class CharcoalRulesTest {
    @Test
    void logPileCountsAreOneToSixteen() {
        assertThat(LogPile.SLOTS).isEqualTo(16);
        for (int logs = -1; logs <= 18; logs++) {
            boolean valid = logs >= 1 && logs <= 16;
            assertThat(LogPile.isValidCount(logs)).as("count %d", logs).isEqualTo(valid);
            assertThat(LogPile.canInsert(logs)).as("insert at %d", logs).isEqualTo(valid && logs < 16);
            assertThat(LogPile.isFull(logs)).as("full at %d", logs).isEqualTo(logs == 16);
        }
    }

    @Test
    void layerAndRowFollowTfcsShapeIndex() {
        for (int logs = 1; logs <= 16; logs++) {
            int i = logs - 1; // LogPileBlock.getShapeByDirByCount: i = count - 1, layer = i / 4, row = i % 4 + 1
            assertThat(LogPile.layer(logs)).as("layer of %d", logs).isEqualTo(i / 4);
            assertThat(LogPile.row(logs)).as("row of %d", logs).isEqualTo(i % 4 + 1);
        }
        assertThatThrownBy(() -> LogPile.layer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogPile.row(17)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validCoverIsInsulationOrSturdyNonFlammable() {
        assertThat(CharcoalSpread.isValidCover(CharcoalSpread.Cover.LOG_PILE)).isTrue();
        assertThat(CharcoalSpread.isValidCover(CharcoalSpread.Cover.PIT_INSULATION)).isTrue();
        assertThat(CharcoalSpread.isValidCover(CharcoalSpread.Cover.STURDY_NON_FLAMMABLE)).isTrue();
        assertThat(CharcoalSpread.isValidCover(CharcoalSpread.Cover.AIR)).isFalse();
        assertThat(CharcoalSpread.isValidCover(CharcoalSpread.Cover.OTHER)).isFalse();
    }

    @Test
    void sweepLightsLogPilesAndPlacesFireInAirWithoutDrawing() {
        AtomicInteger draws = new AtomicInteger();
        List<CharcoalSpread.Step> steps = CharcoalSpread.sweep(List.of(
            CharcoalSpread.Cover.LOG_PILE,      // DOWN: lit
            CharcoalSpread.Cover.AIR,           // UP: fire
            CharcoalSpread.Cover.PIT_INSULATION, // NORTH: nothing
            CharcoalSpread.Cover.STURDY_NON_FLAMMABLE, // SOUTH: nothing
            CharcoalSpread.Cover.LOG_PILE,      // WEST: lit
            CharcoalSpread.Cover.AIR            // EAST: fire
        ), n -> {
            draws.incrementAndGet();
            return 1;
        });
        assertThat(steps).containsExactly(
            new CharcoalSpread.Step(CharcoalSpread.Action.LIGHT_LOG_PILE, 0),
            new CharcoalSpread.Step(CharcoalSpread.Action.PLACE_FIRE, 1),
            new CharcoalSpread.Step(CharcoalSpread.Action.LIGHT_LOG_PILE, 4),
            new CharcoalSpread.Step(CharcoalSpread.Action.PLACE_FIRE, 5));
        assertThat(draws.get()).as("no side is 'other', so no draw").isZero();
        assertThat(CharcoalSpread.LOG_PILE_LIGHT_DELAY).isEqualTo(30);
    }

    @Test
    void otherSideDrawsOneInSevenAndReplacesThePileOnAZero() {
        List<CharcoalSpread.Cover> sides = List.of(
            CharcoalSpread.Cover.AIR,
            CharcoalSpread.Cover.OTHER,   // draw 1: 3, not 0
            CharcoalSpread.Cover.OTHER,   // draw 2: 0, the pile burns away
            CharcoalSpread.Cover.AIR,     // never reached
            CharcoalSpread.Cover.AIR,
            CharcoalSpread.Cover.AIR);
        int[] values = {3, 0};
        AtomicInteger next = new AtomicInteger();
        List<CharcoalSpread.Step> steps = CharcoalSpread.sweep(sides, n -> {
            assertThat(n).isEqualTo(7);
            return values[next.getAndIncrement()];
        });
        assertThat(next.get()).isEqualTo(2);
        assertThat(steps).containsExactly(
            new CharcoalSpread.Step(CharcoalSpread.Action.PLACE_FIRE, 0),
            new CharcoalSpread.Step(CharcoalSpread.Action.FIRE_REPLACES_PILE, 2));
    }

    @Test
    void sweepNeedsSixSides() {
        assertThatThrownBy(() -> CharcoalSpread.sweep(List.of(CharcoalSpread.Cover.AIR), n -> 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
