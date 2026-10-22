/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/util/loot/AnimalYieldProvider.java @ c3b4d9536, NotPredatedCondition.java and the animal loot tables
 * (hand-checked values)
 */
package dev.tfp.core.hunt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.hunt.HuntKill.Killer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for the drop rules: the size scale, the conditions, the draw order and the species lookup. */
class HuntDropsTest {

    @Test
    void sizeScaleMapsTheSizeRangeToUnit() {
        assertThat(new HuntKill(Killer.NONE, false, false, false, 1f, 0f, 0f).sizeScale()).isZero();
        assertThat(new HuntKill(Killer.NONE, false, false, false, 46f, 0f, 0f).sizeScale()).isEqualTo(1f);
        assertThat(new HuntKill(Killer.NONE, false, false, false, 0f, 0f, 0f).sizeScale()).isZero();
        assertThat(new HuntKill(Killer.NONE, false, false, false, 100f, 0f, 0f).sizeScale()).isEqualTo(1f);
        assertThat(new HuntKill(Killer.NONE, false, false, false, 23.5f, 0f, 0f).sizeScale()).isEqualTo(0.5f);
    }

    @Test
    void familiarityAboveHalfAddsUpToEightSize() {
        // Familiarity 0.75 adds clampedMap(0.75, 0.5, 1, 1, 8) = 4.5, so 19 + 4.5 = 23.5, the midpoint.
        assertThat(new HuntKill(Killer.NONE, false, false, false, 19f, 0.75f, 0f).sizeScale()).isEqualTo(0.5f);
        // At exactly 0.5 nothing is added.
        assertThat(new HuntKill(Killer.NONE, false, false, false, 23.5f, 0.5f, 0f).sizeScale()).isEqualTo(0.5f);
        assertThat(new HuntKill(Killer.NONE, false, false, false, 1f, 1f, 0f).sizeScale()).isEqualTo(1f / 45f * 8f);
    }

    @Test
    void playerAttackAddsSizeOnlyWhenAPlayerKilled() {
        // Attack 8 adds clampedMap(8, 0, 16, 1, 8) = 4.5, so 19 + 4.5 = 23.5 and (23.5 - 1) / 45 = 0.5.
        assertThat(new HuntKill(Killer.PLAYER, true, false, false, 19f, 0f, 8f).sizeScale()).isEqualTo(0.5f);
        // The attack is ignored without a last-damaging player.
        assertThat(new HuntKill(Killer.OTHER, false, false, false, 18f, 0f, 8f).sizeScale()).isCloseTo(17f / 45f, within(1e-6f));
    }

    @Test
    void notPredatedRejectsOnlyPredators() {
        assertThat(HuntCondition.NOT_PREDATED.test(kill(Killer.NONE), () -> 0d)).isTrue();
        assertThat(HuntCondition.NOT_PREDATED.test(kill(Killer.PLAYER), () -> 0d)).isTrue();
        assertThat(HuntCondition.NOT_PREDATED.test(kill(Killer.OTHER), () -> 0d)).isTrue();
        assertThat(HuntCondition.NOT_PREDATED.test(kill(Killer.LAND_PREDATOR), () -> 0d)).isFalse();
        assertThat(HuntCondition.NOT_PREDATED.test(kill(Killer.OCEAN_PREDATOR), () -> 0d)).isFalse();
    }

    @Test
    void playerKillCountsTheDamageSourceTag() {
        HuntKill tagged = new HuntKill(Killer.OTHER, false, true, false, 1f, 0f, 0f);
        assertThat(HuntCondition.PLAYER_KILL.test(tagged, () -> 0d)).isTrue();
        assertThat(HuntCondition.KILLED_BY_PLAYER.test(tagged, () -> 0d)).isFalse();
        assertThat(HuntCondition.PLAYER_KILL.test(kill(Killer.NONE), () -> 0d)).isFalse();
    }

    @Test
    void randomChanceDrawsOnceAndPassesBelowTheChance() {
        AtomicInteger draws = new AtomicInteger();
        assertThat(HuntCondition.randomChance(0.5f).test(kill(Killer.NONE), () -> {
            draws.incrementAndGet();
            return 0.49d;
        })).isTrue();
        assertThat(HuntCondition.randomChance(0.5f).test(kill(Killer.NONE), () -> 0.5d)).isFalse();
        assertThat(draws.get()).isEqualTo(1);
    }

    @Test
    void uniformCountCoversBothEnds() {
        HuntCount.Uniform count = new HuntCount.Uniform(1, 4);
        assertThat(count.roll(kill(Killer.NONE), () -> 0d)).isEqualTo(1);
        assertThat(count.roll(kill(Killer.NONE), () -> 0.5d)).isEqualTo(3);
        assertThat(count.roll(kill(Killer.NONE), () -> 0.999_999d)).isEqualTo(4);
    }

    @Test
    void cowYieldsBeefHideAndBoneByItsRules() {
        // Scale 0 at draw 0: beef 6, the large hide and one bone; a predator takes the beef alone.
        assertThat(HuntDrops.yields("cow", bystander(), () -> 0d)).containsExactly(
            new HuntYield("tfc:food/beef", 6), new HuntYield("tfc:large_raw_hide", 1), new HuntYield("minecraft:bone", 1));
        assertThat(HuntDrops.yields("cow", kill(Killer.LAND_PREDATOR), () -> 0d)).containsExactly(new HuntYield("tfc:food/beef", 6));
        // A player kill at the top of the size range: beef from the maximum bound (17 at draw 0).
        assertThat(HuntDrops.yields("cow", new HuntKill(Killer.PLAYER, true, false, true, 46f, 0f, 0f), () -> 0d))
            .containsExactly(new HuntYield("tfc:food/beef", 17), new HuntYield("tfc:large_raw_hide", 1), new HuntYield("minecraft:bone", 1));
    }

    @Test
    void randomDropsReadTheirDrawsInTableOrder() {
        // Rabbit: meat (not predated), small hide (chance 0.5), bone (not predated), foot (chance 0.1).
        // The draws 0.3 and 0.9 pass the hide and fail the foot.
        List<Double> draws = List.of(0.3d, 0.9d);
        AtomicInteger next = new AtomicInteger();
        assertThat(HuntDrops.yields("rabbit", bystander(), () -> draws.get(next.getAndIncrement())))
            .containsExactly(new HuntYield("tfc:food/rabbit", 1), new HuntYield("tfc:small_raw_hide", 1), new HuntYield("minecraft:bone", 1));
        assertThat(next.get()).isEqualTo(2);
        // A predator takes the meat and the bone; the hide and the foot still roll their own chances.
        assertThat(HuntDrops.yields("rabbit", kill(Killer.LAND_PREDATOR), () -> 0d))
            .containsExactly(new HuntYield("tfc:small_raw_hide", 1), new HuntYield("minecraft:rabbit_foot", 1));
    }

    @Test
    void speciesWithoutDropsYieldNothing() {
        assertThat(HuntDrops.drops("jellyfish")).isEmpty();
        assertThat(HuntDrops.yields("jellyfish", bystander(), () -> 0d)).isEmpty();
        assertThat(HuntDrops.species()).hasSize(83).contains("cow", "cod", "rat");
    }

    @Test
    void unknownSpeciesIsRejected() {
        assertThatThrownBy(() -> HuntDrops.drops("dragon")).isInstanceOf(IllegalArgumentException.class);
    }

    private static HuntKill kill(Killer killer) {
        return new HuntKill(killer, false, false, false, 1f, 0f, 0f);
    }

    private static HuntKill bystander() {
        return new HuntKill(Killer.NONE, false, false, true, 1f, 0f, 0f);
    }
}
