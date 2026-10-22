/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (getAttackTarget, getDisturbedAttackTarget,
 * isPacified, hasNearbyAttacker, MAX_WANDER_DISTANCE, MAX_ATTACK_DISTANCE, initHuntActivity, initFightActivity,
 * initRestActivity), src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorBehaviors.java (becomePassiveIf,
 * stopAttackingIf, stopAttackingIfTooFarFromHome, startSleeping, tickScheduleAndWake, wakeFromDisturbance, findNewHome),
 * src/main/java/net/dries007/tfc/common/entities/predator/Predator.java (hurt)
 */
package dev.tfp.core.predator;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import dev.tfp.core.prey.Position;
import dev.tfp.core.prey.Sighting;

/**
 * TFC's predator hunt rules as pure functions of the stored {@link PredatorMemory}, the schedule, the sighted entities and
 * the distances the caller measures. Nothing here ticks, draws random numbers or reads the world.
 *
 * <p>The rules are:
 * <ul>
 *   <li>Schedule ({@link PredatorSchedule#activityAt}): {@code HUNT} or {@code REST} from the day time. There is no hunger gate.</li>
 *   <li>Prey choice ({@link #attackTarget}): while not pacified, the nearest attackable player, else the nearest visible
 *       hunted prey that is not in water. A rest activity attacks only after a disturbance.</li>
 *   <li>Eligibility: a baby or a pacified or recently-hunted predator never picks a target.</li>
 *   <li>Low health ({@link #becomePassive}): below 5 health the predator drops its target and is pacified for 200 ticks.</li>
 *   <li>Give up ({@link #stopAttacking}): the target is too far from home (more than 80 blocks), unreachable for more than
 *       200 ticks, dead, out of reach of attack, or on another level. A dead target sets {@code HUNTED_RECENTLY} for 12000 ticks.</li>
 *   <li>Home ({@link #shouldFallAsleep}, {@link #wakesFromDisturbance}, {@link #needsNewHome}): sleep within 5 blocks of home,
 *       wake beyond 5 blocks, in water or with a living entity touching, and choose a new home beyond 100 blocks.</li>
 *   <li>Hurt ({@link #hurt}): a living attacker that is not creative or spectator becomes the target, and the pacified,
 *       hunted-recently and unreachable memories clear.</li>
 * </ul>
 *
 * <p>Distances are squared, as TFC compares them. The caller passes the squared distance from home.
 * Out of scope: the brain and activity priorities, the sensors, the walk and look targets, the melee attack, navigation,
 * the pack alpha and its respect, the temptation, the ram and attack sounds, and the fleeing of prey.
 */
public final class PredatorRules {

    /** TFC {@code getHealth() < 5f}: strictly below this health the predator becomes passive. */
    public static final float PASSIVE_HEALTH = 5.0f;

    /** TFC {@code becomePassiveIf(..., 200)}: the pacified time in game ticks. */
    public static final long PASSIVE_TICKS = 200;

    /** TFC {@code setMemoryWithExpiry(HUNTED_RECENTLY, true, 12000)}: the time after a kill, in game ticks. */
    public static final long HUNTED_RECENTLY_TICKS = 12000;

    /** TFC {@code setMemory(WAKEUP_TICKS, 100)}: the time a disturbance keeps the predator from sleeping, in game ticks. */
    public static final long WAKEUP_TICKS = 100;

    /** TFC {@code isTiredOfTryingToReachTarget}: strictly more than this many ticks unreachable gives up. */
    public static final long UNREACHABLE_GIVE_UP_TICKS = 200;

    /** TFC {@code MAX_ATTACK_DISTANCE = 80 * 80}: the squared distance from home above which the predator stops attacking. */
    public static final double MAX_ATTACK_DISTANCE_SQ = 80.0 * 80.0;

    /** TFC {@code MAX_WANDER_DISTANCE = 100 * 100}: the squared distance from home above which it looks for a new home. */
    public static final double MAX_WANDER_DISTANCE_SQ = 100.0 * 100.0;

    /** TFC {@code startSleeping} and {@code wakeFromDisturbance}: the squared distance from home that separates them (25, 5 blocks). */
    public static final double SLEEP_RANGE_SQ = 25.0;

    /** TFC {@code findNewHome}: a squared distance below this while in water also finds a new home. */
    public static final double WATER_HOME_RANGE_SQ = 9.0;

    /** TFC {@code hasNearbyAttacker}: the squared distance from the hurt-by entity below which it is nearby (16 blocks). */
    public static final double NEARBY_ATTACKER_SQ = 256.0;

    private PredatorRules() {
    }

    /**
     * TFC {@code getAttackTarget} with the rest-activity gate of {@code getDisturbedAttackTarget}. In the rest activity no
     * target is picked unless the predator was disturbed (the {@code WAKEUP_TICKS} memory is present). Then, while pacified,
     * a baby, a predator with {@code PACIFIED} or {@code HUNTED_RECENTLY}, there is no target. Otherwise the nearest
     * attackable player is the target; with no such player, the nearest visible prey that is huntable (see
     * {@link HuntedPrey}) and not in water. Ties keep the earlier candidate.
     *
     * @param scheduled  the scheduled activity at this tick
     * @param memory     the stored memory
     * @param now        the current game tick
     * @param baby       the predator is a baby
     * @param predator   the predator's position
     * @param visible    the visible living entities, in any order
     * @return the attack target, or empty
     */
    public static Optional<Sighting> attackTarget(PredatorSchedule.Activity scheduled, PredatorMemory memory, long now,
                                                  boolean baby, Position predator, List<PreyCandidate> visible) {
        if (scheduled == PredatorSchedule.Activity.REST && !memory.wakeup(now)) {
            return Optional.empty();
        }
        if (baby || memory.pacified(now) || memory.huntedRecently(now)) {
            return Optional.empty();
        }
        final Optional<PreyCandidate> player = nearest(predator, visible, PreyCandidate::attackablePlayer);
        if (player.isPresent()) {
            return player.map(PreyCandidate::sighting);
        }
        return nearest(predator, visible, PreyCandidate::huntablePrey).map(PreyCandidate::sighting);
    }

    /**
     * The low-health trigger of {@code becomePassiveIf(p -> p.getHealth() < 5f, 200)}: health strictly below 5 and no
     * {@code PACIFIED} memory. The {@code ATTACK_TARGET} precondition is {@code registered}, not {@code present}, so it
     * does not gate the trigger.
     *
     * @param memory the stored memory
     * @param now    the current game tick
     * @param health the predator's health
     * @return {@code true} if the predator becomes passive now
     */
    public static boolean passiveTriggered(PredatorMemory memory, long now, float health) {
        return health < PASSIVE_HEALTH && !memory.pacified(now);
    }

    /**
     * Applies the low-health trigger: when {@link #passiveTriggered}, the target is dropped and {@code PACIFIED} is set for
     * {@link #PASSIVE_TICKS}; otherwise the memory is unchanged.
     *
     * @param memory the stored memory
     * @param now    the current game tick
     * @param health the predator's health
     * @return the new memory
     */
    public static PredatorMemory becomePassive(PredatorMemory memory, long now, float health) {
        if (!passiveTriggered(memory, now, health)) {
            return memory;
        }
        return new PredatorMemory(now + PASSIVE_TICKS, memory.huntedRecentlyUntil(), memory.wakeupUntil(),
            memory.cantReachSince(), false, memory.sleeping());
    }

    /**
     * TFC {@code stopAttackingIf} with {@code stopAttackingIfTooFarFromHome} as its predicate. Only while attacking: the
     * target is dropped when the attacker cannot attack it, it has been unreachable for more than 200 ticks, it is not
     * alive, it is on another level, or the predator is more than 80 blocks from home. Dropping a dead target sets
     * {@code HUNTED_RECENTLY} for 12000 ticks.
     *
     * @param memory        the stored memory
     * @param now           the current game tick
     * @param target        the target's state, see {@link Target}
     * @param homeDistanceSq the squared distance from the predator to home
     * @return the new memory
     */
    public static PredatorMemory stopAttacking(PredatorMemory memory, long now, Target target, double homeDistanceSq) {
        if (!memory.attacking()) {
            return memory;
        }
        final boolean stop = !target.canAttack()
            || memory.cantReachTooLong(now)
            || !target.alive()
            || !target.sameLevel()
            || homeDistanceSq > MAX_ATTACK_DISTANCE_SQ;
        if (!stop) {
            return memory;
        }
        final long huntedRecently = target.deadOrDying() ? now + HUNTED_RECENTLY_TICKS : memory.huntedRecentlyUntil();
        return new PredatorMemory(memory.pacifiedUntil(), huntedRecently, memory.wakeupUntil(), memory.cantReachSince(),
            false, memory.sleeping());
    }

    /**
     * The state of an attack target as {@link #stopAttacking} reads it.
     *
     * @param canAttack   the predator may attack the target (TFC {@code canAttack})
     * @param alive       the target is alive ({@code isAlive})
     * @param deadOrDying the target is dead or dying ({@code isDeadOrDying})
     * @param sameLevel   the target is on the predator's level
     */
    public record Target(boolean canAttack, boolean alive, boolean deadOrDying, boolean sameLevel) {
    }

    /**
     * TFC {@code hurt}: while alive, the {@code PACIFIED}, {@code HUNTED_RECENTLY} and {@code CANT_REACH} memories clear,
     * the predator wakes and the fight starts if there is a target. The attacker becomes the target when it is a living
     * entity that is not creative or spectator; the caller passes that as {@code attackerIsTarget}.
     *
     * <p>The wake-up memory is not cleared by TFC's hurt. When the predator is not alive, nothing changes.
     *
     * @param memory         the stored memory
     * @param alive          the predator is alive after the damage
     * @param attackerIsTarget the attacker is a living entity that may be attacked
     * @return the new memory
     */
    public static PredatorMemory hurt(PredatorMemory memory, boolean alive, boolean attackerIsTarget) {
        if (!alive) {
            return memory;
        }
        return new PredatorMemory(PredatorMemory.NONE, PredatorMemory.NONE, memory.wakeupUntil(), PredatorMemory.NONE,
            memory.attacking() || attackerIsTarget, false);
    }

    /**
     * TFC {@code startSleeping}: a predator within 5 blocks of home (squared distance strictly below 25), awake, not in water,
     * with no target and no wake-up memory, falls asleep.
     *
     * @param memory         the stored memory
     * @param now            the current game tick
     * @param homeDistanceSq the squared distance from the predator to home
     * @param inWater        the predator is in water or a bubble column
     * @return {@code true} if the predator falls asleep now
     */
    public static boolean shouldFallAsleep(PredatorMemory memory, long now, double homeDistanceSq, boolean inWater) {
        return homeDistanceSq < SLEEP_RANGE_SQ && !memory.sleeping() && !inWater && !memory.attacking() && !memory.wakeup(now);
    }

    /**
     * TFC {@code wakeFromDisturbance}: a sleeping predator without a target wakes when it is more than 5 blocks from home
     * (squared distance strictly above 25), in water, or with a living entity touching its box. Waking sets the wake-up
     * memory for {@link #WAKEUP_TICKS}.
     *
     * @param memory         the stored memory
     * @param now            the current game tick
     * @param homeDistanceSq the squared distance from the predator to home
     * @param inWater        the predator is in water or a bubble column
     * @param livingTouching a living entity other than the predator is in its bounding box
     * @return the new memory; unchanged when it does not wake
     */
    public static PredatorMemory wakesFromDisturbance(PredatorMemory memory, long now, double homeDistanceSq,
                                                      boolean inWater, boolean livingTouching) {
        final boolean disturbed = homeDistanceSq > SLEEP_RANGE_SQ || inWater || livingTouching;
        if (!memory.sleeping() || memory.attacking() || !disturbed) {
            return memory;
        }
        return new PredatorMemory(memory.pacifiedUntil(), memory.huntedRecentlyUntil(), now + WAKEUP_TICKS,
            memory.cantReachSince(), false, false);
    }

    /**
     * TFC {@code findNewHome}: a predator beyond 100 blocks of home (squared distance strictly above 10000), or in water
     * with a squared distance below 9 from home, looks for a new home.
     *
     * @param homeDistanceSq the squared distance from the predator to home
     * @param inWater        the predator is in water or a bubble column
     * @return {@code true} if a new home is wanted
     */
    public static boolean needsNewHome(double homeDistanceSq, boolean inWater) {
        return homeDistanceSq > MAX_WANDER_DISTANCE_SQ || (homeDistanceSq < WATER_HOME_RANGE_SQ && inWater);
    }

    /**
     * TFC {@code hasNearbyAttacker}: the hurt-by entity is within 16 blocks (squared distance strictly below 256). The
     * avoid activity moves the predator away from it.
     *
     * @param hurtByPresent   the {@code HURT_BY_ENTITY} memory is present
     * @param attackerDistanceSq the squared distance to the hurt-by entity
     * @return {@code true} if the attacker is nearby
     */
    public static boolean nearbyAttacker(boolean hurtByPresent, double attackerDistanceSq) {
        return hurtByPresent && attackerDistanceSq < NEARBY_ATTACKER_SQ;
    }

    /**
     * TFC {@code tickScheduleAndWake}: when the schedule moves a sleeping predator from {@code REST} to another activity,
     * it wakes. This applies only without a target. The glue calls it with the scheduled activity before and after.
     *
     * @param memory the stored memory
     * @param before the scheduled activity on the previous check
     * @param after  the scheduled activity now
     * @return the new memory
     */
    public static PredatorMemory wakeAtScheduleChange(PredatorMemory memory, PredatorSchedule.Activity before,
                                                      PredatorSchedule.Activity after) {
        if (memory.attacking() || before != PredatorSchedule.Activity.REST || after == PredatorSchedule.Activity.REST) {
            return memory;
        }
        return new PredatorMemory(memory.pacifiedUntil(), memory.huntedRecentlyUntil(), memory.wakeupUntil(),
            memory.cantReachSince(), memory.attacking(), false);
    }

    private static Optional<PreyCandidate> nearest(Position predator, List<PreyCandidate> visible,
                                                   Predicate<PreyCandidate> accept) {
        PreyCandidate best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (PreyCandidate candidate : visible) {
            if (!accept.test(candidate)) {
                continue;
            }
            final double distance = predator.distanceSq(candidate.sighting().at());
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
