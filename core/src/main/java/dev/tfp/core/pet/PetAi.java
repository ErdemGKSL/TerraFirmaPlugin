/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/pet/TamableAi.java @ c3b4d9536 (updateActivity, isFarFromHome,
 * wantsToStopSleeping, couldFlee, getAttackTarget, getUnwantedAttackTarget), ai/pet/FollowOwnerBehavior.java,
 * livestock/pet/Dog.java and TFCCat.java (canAttack), same commit
 */
package dev.tfp.core.pet;

/**
 * The pet brain's periodic decisions as pure functions of a snapshot: which activity the brain switches to once a second,
 * the home distances, the flee test, the sleep timers and the attack-target rules. The goals only apply the answer.
 * Nothing here ticks and nothing reads the world; the caller supplies the calendar tick.
 */
public final class PetAi {
    /** TFC {@code TamableAi.HOME_WANDER_DISTANCE}: farther than this from home, an idling pet walks back. */
    public static final int HOME_WANDER_DISTANCE = 36;
    /** TFC {@code TamableAi.HOME_LOST_DISTANCE}: farther than this, an idle-at-home pet gives up its home. */
    public static final int HOME_LOST_DISTANCE = 120;
    /** TFC {@code TamableAi.couldFlee}: below this health (or on fire) the pet flees. */
    public static final float FLEE_HEALTH = 5f;
    /** TFC {@code updateActivity}: an idle-at-home pet rests once more than this many ticks have passed since it last slept. */
    public static final long REST_AFTER_TICKS = 12000L;
    /** TFC {@code wantsToStopSleeping}: a sleeper wakes once more than this many ticks have passed. */
    public static final long SLEEP_TIMEOUT_TICKS = 2000L;
    /** TFC {@code TamableAi.getAttackTarget}: the owner's last fight counts for this many ticks. */
    public static final int OWNER_FIGHT_TICKS = 120;
    /** TFC {@code FollowOwnerBehavior}: the walk speed towards the owner. */
    public static final float FOLLOW_SPEED = 1.1f;
    /** TFC {@code FollowOwnerBehavior}: the follow stops this close (blocks) to the owner. */
    public static final int FOLLOW_CLOSE_ENOUGH = 5;
    /** TFC {@code returnHomeIfTooFar}: speed 1, close enough 10. */
    public static final float RETURN_HOME_SPEED = 1f;
    public static final int RETURN_HOME_CLOSE_ENOUGH = 10;
    /** TFC {@code initIdleAtHomeActivity}: the stroll speed around home. */
    public static final float HOME_STROLL_SPEED = 0.6f;
    /** TFC {@code StrollToPoi.create(HOME, 0.6F, 10, HOME_WANDER_DISTANCE - 10)}: close enough 10, max distance 26. */
    public static final int STROLL_TO_HOME_CLOSE_ENOUGH = 10;
    public static final int STROLL_TO_HOME_MAX = HOME_WANDER_DISTANCE - 10;
    /** TFC {@code SetWalkTargetFromLookTarget.create(1.0F, 3)}. */
    public static final float LOOK_WALK_SPEED = 1.0f;
    /** TFC {@code SetWalkTargetAwayFrom.entity(AVOID_TARGET, 1.3F, 15, false)}. */
    public static final float AVOID_SPEED = 1.3f;
    public static final int AVOID_DISTANCE = 15;
    /** TFC {@code SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.15F)}. */
    public static final float CHASE_SPEED = 1.15f;
    /** TFC {@code MeleeAttack.create(40)}: ticks between bites. */
    public static final int ATTACK_COOLDOWN_TICKS = 40;
    /** TFC {@code SetLookTarget.create(EntityType.PLAYER, 6.0F, UniformInt.of(30, 60))} (idle at home, hunt, follow). */
    public static final float LOOK_PLAYER_RANGE = 6f;
    /** TFC {@code SetLookTarget.create(EntityType.PLAYER, 8f, ...)} in the sit activity. */
    public static final float SIT_LOOK_RANGE = 8f;
    public static final int LOOK_MIN_TICKS = 30;
    public static final int LOOK_MAX_TICKS = 60;
    /** TFC {@code TamableFindSleepPos}: {@code LandRandomPos.getPos(entity, 12, 5, ...)}. */
    public static final int SLEEP_POS_HORIZONTAL = 12;
    public static final int SLEEP_POS_VERTICAL = 5;
    /** TFC {@code TamableSleepBehavior}: the bed spot counts as reached within one block. */
    public static final double SLEEP_REACHED_DISTANCE = 1.0;

    /** The inputs of {@link #update}: what TFC's {@code updateActivity} reads from the brain. */
    public record Situation(
        PetActivity current,
        float health,
        boolean onFire,
        boolean hasAvoidTarget,
        boolean fleeTargetPresent,
        boolean hasAttackTarget,
        boolean hasOwner,
        boolean farFromHome,
        boolean hurtRecently,
        boolean sleeping,
        long sitTick,
        long lastSlept,
        long now
    ) {
    }

    /** What {@code updateActivity} does. */
    public enum Action {
        /** Nothing changes. */
        NONE,
        /** Switch to {@link PetActivity#AVOID}. */
        AVOID,
        /** The avoid activity ended: restore the stored command's activity on the next tick ({@code refreshCommandOnNextTick}). */
        REFRESH_COMMAND,
        /** Switch to {@link PetActivity#FIGHT}. */
        FIGHT,
        /** {@code beginIdle(entity, true)}: stop sitting and sleeping, store RELAX, idle at home. */
        IDLE_AT_HOME,
        /** {@code beginIdle(entity, false)}: the same, but in the idle activity. */
        IDLE,
        /** Switch to {@link PetActivity#REST}. */
        REST,
        /** {@code LAST_SLEPT} was absent: set it to 0 (so the pet rests at the first check). */
        MARK_NEVER_SLEPT
    }

    /**
     * The result of one {@link #update}.
     *
     * @param startFlee {@code true} if TFC started fleeing from the flee target first ({@code startFleeing}: stop sitting and
     *                  sleeping, RELAX if it was sitting, set the avoid target), before {@code action}
     * @param action    what to do next
     */
    public record Update(boolean startFlee, Action action) {
    }

    private PetAi() {
    }

    /** TFC {@code couldFlee}: {@code getHealth() < 5f || isOnFire()}. */
    public static boolean couldFlee(float health, boolean onFire) {
        return health < FLEE_HEALTH || onFire;
    }

    /**
     * TFC {@code isFarFromHome}: with no home, or a home in another dimension, the pet is far; else squared block distance
     * strictly above {@code distance * distance}.
     */
    public static boolean isFarFromHome(PetState.Home home, String dimension, int x, int y, int z, int distance) {
        if (home == null || !home.dimension().equals(dimension)) {
            return true;
        }
        final long dx = (long) home.x() - x;
        final long dy = (long) home.y() - y;
        final long dz = (long) home.z() - z;
        return dx * dx + dy * dy + dz * dz > (long) distance * distance;
    }

    /**
     * TFC {@code wantsToStopSleeping}: a hurt-by memory, or more than {@link #SLEEP_TIMEOUT_TICKS} since {@code LAST_SLEPT}.
     *
     * @param lastSlept the {@code LAST_SLEPT} tick, or {@link PetState#NO_SIT_TICK} when the memory is absent
     */
    public static boolean wantsToStopSleeping(boolean hurtRecently, long lastSlept, long now) {
        if (hurtRecently) {
            return true;
        }
        return lastSlept != PetState.NO_SIT_TICK && now > lastSlept + SLEEP_TIMEOUT_TICKS;
    }

    /**
     * TFC {@code TamableAi.updateActivity}: run each tick for the activity switches; {@code doMoreChecks} is
     * {@code tickCount % 20 == 0}. The flee, avoid and fight switches run every call; the owner-lost, rest, sit and sleep
     * checks only when {@code doMoreChecks}.
     *
     * @return the decision, or {@link Action#NONE} when the brain has no non-core activity ({@code current == null})
     */
    public static Update update(Situation s, boolean doMoreChecks) {
        if (s.current() == null) {
            return new Update(false, Action.NONE);
        }
        boolean startFlee = false;
        boolean avoid = s.hasAvoidTarget();
        final boolean flee = couldFlee(s.health(), s.onFire());
        if (flee && !avoid && s.fleeTargetPresent()) {
            startFlee = true;
            avoid = true;
        }
        if (avoid && flee) {
            return new Update(startFlee, Action.AVOID);
        } else if (s.current() == PetActivity.AVOID) {
            return new Update(startFlee, Action.REFRESH_COMMAND);
        } else if (s.hasAttackTarget()) {
            return new Update(startFlee, Action.FIGHT);
        }
        if (!doMoreChecks) {
            return new Update(startFlee, Action.NONE);
        }
        final PetActivity current = s.current();
        Action action = Action.NONE;
        if ((current == PetActivity.HUNT || current == PetActivity.FOLLOW) && !s.hasOwner()) {
            action = s.farFromHome() ? Action.IDLE : Action.IDLE_AT_HOME;
        } else if (current == PetActivity.IDLE_AT_HOME) {
            if (s.farFromHome()) {
                action = Action.IDLE;
            } else if (s.lastSlept() == PetState.NO_SIT_TICK) {
                action = Action.MARK_NEVER_SLEPT;
            } else if (s.now() > s.lastSlept() + REST_AFTER_TICKS) {
                action = Action.REST;
            }
        } else if (current == PetActivity.SIT && wantsToStopSitting(s)) {
            action = s.farFromHome() ? Action.IDLE : Action.IDLE_AT_HOME;
        } else if (current == PetActivity.REST && s.sleeping() && wantsToStopSleeping(s.hurtRecently(), s.lastSlept(), s.now())) {
            action = s.farFromHome() ? Action.IDLE : Action.IDLE_AT_HOME;
        }
        return new Update(startFlee, action);
    }

    private static boolean wantsToStopSitting(Situation s) {
        return s.hurtRecently() || s.sitTick() != PetState.NO_SIT_TICK && s.now() > s.sitTick() + PetRules.SIT_TIMEOUT_TICKS;
    }

    /**
     * TFC {@code TamableAi.updateActivity} interested flag: set (every 20 ticks) when the look target is the owner, the pet is
     * sitting and the owner is visible to it.
     */
    public static boolean interested(boolean lookingAtOwner, boolean sitting, boolean ownerVisible) {
        return lookingAtOwner && sitting && ownerVisible;
    }

    /**
     * TFC {@code getFleeTarget} and the filter on it: the attack target, else the hurt-by entity, unless it is the owner.
     * Returns 0 for none, 1 for the attack target, 2 for the hurt-by entity.
     */
    public static int fleeTarget(boolean hasAttackTarget, boolean attackTargetIsOwner, boolean hasHurtBy, boolean hurtByIsOwner) {
        if (hasAttackTarget) {
            return attackTargetIsOwner ? 0 : 1;
        }
        if (hasHurtBy) {
            return hurtByIsOwner ? 0 : 2;
        }
        return 0;
    }

    /** What attack-target picking returns: nothing, the owner's attacker, the owner's victim, or the hurt-by entity. */
    public enum Pick {
        NONE, OWNER_ATTACKER, OWNER_VICTIM, HURT_BY
    }

    /**
     * TFC {@code getAttackTarget} (the hunt activity): a pet that could flee picks nothing; else the owner's last attacker if
     * the pet may attack it and it was within {@link #OWNER_FIGHT_TICKS}; else the owner's last victim the same way; else the
     * hurt-by entity.
     *
     * @param ownerTickCount             the owner's {@code tickCount}
     * @param attackerStamp              the owner's {@code getLastHurtByMobTimestamp}
     * @param victimStamp                the owner's {@code getLastHurtMobTimestamp}
     */
    public static Pick huntTarget(boolean couldFlee, boolean ownerOnline, boolean attackerAllowed, long ownerTickCount,
                                  long attackerStamp, boolean victimAllowed, long victimStamp, boolean hasHurtBy) {
        if (couldFlee) {
            return Pick.NONE;
        }
        if (ownerOnline) {
            if (attackerAllowed && ownerTickCount < attackerStamp + OWNER_FIGHT_TICKS) {
                return Pick.OWNER_ATTACKER;
            }
            if (victimAllowed && ownerTickCount < victimStamp + OWNER_FIGHT_TICKS) {
                return Pick.OWNER_VICTIM;
            }
        }
        return hasHurtBy ? Pick.HURT_BY : Pick.NONE;
    }

    /**
     * TFC {@code getUnwantedAttackTarget} (idle at home): nothing when the pet could flee; the hurt-by entity if any; else
     * the closest visible pest.
     *
     * @return 0 none, 1 hurt-by entity, 2 closest visible pest
     */
    public static int unwantedTarget(boolean couldFlee, boolean hasHurtBy, boolean pestVisible) {
        if (couldFlee) {
            return 0;
        }
        if (hasHurtBy) {
            return 1;
        }
        return pestVisible ? 2 : 0;
    }

    /**
     * TFC {@code canAttack} of a pet: {@code !isOwnedBy(target) && super.canAttack} and then the species rule: a dog attacks
     * what {@code #tfc:hunted_by_dogs} lists or any monster; a cat attacks what {@code #tfc:hunted_by_cats} lists.
     */
    public static boolean canAttack(PetSpecies species, boolean targetIsOwner, boolean huntedBySpecies, boolean monster) {
        if (targetIsOwner) {
            return false;
        }
        return switch (species) {
            case DOG -> huntedBySpecies || monster;
            case CAT -> huntedBySpecies;
        };
    }

    /**
     * TFC {@code TamableMammal.createAttributes} (the pet attributes): max health 20, movement speed 0.3, attack damage 2.
     */
    public static final double MAX_HEALTH = 20.0;
    public static final double MOVEMENT_SPEED = 0.3;
    public static final double ATTACK_DAMAGE = 2.0;
}
