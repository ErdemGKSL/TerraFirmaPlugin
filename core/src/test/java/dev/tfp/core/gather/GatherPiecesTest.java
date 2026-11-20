/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.gather;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** PAR-GATHER-01: block ids to pieces and the loose rock stacking rule. */
class GatherPiecesTest {
    @Test
    void parsesLooseMossyAndGroundcoverBlocks() {
        assertThat(GatherPieces.of("tfp:rock/loose/granite", Map.of("count", "2")))
            .contains(new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 2));
        assertThat(GatherPieces.of("tfp:rock/mossy_loose/tuff", Map.of()))
            .contains(new GatherPiece.LooseRock(LooseRockType.TUFF, true, 1));
        assertThat(GatherPieces.of("tfp:groundcover/stick", Map.of()))
            .contains(new GatherPiece.Groundcover(GroundcoverType.STICK));
        assertThat(GatherPieces.of("tfp:groundcover/sea_urchin", Map.of()))
            .contains(new GatherPiece.Groundcover(GroundcoverType.SEA_URCHIN));
    }

    @Test
    void unknownBlocksAndBadCountsAreHandledSafely() {
        assertThat(GatherPieces.of("tfp:rock/raw/granite", Map.of())).isEmpty();
        assertThat(GatherPieces.of("tfp:rock/loose/nonsense", Map.of())).isEmpty();
        assertThat(GatherPieces.of("tfp:groundcover/nonsense", Map.of())).isEmpty();
        assertThat(GatherPieces.of("tfp:rock/loose/granite", Map.of("count", "9")))
            .contains(new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 1));
        assertThat(GatherPieces.of("tfp:rock/loose/granite", Map.of("count", "x")))
            .contains(new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 1));
    }

    @Test
    void stackingNeedsTheSameRockItemAndRoomForThree() {
        var one = new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 1);
        assertThat(GatherPieces.stack(one, "tfp:rock/loose/granite")).contains(2);
        assertThat(GatherPieces.stack(one, "tfp:rock/loose/basalt")).isEmpty();
        assertThat(GatherPieces.stack(one, "tfp:rock/mossy_loose/granite")).isEmpty();
        assertThat(GatherPieces.stack(new GatherPiece.LooseRock(LooseRockType.GRANITE, false, 3), "tfp:rock/loose/granite")).isEmpty();
        var mossy = new GatherPiece.LooseRock(LooseRockType.GRANITE, true, 2);
        assertThat(GatherPieces.stack(mossy, "tfp:rock/mossy_loose/granite")).contains(3);
    }

    @Test
    void yieldItemsMoveToOurNamespace() {
        assertThat(GatherPieces.ourItem(new GatherYield("tfc:rock/loose/granite", 2))).isEqualTo("tfp:rock/loose/granite");
        assertThat(GatherPieces.ourItem(new GatherYield("minecraft:stick", 1))).isEqualTo("minecraft:stick");
    }
}
