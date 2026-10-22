/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/items/TFCFishingRodItem.java @ c3b4d9536, common/entities/misc/TFCFishingHook.java,
 * common/entities/ai/GetHookedGoal.java (hand-checked values)
 */
package dev.tfp.core.fishing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.tfp.core.fishing.Hook.Hooked;
import dev.tfp.core.fishing.Hook.Retrieve;
import dev.tfp.core.metal.MetalTier;
import org.junit.jupiter.api.Test;

/** Unit tests for the rod strength, the bait rules, the hook pull and its exhaustion, and the pull velocity. */
class FishingRulesTest {

    @Test
    void rodStrengthIsTierSpeedOverTwelvePlusOne() {
        assertThat(FishingRod.strength(MetalTier.COPPER)).isCloseTo(5.25f / 12f + 1f, within(1e-6f));
        assertThat(FishingRod.strength(MetalTier.STEEL)).isCloseTo(9.5f / 12f + 1f, within(1e-6f));
        assertThat(FishingRod.canCast(false)).isFalse();
        assertThat(FishingRod.canCast(true)).isTrue();
    }

    @Test
    void baitTypeChecksSmallFirst() {
        assertThat(BaitType.of(true, true)).isEqualTo(BaitType.SMALL);
        assertThat(BaitType.of(false, true)).isEqualTo(BaitType.LARGE);
        assertThat(BaitType.of(true, false)).isEqualTo(BaitType.SMALL);
        assertThat(BaitType.of(false, false)).isEqualTo(BaitType.NONE);
    }

    @Test
    void rodsTakeBaitByTheirHoldingTags() {
        // copper holds small only, wrought iron holds both (holds_small contains holds_large)
        assertThat(BaitRules.canLoad(true, BaitType.SMALL, true, false)).isTrue();
        assertThat(BaitRules.canLoad(true, BaitType.LARGE, true, false)).isFalse();
        assertThat(BaitRules.canLoad(true, BaitType.LARGE, true, true)).isTrue();
        assertThat(BaitRules.canLoad(true, BaitType.NONE, true, true)).isFalse();
        assertThat(BaitRules.canLoad(false, BaitType.SMALL, true, true)).isFalse();
    }

    @Test
    void largeAnimalsTakeLargeBaitOnly() {
        assertThat(BaitRules.hookAccepts(BaitType.LARGE, true)).isTrue();
        assertThat(BaitRules.hookAccepts(BaitType.SMALL, true)).isFalse();
        assertThat(BaitRules.hookAccepts(BaitType.SMALL, false)).isTrue();
        assertThat(BaitRules.hookAccepts(BaitType.LARGE, false)).isFalse();
        assertThat(BaitRules.hookAccepts(BaitType.NONE, false)).isFalse();
    }

    @Test
    void largeAnimalsAlwaysEatTheBaitAndDrawNothing() {
        assertThat(BaitRules.eatsBait(true, () -> {
            throw new AssertionError("no draw for a large animal");
        })).isTrue();
    }

    @Test
    void smallAnimalsEatTheBaitWithChanceOneTwelfth() {
        assertThat(BaitRules.eatsBait(false, () -> 0.0)).isTrue();
        assertThat(BaitRules.eatsBait(false, () -> 0.08)).isTrue();
        assertThat(BaitRules.eatsBait(false, () -> 0.09)).isFalse();
        assertThat(BaitRules.eatsBait(false, () -> 0.999)).isFalse();
    }

    @Test
    void aPullOnTheGroundWithALiveCatchTakesTwoDamageAndKeepsTheHook() {
        Retrieve r = Hook.fresh().retrieve(100L, Hooked.LIVE, true, true);
        assertThat(r.damage()).isEqualTo(2);
        assertThat(r.pulled()).isTrue();
        assertThat(r.discarded()).isFalse();
        assertThat(r.baitEaten()).isFalse();
        assertThat(r.warning()).isFalse();
        assertThat(r.tooHard()).isFalse();
        assertThat(r.next()).isEqualTo(new Hook(0, 100L));
    }

    @Test
    void aPullInTheAirTakesOneDamageAndDiscardsWithoutACatch() {
        Retrieve r = Hook.fresh().retrieve(100L, Hooked.NONE, true, false);
        assertThat(r.damage()).isEqualTo(1);
        assertThat(r.pulled()).isFalse();
        assertThat(r.discarded()).isTrue();
    }

    @Test
    void aRemovedCatchDiscardsTheHook() {
        Retrieve r = Hook.fresh().retrieve(100L, Hooked.REMOVED, true, true);
        assertThat(r.pulled()).isTrue();
        assertThat(r.discarded()).isTrue();
    }

    @Test
    void withoutAnOwnerReadyNothingIsPulledNoDamageIsTakenAndTheTimeStillMoves() {
        Retrieve r = Hook.fresh().retrieve(100L, Hooked.LIVE, false, true);
        assertThat(r.damage()).isZero();
        assertThat(r.pulled()).isFalse();
        assertThat(r.discarded()).isFalse();
        assertThat(r.next()).isEqualTo(new Hook(0, 100L));
    }

    @Test
    void rapidPullsBuildExhaustionUntilTheHookBreaks() {
        Hook hook = new Hook(0, 100L);
        Retrieve first = hook.retrieve(101L, Hooked.LIVE, true, true);
        assertThat(first.next()).isEqualTo(new Hook(48, 101L));
        assertThat(first.warning()).isTrue();
        assertThat(first.damage()).isEqualTo(2);

        Retrieve second = first.next().retrieve(102L, Hooked.LIVE, true, true);
        assertThat(second.next()).isEqualTo(new Hook(96, 102L));

        Retrieve third = second.next().retrieve(103L, Hooked.LIVE, true, true);
        assertThat(third.tooHard()).isTrue();
        assertThat(third.baitEaten()).isTrue();
        assertThat(third.discarded()).isTrue();
        assertThat(third.pulled()).isFalse();
        assertThat(third.damage()).isEqualTo(1);
        assertThat(third.next()).isEqualTo(new Hook(144, 102L));
    }

    @Test
    void aPullOutsideTheWindowAddsNoExhaustion() {
        Retrieve edge = new Hook(7, 100L).retrieve(125L, Hooked.LIVE, true, true);
        assertThat(edge.next()).isEqualTo(new Hook(7, 125L));
        assertThat(edge.warning()).isFalse();

        Retrieve inside = new Hook(7, 100L).retrieve(124L, Hooked.LIVE, true, true);
        assertThat(inside.next().exhaustion()).isEqualTo(9);
        assertThat(inside.warning()).isTrue();
    }

    @Test
    void exhaustionFallsOnePerHookTickToZero() {
        assertThat(new Hook(5, 0L).tick()).isEqualTo(new Hook(4, 0L));
        assertThat(new Hook(0, 0L).tick()).isEqualTo(new Hook(0, 0L));
    }

    @Test
    void theHookedAnimalIsPulledTowardTheOwnerAndLiftedWhenClose() {
        // owner above the hook, within 5 on x and z: dy becomes 4, then normalised to strength 1
        Hook.Velocity v = Hook.pullVelocity(0, 70, 0, 3, 64, 0, 1f);
        assertThat(v.x()).isCloseTo(-0.6, within(1e-9));
        assertThat(v.y()).isCloseTo(0.8, within(1e-9));
        assertThat(v.z()).isCloseTo(0.0, within(1e-9));

        Hook.Velocity still = Hook.pullVelocity(4, 64, 0, 4, 64, 0, 2f);
        assertThat(still).isEqualTo(new Hook.Velocity(0, 0, 0));
    }
}
