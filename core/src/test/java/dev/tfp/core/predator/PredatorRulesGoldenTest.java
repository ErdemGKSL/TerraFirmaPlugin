/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.predator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.Sighting;

/**
 * Golden values for the predator hunt trigger, prey choice and low-health pass at c3b4d9536.
 *
 * <p>Sources: {@code ai/predator/PredatorAi.getAttackTarget} (player memory first, then
 * {@code findClosest(HUNTED_BY_LAND_PREDATORS and !isInWater)}), {@code PredatorAi.getDisturbedAttackTarget} (the rest
 * gate on {@code WAKEUP_TICKS}), {@code PredatorAi.isPacified} (baby, {@code PACIFIED}, {@code HUNTED_RECENTLY}),
 * {@code PredatorBehaviors.becomePassiveIf(p -> p.getHealth() < 5f, 200)} as used in the hunt and fight activities.
 * Distances are squared, so a candidate {@code d} blocks away on the x axis has squared distance {@code d * d}.
 * TFC has no ServerConfig value here.
 */
class PredatorRulesGoldenTest {

    private static final Position HOME_SPOT = new Position(0, 64, 0);
    private static final long NOW = 50_000L;

    private static PreyCandidate deer(double d) {
        return new PreyCandidate(new Sighting("tfc:deer", false, false, false, false, new Position(d, 64, 0)), false);
    }

    private static PreyCandidate cow(double d) {
        return new PreyCandidate(new Sighting("tfc:cow", false, false, false, false, new Position(d, 64, 0)), false);
    }

    private static PreyCandidate rat(double d) {
        return new PreyCandidate(new Sighting("tfc:rat", false, false, false, false, new Position(d, 64, 0)), false);
    }

    private static PreyCandidate wolf(double d) {
        return new PreyCandidate(new Sighting("tfc:wolf", false, false, false, false, new Position(d, 64, 0)), false);
    }

    private static PreyCandidate player(double d, boolean creative) {
        return new PreyCandidate(new Sighting("minecraft:player", true, creative, false, false, new Position(d, 64, 0)), false);
    }

    private static PredatorMemory awake() {
        return PredatorMemory.EMPTY;
    }

    private static PredatorMemory pacifiedUntil(long until) {
        return new PredatorMemory(until, PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE, false, false);
    }

    private static PredatorMemory disturbedUntil(long until) {
        return new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, until, PredatorMemory.NONE, false, false);
    }

    @Test
    void constantsMatchTfcLiterals() {
        // becomePassiveIf(..., 200) and getHealth() < 5f
        assertThat(PredatorRules.PASSIVE_HEALTH).isEqualTo(5.0f);
        assertThat(PredatorRules.PASSIVE_TICKS).isEqualTo(200);
        // setMemoryWithExpiry(HUNTED_RECENTLY, true, 12000)
        assertThat(PredatorRules.HUNTED_RECENTLY_TICKS).isEqualTo(12000);
        // setMemory(WAKEUP_TICKS, 100)
        assertThat(PredatorRules.WAKEUP_TICKS).isEqualTo(100);
    }

    @Test
    void theHuntTriggerIsTheHuntActivityOnlyAndAnyDistance() {
        // a diurnal bear at day time 0 is hunting; a visible deer 2 blocks away is chosen
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(deer(2)));
        assertThat(target).map(Sighting::type).contains("tfc:deer");
    }

    @Test
    void theRestActivityAttacksOnlyWhenDisturbed() {
        // REST without WAKEUP_TICKS: getDisturbedAttackTarget is empty
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.REST, awake(), NOW, false, HOME_SPOT, List.of(deer(3))))
            .isEmpty();
        // REST with the memory present (wakeupUntil = now + 1): the normal target applies
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.REST, disturbedUntil(NOW + 1), NOW, false, HOME_SPOT,
            List.of(deer(3)))).map(Sighting::type).contains("tfc:deer");
    }

    @Test
    void wakeupMemoryExpiresAfterOneHundredTicks() {
        // set at NOW for 100 ticks: present at NOW + 99, gone at NOW + 100
        final PredatorMemory disturbed = disturbedUntil(NOW + PredatorRules.WAKEUP_TICKS);
        assertThat(disturbed.wakeup(NOW + 99)).isTrue();
        assertThat(disturbed.wakeup(NOW + 100)).isFalse();
    }

    @Test
    void aBabyNeverPicksATarget() {
        // isPacified: baby is true, so no target even with a deer at 1 block
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, true, HOME_SPOT, List.of(deer(1))))
            .isEmpty();
    }

    @Test
    void aPacifiedOrHuntedRecentlyPredatorPicksNoTarget() {
        final PredatorMemory pacified = pacifiedUntil(NOW + 50);
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, pacified, NOW, false, HOME_SPOT, List.of(deer(3))))
            .isEmpty();
        final PredatorMemory hunted = new PredatorMemory(PredatorMemory.NONE, NOW + 10, PredatorMemory.NONE,
            PredatorMemory.NONE, false, false);
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, hunted, NOW, false, HOME_SPOT, List.of(deer(3))))
            .isEmpty();
    }

    @Test
    void anAttackablePlayerBeatsAnyCloserPrey() {
        // player memory first: the player at 10 blocks wins over the deer at 3
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(deer(3), player(10, false)));
        assertThat(target).map(Sighting::type).contains("minecraft:player");
    }

    @Test
    void aCreativeOrSpectatorPlayerIsSkippedAndPreyIsChosen() {
        // NO_CREATIVE_OR_SPECTATOR fails for a creative player, so the deer at 5 is the target
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(player(2, true), deer(5)));
        assertThat(target).map(Sighting::type).contains("tfc:deer");
    }

    @Test
    void preyInWaterIsNotHunted() {
        // !e.isInWater(): the cow at 2 in water is refused, the cow at 5 is taken
        final PreyCandidate cowInWater = new PreyCandidate(
            new Sighting("tfc:cow", false, false, false, false, new Position(2, 64, 0)), true);
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(cowInWater, cow(5)));
        assertThat(target).map(s -> s.at().x()).contains(5.0);
    }

    @Test
    void theNearestHuntablePreyIsChosen() {
        // rat at 6 and deer at 4: the deer
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(rat(6), deer(4)));
        assertThat(target).map(Sighting::type).contains("tfc:deer");
    }

    @Test
    void aNonHuntedTypeIsNeverChosenAsPrey() {
        // a wolf at 1 block is not in the tag; the cow at 9 is the target
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(wolf(1), cow(9)));
        assertThat(target).map(Sighting::type).contains("tfc:cow");
    }

    @Test
    void aTieKeepsTheEarlierCandidate() {
        // deer and cow both at 4 blocks: the earlier one in the list (findClosest keeps the first at equal distance)
        final Optional<Sighting> target = PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false,
            HOME_SPOT, List.of(deer(4), cow(4)));
        assertThat(target).map(Sighting::type).contains("tfc:deer");
    }

    @Test
    void noVisiblePreyMeansNoTarget() {
        assertThat(PredatorRules.attackTarget(PredatorSchedule.Activity.HUNT, awake(), NOW, false, HOME_SPOT, List.of(wolf(2))))
            .isEmpty();
    }

    @Test
    void lowHealthBelowFiveBecomesPassive() {
        // getHealth() < 5f: 4.99 triggers, 5.0 does not
        assertThat(PredatorRules.passiveTriggered(awake(), NOW, 4.99f)).isTrue();
        assertThat(PredatorRules.passiveTriggered(awake(), NOW, 5.0f)).isFalse();
    }

    @Test
    void lowHealthDropsTheTargetAndPacifiesForTwoHundredTicks() {
        // hunting a deer, health 4.0: attack erased, PACIFIED for 200 ticks
        final PredatorMemory hunting = new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, PredatorMemory.NONE,
            PredatorMemory.NONE, true, false);
        final PredatorMemory passive = PredatorRules.becomePassive(hunting, NOW, 4.0f);
        assertThat(passive.attacking()).isFalse();
        assertThat(passive.pacified(NOW + 199)).isTrue();
        assertThat(passive.pacified(NOW + 200)).isFalse();
    }

    @Test
    void alreadyPacifiedPredatorIsNotRetriggered() {
        // absent(PACIFIED) fails: the expiry is not extended by another low-health tick
        final PredatorMemory pacified = pacifiedUntil(NOW + 10);
        assertThat(PredatorRules.becomePassive(pacified, NOW, 1.0f)).isEqualTo(pacified);
    }
}
