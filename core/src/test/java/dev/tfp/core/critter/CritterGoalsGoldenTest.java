/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AquaticCritter.java @ c3b4d9536 (registerGoals, CritterEscapeGoal),
 * src/main/java/net/dries007/tfc/common/entities/ai/prey/PestFeastBehavior.java (afterReached),
 * src/main/java/net/dries007/tfc/ForgeEventHandler.java (food count, lines 1210 to 1220)
 */
package dev.tfp.core.critter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The critter goal numbers and the container food scans, each checked against the TFC line it follows. */
class CritterGoalsGoldenTest {
    /** {@code registerGoals}: escape 8 / 2.0 / 2.0, predator avoid 8 / 5 / 5.4, random swim 1.0 every 30 ticks. */
    @Test
    void goalLiteralsAreTfcs() {
        assertThat(CritterEscape.PLAYER_DISTANCE).isEqualTo(8.0f);
        assertThat(CritterEscape.PLAYER_SPEED).isEqualTo(2.0);
        assertThat(CritterEscape.PREDATOR_DISTANCE).isEqualTo(8.0f);
        assertThat(CritterEscape.PREDATOR_WALK_SPEED).isEqualTo(5.0);
        assertThat(CritterEscape.PREDATOR_SPRINT_SPEED).isEqualTo(5.4);
        assertThat(CritterEscape.RANDOM_SWIM_INTERVAL).isEqualTo(30);
    }

    /** {@code shouldEscape}: only after a hit, and vanilla forgets the attacker after 100 ticks. */
    @Test
    void escapesOnlyAfterAHit() {
        assertThat(CritterEscape.shouldEscape(-1)).isFalse();
        assertThat(CritterEscape.shouldEscape(0)).isTrue();
        assertThat(CritterEscape.shouldEscape(100)).isTrue();
        assertThat(CritterEscape.shouldEscape(101)).isFalse();
    }

    /** {@code canContinueToUse}: {@code !pathNav.isDone() || distanceToSqr > 49}. */
    @Test
    void escapeContinuesUntilFarAndDone() {
        assertThat(CritterEscape.continues(false, 1.0)).isTrue();
        assertThat(CritterEscape.continues(true, 49.0)).isFalse();
        assertThat(CritterEscape.continues(true, 49.5)).isTrue();
    }

    /** {@code afterReached}: the first food slot in slot order; none means forget the smell. */
    @Test
    void feastTakesTheFirstFoodSlot() {
        assertThat(PestFeast.firstFoodSlot(new boolean[] {false, false, true, true})).isEqualTo(2);
        assertThat(PestFeast.firstFoodSlot(new boolean[] {false, false})).isEqualTo(-1);
        assertThat(PestFeast.firstFoodSlot(new boolean[0])).isEqualTo(-1);
        assertThat(PestFeast.WALK_TICKS).isEqualTo(100);
    }

    /** The container scan counts food stacks and stops at five. */
    @Test
    void foodCountStopsAtFive() {
        assertThat(PestInfestation.countFood(new boolean[] {true, false, true})).isEqualTo(2);
        assertThat(PestInfestation.countFood(new boolean[] {true, true, true, true, true, true, true})).isEqualTo(5);
        assertThat(PestInfestation.countFood(new boolean[27])).isZero();
    }

    /** {@code Pest.tick}: counter -1 -> 0 on the pickup tick, cleared when it passes 400, so after 402 server ticks. */
    @Test
    void heldItemIsClearedAfterFourHundredAndTwoTicks() {
        assertThat(PestFeast.ticksUntilCleared()).isEqualTo(402);
    }
}
