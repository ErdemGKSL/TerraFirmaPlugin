/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/predator/PredatorAi.java @ c3b4d9536 (initCoreActivity,
 * initHuntActivity, initRetreatActivity, initRestActivity, initFightActivity, createIdleMovementBehaviors),
 * ai/predator/PackPredatorAi.java (initHuntActivity), ai/predator/AmphibiousPredatorAi.java (initRetreatActivity,
 * initFightActivity), ai/predator/PredatorBehaviors.java (becomePassiveIf, stopAttackingIfTooFarFromHome, startSleeping,
 * tickScheduleAndWake, wakeFromDisturbance, findNewHome, listenToAlpha), ai/predator/AggressiveSwim.java, same commit
 */
package dev.tfp.paper.predator;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.predator.PackRules;
import dev.tfp.core.predator.PredatorActivity;
import dev.tfp.core.predator.PredatorIdle;
import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.core.predator.PredatorMemory;
import dev.tfp.core.predator.PredatorRules;
import dev.tfp.core.predator.PredatorSchedule;
import dev.tfp.core.predator.PreyCandidate;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.PreyIdle;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * TFC's predator brain as Paper goals. One goal per activity ({@link PredatorActivity}): the fight, the retreat, the rest
 * and the hunt, each active only while the brain's activity is its own, plus the aggressive swim. Each activity goal
 * asks its question every {@value #PERIOD} ticks and runs its behaviours on a {@value #THINK}-tick beat, staggered per
 * predator by its tick count; only the melee reach check runs every tick. The decisions are the pure
 * {@code dev.tfp.core.predator} rules on a snapshot (positions, flags, memories); the goals only apply them.
 */
final class PredatorGoals {
    static final int PERIOD = 5;
    static final int THINK = 5;
    /** Vanilla {@code NearestLivingEntitySensor}: every 20 ticks, 16 blocks. */
    static final int SENSE_TICKS = 20;
    static final double SENSE_RANGE = 16.0;
    /** {@code RandomPos}: up to 10 candidates. */
    static final int ATTEMPTS = 10;

    private PredatorGoals() {
    }

    /** The shared parts of every goal. */
    record Deps(PredatorBody body, LongSupplier clock, RandomGenerator random) {
    }

    static boolean beat(PredatorBrain brain, int every) {
        return (brain.mob.getTicksLived() + brain.mob.getEntityId()) % every == 0;
    }

    /** The visible entities, re-read at most every {@link #SENSE_TICKS}. */
    static List<LivingEntity> seen(PredatorBrain brain, PredatorBody body, long now) {
        if (brain.seenAt == Long.MIN_VALUE || now - brain.seenAt >= SENSE_TICKS || now < brain.seenAt) {
            brain.seen = body.visible(brain.mob, SENSE_RANGE);
            brain.seenAt = now;
        }
        return brain.seen;
    }

    /** {@code PredatorAi.getAttackTarget} (or the disturbed form at rest) on the current sighting snapshot. */
    static LivingEntity ownTarget(PredatorBrain brain, Deps d, long now, PredatorSchedule.Activity activity) {
        List<LivingEntity> entities = new ArrayList<>();
        List<PreyCandidate> candidates = PredatorSenses.candidates(seen(brain, d.body(), now), d.body().peaceful(brain.mob),
                entities);
        Optional<Sighting> found = PredatorRules.attackTarget(activity, brain.memory, now,
                PredatorSenses.baby(brain.mob, now), PredatorSenses.at(brain.mob), candidates);
        if (found.isEmpty()) {
            return null;
        }
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).sighting() == found.get()) {
                return entities.get(i);
            }
        }
        return null;
    }

    /** {@code StartAttacking}: a found target that {@code canAttack} allows becomes {@code ATTACK_TARGET}. */
    static boolean startAttacking(PredatorBrain brain, Deps d, LivingEntity found) {
        if (found == null || !PredatorSenses.canAttack(found, d.body().peaceful(brain.mob))) {
            return false;
        }
        brain.setTarget(found);
        brain.update(PredatorBrain.withCantReach(brain.memory, PredatorMemory.NONE));
        return true;
    }

    /** {@code findNewHome}: a land position within 10 x 5 becomes home and the walk target at speed 1.0. */
    static void findNewHome(PredatorBrain brain, Deps d) {
        if (!PredatorRules.needsNewHome(brain.homeDistanceSq(), d.body().inWater(brain.mob))) {
            return;
        }
        for (int i = 0; i < ATTEMPTS; i++) {
            PreyFlee.Offset o = PreyIdle.randomOffset(PredatorIdle.NEW_HOME_HORIZONTAL, PredatorIdle.NEW_HOME_VERTICAL,
                    d.random());
            Location to = d.body().standable(brain.mob, o.dx(), o.dy(), o.dz());
            if (to != null) {
                brain.setHome(to.getBlockX(), to.getBlockY(), to.getBlockZ());
                d.body().moveTo(brain.mob, to, PredatorIdle.NEW_HOME_SPEED);
                return;
            }
        }
    }

    /** {@code createIdleMovementBehaviors}: the first applicable movement in the shuffled order. */
    static void idle(PredatorBrain brain, Deps d, long now) {
        if (now < brain.waitUntil || d.body().hasPath(brain.mob)) {
            return;
        }
        for (PredatorIdle.Movement m : PredatorIdle.order(d.random())) {
            if (tryMovement(brain, d, now, m)) {
                return;
            }
        }
    }

    private static boolean tryMovement(PredatorBrain brain, Deps d, long now, PredatorIdle.Movement m) {
        PredatorBody body = d.body();
        switch (m) {
            case STROLL -> {
                return strollWithin(brain, d, PredatorIdle.STROLL_HORIZONTAL, PredatorIdle.STROLL_VERTICAL,
                        PredatorIdle.STROLL_SPEED);
            }
            case WALK_TO_LOOK_TARGET -> {
                LivingEntity t = brain.lookTarget;
                if (!PredatorBrain.alive(t) || now >= brain.lookUntil) {
                    return false;
                }
                double close = PredatorIdle.LOOK_WALK_CLOSE_ENOUGH;
                return PredatorSenses.at(t).distanceSq(PredatorSenses.at(brain.mob)) > close * close
                        && body.moveTo(brain.mob, t, PredatorIdle.STROLL_SPEED);
            }
            case WAIT -> {
                brain.waitUntil = now + PreyIdle.duration(PredatorIdle.WAIT_MIN, PredatorIdle.WAIT_MAX, d.random());
                return true;
            }
            case STROLL_TO_HOME -> {
                if (!brain.homeWithin(PredatorIdle.HOME_STROLL_MAX_DISTANCE)) {
                    return false;
                }
                return strollHome(brain, d, now, PredatorIdle.HOME_STROLL_SPEED, PredatorIdle.HOME_STROLL_CLOSE_ENOUGH);
            }
            case STROLL_AROUND_HOME -> {
                if (!brain.homeWithin(PredatorIdle.WANDER_POI_DISTANCE)) {
                    return false;
                }
                if (now <= brain.nextAround) {
                    return true;
                }
                brain.nextAround = now + PredatorIdle.AROUND_INTERVAL_TICKS;
                strollWithin(brain, d, PredatorIdle.AROUND_HORIZONTAL, PredatorIdle.AROUND_VERTICAL,
                        PredatorIdle.HOME_STROLL_SPEED);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean strollWithin(PredatorBrain brain, Deps d, int h, int v, double speed) {
        for (int i = 0; i < ATTEMPTS; i++) {
            PreyFlee.Offset o = PreyIdle.randomOffset(h, v, d.random());
            Location to = d.body().standable(brain.mob, o.dx(), o.dy(), o.dz());
            if (to != null && d.body().moveTo(brain.mob, to, speed)) {
                return true;
            }
        }
        return false;
    }

    /** {@code StrollToPoi(HOME, speed, closeEnough, ...)}: at most one new walk target home per 80 ticks. */
    static boolean strollHome(PredatorBrain brain, Deps d, long now, double speed, int closeEnough) {
        if (now <= brain.nextPoi) {
            return true;
        }
        brain.nextPoi = now + PredatorIdle.POI_INTERVAL_TICKS;
        if (brain.homeDistanceSq() > (double) closeEnough * closeEnough) {
            d.body().moveTo(brain.mob, brain.homeLocation(), speed);
        }
        return true;
    }

    /** {@code SetLookTarget.create(8, 30..60)} and {@code LookAtTargetSink(45, 90)}: the nearest visible living entity. */
    static void look(PredatorBrain brain, Deps d, long now) {
        if (PredatorBrain.alive(brain.lookTarget) && now < brain.lookUntil) {
            d.body().lookAt(brain.mob, brain.lookTarget);
        }
        if (now < brain.nextLook) {
            return;
        }
        brain.nextLook = now + PreyIdle.duration(PredatorIdle.LOOK_INTERVAL_MIN, PredatorIdle.LOOK_INTERVAL_MAX, d.random());
        LivingEntity best = null;
        double bestSq = PredatorIdle.LOOK_RANGE * PredatorIdle.LOOK_RANGE;
        for (LivingEntity e : seen(brain, d.body(), now)) {
            double dsq = PredatorSenses.at(e).distanceSq(PredatorSenses.at(brain.mob));
            if (dsq <= bestSq && !PredatorSenses.creativeOrSpectator(e)) {
                best = e;
                bestSq = dsq;
            }
        }
        if (best != null) {
            brain.lookTarget = best;
            brain.lookUntil = now + PreyIdle.duration(PredatorIdle.LOOK_HOLD_MIN, PredatorIdle.LOOK_HOLD_MAX, d.random());
        }
    }

    /** {@code new AggressiveSwim(0.8F)}: jump on 80 % of ticks in water, lunging at a far target. */
    static final class Swim extends AnimalGoal {
        private final PredatorBrain brain;
        private final Deps d;

        Swim(PredatorBrain brain, Deps d) {
            super(brain.mob, "predator_swim", 1, GoalType.JUMP);
            this.brain = brain;
            this.d = d;
        }

        @Override
        protected boolean wants() {
            return d.body().inWater(mob) || mob.isInLava();
        }

        @Override
        protected void step() {
            if (d.random().nextFloat() >= PredatorMelee.SWIM_JUMP_CHANCE) {
                return;
            }
            LivingEntity t = brain.target;
            double[] v = {0, 0, 0};
            if (PredatorBrain.alive(t)) {
                Location p = mob.getLocation();
                Location q = t.getLocation();
                v = PredatorMelee.lunge(p.getX(), p.getY(), p.getZ(), q.getX(), q.getY(), q.getZ()).orElse(v);
            }
            d.body().jump(mob, v[0], v[1], v[2]);
        }
    }

    /** The goal of one activity: active while the brain's activity is {@code activity}. */
    abstract static class ActivityGoal extends AnimalGoal {
        final PredatorBrain brain;
        final Deps d;
        private final PredatorActivity activity;

        ActivityGoal(PredatorBrain brain, Deps d, String name, PredatorActivity activity, GoalType... types) {
            super(brain.mob, name, PERIOD, types);
            this.brain = brain;
            this.d = d;
            this.activity = activity;
        }

        @Override
        protected boolean wants() {
            return brain.activity(d.clock().getAsLong()) == activity;
        }

        @Override
        protected void step() {
            if (beat(brain, THINK)) {
                think(d.clock().getAsLong());
            }
        }

        abstract void think(long now);
    }

    /**
     * {@code FIGHT}: low-health retreat, chase at 1.15 (amphibious 1.6 in water, else 1.0) unless visible and in reach,
     * a melee hit every 40 ticks when visible and in reach, and the give-up rules.
     */
    static final class Fight extends ActivityGoal {
        Fight(PredatorBrain brain, Deps d) {
            super(brain, d, "predator_fight", PredatorActivity.FIGHT, GoalType.MOVE, GoalType.LOOK);
        }

        @Override
        protected void begin() {
            think(d.clock().getAsLong());
        }

        @Override
        protected void step() {
            super.step();
            LivingEntity t = brain.target;
            if (!PredatorBrain.alive(t) || !t.getWorld().equals(mob.getWorld())) {
                return;
            }
            long now = d.clock().getAsLong();
            boolean visible = d.body().sees(mob, t, SENSE_RANGE);
            boolean inRange = PredatorMelee.withinMeleeRange(d.body().box(mob), d.body().box(t));
            if (PredatorMelee.attacks(now, brain.coolingUntil, visible, inRange)) {
                brain.coolingUntil = now + PredatorMelee.ATTACK_COOLDOWN_TICKS;
                d.body().attack(mob, t, brain.profile.sound());
                if (brain.profile.pack()) {
                    boolean dead = t.isDead() || t.getHealth() <= 0;
                    if (dead) {
                        brain.setRespect(PackRules.respectAfterHit(brain.respect(), true,
                                d.random().nextInt(PackRules.KILL_RESPECT_BOUND)));
                    }
                }
            }
        }

        @Override
        void think(long now) {
            // becomePassiveIf(health < 5, 200), then the walk target, then stopAttackingIfTooFarFromHome
            brain.update(PredatorRules.becomePassive(brain.memory, now, (float) mob.getHealth()));
            LivingEntity t = brain.target;
            if (t != null && PredatorBrain.alive(t) && t.getWorld().equals(mob.getWorld())) {
                boolean visible = d.body().sees(mob, t, SENSE_RANGE);
                boolean inRange = PredatorMelee.withinMeleeRange(d.body().box(mob), d.body().box(t));
                if (PredatorMelee.chases(visible, inRange)) {
                    boolean ok = d.body().moveTo(mob, t,
                            PredatorMelee.chaseSpeed(brain.profile.kind(), d.body().inWater(mob)));
                    // MoveToTargetSink: CANT_REACH_WALK_TARGET_SINCE starts with the first failed path, clears on a path
                    if (!ok && brain.memory.cantReachSince() == PredatorMemory.NONE) {
                        brain.update(PredatorBrain.withCantReach(brain.memory, now));
                    } else if (ok) {
                        brain.update(PredatorBrain.withCantReach(brain.memory, PredatorMemory.NONE));
                    }
                } else {
                    d.body().stop(mob);
                }
                d.body().lookAt(mob, t);
            }
            if (brain.memory.attacking()) {
                PredatorRules.Target state = PredatorSenses.target(mob, brain.target, d.body().peaceful(mob));
                brain.update(PredatorRules.stopAttacking(brain.memory, now, state, brain.homeDistanceSq()));
                if (!brain.memory.attacking() && state.deadOrDying()) {
                    // stopAttackingIfTooFarFromHome: a kill also forgets HURT_BY_ENTITY
                    brain.attacker = null;
                }
            }
        }

        @Override
        protected void end() {
            d.body().stop(mob);
        }
    }

    /** {@code AVOID}: away from a hurt-by attacker within 16 blocks at 1.2, else home, else idle. */
    static final class Retreat extends ActivityGoal {
        Retreat(PredatorBrain brain, Deps d) {
            super(brain, d, "predator_retreat", PredatorActivity.AVOID, GoalType.MOVE, GoalType.LOOK);
        }

        @Override
        void think(long now) {
            LivingEntity by = brain.attackerAt(now);
            double dsq = by == null ? Double.POSITIVE_INFINITY
                    : PredatorSenses.at(by).distanceSq(PredatorSenses.at(mob));
            if (PredatorRules.nearbyAttacker(by != null, dsq)) {
                if (!d.body().hasPath(mob)) {
                    Location self = mob.getLocation();
                    Location from = by.getLocation();
                    for (int i = 0; i < ATTEMPTS; i++) {
                        Optional<PreyFlee.Offset> off = PreyFlee.awayOffset(self.getX() - from.getX(),
                                self.getZ() - from.getZ(), d.random());
                        if (off.isEmpty()) {
                            continue;
                        }
                        Location to = d.body().standable(mob, off.get().dx(), off.get().dy(), off.get().dz());
                        if (to != null && d.body().moveTo(mob, to, PredatorIdle.RETREAT_SPEED)) {
                            break;
                        }
                    }
                }
                return;
            }
            if (brain.homeWithin(PredatorIdle.WANDER_POI_DISTANCE)) {
                strollHome(brain, d, now, PredatorIdle.retreatHomeSpeed(brain.profile.kind()),
                        PredatorIdle.REST_HOME_CLOSE_ENOUGH);
            }
            idle(brain, d, now);
        }

        @Override
        protected void end() {
            d.body().stop(mob);
        }
    }

    /** {@code REST}: a disturbed target, a new home, home at 1.2, sleep within 5 blocks, wake on schedule or disturbance. */
    static final class Rest extends ActivityGoal {
        Rest(PredatorBrain brain, Deps d) {
            super(brain, d, "predator_rest", PredatorActivity.REST, GoalType.MOVE, GoalType.LOOK);
        }

        @Override
        protected void begin() {
            think(d.clock().getAsLong());
        }

        @Override
        void think(long now) {
            if (startAttacking(brain, d, ownTarget(brain, d, now, PredatorSchedule.Activity.REST))) {
                return;
            }
            boolean water = d.body().inWater(mob);
            findNewHome(brain, d);
            if (!brain.memory.sleeping()) {
                strollHome(brain, d, now, PredatorIdle.REST_HOME_SPEED, PredatorIdle.REST_HOME_CLOSE_ENOUGH);
            }
            double home = brain.homeDistanceSq();
            if (PredatorRules.shouldFallAsleep(brain.memory, now, home, water)) {
                brain.lookTarget = null;
                d.body().stop(mob);
                brain.update(PredatorBrain.with(brain.memory, false, true));
            }
            brain.tickSchedule();
            if (brain.memory.sleeping()) {
                PredatorMemory before = brain.memory;
                PredatorMemory next = PredatorRules.wakesFromDisturbance(before, now, home, water,
                        !d.body().touching(mob).isEmpty());
                brain.update(brain.profile.pack() ? PackRules.packWakeup(next, before) : next);
            }
            if (brain.memory.sleeping()) {
                // MoveToTargetSinkIfNotSleeping: a sleeper does not walk
                d.body().stop(mob);
            }
        }

        @Override
        protected void end() {
            d.body().stop(mob);
        }
    }

    /**
     * {@code HUNT}: the passive checks, the attack choice (pack members take the alpha's target), the look target, a new
     * home and following the alpha (packs), babies following adults, the idle movement and the schedule.
     */
    static final class Hunt extends ActivityGoal {
        Hunt(PredatorBrain brain, Deps d) {
            super(brain, d, "predator_hunt", PredatorActivity.HUNT, GoalType.MOVE, GoalType.LOOK);
        }

        @Override
        void think(long now) {
            float health = (float) mob.getHealth();
            if (brain.profile.pack()) {
                PredatorBrain alpha = brain.alphaOrSelf();
                boolean alphaPacified = alpha.memory.pacified(now);
                brain.update(PackRules.becomePassive(brain.memory, now, health, alphaPacified));
                Optional<LivingEntity> alphaTarget = Optional.ofNullable(alpha.target).filter(PredatorBrain::alive);
                LivingEntity found = PackRules.attackTarget(alpha == brain,
                        () -> Optional.ofNullable(ownTarget(brain, d, now, PredatorSchedule.Activity.HUNT)), alphaTarget)
                        .orElse(null);
                if (!brain.memory.pacified(now) && startAttacking(brain, d, found)) {
                    return;
                }
            } else {
                brain.update(PredatorRules.becomePassive(brain.memory, now, health));
                if (startAttacking(brain, d, ownTarget(brain, d, now, PredatorSchedule.Activity.HUNT))) {
                    return;
                }
            }
            if (brain.memory.pacified(now)) {
                return;
            }
            look(brain, d, now);
            boolean following = false;
            if (brain.profile.pack()) {
                findNewHome(brain, d);
                following = listenToAlpha(now);
            }
            if (!following && !followAdult(now)) {
                idle(brain, d, now);
            }
            brain.tickSchedule();
        }

        /** {@code listenToAlpha}: a member copies the alpha's home and walks to it at 1.1 until within 6 blocks. */
        private boolean listenToAlpha(long now) {
            PredatorBrain alpha = brain.alphaOrSelf();
            if (alpha == brain) {
                return false;
            }
            int[] h = alpha.home();
            brain.setHome(h[0], h[1], h[2]);
            double close = PackRules.LISTEN_CLOSE_ENOUGH;
            if (PredatorSenses.at(alpha.mob).distanceSq(PredatorSenses.at(mob)) > close * close) {
                d.body().moveTo(mob, alpha.mob, PackRules.LISTEN_SPEED);
                return true;
            }
            return false;
        }

        /** {@code BabyFollowAdult.create(UniformInt.of(5, 16), 1.25F)}: the nearest visible adult of the species. */
        private boolean followAdult(long now) {
            if (!PredatorSenses.baby(mob, now)) {
                return false;
            }
            LivingEntity adult = null;
            double best = Double.POSITIVE_INFINITY;
            for (LivingEntity e : seen(brain, d.body(), now)) {
                if (!brain.profile.id().equals(PredatorSenses.species(e)) || PredatorSenses.baby(e, now)) {
                    continue;
                }
                double dsq = PredatorSenses.at(e).distanceSq(PredatorSenses.at(mob));
                if (dsq < best) {
                    best = dsq;
                    adult = e;
                }
            }
            if (adult != null && PreyIdle.followsAdult(true, best)) {
                d.body().moveTo(mob, adult, PreyIdle.FOLLOW_SPEED);
                return true;
            }
            return false;
        }

        @Override
        protected void end() {
            d.body().stop(mob);
        }
    }
}
