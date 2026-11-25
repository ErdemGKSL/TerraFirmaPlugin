/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/aquatic/AmphibiousAnimal.java @ c3b4d9536 (customServerAiStep, hurt,
 * createAttributes), ai/amphibian/AmphibianAi.java (updateActivity), ai/amphibian/AmphibianPlayDeadBehavior.java (update),
 * ai/amphibian/PinnipedAI.java (updateActivity, MEMORY_TYPES), aquatic/Penguin.java, aquatic/LeopardSeal.java, same commit
 */
package dev.tfp.core.amphibian;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.aquatic.PlayDeadTimer;
import org.junit.jupiter.api.Test;

/**
 * Golden tests of the amphibian brains' activity and play-dead wiring, hand-traced from the TFC methods named above at
 * c3b4d9536: the countdown runs in {@code updateActivity} after the behaviours, the activity is chosen unless it was
 * {@code PLAY_DEAD}, and the flag is set out of water only.
 */
class AmphibianMemoryGoldenTest {

    @Test
    void speciesFactsMatchTfc() {
        // AmphibiousAnimal.createAttributes: 14 hp, speed 1.0, 2 damage, step 1.0; Penguin 8 hp; LeopardSeal 24 hp / 6 damage
        assertThat(AmphibianSpecies.TURTLE.attributes()).isEqualTo(new AmphibianSpecies.Attributes(14, 1, 2, 1));
        assertThat(AmphibianSpecies.PENGUIN.attributes()).isEqualTo(new AmphibianSpecies.Attributes(8, 1, 2, 1));
        assertThat(AmphibianSpecies.LEOPARD_SEAL.attributes()).isEqualTo(new AmphibianSpecies.Attributes(24, 1, 6, 1));
        // isPlayingDeadEffective: true by default, false for Penguin and LeopardSeal
        assertThat(AmphibianSpecies.TURTLE.playingDeadEffective()).isTrue();
        assertThat(AmphibianSpecies.PENGUIN.playingDeadEffective()).isFalse();
        assertThat(AmphibianSpecies.LEOPARD_SEAL.playingDeadEffective()).isFalse();
        // PinnipedAI.MEMORY_TYPES lacks PLAY_DEAD_TICKS
        assertThat(AmphibianSpecies.LEOPARD_SEAL.playsDead()).isFalse();
        assertThat(AmphibianSpecies.of("tfc:penguin")).contains(AmphibianSpecies.PENGUIN);
        assertThat(AmphibianSpecies.TURTLE.foodTag()).isEqualTo("tfc:turtle_food");
        assertThat(AmphibianSpecies.LEOPARD_SEAL.foodTag()).isEqualTo("tfc:seal_food");
        // getSpeedModifier 0.5 / 0.15, getChasingSpeedModifier 0.6 / 0.17, getFleeingSpeedModifier 0.8 / 0.2
        assertThat(AmphibianSpecies.walkSpeed(true)).isEqualTo(0.5f);
        assertThat(AmphibianSpecies.walkSpeed(false)).isEqualTo(0.15f);
        assertThat(AmphibianSpecies.chaseSpeed(true)).isEqualTo(0.6f);
        assertThat(AmphibianSpecies.chaseSpeed(false)).isEqualTo(0.17f);
        assertThat(AmphibianSpecies.fleeSpeed(true)).isEqualTo(0.8f);
        assertThat(AmphibianSpecies.fleeSpeed(false)).isEqualTo(0.2f);
    }

    @Test
    void aHitTurtleOnLandPlaysDeadFor200TicksThenGoesBackToIdle() {
        AmphibianMemory m = AmphibianMemory.FRESH.hurt(AmphibianSpecies.TURTLE, 2f, true, true);
        assertThat(m.timer()).isEqualTo(new PlayDeadTimer(200, false));
        assertThat(m.hurtBy()).isTrue();
        // tick 1: countdown 200 -> 199, IDLE -> PLAY_DEAD, flag set (out of water)
        m = m.tick(AmphibianSpecies.TURTLE, false);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        assertThat(m.timer()).isEqualTo(new PlayDeadTimer(199, true));
        // ticks 2..200: down to 0; the flag drops when the countdown reaches 0
        for (int i = 2; i <= 199; i++) {
            m = m.tick(AmphibianSpecies.TURTLE, false);
        }
        assertThat(m.timer()).isEqualTo(new PlayDeadTimer(1, true));
        m = m.tick(AmphibianSpecies.TURTLE, false);
        assertThat(m.timer()).isEqualTo(new PlayDeadTimer(0, false));
        assertThat(m.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        // tick 201: the countdown at 0 is erased with HURT_BY_ENTITY and the brain uses its default activity
        m = m.tick(AmphibianSpecies.TURTLE, false);
        assertThat(m.timer().hasTimer()).isFalse();
        assertThat(m.hurtBy()).isFalse();
        assertThat(m.activity()).isEqualTo(AmphibianActivity.IDLE);
        assertThat(m.playingDead()).isFalse();
    }

    @Test
    void inWaterTheFlagKeepsItsValueAndIsSetOnceAshore() {
        AmphibianMemory m = AmphibianMemory.FRESH.hurt(AmphibianSpecies.PENGUIN, 1f, true, true);
        m = m.tick(AmphibianSpecies.PENGUIN, true);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        assertThat(m.playingDead()).isFalse();
        m = m.tick(AmphibianSpecies.PENGUIN, false);
        assertThat(m.playingDead()).isTrue();
        assertThat(m.timer().ticks()).isEqualTo(198);
        // back in water it stays "dead" until ashore again after the countdown
        for (int i = 0; i < 200; i++) {
            m = m.tick(AmphibianSpecies.PENGUIN, true);
        }
        assertThat(m.activity()).isEqualTo(AmphibianActivity.IDLE);
        assertThat(m.playingDead()).isTrue();
        m = m.tick(AmphibianSpecies.PENGUIN, false);
        assertThat(m.playingDead()).isFalse();
    }

    @Test
    void smallHitsDeadMobsAndHitsWhilePlayingDeadDoNotArmTheTimer() {
        // amount > 0.5F, getHealth() > 0, !isPlayingDead()
        assertThat(AmphibianMemory.FRESH.hurt(AmphibianSpecies.TURTLE, 0.5f, true, true).timer().hasTimer()).isFalse();
        assertThat(AmphibianMemory.FRESH.hurt(AmphibianSpecies.TURTLE, 3f, false, true).timer().hasTimer()).isFalse();
        AmphibianMemory dead = AmphibianMemory.FRESH.hurt(AmphibianSpecies.TURTLE, 3f, true, true)
                .tick(AmphibianSpecies.TURTLE, false).tick(AmphibianSpecies.TURTLE, false);
        assertThat(dead.timer().ticks()).isEqualTo(198);
        assertThat(dead.hurt(AmphibianSpecies.TURTLE, 3f, true, true).timer().ticks()).isEqualTo(198);
    }

    @Test
    void aHitDuringAFightTurnsItIntoPlayingDeadAndDropsTheTarget() {
        AmphibianMemory m = AmphibianMemory.FRESH.startAttacking().tick(AmphibianSpecies.TURTLE, true);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.FIGHT);
        m = m.hurt(AmphibianSpecies.TURTLE, 2f, true, true).tick(AmphibianSpecies.TURTLE, true);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.PLAY_DEAD);
        assertThat(m.attackTarget()).isFalse();
    }

    @Test
    void aHitSealRetreatsAndNeverPlaysDead() {
        AmphibianMemory m = AmphibianMemory.FRESH.hurt(AmphibianSpecies.LEOPARD_SEAL, 5f, true, true);
        assertThat(m.timer().hasTimer()).isFalse();
        m = m.tick(AmphibianSpecies.LEOPARD_SEAL, false);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.AVOID);
        assertThat(m.playingDead()).isFalse();
        // PinnipedAI.updateActivity never leaves AVOID; the sensor dropping the attacker does not change the activity
        m = m.attackerGone().startAttacking().tick(AmphibianSpecies.LEOPARD_SEAL, false);
        assertThat(m.activity()).isEqualTo(AmphibianActivity.AVOID);
        // a hit by something that is not a living entity does not set HURT_BY_ENTITY
        assertThat(AmphibianMemory.FRESH.hurt(AmphibianSpecies.LEOPARD_SEAL, 5f, true, false)
                .tick(AmphibianSpecies.LEOPARD_SEAL, false).activity()).isEqualTo(AmphibianActivity.IDLE);
    }

    @Test
    void huntedRecentlyLastsAThousandTicks() {
        AmphibianMemory m = AmphibianMemory.FRESH.killed(5_000);
        assertThat(m.huntedRecently(5_999)).isTrue();
        assertThat(m.huntedRecently(6_000)).isFalse();
        assertThat(AmphibianMemory.FRESH.huntedRecently(0)).isFalse();
    }
}
