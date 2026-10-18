/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/blockentities/LobsterTrapBlockEntity.java @ c3b4d9536 (hand-checked values)
 */
package dev.tfp.core.fishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tfp.core.fishing.LobsterTrap.Contents;
import dev.tfp.core.fishing.LobsterTrap.State;
import dev.tfp.core.hunt.HuntDrops;
import dev.tfp.core.hunt.HuntKill;
import dev.tfp.core.hunt.HuntKill.Killer;
import dev.tfp.core.hunt.HuntYield;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

/** Unit tests for the lobster trap: its state, bait, the catch and its junk roll, and the take. */
class LobsterTrapTest {

    private static final String SEAWEED = "tfc:food/fresh_seaweed";
    private static final List<String> JUNK = List.of(SEAWEED);
    /** The kill by the trap: the damage source counts as a player kill, and no player or predator is involved. */
    private static final HuntKill TRAP_KILL = new HuntKill(Killer.NONE, false, true, false, 1f, 0f, 0f);

    /** Draws from a fixed list, failing when a rule reads more draws than it should. */
    private static DoubleSupplier draws(double... values) {
        int[] next = {0};
        return () -> {
            if (next[0] >= values.length) {
                throw new AssertionError("more draws than expected: " + next[0]);
            }
            return values[next[0]++];
        };
    }

    @Test
    void theStateIsDerivedFromBaitAndItems() {
        assertThat(LobsterTrap.state(new Contents(0, 0))).isEqualTo(State.OPEN);
        assertThat(LobsterTrap.state(new Contents(3, 0))).isEqualTo(State.BAITED);
        assertThat(LobsterTrap.state(new Contents(3, 1))).isEqualTo(State.BAITED);
        assertThat(LobsterTrap.state(new Contents(0, 1))).isEqualTo(State.CLOSED);
        assertThat(LobsterTrap.state(new Contents(3, 6))).isEqualTo(State.CLOSED);
        assertThat(LobsterTrap.state(new Contents(0, 6))).isEqualTo(State.CLOSED);
    }

    @Test
    void onlyAnEmptyTrapTakesLobsterBait() {
        assertThat(LobsterTrap.bait(new Contents(0, 0), true)).isEqualTo(new Contents(3, 0));
        assertThat(LobsterTrap.bait(new Contents(3, 0), true)).isEqualTo(new Contents(3, 0));
        assertThat(LobsterTrap.bait(new Contents(0, 1), true)).isEqualTo(new Contents(0, 1));
        assertThat(LobsterTrap.bait(new Contents(0, 0), false)).isEqualTo(new Contents(0, 0));
    }

    @Test
    void aTrapCatchesOnlyWhenBaitedWithAFreeSlot() {
        assertThat(LobsterTrap.canCatch(new Contents(3, 0))).isTrue();
        assertThat(LobsterTrap.canCatch(new Contents(3, 5))).isTrue();
        assertThat(LobsterTrap.canCatch(new Contents(3, 6))).isFalse();
        assertThat(LobsterTrap.canCatch(new Contents(0, 0))).isFalse();
    }

    @Test
    void aCatchTakesOneBaitUnitAndInsertsTheMobsLootWhenNotJunk() {
        // 0.5 is not below the junk chance, so the loot is rolled; the shellfish drop reads no draws
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(3, 0), "lobster", TRAP_KILL, JUNK, draws(0.5));
        int units = HuntDrops.yields("lobster", TRAP_KILL, () -> 0.5).stream().mapToInt(HuntYield::count).sum();
        assertThat(units).isGreaterThan(0);
        assertThat(catchResult.contents()).isEqualTo(new Contents(2, Math.min(6, units)));
        assertThat(catchResult.lost()).isEqualTo(Math.max(0, units - 6));
        assertThat(catchResult.inserted().stream().mapToInt(HuntYield::count).sum() + catchResult.lost()).isEqualTo(units);
        assertThat(catchResult.inserted()).allMatch(y -> y.item().equals("tfc:food/shellfish"));
    }

    @Test
    void aJunkRollInsertsOneJunkItemAndReadsExactlyTwoDraws() {
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(3, 0), "crayfish", TRAP_KILL, JUNK, draws(0.05, 0.0));
        assertThat(catchResult.inserted()).containsExactly(new HuntYield(SEAWEED, 1));
        assertThat(catchResult.contents()).isEqualTo(new Contents(2, 1));
        assertThat(catchResult.lost()).isZero();
    }

    @Test
    void theJunkRollIsStrictlyBelowTheChance() {
        // exactly 0.1f is not junk: the next draw belongs to the loot table, which reads none for shellfish
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(3, 0), "isopod", TRAP_KILL, JUNK, draws(LobsterTrap.JUNK_CHANCE));
        assertThat(catchResult.inserted()).noneMatch(y -> y.item().equals(SEAWEED));
    }

    @Test
    void junkWithAnEmptyTagInsertsNothing() {
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(1, 0), "isopod", TRAP_KILL, List.of(), draws(0.0, 0.0));
        assertThat(catchResult.inserted()).isEmpty();
        assertThat(catchResult.contents()).isEqualTo(new Contents(0, 0));
    }

    @Test
    void aFullFreeSlotCountLosesTheRemainder() {
        int units = HuntDrops.yields("lobster", TRAP_KILL, () -> 0.5).stream().mapToInt(HuntYield::count).sum();
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(3, 5), "lobster", TRAP_KILL, JUNK, draws(0.5));
        assertThat(catchResult.contents().occupied()).isEqualTo(6);
        assertThat(catchResult.inserted().stream().mapToInt(HuntYield::count).sum()).isEqualTo(Math.min(1, units));
        assertThat(catchResult.lost()).isEqualTo(units - Math.min(1, units));
    }

    @Test
    void theCatchStateIsClosedOnceItemsAreInAndBaitIsGone() {
        LobsterTrap.Catch catchResult = LobsterTrap.catchMob(new Contents(1, 0), "lobster", TRAP_KILL, JUNK, draws(0.5));
        assertThat(catchResult.contents().bait()).isZero();
        assertThat(LobsterTrap.state(catchResult.contents())).isEqualTo(State.CLOSED);
    }

    @Test
    void catchRejectsAClosedTrapAPlainSpeciesAndAKillThatIsNotAPlayerKill() {
        assertThatThrownBy(() -> LobsterTrap.catchMob(new Contents(0, 0), "lobster", TRAP_KILL, JUNK, draws()))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LobsterTrap.catchMob(new Contents(3, 0), "cod", TRAP_KILL, JUNK, draws()))
            .isInstanceOf(IllegalArgumentException.class);
        HuntKill notAPlayerKill = new HuntKill(Killer.NONE, false, false, false, 1f, 0f, 0f);
        assertThatThrownBy(() -> LobsterTrap.catchMob(new Contents(3, 0), "lobster", notAPlayerKill, JUNK, draws()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void takingItemsEmptiesTheSlotsAndKeepsTheBait() {
        assertThat(LobsterTrap.take(new Contents(3, 2))).isEqualTo(new LobsterTrap.Take(new Contents(3, 0), 2, false));
        assertThat(LobsterTrap.take(new Contents(3, 0))).isEqualTo(new LobsterTrap.Take(Contents.empty(), 0, true));
        assertThat(LobsterTrap.take(Contents.empty())).isEqualTo(new LobsterTrap.Take(Contents.empty(), 0, false));
    }

    @Test
    void theContentsRejectOutOfRangeValues() {
        assertThatThrownBy(() -> new Contents(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Contents(0, 7)).isInstanceOf(IllegalArgumentException.class);
    }
}
