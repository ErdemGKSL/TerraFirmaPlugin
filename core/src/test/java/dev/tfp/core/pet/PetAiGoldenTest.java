/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */
package dev.tfp.core.pet;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tfp.core.pet.PetAi.Action;
import dev.tfp.core.pet.PetAi.Pick;
import dev.tfp.core.pet.PetAi.Situation;
import org.junit.jupiter.api.Test;

/**
 * Golden values for the pet brain's periodic decisions at c3b4d9536, read from {@code TamableAi.updateActivity} (flee and
 * avoid and fight switches every call; owner-lost, rest, sit and sleep checks every 20 ticks), {@code isFarFromHome}
 * (strict squared distance, absent or foreign home is far), {@code getAttackTarget} / {@code getUnwantedAttackTarget} and
 * the species {@code canAttack} rules.
 */
class PetAiGoldenTest {
    private static final PetState.Home HOME = new PetState.Home("minecraft:overworld", 0, 64, 0);

    private static Situation at(PetActivity current) {
        return new Situation(current, 20f, false, false, false, false, true, false, false, false, PetState.NO_SIT_TICK,
            PetState.NO_SIT_TICK, 100_000L);
    }

    private static Situation with(Situation s, java.util.function.UnaryOperator<Builder> f) {
        return f.apply(new Builder(s)).build();
    }

    /** A tiny copy-and-change helper for the 13-field record. */
    private static final class Builder {
        PetActivity current;
        float health;
        boolean onFire, avoid, fleeTarget, attack, owner, far, hurt, sleeping;
        long sitTick, lastSlept, now;

        Builder(Situation s) {
            current = s.current();
            health = s.health();
            onFire = s.onFire();
            avoid = s.hasAvoidTarget();
            fleeTarget = s.fleeTargetPresent();
            attack = s.hasAttackTarget();
            owner = s.hasOwner();
            far = s.farFromHome();
            hurt = s.hurtRecently();
            sleeping = s.sleeping();
            sitTick = s.sitTick();
            lastSlept = s.lastSlept();
            now = s.now();
        }

        Situation build() {
            return new Situation(current, health, onFire, avoid, fleeTarget, attack, owner, far, hurt, sleeping, sitTick, lastSlept, now);
        }
    }

    @Test
    void homeDistanceIsStrictAndAbsentOrForeignHomeIsFar() {
        // isFarFromHome: dimension differs or distSqr > distance^2, orElse(true) with no HOME memory
        assertThat(PetAi.isFarFromHome(HOME, "minecraft:overworld", 36, 64, 0, 36)).isFalse();
        assertThat(PetAi.isFarFromHome(HOME, "minecraft:overworld", 37, 64, 0, 36)).isTrue();
        assertThat(PetAi.isFarFromHome(HOME, "minecraft:overworld", 21, 64, 29, 36)).isFalse(); // 441 + 841 = 1282 <= 1296
        assertThat(PetAi.isFarFromHome(HOME, "minecraft:overworld", 22, 64, 29, 36)).isTrue(); // 484 + 841 = 1325 > 1296
        assertThat(PetAi.isFarFromHome(HOME, "minecraft:the_nether", 0, 64, 0, 36)).isTrue();
        assertThat(PetAi.isFarFromHome(null, "minecraft:overworld", 0, 64, 0, 120)).isTrue();
        assertThat(PetAi.HOME_WANDER_DISTANCE).isEqualTo(36);
        assertThat(PetAi.HOME_LOST_DISTANCE).isEqualTo(120);
    }

    @Test
    void fleesBelowFiveHealthOrOnFire() {
        assertThat(PetAi.couldFlee(4.99f, false)).isTrue();
        assertThat(PetAi.couldFlee(5f, false)).isFalse();
        assertThat(PetAi.couldFlee(20f, true)).isTrue();
    }

    @Test
    void aHurtPetWithAThreatStartsFleeingThenAvoids() {
        Situation s = with(at(PetActivity.IDLE_AT_HOME), b -> {
            b.health = 4f;
            b.fleeTarget = true;
            return b;
        });
        PetAi.Update u = PetAi.update(s, false);
        assertThat(u.startFlee()).isTrue();
        assertThat(u.action()).isEqualTo(Action.AVOID);
        // without a threat to flee from there is nothing to avoid, so the fight/idle rules apply
        Situation noThreat = with(s, b -> {
            b.fleeTarget = false;
            return b;
        });
        assertThat(PetAi.update(noThreat, false).action()).isEqualTo(Action.NONE);
    }

    @Test
    void theAvoidActivityEndsWithARefreshOfTheStoredCommand() {
        Situation s = at(PetActivity.AVOID);
        assertThat(PetAi.update(s, false).action()).isEqualTo(Action.REFRESH_COMMAND);
        // still fleeing: stays in avoid
        Situation still = with(s, b -> {
            b.avoid = true;
            b.health = 3f;
            return b;
        });
        assertThat(PetAi.update(still, false).action()).isEqualTo(Action.AVOID);
        // an avoid target but healthy again: the activity ends
        Situation healed = with(s, b -> {
            b.avoid = true;
            return b;
        });
        assertThat(PetAi.update(healed, false).action()).isEqualTo(Action.REFRESH_COMMAND);
    }

    @Test
    void anAttackTargetStartsAFightEveryCall() {
        Situation s = with(at(PetActivity.HUNT), b -> {
            b.attack = true;
            return b;
        });
        assertThat(PetAi.update(s, false).action()).isEqualTo(Action.FIGHT);
        assertThat(PetAi.update(s, true).action()).isEqualTo(Action.FIGHT);
    }

    @Test
    void theSlowChecksOnlyRunEveryTwentyTicks() {
        Situation sit = with(at(PetActivity.SIT), b -> {
            b.hurt = true;
            return b;
        });
        assertThat(PetAi.update(sit, false).action()).isEqualTo(Action.NONE);
        assertThat(PetAi.update(sit, true).action()).isEqualTo(Action.IDLE_AT_HOME);
    }

    @Test
    void anOwnerlessFollowerBeginsIdleHomeOrAway() {
        for (PetActivity a : new PetActivity[] {PetActivity.HUNT, PetActivity.FOLLOW}) {
            Situation lost = with(at(a), b -> {
                b.owner = false;
                return b;
            });
            assertThat(PetAi.update(lost, true).action()).isEqualTo(Action.IDLE_AT_HOME);
            Situation lostFar = with(lost, b -> {
                b.far = true;
                return b;
            });
            assertThat(PetAi.update(lostFar, true).action()).isEqualTo(Action.IDLE);
            assertThat(PetAi.update(at(a), true).action()).isEqualTo(Action.NONE);
        }
    }

    @Test
    void idleAtHomeRestsAfterTwelveThousandTicksSinceLastSleep() {
        Situation s = at(PetActivity.IDLE_AT_HOME);
        // LAST_SLEPT absent: set to 0, rest at the next check
        assertThat(PetAi.update(s, true).action()).isEqualTo(Action.MARK_NEVER_SLEPT);
        Situation slept = with(s, b -> {
            b.lastSlept = 88_000L;
            return b;
        });
        assertThat(PetAi.update(slept, true).action()).isEqualTo(Action.NONE); // 100000 > 100000 is false
        Situation later = with(slept, b -> {
            b.now = 100_001L;
            return b;
        });
        assertThat(PetAi.update(later, true).action()).isEqualTo(Action.REST);
        Situation far = with(later, b -> {
            b.far = true;
            return b;
        });
        assertThat(PetAi.update(far, true).action()).isEqualTo(Action.IDLE);
    }

    @Test
    void aSittingPetStopsAfterTheTimeoutAndASleeperAfterTheSleepTimeout() {
        Situation sit = with(at(PetActivity.SIT), b -> {
            b.sitTick = 98_000L;
            return b;
        });
        assertThat(PetAi.update(sit, true).action()).isEqualTo(Action.NONE); // 100000 > 100000 is false
        Situation late = with(sit, b -> {
            b.now = 100_001L;
            return b;
        });
        assertThat(PetAi.update(late, true).action()).isEqualTo(Action.IDLE_AT_HOME);

        Situation rest = with(at(PetActivity.REST), b -> {
            b.sleeping = true;
            b.lastSlept = 98_000L;
            return b;
        });
        assertThat(PetAi.update(rest, true).action()).isEqualTo(Action.NONE);
        assertThat(PetAi.update(with(rest, b -> {
            b.now = 100_001L;
            return b;
        }), true).action()).isEqualTo(Action.IDLE_AT_HOME);
        // a pet still walking to its bed (not sleeping yet) is left alone
        assertThat(PetAi.update(with(rest, b -> {
            b.sleeping = false;
            b.now = 200_000L;
            return b;
        }), true).action()).isEqualTo(Action.NONE);
        assertThat(PetAi.wantsToStopSleeping(true, PetState.NO_SIT_TICK, 0L)).isTrue();
        assertThat(PetAi.wantsToStopSleeping(false, PetState.NO_SIT_TICK, 0L)).isFalse();
    }

    @Test
    void fleeTargetPrefersTheAttackTargetThenTheHurtByAndNeverTheOwner() {
        assertThat(PetAi.fleeTarget(true, false, true, false)).isEqualTo(1);
        assertThat(PetAi.fleeTarget(true, true, true, false)).isEqualTo(0); // Optional.or only if the first is empty
        assertThat(PetAi.fleeTarget(false, false, true, false)).isEqualTo(2);
        assertThat(PetAi.fleeTarget(false, false, true, true)).isEqualTo(0);
        assertThat(PetAi.fleeTarget(false, false, false, false)).isEqualTo(0);
    }

    @Test
    void huntTargetIsTheOwnersLastFightWithin120TicksElseWhoHurtIt() {
        assertThat(PetAi.huntTarget(true, true, true, 100, 100, true, 100, true)).isEqualTo(Pick.NONE);
        assertThat(PetAi.huntTarget(false, true, true, 219, 100, true, 0, false)).isEqualTo(Pick.OWNER_ATTACKER);
        assertThat(PetAi.huntTarget(false, true, true, 220, 100, true, 0, false)).isEqualTo(Pick.NONE);
        assertThat(PetAi.huntTarget(false, true, false, 150, 100, true, 100, false)).isEqualTo(Pick.OWNER_VICTIM);
        assertThat(PetAi.huntTarget(false, true, false, 220, 100, false, 100, true)).isEqualTo(Pick.HURT_BY);
        assertThat(PetAi.huntTarget(false, false, true, 100, 100, true, 100, true)).isEqualTo(Pick.HURT_BY);
    }

    @Test
    void unwantedTargetIsWhoHurtItElseAPest() {
        assertThat(PetAi.unwantedTarget(true, true, true)).isEqualTo(0);
        assertThat(PetAi.unwantedTarget(false, true, true)).isEqualTo(1);
        assertThat(PetAi.unwantedTarget(false, false, true)).isEqualTo(2);
        assertThat(PetAi.unwantedTarget(false, false, false)).isEqualTo(0);
    }

    @Test
    void speciesCanAttackRules() {
        assertThat(PetAi.canAttack(PetSpecies.DOG, false, true, false)).isTrue();
        assertThat(PetAi.canAttack(PetSpecies.DOG, false, false, true)).isTrue(); // any Monster
        assertThat(PetAi.canAttack(PetSpecies.DOG, false, false, false)).isFalse();
        assertThat(PetAi.canAttack(PetSpecies.DOG, true, true, true)).isFalse(); // never the owner
        assertThat(PetAi.canAttack(PetSpecies.CAT, false, true, false)).isTrue();
        assertThat(PetAi.canAttack(PetSpecies.CAT, false, false, true)).isFalse(); // cats do not attack monsters
    }

    @Test
    void interestedNeedsALookAtTheSittingPetsVisibleOwner() {
        assertThat(PetAi.interested(true, true, true)).isTrue();
        assertThat(PetAi.interested(true, false, true)).isFalse();
        assertThat(PetAi.interested(true, true, false)).isFalse();
    }
}
