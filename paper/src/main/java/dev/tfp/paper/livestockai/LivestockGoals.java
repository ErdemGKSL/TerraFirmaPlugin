/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/livestock/LivestockAi.java @ c3b4d9536 (core, idle and
 * retreat activities), ai/livestock/OviparousAi.java, ai/livestock/BreedBehavior.java,
 * ai/livestock/DelegatingTemptingSensor.java, livestock/camel/TFCCamelAi.java, livestock/horse/TFCHorse.java
 * (registerGoals: the food tempt goal), ai/prey/AvoidPredatorAndRammersBehavior.java, ai/prey/PreyAi.java
 * (wantsToStopFleeing), same commit
 */
package dev.tfp.paper.livestockai;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.livestockai.BreedApproach;
import dev.tfp.core.livestockai.Temptation;
import dev.tfp.core.prey.FleeState;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.core.prey.PreyIdle;
import dev.tfp.core.prey.Sighting;
import dev.tfp.paper.animal.AnimalGoal;
import dev.tfp.paper.prey.Reading;
import dev.tfp.paper.prey.SpeciesTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * TFC's livestock brain as Paper goals, one class per behaviour. Each asks its question on a staggered period
 * ({@link AnimalGoal}); the decisions are the pure {@code dev.tfp.core.livestockai} and {@code dev.tfp.core.prey} rules on
 * a snapshot of positions, and the goals only apply them (path, stop, look).
 *
 * <p>Priorities (lower first; one {@code MOVE} goal at a time): swim 0, panic 1, avoid 2, breed 3, tempt 4, baby follows
 * adult 5, idle movement 6, look at player 7.
 */
final class LivestockGoals {
    /** Vanilla {@code NearestLivingEntitySensor}: every 20 ticks, 16 blocks. */
    static final int SENSE_TICKS = 20;
    static final double SENSE_RANGE = 16.0;
    /** {@code RandomPos.generateRandomPos}: up to 10 candidates. */
    static final int ATTEMPTS = 10;
    /** Re-path at most every this many ticks while following something. */
    static final int REPATH = 10;

    static final int SWIM = 0;
    static final int PANIC = 1;
    static final int AVOID = 2;
    static final int BREED = 3;
    static final int TEMPT = 4;
    static final int FOLLOW_ADULT = 5;
    static final int IDLE = 6;
    static final int LOOK = 7;

    private LivestockGoals() {
    }

    /**
     * The sensor reading ({@code NEAREST_VISIBLE_LIVING_ENTITIES}, line of sight tested lazily), re-read at most every
     * {@link #SENSE_TICKS}. Shared by the brain's goals. The first reading is dated back by up to {@link #SENSE_TICKS} - 1
     * ticks by entity id (vanilla's sensor starts at a random tick), so a herd's readings spread over the period.
     */
    static Reading seen(LivestockBrain brain, LivestockBody body, long now) {
        if (brain.seenAt == Long.MIN_VALUE || now - brain.seenAt >= SENSE_TICKS || now < brain.seenAt) {
            Mob mob = brain.mob;
            brain.seen = new Reading(body.nearby(mob, SENSE_RANGE, now), e -> body.sees(mob, e), brain.kinds);
            brain.seenAt = brain.seenAt == Long.MIN_VALUE ? now - Math.floorMod(mob.getEntityId() * 7, SENSE_TICKS) : now;
        }
        return brain.seen;
    }

    private static boolean repath(Mob mob) {
        return (mob.getTicksLived() + mob.getEntityId()) % REPATH == 0;
    }

    /** {@code new Swim(0.8F)}: in water, swim up on 80 % of ticks. */
    static final class Swim extends AnimalGoal {
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final RandomGenerator random;

        Swim(LivestockBrain brain, LivestockBody body, RandomGenerator random) {
            super(brain.mob, "livestock_swim", 1, GoalType.JUMP);
            this.brain = brain;
            this.body = body;
            this.random = random;
        }

        @Override
        protected boolean wants() {
            return body.inWater(mob);
        }

        @Override
        protected void step() {
            if (random.nextFloat() < brain.profile.swimChance()) {
                body.swimUp(mob, PreyIdle.SWIM_UP);
            }
        }
    }

    /** {@code AnimalPanic(2.0F)} ({@code CamelPanic(4.0F)}): after a hit, run to random spots within 5 blocks. */
    static final class Panic extends AnimalGoal {
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;

        Panic(LivestockBrain brain, LivestockBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "livestock_panic", 1, GoalType.MOVE);
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
                    body.moveTo(mob, to, brain.profile.panicSpeed());
                }
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
        }
    }

    /**
     * The {@code AVOID} activity: {@code AvoidPredatorAndRammersBehavior.create(true)} (land predators and ramming
     * animals, never players) starts it; {@code SetWalkTargetAwayFrom(AVOID_TARGET, 1.3F, 15)} runs away while the threat
     * is closer than 15 blocks; {@code wantsToStopFleeing} ends it beyond 20 blocks or when the retreat time is over.
     */
    static final class Avoid extends AnimalGoal {
        static final int PERIOD = 5;
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LivestockSenses senses;
        private final LongSupplier clock;
        private final RandomGenerator random;

        Avoid(LivestockBrain brain, LivestockBody body, LivestockSenses senses, LongSupplier clock,
                RandomGenerator random) {
            super(brain.mob, "livestock_avoid", PERIOD, GoalType.MOVE);
            this.brain = brain;
            this.body = body;
            this.senses = senses;
            this.clock = clock;
            this.random = random;
        }

        private double threatDistanceSq() {
            return LivestockSenses.distanceSq(brain.threat, mob);
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            if (brain.fleeing(now)) {
                return !PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq());
            }
            brain.setThreat(null, FleeState.IDLE);
            Reading seen = seen(brain, body, now);
            // findClosest(isAvoided): the kind filter, then the line of sight, then the sighting, for candidates only
            List<Sighting> sightings = null;
            List<LivingEntity> who = null;
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if ((seen.kind(i) & SpeciesTags.MAY_AVOID) == 0 || !seen.visible(i)) {
                    continue;
                }
                if (sightings == null) {
                    sightings = new ArrayList<>();
                    who = new ArrayList<>();
                }
                sightings.add(senses.sighting(e, now));
                who.add(e);
            }
            if (sightings == null) {
                return false;
            }
            Optional<Sighting> found = PreyFlee.trigger(false, LivestockSenses.at(mob), sightings, brain.profile.avoidance());
            if (found.isEmpty()) {
                return false;
            }
            for (int k = 0; k < sightings.size(); k++) {
                if (sightings.get(k) == found.get()) {
                    brain.setThreat(who.get(k), PreyFlee.sighted(found.get().type()));
                    return true;
                }
            }
            return false;
        }

        @Override
        protected boolean keepsGoing() {
            long now = clock.getAsLong();
            return brain.fleeing(now) && !PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq());
        }

        @Override
        protected void begin() {
            body.stop(mob);
            brain.tempting = null;
            run();
        }

        @Override
        protected void step() {
            if (repath(mob) && !body.hasPath(mob)) {
                run();
            }
        }

        private void run() {
            if (!LivestockBrain.alive(brain.threat) || !PreyFlee.runsAway(threatDistanceSq())) {
                return;
            }
            Location self = mob.getLocation();
            Location from = brain.threat.getLocation();
            for (int i = 0; i < ATTEMPTS; i++) {
                Optional<PreyFlee.Offset> off = PreyFlee.awayOffset(self.getX() - from.getX(), self.getZ() - from.getZ(), random);
                if (off.isEmpty()) {
                    continue;
                }
                Location to = body.standable(mob, off.get().dx(), off.get().dy(), off.get().dz());
                if (to != null && body.moveTo(mob, to, brain.profile.away())) {
                    return;
                }
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
            long now = clock.getAsLong();
            if (!brain.fleeing(now) || PreyFlee.wantsToStopFleeing(brain.flee, now, threatDistanceSq())) {
                brain.setThreat(null, FleeState.IDLE);
            }
        }
    }

    /**
     * TFC's {@code BreedBehavior}: a male, once every 200 ticks, picks the closest visible animal of its species it can mate
     * with; both walk to each other; within 3 blocks once 60 to 109 ticks have passed, they pair.
     */
    static final class Breed extends AnimalGoal {
        static final int PERIOD = 10;
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;
        /** {@code animal.canMate(target)} on the two records at a tick. */
        private final BiPredicate<LivingEntity, LivingEntity> canMate;
        private final Function<LivingEntity, LivestockBrain> brains;
        /** The pairing ({@code getBreedOffspring}): male, female. */
        private final BiConsumer<LivingEntity, LivingEntity> pair;

        Breed(LivestockBrain brain, LivestockBody body, LongSupplier clock, RandomGenerator random,
                BiPredicate<LivingEntity, LivingEntity> canMate, Function<LivingEntity, LivestockBrain> brains,
                BiConsumer<LivingEntity, LivingEntity> pair) {
            super(brain.mob, "livestock_breed", PERIOD, GoalType.MOVE, GoalType.LOOK);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
            this.random = random;
            this.canMate = canMate;
            this.brains = brains;
            this.pair = pair;
        }

        private boolean valid() {
            LivingEntity p = brain.breedPartner;
            return LivestockBrain.alive(p) && brain.species.equals(LivestockSenses.species(p)) && canMate.test(mob, p);
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            if (brain.breedPartner != null) {
                if (BreedApproach.continues(valid(), now, brain.pairAt)) {
                    return true; // the partner chose us
                }
                brain.breedPartner = null;
            }
            boolean male = LivestockSenses.record(mob).map(r -> r.state().male()).orElse(false);
            if (!BreedApproach.attempts(now, brain.nextBreedAttempt, male)) {
                return false;
            }
            brain.nextBreedAttempt = now + BreedApproach.ATTEMPT_INTERVAL;
            Reading seen = seen(brain, body, now);
            // entities of our species; the line of sight only for those it could mate with
            List<LivingEntity> mates = new ArrayList<>();
            List<BreedApproach.Partner> partners = new ArrayList<>();
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if (!brain.species.equals(seen.species(i))) {
                    continue;
                }
                mates.add(e);
                partners.add(new BreedApproach.Partner(LivestockSenses.distanceSq(e, mob), true,
                        canMate.test(mob, e) && seen.visible(i)));
            }
            OptionalInt found = BreedApproach.partner(partners);
            if (found.isEmpty()) {
                return false;
            }
            LivingEntity target = mates.get(found.getAsInt());
            brain.breedPartner = target;
            brain.pairAt = BreedApproach.pairAt(now, random);
            LivestockBrain other = brains.apply(target);
            if (other != null) {
                other.breedPartner = mob;
                other.pairAt = brain.pairAt;
            }
            return true;
        }

        @Override
        protected boolean keepsGoing() {
            return BreedApproach.continues(valid(), clock.getAsLong(), brain.pairAt);
        }

        @Override
        protected void begin() {
            brain.tempting = null;
            walk();
        }

        @Override
        protected void step() {
            LivingEntity p = brain.breedPartner;
            if (!LivestockBrain.alive(p)) {
                return;
            }
            body.lookAt(mob, p);
            if (repath(mob)) {
                walk();
            }
            long now = clock.getAsLong();
            if (BreedApproach.pairs(LivestockSenses.distanceSq(p, mob), now, brain.pairAt)
                    && LivestockSenses.record(mob).map(r -> r.state().male()).orElse(false)) {
                pair.accept(mob, p);
                clear(p);
            }
        }

        private void walk() {
            LivingEntity p = brain.breedPartner;
            if (LivestockBrain.alive(p) && LivestockSenses.distanceSq(p, mob) > BreedApproach.WALK_CLOSE * BreedApproach.WALK_CLOSE) {
                body.moveTo(mob, p, brain.profile.breed());
            }
        }

        private void clear(LivingEntity p) {
            LivestockBrain other = brains.apply(p);
            if (other != null && other.breedPartner == mob) {
                other.breedPartner = null;
            }
            brain.breedPartner = null;
        }

        @Override
        protected void end() {
            body.stop(mob);
            if (brain.breedPartner != null) {
                clear(brain.breedPartner);
            }
        }
    }

    /**
     * {@code DelegatingTemptingSensor} + {@code FollowTemptation}: walk to the nearest player within 10 blocks holding
     * the species' food, stand within 2.5 blocks looking at them, then 100 ticks of cooldown. Horses use the same rules
     * for {@code TemptGoal(1.25, food)}.
     */
    static final class Tempt extends AnimalGoal {
        static final int PERIOD = 10;
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private final float adultSpeed;
        private final float babySpeed;

        Tempt(LivestockBrain brain, LivestockBody body, LongSupplier clock, float adultSpeed, float babySpeed) {
            super(brain.mob, "livestock_tempt", PERIOD, GoalType.MOVE, GoalType.LOOK);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
            this.adultSpeed = adultSpeed;
            this.babySpeed = babySpeed;
        }

        private Player sense() {
            List<Player> players = body.players(mob, Temptation.RANGE);
            List<Temptation.Candidate> candidates = new ArrayList<>(players.size());
            for (Player p : players) {
                candidates.add(new Temptation.Candidate(LivestockSenses.distanceSq(p, mob),
                        p.getGameMode() == org.bukkit.GameMode.SPECTATOR, !p.isDead() && p.isValid(),
                        holds(p.getInventory().getItemInMainHand()) || holds(p.getInventory().getItemInOffHand())));
            }
            OptionalInt i = Temptation.temptingPlayer(candidates);
            return i.isPresent() ? players.get(i.getAsInt()) : null;
        }

        private boolean holds(ItemStack stack) {
            return stack != null && !stack.isEmpty() && brain.food.test(stack);
        }

        private boolean decide() {
            long now = clock.getAsLong();
            brain.tempting = sense();
            return Temptation.follows(brain.tempting != null, now, brain.temptCooldownUntil, brain.breeding(),
                    brain.panicking(now));
        }

        @Override
        protected boolean wants() {
            return decide();
        }

        @Override
        protected boolean keepsGoing() {
            return decide();
        }

        @Override
        protected void step() {
            Player p = brain.tempting;
            if (p == null || !p.isValid()) {
                return;
            }
            body.lookAt(mob, p);
            if (!repath(mob)) {
                return;
            }
            if (Temptation.walks(LivestockSenses.distanceSq(p, mob))) {
                body.moveTo(mob, p, LivestockSenses.baby(mob, clock.getAsLong()) ? babySpeed : adultSpeed);
            } else {
                body.stop(mob);
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
            brain.tempting = null;
            brain.temptCooldownUntil = Temptation.cooldownUntil(clock.getAsLong());
        }
    }

    /** {@code BabyFollowAdult.create(UniformInt.of(5, 16), speed)}: a child walks to its nearest visible adult. */
    static final class FollowAdult extends AnimalGoal {
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private LivingEntity adult;

        FollowAdult(LivestockBrain brain, LivestockBody body, LongSupplier clock) {
            super(brain.mob, "livestock_follow_adult", 10, GoalType.MOVE);
            this.brain = brain;
            this.body = body;
            this.clock = clock;
        }

        @Override
        protected boolean wants() {
            long now = clock.getAsLong();
            adult = null;
            if (!LivestockSenses.baby(mob, now)) {
                return false;
            }
            double best = Double.POSITIVE_INFINITY;
            Reading seen = seen(brain, body, now);
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if (!brain.species.equals(seen.species(i))) {
                    continue;
                }
                double d = LivestockSenses.distanceSq(e, mob);
                if (d < best && !LivestockSenses.baby(e, now) && seen.visible(i)) {
                    best = d;
                    adult = e;
                }
            }
            return adult != null && PreyIdle.followsAdult(true, best);
        }

        @Override
        protected boolean keepsGoing() {
            return LivestockBrain.alive(adult) && PreyIdle.followsAdult(true, LivestockSenses.distanceSq(adult, mob));
        }

        @Override
        protected void step() {
            if (repath(mob) && LivestockBrain.alive(adult)) {
                body.moveTo(mob, adult, brain.profile.followAdult());
            }
        }

        @Override
        protected void end() {
            body.stop(mob);
            adult = null;
        }

        LivingEntity adult() {
            return adult;
        }
    }

    /** {@code createIdleMovementBehaviors}: stroll, walk to the look target, or graze in place for 30 to 60 ticks. */
    static final class Idle extends AnimalGoal {
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;
        private PreyIdle.Movement current;
        private long waitUntil;

        Idle(LivestockBrain brain, LivestockBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "livestock_idle", 20, GoalType.MOVE);
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
            current = PreyIdle.movement(random);
            switch (current) {
                case STROLL -> {
                    PreyFlee.Offset o = PreyIdle.randomOffset(PreyIdle.STROLL_HORIZONTAL, PreyIdle.STROLL_VERTICAL, random);
                    Location to = body.standable(mob, o.dx(), o.dy(), o.dz());
                    return to != null && body.moveTo(mob, to, brain.profile.stroll());
                }
                case WALK_TO_LOOK_TARGET -> {
                    LivingEntity t = brain.lookTarget;
                    return LivestockBrain.alive(t) && farFromLook(t) && body.moveTo(mob, t, brain.profile.stroll());
                }
                default -> {
                    waitUntil = clock.getAsLong() + PreyIdle.duration(PreyIdle.WAIT_MIN, PreyIdle.WAIT_MAX, random);
                    return true;
                }
            }
        }

        private boolean farFromLook(LivingEntity t) {
            return LivestockSenses.distanceSq(t, mob) > PreyIdle.LOOK_WALK_CLOSE * PreyIdle.LOOK_WALK_CLOSE;
        }

        @Override
        protected boolean keepsGoing() {
            if (current == null) {
                return false;
            }
            return switch (current) {
                case WAIT -> clock.getAsLong() < waitUntil;
                case WALK_TO_LOOK_TARGET -> LivestockBrain.alive(brain.lookTarget) && farFromLook(brain.lookTarget)
                        && body.hasPath(mob);
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

    /** {@code SetLookTarget.create(PLAYER, 6, 30..60)} with {@code LookAtTargetSink(45, 90)}. */
    static final class Look extends AnimalGoal {
        private final LivestockBrain brain;
        private final LivestockBody body;
        private final LongSupplier clock;
        private final RandomGenerator random;
        private long nextTry = Long.MIN_VALUE;
        private long until;

        Look(LivestockBrain brain, LivestockBody body, LongSupplier clock, RandomGenerator random) {
            super(brain.mob, "livestock_look", 5, GoalType.LOOK);
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
            Reading seen = seen(brain, body, now);
            for (int i = 0; i < seen.size(); i++) {
                LivingEntity e = seen.get(i);
                if (!(e instanceof Player p) || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                    continue;
                }
                double d = LivestockSenses.distanceSq(e, mob);
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
            return clock.getAsLong() < until && LivestockBrain.alive(brain.lookTarget);
        }

        @Override
        protected void step() {
            if (LivestockBrain.alive(brain.lookTarget)) {
                body.lookAt(mob, brain.lookTarget);
            }
        }

        @Override
        protected void end() {
            brain.lookTarget = null;
        }
    }
}
