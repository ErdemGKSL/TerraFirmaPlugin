/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.worldgen;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.gather.GatherPiece;
import dev.tfp.core.gather.GatherPieces;
import dev.tfp.core.worldgen.groundcover.Groundcover;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * PAR-GATHER-12: the block states the terrain writer asks CraftEngine for read back as the pieces PAR-GATHER-01's pickup and
 * stacking key on, with the {@code count} property of the generated blocks.
 */
class GroundcoverCeIdTest {
    private static Map<String, String> properties(String ref) {
        Map<String, String> m = new HashMap<>();
        int i = ref.indexOf('[');
        if (i >= 0) {
            for (String kv : ref.substring(i + 1, ref.length() - 1).split(",")) {
                String[] p = kv.split("=", 2);
                m.put(p[0], p[1]);
            }
        }
        return m;
    }

    private static String block(String ref) {
        int i = ref.indexOf('[');
        return i < 0 ? ref : ref.substring(0, i);
    }

    @Test
    void aLooseRockCarriesItsCount() {
        Groundcover dry = new Groundcover(0, 64, 0, Groundcover.Kind.LOOSE_ROCK, "tfp:rock/loose/granite", 2, false);
        Groundcover wet = new Groundcover(0, 64, 0, Groundcover.Kind.MOSSY_LOOSE_ROCK, "tfp:rock/mossy_loose/slate", 3, true);
        assertThat(GroundcoverBlocks.ceId(dry)).isEqualTo("tfp:rock/loose/granite[count=2]");
        assertThat(GroundcoverBlocks.ceId(wet)).isEqualTo("tfp:rock/mossy_loose/slate[count=3]");
        GatherPiece.LooseRock rock = (GatherPiece.LooseRock) GatherPieces
            .of(block(GroundcoverBlocks.ceId(wet)), properties(GroundcoverBlocks.ceId(wet))).orElseThrow();
        assertThat(rock.mossy()).isTrue();
        assertThat(rock.count()).isEqualTo(3);
    }

    @Test
    void otherPiecesCarryNoProperties() {
        Groundcover stick = new Groundcover(0, 64, 0, Groundcover.Kind.STICK, "tfp:groundcover/stick", 1, false);
        assertThat(GroundcoverBlocks.ceId(stick)).isEqualTo("tfp:groundcover/stick");
        assertThat(GatherPieces.of(block(GroundcoverBlocks.ceId(stick)), properties(GroundcoverBlocks.ceId(stick)))).isPresent();
    }
}
