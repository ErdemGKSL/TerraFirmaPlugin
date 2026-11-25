/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Test;

import dev.tfp.core.calendar.CalendarTicks;

/**
 * Golden values for the prey flee rules at c3b4d9536.
 *
 * <p>Sources: {@code ai/prey/PreyAi} (AVOID_RANGE = 20 * 20, RETREAT_DURATION = TimeUtil.rangeOfSeconds(7, 22),
 * wasHurtBy, setAvoidTarget, wantsToStopFleeing), {@code ai/prey/AvoidPredatorsBehavior.isAvoided(false)},
 * {@code ai/prey/AvoidPredatorAndRammersBehavior.create(false)}, {@code prey/Prey.hurt}, and the tag files
 * {@code generated/data/tfc/tags/entity_type/land_predators.json} and {@code ramming_animals.json}. The flee
 * distance 15 is the {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.1F, 15, false)} argument.
 * TFC has no ServerConfig number for any of these, so the constants are checked against the source literals.
 */
class PreyFleeGoldenTest {

    private static final Position ORIGIN = new Position(0, 64, 0);

    private static Sighting boar(double distance) {
        // ramming_animals.json lists tfc:boar; a ramming animal is avoided when it is not a player
        return new Sighting("tfc:boar", false, false, false, true, new Position(distance, 64, 0));
    }

    private static Sighting bear(double distance) {
        // land_predators.json lists #tfc:bears
        return new Sighting("tfc:brown_bear", false, false, true, false, new Position(distance, 64, 0));
    }

    private static Sighting player(double distance, boolean creative) {
        return new Sighting("minecraft:player", true, creative, false, false, new Position(distance, 64, 0));
    }

    private static Sighting cow(double distance) {
        return new Sighting("tfc:cow", false, false, false, false, new Position(distance, 64, 0));
    }

    @Test
    void constantsMatchTfcLiterals() {
        // AVOID_RANGE = 20 * 20
        assertThat(PreyFlee.AVOID_RANGE_SQ).isEqualTo(400);
        // rangeOfSeconds(7, 22) at 20 ticks per second
        assertThat(PreyFlee.RETREAT_MIN_TICKS).isEqualTo(140);
        assertThat(PreyFlee.RETREAT_MAX_TICKS).isEqualTo(440);
        assertThat(CalendarTicks.PLAYER_TICKS_PER_SECOND).isEqualTo(20);
        // SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.1F, 15, false) and getPosAway(mob, 16, 7, pos)
        assertThat(PreyFlee.AWAY_DISTANCE).isEqualTo(15);
        assertThat(PreyFlee.FLEE_SPEED).isEqualTo(1.1f);
        assertThat(PreyFlee.ARMADILLO_FLEE_SPEED).isEqualTo(2.0f);
        assertThat(PreyFlee.AWAY_HORIZONTAL).isEqualTo(16);
        assertThat(PreyFlee.AWAY_VERTICAL).isEqualTo(7);
    }

    @Test
    void ramAndLandPredatorTriggerAtNearAndFarDistances() {
        // No distance threshold on the trigger: the sensor sets what is visible. Near and far both trigger.
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(boar(1)))).contains(boar(1));
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(bear(16)))).contains(bear(16));
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(player(3, false)))).contains(player(3, false));
    }

    @Test
    void creativeOrSpectatorPlayerAndPlainAnimalDoNotTrigger() {
        // isAvoided: NO_CREATIVE_OR_SPECTATOR must pass; a cow is in neither tag
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(player(1, true)))).isEmpty();
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(cow(2)))).isEmpty();
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of())).isEmpty();
    }

    @Test
    void creativePlayerInRamTagIsNotAvoided() {
        // AvoidPredatorAndRammers: the ramming clause needs !(Player), and the player clause needs no creative mode
        final Sighting creativeRammer = new Sighting("minecraft:player", true, true, false, true, new Position(1, 64, 0));
        assertThat(creativeRammer.avoided()).isFalse();
    }

    @Test
    void noTriggerWhileAlreadyFleeing() {
        // absent(AVOID_TARGET): the activity only starts without a memory
        assertThat(PreyFlee.trigger(true, ORIGIN, List.of(bear(2)))).isEmpty();
    }

    @Test
    void nearestAvoidedSightingWins() {
        // findClosest: the wolf at 3 blocks is nearer than the boar at 5, and the cow at 1 is not avoided
        final List<Sighting> visible = List.of(boar(5), cow(1), bear(3));
        assertThat(PreyFlee.trigger(false, ORIGIN, visible)).contains(bear(3));
    }

    @Test
    void retreatLengthIsSevenToTwentyTwoSecondsInPlayerTicks() {
        assertThat(PreyFlee.retreatTicksForOffset(0)).isEqualTo(140);
        assertThat(PreyFlee.retreatTicksForOffset(300)).isEqualTo(440);
        assertThatThrownBy(() -> PreyFlee.retreatTicksForOffset(301)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreyFlee.retreatTicksForOffset(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fleeStopsAfterTheRetreatRunsOut() {
        // setMemoryWithExpiry: the flee starts at tick 1000 with 140 ticks, and the memory is gone at 1140
        final FleeState flee = PreyFlee.startFlee(1000, "tfc:brown_bear", 140);
        assertThat(flee.expiresAtTick()).isEqualTo(1140);
        assertThat(flee.isActive(1139)).isTrue();
        assertThat(PreyFlee.wantsToStopFleeing(flee, 1139, 0)).isFalse();
        assertThat(flee.isActive(1140)).isFalse();
        assertThat(PreyFlee.wantsToStopFleeing(flee, 1140, 0)).isTrue();
    }

    @Test
    void fleeStopsOnlyStrictlyBeyondTwentyBlocks() {
        // distanceToSqr > AVOID_RANGE (400): exactly 20 blocks keeps fleeing, a little more stops
        final FleeState flee = PreyFlee.startFlee(0, "tfc:brown_bear", 300);
        assertThat(PreyFlee.wantsToStopFleeing(flee, 0, 399.0)).isFalse();
        assertThat(PreyFlee.wantsToStopFleeing(flee, 0, 400.0)).isFalse();
        assertThat(PreyFlee.wantsToStopFleeing(flee, 0, 400.25)).isTrue();
    }

    @Test
    void idleStateHasNoThreatAndStopsAtOnce() {
        assertThat(FleeState.IDLE.isActive(0)).isFalse();
        assertThat(PreyFlee.wantsToStopFleeing(FleeState.IDLE, 0, 0)).isTrue();
    }

    @Test
    void hurtBySameTypeKeepsTheThreatAndHurtByAnotherSwitchesIt() {
        // wasHurtBy: avoid.getType() != attacker.getType() is required to switch
        final FleeState flee = PreyFlee.startFlee(0, "tfc:boar", 300);
        assertThat(PreyFlee.hurt(flee, 10, "tfc:boar", 200)).isEqualTo(flee);
        assertThat(PreyFlee.hurt(flee, 10, "minecraft:player", 200)).isEqualTo(new FleeState("minecraft:player", 210));
    }

    @Test
    void hurtOnlySwitchesWhileTheMemoryIsPresent() {
        // getAvoidTarget is empty once expired, and with no flee the TFC branch does nothing
        final FleeState expired = PreyFlee.startFlee(0, "tfc:boar", 140);
        assertThat(PreyFlee.hurt(expired, 140, "minecraft:player", 200)).isEqualTo(expired);
        assertThat(PreyFlee.hurt(FleeState.IDLE, 0, "minecraft:player", 200)).isEqualTo(FleeState.IDLE);
    }

    @Test
    void sameTypeFriendTakesTheNearerOfItsThreatAndTheAttacker() {
        // friend at 0; its threat is a boar at 10; the attacker wolf at 3 is nearer, so the friend switches to the wolf
        final Position friend = ORIGIN;
        final FleeState friendFlee = PreyFlee.startFlee(0, "tfc:boar", 140);
        final Sighting attacker = bear(3);
        assertThat(PreyFlee.broadcast(friend, friendFlee, 0, new Position(10, 64, 0), attacker, 250))
            .isEqualTo(new FleeState("tfc:brown_bear", 250));
        // attacker at 30 is farther than the boar at 10, so the friend keeps the boar with the fresh length
        final Sighting farAttacker = bear(30);
        assertThat(PreyFlee.broadcast(friend, friendFlee, 0, new Position(10, 64, 0), farAttacker, 250))
            .isEqualTo(new FleeState("tfc:boar", 250));
    }

    @Test
    void friendWithoutAnActiveThreatTakesTheAttacker() {
        final Sighting attacker = bear(4);
        assertThat(PreyFlee.broadcast(ORIGIN, FleeState.IDLE, 100, null, attacker, 200))
            .isEqualTo(new FleeState("tfc:brown_bear", 300));
        // an expired threat is absent too
        final FleeState expired = PreyFlee.startFlee(0, "tfc:boar", 140);
        assertThat(PreyFlee.broadcast(ORIGIN, expired, 140, new Position(10, 64, 0), attacker, 200))
            .isEqualTo(new FleeState("tfc:brown_bear", 340));
    }

    @Test
    void awayCandidateWithCentreDrawsPointsStraightAwayFromTheThreat() {
        // angle draw 0.5 -> no offset; radius draw 0.5 -> sqrt(0.5) * sqrt(2) * 16 = 16 (float sqrt(2) rounding)
        final double[] xz = PreyFlee.awayXz(10, 0, 0.5f, 0.5).orElseThrow();
        assertThat(xz[0]).isCloseTo(16.0, org.assertj.core.api.Assertions.within(1e-5));
        // vanilla subtracts the float PI / 2, so the straight-away point is off by about 1e-6
        assertThat(xz[1]).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-5));
        // away along -z: the candidate is on -z
        final double[] south = PreyFlee.awayXz(0, -3, 0.5f, 0.25).orElseThrow();
        assertThat(south[0]).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-5));
        assertThat(south[1]).isCloseTo(-Math.sqrt(0.25) * (float) Math.sqrt(2.0f) * 16, org.assertj.core.api.Assertions.within(1e-5));
    }

    @Test
    void awayCandidateTurnsAtMostAQuarterTurnAndRejectsOutOfRange() {
        // angle draw 1 -> +PI/2: away +x becomes +z
        final double[] turned = PreyFlee.awayXz(1, 0, 1.0f, 0.25).orElseThrow();
        assertThat(turned[0]).isCloseTo(0.0, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(turned[1]).isGreaterThan(0.0);
        // radius draw 0.99 straight away: 0.995 * 22.6 = 22.5 > 16, rejected (vanilla returns null)
        assertThat(PreyFlee.awayXz(1, 0, 0.5f, 0.99)).isEmpty();
        // a diagonal at full radius stays within 16 on both axes and is accepted
        assertThat(PreyFlee.awayXz(1, 1, 0.5f, 0.99)).isPresent();
    }

    @Test
    void runsAwayOnlyWhileCloserThanFifteenBlocks() {
        assertThat(PreyFlee.runsAway(224.99)).isTrue();
        assertThat(PreyFlee.runsAway(225.0)).isFalse();
    }

    @Test
    void sightedMemoryHasNoExpiry() {
        // AvoidPredatorsBehavior: avoiding.set(closest) without expiry; only distance ends it
        final FleeState seen = PreyFlee.sighted("minecraft:player");
        assertThat(seen.isActive(Long.MAX_VALUE - 1)).isTrue();
        assertThat(PreyFlee.wantsToStopFleeing(seen, 1_000_000, 400.0)).isFalse();
        assertThat(PreyFlee.wantsToStopFleeing(seen, 1_000_000, 401.0)).isTrue();
    }

    @Test
    void avoidanceFormsMatchEachBrain() {
        // rammers (AvoidPredatorsBehavior.create(true)): players and rammers are not feared, predators are
        assertThat(Avoidance.PREDATORS_ONLY.avoids(player(2, false))).isFalse();
        assertThat(Avoidance.PREDATORS_ONLY.avoids(boar(2))).isFalse();
        assertThat(Avoidance.PREDATORS_ONLY.avoids(bear(2))).isTrue();
        // armadillo (AvoidPredatorAndRammersBehavior.create(true)): rammers and predators, not players
        assertThat(Avoidance.PREDATORS_AND_RAMMERS.avoids(player(2, false))).isFalse();
        assertThat(Avoidance.PREDATORS_AND_RAMMERS.avoids(boar(2))).isTrue();
        assertThat(Avoidance.PREDATORS_AND_RAMMERS.avoids(bear(2))).isTrue();
        // prey and pests: all three, creative players excepted
        assertThat(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS.avoids(player(2, false))).isTrue();
        assertThat(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS.avoids(player(2, true))).isFalse();
        // a rammer seeing a player and a bear: only the bear starts the flee
        assertThat(PreyFlee.trigger(false, ORIGIN, List.of(player(1, false), bear(9)), Avoidance.PREDATORS_ONLY))
            .contains(bear(9));
    }

    @Test
    void seededDrawGivesARetreatInRange() {
        // a seeded java.util.Random is a RandomGenerator; the draw lands inside the inclusive 140..440 range
        final int ticks = PreyFlee.retreatTicks(new Random(1L));
        assertThat(ticks).isBetween(140, 440);
        assertThat(optionalThreat(PreyFlee.trigger(false, ORIGIN, List.of(bear(2))))).isEqualTo("tfc:brown_bear");
    }

    private static String optionalThreat(Optional<Sighting> sighting) {
        return sighting.map(Sighting::type).orElse(null);
    }
}
