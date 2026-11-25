/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.prey;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Golden values for the idle brain ({@code PreyAi}), the roster and attributes ({@code TFCEntities}, {@code Prey},
 * {@code RammingPrey}, {@code Pest}, {@code TFCRabbit}, {@code OviparousAnimal}) and the small prey changes
 * ({@code TFCAvoidEntityGoal}, {@code TFCFox}, {@code TFCRabbit}, {@code TFCPanda}, {@code TFCArmadillo},
 * {@code ScareSensor}) at c3b4d9536.
 */
class WildPreyGoldenTest {

    @Test
    void rosterUsesTheBrainOfEachTfcClass() {
        assertThat(WildPrey.species()).hasSize(16);
        assertThat(WildPrey.kind("tfc:deer")).contains(WildPrey.Kind.PREY);
        assertThat(WildPrey.kind("tfc:turkey")).contains(WildPrey.Kind.WINGED);
        assertThat(WildPrey.kind("tfc:bison")).contains(WildPrey.Kind.RAMMING);
        assertThat(WildPrey.kind("tfc:jerboa")).contains(WildPrey.Kind.PEST);
        assertThat(WildPrey.kind("tfc:cow")).isEmpty();
        // rammers: AvoidPredatorsBehavior.create(true), no AnimalPanic in the core activity
        assertThat(WildPrey.Kind.RAMMING.avoidance()).isEqualTo(Avoidance.PREDATORS_ONLY);
        assertThat(WildPrey.Kind.RAMMING.panics()).isFalse();
        assertThat(WildPrey.Kind.PEST.avoidance()).isEqualTo(Avoidance.PREDATORS_PLAYERS_AND_RAMMERS);
        assertThat(WildPrey.Kind.PREY.panics()).isTrue();
    }

    @Test
    void attributesMatchTfcRegistrations() {
        assertThat(WildPrey.attributes("tfc:deer")).contains(new WildPrey.Attributes(10, 0.3f, Double.NaN, 1.0f));
        assertThat(WildPrey.attributes("tfc:boar")).contains(new WildPrey.Attributes(20, 0.16f, 0.8, 1.0f));
        assertThat(WildPrey.attributes("tfc:wildebeest")).contains(new WildPrey.Attributes(30, 0.16f, 0.9, 1.0f));
        assertThat(WildPrey.attributes("tfc:moose")).contains(new WildPrey.Attributes(40, 0.12f, 1.0, 1.0f));
        assertThat(WildPrey.attributes("tfc:bison")).contains(WildPrey.RAMMING_LARGE);
        assertThat(WildPrey.attributes("tfc:rat")).contains(new WildPrey.Attributes(8, 0.3f, Double.NaN, Double.NaN));
        assertThat(WildPrey.attributes("tfc:rabbit")).contains(new WildPrey.Attributes(12, 0.3f, Double.NaN, Double.NaN));
        assertThat(WildPrey.attributes("tfc:grouse").orElseThrow().stepHeight()).isEqualTo(1.5);
        assertThat(WildPrey.attributes("tfc:cow")).isEmpty();
    }

    @Test
    void idleConstantsMatchPreyAi() {
        assertThat(PreyIdle.SWIM_CHANCE).isEqualTo(0.7f);
        assertThat(PreyIdle.PANIC_SPEED).isEqualTo(2.0f);
        assertThat(PreyIdle.LOOK_PLAYER_RANGE).isEqualTo(6.0);
        assertThat(PreyIdle.FOLLOW_SPEED).isEqualTo(1.25f);
        assertThat(PreyIdle.STROLL_HORIZONTAL).isEqualTo(10);
        assertThat(PreyIdle.STROLL_VERTICAL).isEqualTo(7);
        assertThat(PreyIdle.LOOK_WALK_CLOSE).isEqualTo(3);
    }

    @Test
    void durationsAreInclusiveVanillaDraws() {
        final Random r = new Random(5L);
        int min = Integer.MAX_VALUE, max = 0;
        for (int i = 0; i < 20_000; i++) {
            final int p = PreyIdle.panicTicks(r);
            min = Math.min(min, p);
            max = Math.max(max, p);
        }
        assertThat(min).isEqualTo(100);
        assertThat(max).isEqualTo(120);
        assertThat(PreyIdle.duration(30, 60, new Random(1L))).isEqualTo(30 + new Random(1L).nextInt(31));
    }

    @Test
    void movementIsOneOfThreeAndOffsetsDrawXThenYThenZ() {
        final Random a = new Random(8L);
        final Random b = new Random(8L);
        final PreyFlee.Offset o = PreyIdle.randomOffset(10, 7, a);
        assertThat(o).isEqualTo(new PreyFlee.Offset(b.nextInt(21) - 10, b.nextInt(15) - 7, b.nextInt(21) - 10));
        final Random c = new Random(2L);
        final int[] counts = new int[3];
        for (int i = 0; i < 30_000; i++) {
            counts[PreyIdle.movement(c).ordinal()]++;
        }
        for (int n : counts) {
            assertThat(n).isBetween(9_500, 10_500);
        }
    }

    @Test
    void babiesFollowAdultsBetweenFiveAndSeventeenBlocks() {
        assertThat(PreyIdle.followsAdult(true, 4.99 * 4.99)).isFalse();
        assertThat(PreyIdle.followsAdult(true, 25)).isTrue();
        assertThat(PreyIdle.followsAdult(true, 16.99 * 16.99)).isTrue();
        assertThat(PreyIdle.followsAdult(true, 17 * 17)).isFalse();
        assertThat(PreyIdle.followsAdult(false, 100)).isFalse();
    }

    private static SmallPrey.Threat wolf(double x, double speed, boolean sneaking, boolean water) {
        return new SmallPrey.Threat(new Position(x, 64, 0), speed, sneaking, water, false);
    }

    @Test
    void avoidGoalSpeedsAndStillPredators() {
        final Position self = new Position(0, 64, 0);
        assertThat(SmallPrey.avoidSpeeds("tfc:fox")).contains(new SmallPrey.Speeds(1.6, 1.4));
        assertThat(SmallPrey.avoidSpeeds("tfc:rabbit")).contains(new SmallPrey.Speeds(2.2, 2.2));
        assertThat(SmallPrey.avoidSpeeds("tfc:panda")).contains(new SmallPrey.Speeds(2.0, 2.0));
        assertThat(SmallPrey.FOX.at(48.9)).isEqualTo(1.4);
        assertThat(SmallPrey.FOX.at(49)).isEqualTo(1.6);
        // moving wolf at 8 is in range (<= 8), at 8.1 not
        assertThat(SmallPrey.avoid(self, List.of(wolf(8, 0.1, false, false)))).isPresent();
        assertThat(SmallPrey.avoid(self, List.of(wolf(8.1, 0.1, false, false)))).isEmpty();
        // a still or sneaking wolf is ignored unless it is in water; only the nearest counts
        assertThat(SmallPrey.avoid(self, List.of(wolf(3, 0.005, false, false)))).isEmpty();
        assertThat(SmallPrey.avoid(self, List.of(wolf(3, 0.2, true, false)))).isEmpty();
        assertThat(SmallPrey.avoid(self, List.of(wolf(3, 0.0, false, true)))).isPresent();
        assertThat(SmallPrey.avoid(self, List.of(wolf(2, 0.0, false, false), wolf(5, 0.3, false, false)))).isEmpty();
    }

    @Test
    void armadilloScareBoxAndDamage() {
        final Position self = new Position(0, 64, 0);
        assertThat(SmallPrey.armadilloScared(self, List.of(new SmallPrey.Threat(new Position(6.9, 65.9, 0), 0, false, false, false)))).isTrue();
        assertThat(SmallPrey.armadilloScared(self, List.of(new SmallPrey.Threat(new Position(7, 64, 0), 0, false, false, false)))).isFalse();
        assertThat(SmallPrey.armadilloScared(self, List.of(new SmallPrey.Threat(new Position(1, 66, 0), 0, false, false, false)))).isFalse();
        // a sleeping predator does not scare (TFCArmadillo.isScaredBy)
        assertThat(SmallPrey.armadilloScared(self, List.of(new SmallPrey.Threat(new Position(1, 64, 0), 0, false, false, true)))).isFalse();
        assertThat(SmallPrey.SCARE_MEMORY_TICKS).isEqualTo(80);
        // hit for 9: vanilla (9 - 1) / 2 = 4; TFC (9 - 1 - 1) / 2 = 3.5
        assertThat(SmallPrey.armadilloDamage((9 - 1) / 2.0)).isEqualTo((9 - 1 - 1) / 2.0);
        assertThat(SmallPrey.armadilloDamage(0.2)).isEqualTo(0.0);
    }
}
