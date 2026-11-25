/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/prey/PreyAi.java @ c3b4d9536 (initCoreActivity: Swim,
 * AnimalPanic, LookAtTargetSink; initIdleActivity: SetLookTarget, BabyFollowAdult, createIdleMovementBehaviors)
 */
package dev.tfp.paper.prey;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.PreyIdle;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The core and idle behaviours of TFC's prey brains as Paper goals: swim, panic, look at players, babies follow adults
 * and the three idle movements (stroll, walk to the look target, do nothing). Each asks its question on a staggered
 * period; the choices and durations come from {@link PreyIdle}.
 */
final class PreyIdleGoals {
    private PreyIdleGoals() {
    }

    /** {@code new Swim(0.7F)}: in water, swim up on 70 % of ticks. */
    static final class Swim extends AnimalGoal {
        private final PreyBody body;
        private final RandomGenerator random;

        Swim(PreyBrain brain, PreyBody body, RandomGenerator random) {
            super(brain.mob, "prey_swim", 1, GoalType.JUMP);
            this.body = body;
            this.random = random;
        }

        @Override
        protected boolean wants() {
            return body.inWater(mob);
        }

        @Override
        protected void step() {
            if (random.nextFloat() < PreyIdle.SWIM_CHANCE) {
                body.swimUp(mob, PreyIdle.SWIM_UP);
            }
        }
    }

    /** {@code new AnimalPanic<>(2.0F)}: after a hit, run to random spots within 5 blocks for 100 to 120 ticks. */
    static final class Panic extends AnimalGoal {
        private final PreyBrain brain;
        private final PreyBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;

        Panic(PreyBrain brain, PreyBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "prey_panic", 1, GoalType.MOVE);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
            this.random = random;
        }

        @Override
        protected boolean wants() {
            return brain.panicking(clock.getAsLong());
        }

        @Override
        protected void begin() {
            body.stop(mob);
        }

        @Override
        protected void step() {
            if (!body.hasPath(mob)) {
                PreyFlee.Offset o = PreyIdle.randomOffset(PreyIdle.PANIC_HORIZONTAL, PreyIdle.PANIC_VERTICAL, random);
                Location to = body.standable(mob, o.dx(), o.dy(), o.dz());
                if (to != null) {
                    PathBudget.urgent();
                    body.moveTo(mob, to, PreyIdle.PANIC_SPEED);
                }
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
        }
    }

    /** {@code SetLookTarget.create(PLAYER, 6, 30..60)} with {@code LookAtTargetSink(45, 90)}. */
    static final class Look extends AnimalGoal {
        private final PreyBrain brain;
        private final PreyBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;
        private long nextTry = Long.MIN_VALUE;
        private long until;

        Look(PreyBrain brain, PreyBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "prey_look", 5, GoalType.LOOK);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
            this.random = random;
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            if (now < nextTry) {
                return false;
            }
            nextTry = now + PreyIdle.duration(PreyIdle.LOOK_INTERVAL_MIN, PreyIdle.LOOK_INTERVAL_MAX, random);
            LivingEntity best = null;
            double bestSq = PreyIdle.LOOK_PLAYER_RANGE * PreyIdle.LOOK_PLAYER_RANGE;
            Reading seen = PreyFleeGoal.seen(brain, body, now);
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if (!(e instanceof Player) || PreySenses.creativeOrSpectator(e)) {
                    continue;
                }
                double d = PreySenses.at(e).distanceSq(PreySenses.at(mob));
                if (d <= bestSq && seen.visible(i)) {
                    best = e;
                    bestSq = d;
                }
            }
            if (best == null) {
                return false;
            }
            brain.lookTarget = best;
            until = now + PreyIdle.duration(PreyIdle.LOOK_HOLD_MIN, PreyIdle.LOOK_HOLD_MAX, random);
            return true;
        }

        @Override
        protected boolean keepsGoing() {
            return clock.getAsLong() < until && PreyBrain.alive(brain.lookTarget);
        }

        @Override
        protected void step() {
            if (PreyBrain.alive(brain.lookTarget)) {
                body.lookAt(mob, brain.lookTarget);
            }
        }

        @Override
        protected void end() {
            brain.lookTarget = null;
        }
    }

    /** {@code BabyFollowAdult.create(UniformInt.of(5, 16), 1.25F)}. */
    static final class FollowAdult extends AnimalGoal {
        private final PreyBrain brain;
        private final PreyBody body;
        private final LongSupplier clock;
        private LivingEntity adult;
        private boolean retry;

        FollowAdult(PreyBrain brain, PreyBody body, LongSupplier clock) {
            super(brain.mob, "prey_follow_adult", 10, GoalType.MOVE);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            adult = null;
            if (!PreySenses.baby(mob, now)) {
                return false;
            }
            double best = Double.POSITIVE_INFINITY;
            Reading seen = PreyFleeGoal.seen(brain, body, now);
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if (!brain.species.equals(seen.species(i))) {
                    continue;
                }
                double d = PreySenses.at(e).distanceSq(PreySenses.at(mob));
                if (d < best && !PreySenses.baby(e, now) && seen.visible(i)) {
                    best = d;
                    adult = e;
                }
            }
            return adult != null && PreyIdle.followsAdult(true, best);
        }

        @Override
        protected boolean keepsGoing() {
            return PreyBrain.alive(adult)
                && PreyIdle.followsAdult(true, PreySenses.at(adult).distanceSq(PreySenses.at(mob)));
        }

        @Override
        protected void step() {
            if (((mob.getTicksLived() + mob.getEntityId()) % 10 == 0 || retry) && PreyBrain.alive(adult)) {
                // a first path deferred by the tick's budget is retried on the next tick; a refresh waits for the next re-path
                boolean has = body.hasPath(mob);
                retry = false;
                if (PathBudget.tryPath(has)) {
                    body.moveTo(mob, adult, PreyIdle.FOLLOW_SPEED);
                } else {
                    retry = !has;
                }
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
            adult = null;
            retry = false;
        }
    }

    /** {@code createIdleMovementBehaviors}: one of stroll, walk to the look target, or do nothing for 30 to 60 ticks. */
    static final class Idle extends AnimalGoal {
        private final PreyBrain brain;
        private final PreyBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;
        private PreyIdle.Movement current;
        private long waitUntil;

        Idle(PreyBrain brain, PreyBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "prey_idle", 20, GoalType.MOVE);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
            this.random = random;
        }

        PreyIdle.Movement current() {
            return current;
        }

        @Override
        protected boolean wants() {
            if (body.hasPath(mob)) {
                return false;
            }
            long now = clock.getAsLong();
            if (!PathBudget.mayStart()) {
                return false; // this tick's path starts are used up: ask again next period, before any draw
            }
            current = PreyIdle.movement(random);
            switch (current) {
                case STROLL -> {
                    PreyFlee.Offset o = PreyIdle.randomOffset(PreyIdle.STROLL_HORIZONTAL, PreyIdle.STROLL_VERTICAL, random);
                    Location to = body.standable(mob, o.dx(), o.dy(), o.dz());
                    return to != null && PathBudget.tryStart() && body.moveTo(mob, to, PreyIdle.STROLL_SPEED);
                }
                case WALK_TO_LOOK_TARGET -> {
                    LivingEntity t = brain.lookTarget;
                    return PreyBrain.alive(t) && farFromLook(t) && PathBudget.tryStart()
                            && body.moveTo(mob, t, PreyIdle.STROLL_SPEED);
                }
                default -> {
                    waitUntil = now +PreyIdle.duration(PreyIdle.WAIT_MIN, PreyIdle.WAIT_MAX, random);
                    return true;
                }
            }
        }

        private boolean farFromLook(LivingEntity t) {
            return PreySenses.at(t).distanceSq(PreySenses.at(mob)) > PreyIdle.LOOK_WALK_CLOSE * PreyIdle.LOOK_WALK_CLOSE;
        }

        @Override
        protected boolean keepsGoing() {
            if (current == null) {
                return false;
            }
            return switch (current) {
                case WAIT -> clock.getAsLong() < waitUntil;
                case WALK_TO_LOOK_TARGET -> PreyBrain.alive(brain.lookTarget) && farFromLook(brain.lookTarget) && body.hasPath(mob);
                case STROLL -> body.hasPath(mob);
            };
        }

        @Override
        protected void end() {
            if (current != PreyIdle.Movement.WAIT) {
                body.stop(mob);
            }
            current = null;
        }
    }
}
