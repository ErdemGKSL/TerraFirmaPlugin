/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.paper.rendered;

import static dev.tfp.paper.rendered.MiningTime.BRONZE_SPEED;
import static dev.tfp.paper.rendered.MiningTime.METAL_BLOCK_HARDNESS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PAR-INFRA-32: TFC's break time of a metal block (hardness 5, requires the correct tool) in server ticks. */
class MiningTimeTest {
    @Test
    void handCorrectToolAndWrongToolOnACopperBlock() {
        // Empty hand: speed 1, not correct for drops: 1 / 5 / 100 = 0.002f per tick. 500 in exact arithmetic, but the
        // float sum after 500 ticks is just below 1, so the vanilla client (and CraftEngine) break on tick 501.
        assertThat(MiningTime.ticksToBreak(METAL_BLOCK_HARDNESS, 1f, true, false)).isEqualTo(501);
        // Bronze pickaxe (TFCTiers.BRONZE speed 7.3, copper needs a pickaxe of any metal tier): 7.3 / 5 / 30.
        assertThat(MiningTime.ticksToBreak(METAL_BLOCK_HARDNESS, BRONZE_SPEED, true, true)).isEqualTo(21);
        // Bronze axe: not in mineable/pickaxe, so its speed on the block is 1 and it is not correct: as the hand.
        assertThat(MiningTime.ticksToBreak(METAL_BLOCK_HARDNESS, 1f, true, false)).isEqualTo(501);
        // A pickaxe of too low a tier keeps its speed but divides by 100.
        assertThat(MiningTime.ticksToBreak(METAL_BLOCK_HARDNESS, BRONZE_SPEED, true, false)).isEqualTo(69);
    }

    @Test
    void progressPerTickIsVanillasRule() {
        assertThat(MiningTime.progressPerTick(5f, 7.3f, true, true)).isEqualTo(7.3f / 5f / 30);
        assertThat(MiningTime.progressPerTick(5f, 1f, false, false)).as("no tool requirement: /30").isEqualTo(1f / 5f / 30);
        // Vanilla stone by hand (hardness 1.5, needs a pickaxe): 150 exact, 151 summed (PAR-INFRA-17 measured 151-152).
        assertThat(MiningTime.ticksToBreak(1.5f, 1f, true, false)).isEqualTo(151);
    }

    @Test
    void unbreakableAndInstantBlocks() {
        assertThat(MiningTime.ticksToBreak(-1f, 7.3f, true, true)).isEqualTo(-1);
        assertThat(MiningTime.ticksToBreak(0f, 1f, false, false)).isEqualTo(1);
    }
}
