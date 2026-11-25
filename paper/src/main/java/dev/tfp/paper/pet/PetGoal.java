/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Ported from TerraFirmaCraft (https://github.com/TerraFirmaCraft/TerraFirmaCraft),
 * file: src/main/java/net/dries007/tfc/common/entities/ai/pet/TamableAi.java @ c3b4d9536 (the activities, updateActivity,
 * getAttackTarget, getUnwantedAttackTarget), ai/pet/FollowOwnerBehavior.java, ai/pet/TamableFindSleepPos.java,
 * ai/pet/TamableSleepBehavior.java, ai/pet/MoveOntoBlockBehavior.java, ai/pet/MoveToTargetSinkIfNotSleeping.java
 */
package dev.tfp.paper.pet;

import com.destroystokyo.paper.entity.ai.GoalType;
import dev.tfp.core.pet.PetActivity;
import dev.tfp.core.pet.PetAi;
import dev.tfp.core.pet.PetRules;
import dev.tfp.core.pet.PetState;
import dev.tfp.core.predator.PredatorMelee;
import dev.tfp.core.prey.PreyFlee;
import dev.tfp.paper.animal.AnimalGoal;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

/**
 * TFC's pet brain as one goal: the brain's activity ({@link PetActivity}) decides what it does, as in TFC where the brain
 * runs one activity at a time. The goal thinks on a {@value #THINK}-tick beat staggered per pet; the activity switches are
 * the pure {@link PetAi#update} on a snapshot, and only their result is applied. Only the melee reach check runs more often
 * (it shares the beat here). Everything runs on the pet's owning thread; the owner and other entities are touched only when
 * the current region owns them.
 */
final class PetGoal extends AnimalGoal {
    static final int PERIOD = 5;
    static final int THINK = 5;
    /** The slow checks of {@code updateActivity} ({@code tickCount % 20 == 0}). */
    static final int SLOW = 20;
    /** Vanilla {@code NearestLivingEntitySensor}: 16 blocks. */
    static final double SENSE_RANGE = 16.0;
    /** {@code RandomPos}: up to 10 candidates. */
    static final int ATTEMPTS = 10;
    /** {@code MoveOntoBlockBehavior}: the walk to the bed is given up after this many ticks. */
    static final int BED_WALK_TICKS = 100;

    /** The shared parts of the goal. */
    record Deps(PetBody body, LongSupplier clock, RandomGenerator random, PetSenses senses, Fights fights) {
    }

    /** What the senses of a pet ask about a world entity (tag memberships), as a seam for tests. */
    interface PetSenses {
        /** {@code TFCTags.Blocks.PET_SITS_ON} at the block. */
        boolean sitsOn(org.bukkit.block.Block block);

        /** The entity is in {@code #tfc:hunted_by_dogs} / {@code #tfc:hunted_by_cats}. */
        boolean huntedBy(dev.tfp.core.pet.PetSpecies species, LivingEntity e);

        /** {@code instanceof Pest} (rat, jerboa, lemming, mongoose). */
        boolean pest(LivingEntity e);

        /** {@code instanceof Monster}. */
        boolean monster(LivingEntity e);
    }

    /** The last fights of the owners: who hurt an owner and whom an owner hurt, with the player tick. */
    interface Fights {
        /** The last living entity that hurt {@code owner}, or null. */
        LivingEntity attackerOf(java.util.UUID owner);

        long attackerStamp(java.util.UUID owner);

        /** The last living entity {@code owner} hurt, or null. */
        LivingEntity victimOf(java.util.UUID owner);

        long victimStamp(java.util.UUID owner);
    }

    private final PetBrain brain;
    private final Deps d;

    PetGoal(PetBrain brain, Deps d) {
        super(brain.mob, "pet_brain", PERIOD, GoalType.MOVE, GoalType.LOOK);
        this.brain = brain;
        this.d = d;
    }

    @Override
    protected boolean wants() {
        return true;
    }

    @Override
    protected void begin() {
        think(d.clock().getAsLong());
    }

    @Override
    protected void end() {
        d.body().stop(mob);
    }

    @Override
    protected void step() {
        if ((mob.getTicksLived() + mob.getEntityId()) % THINK == 0) {
            think(d.clock().getAsLong());
        }
    }

    boolean slowBeat() {
        return (mob.getTicksLived() + mob.getEntityId()) % SLOW == 0;
    }

    /** One think: the stored command's activity, the activity switches, then the activity's behaviours. */
    void think(long now) {
        // TamableMammal.tick: a pending command refresh restores the stored command's activity
        if (brain.needsCommandUpdate) {
            PetActivity stored = brain.state.command().activity();
            if (stored != null) {
                brain.setActivity(stored);
            }
            brain.needsCommandUpdate = false;
        }
        apply(PetAi.update(situation(now), slowBeat()), now);
        switch (brain.activity) {
            case IDLE -> idle(now);
            case IDLE_AT_HOME -> idleAtHome(now);
            case REST -> rest(now);
            case AVOID -> avoid(now);
            case HUNT -> hunt(now);
            case FIGHT -> fight(now);
            case FOLLOW -> follow(now);
            case SIT -> sit(now);
        }
    }

    private PetAi.Situation situation(long now) {
        Player owner = brain.owner();
        boolean hasOwner = owner != null && owner.getWorld().equals(mob.getWorld());
        boolean attackIsOwner = brain.isOwnedBy(brain.target);
        boolean hurtIsOwner = brain.isOwnedBy(brain.attacker);
        boolean hurt = brain.hurtRecently(now);
        boolean hasTarget = hasTarget();
        int flee = PetAi.fleeTarget(hasTarget, attackIsOwner, hurt, hurtIsOwner);
        return new PetAi.Situation(brain.activity, (float) mob.getHealth(), mob.getFireTicks() > 0, brain.hasAvoid(now),
                flee != 0, hasTarget, hasOwner, farFromHome(PetAi.HOME_LOST_DISTANCE), hurt, brain.state.sleeping(),
                brain.state.sitTick(), brain.lastSlept, now);
    }

    private boolean hasTarget() {
        return PetBrain.alive(brain.target) && brain.target.getWorld().equals(mob.getWorld());
    }

    boolean farFromHome(int distance) {
        Location l = mob.getLocation();
        return PetAi.isFarFromHome(brain.state.home(), d.body().dimension(mob), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
                distance);
    }

    private void apply(PetAi.Update u, long now) {
        if (u.startFlee()) {
            startFleeing(now);
        }
        switch (u.action()) {
            case NONE -> {
            }
            case AVOID -> brain.setActivity(PetActivity.AVOID);
            case REFRESH_COMMAND -> {
                // the avoid activity ends: the default activity until the stored command is restored next tick
                brain.setActivity(PetActivity.IDLE);
                brain.needsCommandUpdate = true;
            }
            case FIGHT -> brain.setActivity(PetActivity.FIGHT);
            case IDLE_AT_HOME -> beginIdle(true);
            case IDLE -> beginIdle(false);
            case REST -> brain.setActivity(PetActivity.REST);
            case MARK_NEVER_SLEPT -> {
                brain.lastSlept = 0L;
                brain.save();
            }
        }
    }

    /** {@code TamableAi.startFleeing}: stop sitting and sleeping, forget a sit command, avoid the threat. */
    private void startFleeing(long now) {
        LivingEntity threat = hasTarget() && !brain.isOwnedBy(brain.target) ? brain.target
                : brain.hurtRecently(now) && !brain.isOwnedBy(brain.attacker) ? brain.attacker : null;
        if (threat == null) {
            return;
        }
        PetState s = brain.state;
        brain.setState(new PetState(s.sitting() ? dev.tfp.core.pet.PetCommand.RELAX : s.command(), false, false, s.sitTick(), s.home()));
        brain.avoid = threat;
        brain.avoidUntil = now + PreyFlee.retreatTicks(d.random());
    }

    /** {@code TamableAi.beginIdle}: stop sitting and sleeping, store RELAX, idle at home or in the idle activity. */
    private void beginIdle(boolean home) {
        PetRules.Outcome out = PetRules.stopSitting(brain.state, home);
        brain.setState(out.state());
        brain.setActivity(out.activity());
    }

    // ---- activities

    /** {@code SetLookTarget.create(EntityType.PLAYER, range, UniformInt.of(30, 60))}: look at a player now and then. */
    private void lookAtPlayers(long now, double range) {
        if (brain.lookTarget != null && now < brain.lookUntil && PetBrain.alive(brain.lookTarget)) {
            d.body().lookAt(mob, brain.lookTarget);
            return;
        }
        brain.lookTarget = null;
        if (now < brain.nextLook) {
            return;
        }
        Optional<LivingEntity> player = d.body().nearby(mob, range).stream()
                .filter(e -> e instanceof Player && d.body().sees(mob, e))
                .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(mob.getLocation())));
        int ticks = PetAi.LOOK_MIN_TICKS + d.random().nextInt(PetAi.LOOK_MAX_TICKS - PetAi.LOOK_MIN_TICKS + 1);
        brain.nextLook = now + ticks;
        if (player.isPresent()) {
            brain.lookTarget = player.get();
            brain.lookUntil = now + ticks;
            d.body().lookAt(mob, player.get());
        }
    }

    /** The idle activity ({@code LivestockAi.initIdleActivity}): a stroll now and then. */
    private void idle(long now) {
        lookAtPlayers(now, PetAi.LOOK_PLAYER_RANGE);
        stroll(now);
    }

    private void stroll(long now) {
        if (now < brain.waitUntil || d.body().hasPath(mob)) {
            return;
        }
        brain.waitUntil = now + 30 + d.random().nextInt(31);
        Location to = d.body().standable(mob, d.random().nextInt(21) - 10, d.random().nextInt(7) - 3, d.random().nextInt(21) - 10);
        if (to != null) {
            d.body().moveTo(mob, to, PetAi.HOME_STROLL_SPEED);
        }
    }

    /** {@code IDLE_AT_HOME}: look at players, return home when too far, take unwanted targets, stroll around home. */
    private void idleAtHome(long now) {
        lookAtPlayers(now, PetAi.LOOK_PLAYER_RANGE);
        PetState.Home home = brain.state.home();
        if (home != null && farFromHome(PetAi.HOME_WANDER_DISTANCE)) {
            if (home.dimension().equals(d.body().dimension(mob))) {
                Location target = new Location(mob.getWorld(), home.x() + 0.5, home.y(), home.z() + 0.5);
                if (mob.getLocation().distanceSquared(target) > PetAi.RETURN_HOME_CLOSE_ENOUGH * PetAi.RETURN_HOME_CLOSE_ENOUGH) {
                    d.body().moveTo(mob, target, PetAi.RETURN_HOME_SPEED);
                }
            }
        }
        if (!hasTarget()) {
            startAttacking(unwantedTarget(now));
        }
        if (now < brain.waitUntil || d.body().hasPath(mob)) {
            return;
        }
        // RunOne: the first of four behaviours in a random order that can start
        int first = d.random().nextInt(4);
        for (int i = 0; i < 4; i++) {
            switch ((first + i) % 4) {
                case 0 -> {
                    if (strollToHome()) {
                        return;
                    }
                }
                case 1 -> {
                    if (strollAroundHome()) {
                        brain.waitUntil = now + 30;
                        return;
                    }
                }
                case 2 -> {
                    if (walkToLookTarget()) {
                        return;
                    }
                }
                default -> {
                    brain.waitUntil = now + 30 + d.random().nextInt(31);
                    return;
                }
            }
        }
    }

    /** {@code StrollToPoi.create(HOME, 0.6F, 10, 26)}: walk home when farther than 26 blocks. */
    private boolean strollToHome() {
        PetState.Home home = brain.state.home();
        if (home == null || !home.dimension().equals(d.body().dimension(mob))) {
            return false;
        }
        Location target = new Location(mob.getWorld(), home.x() + 0.5, home.y(), home.z() + 0.5);
        double max = PetAi.STROLL_TO_HOME_MAX;
        if (mob.getLocation().distanceSquared(target) <= max * max) {
            return false;
        }
        return d.body().moveTo(mob, target, PetAi.HOME_STROLL_SPEED);
    }

    /** {@code StrollAroundPoi.create(HOME, 0.6F, 36)}: a random land spot around home. */
    private boolean strollAroundHome() {
        PetState.Home home = brain.state.home();
        if (home == null || !home.dimension().equals(d.body().dimension(mob))) {
            return false;
        }
        Location center = new Location(mob.getWorld(), home.x() + 0.5, home.y(), home.z() + 0.5);
        Location to = d.body().standableAround(mob, center, d.random().nextInt(21) - 10, d.random().nextInt(7) - 3,
                d.random().nextInt(21) - 10);
        return to != null && d.body().moveTo(mob, to, PetAi.HOME_STROLL_SPEED);
    }

    /** {@code SetWalkTargetFromLookTarget.create(1.0F, 3)}: walk to what it looks at when farther than 3 blocks. */
    private boolean walkToLookTarget() {
        LivingEntity look = brain.lookTarget;
        if (!PetBrain.alive(look) || !Bukkit.isOwnedByCurrentRegion(look)
                || look.getLocation().distanceSquared(mob.getLocation()) <= 9) {
            return false;
        }
        return d.body().moveTo(mob, look, PetAi.LOOK_WALK_SPEED);
    }

    /** {@code TamableFindSleepPos} and {@code TamableSleepBehavior}. */
    private void rest(long now) {
        if (brain.state.sleeping()) {
            // MoveToTargetSinkIfNotSleeping: a sleeper does not walk
            d.body().stop(mob);
            return;
        }
        if (d.body().inWater(mob)) {
            return;
        }
        if (brain.sleepPos == null) {
            Location bed = d.body().findBed(mob, PetAi.SLEEP_POS_HORIZONTAL, PetAi.SLEEP_POS_VERTICAL, d.senses()::sitsOn);
            // without a bed spot TFC keeps the pet's own block as the sleep position unless it holds a fluid
            brain.sleepPos = bed != null ? bed : mob.getLocation().getBlock().isLiquid() ? null : mob.getLocation().getBlock().getLocation();
            brain.sleepWalkUntil = now + BED_WALK_TICKS;
            if (brain.sleepPos == null) {
                return;
            }
        }
        Location spot = brain.sleepPos;
        if (mob.getLocation().getBlock().getLocation().distanceSquared(spot.getBlock().getLocation()) < 1.0) {
            d.body().stop(mob);
            brain.lookTarget = null;
            brain.lastSlept = now;
            brain.setState(new PetState(brain.state.command(), brain.state.sitting(), true, brain.state.sitTick(),
                    brain.state.home()));
            return;
        }
        if (now >= brain.sleepWalkUntil) {
            // MoveOntoBlockBehavior.tiredOfWalking: choose another spot
            brain.sleepPos = null;
            return;
        }
        if (!d.body().hasPath(mob)) {
            d.body().moveTo(mob, spot, 1.0);
        }
    }

    /** {@code AVOID}: run away from the threat while it is closer than 15 blocks; end by {@code wantsToStopFleeing}. */
    private void avoid(long now) {
        LivingEntity threat = brain.avoid;
        if (!brain.hasAvoid(now)) {
            brain.avoid = null;
            return;
        }
        double distSq = Bukkit.isOwnedByCurrentRegion(threat) ? threat.getLocation().distanceSquared(mob.getLocation())
                : 0;
        if (PreyFlee.wantsToStopFleeing(new dev.tfp.core.prey.FleeState("threat", brain.avoidUntil), now, distSq)) {
            brain.avoid = null;
            brain.avoidUntil = Long.MIN_VALUE;
            return;
        }
        lookAtPlayers(now, 8.0);
        if (PreyFlee.runsAway(distSq) && !d.body().hasPath(mob)) {
            Location here = mob.getLocation();
            Location there = threat.getLocation();
            PreyFlee.awayOffset(here.getX() - there.getX(), here.getZ() - there.getZ(), d.random()).ifPresent(o -> {
                Location to = d.body().standable(mob, o.dx(), o.dy(), o.dz());
                if (to != null) {
                    d.body().moveTo(mob, to, PetAi.AVOID_SPEED);
                }
            });
        }
    }

    /** {@code HUNT}: follow the owner, take the owner's fight or whoever hurt it, look at players. */
    private void hunt(long now) {
        followOwner();
        if (!hasTarget()) {
            startAttacking(huntTarget(now));
        }
        lookAtPlayers(now, PetAi.LOOK_PLAYER_RANGE);
    }

    private void follow(long now) {
        followOwner();
        lookAtPlayers(now, PetAi.LOOK_PLAYER_RANGE);
    }

    /** {@code FollowOwnerBehavior}: with no walk target and no attack target, walk to the owner at 1.1 until 5 blocks away. */
    private void followOwner() {
        Player owner = brain.ownerHere();
        if (owner == null || hasTarget()) {
            return;
        }
        double distSq = owner.getLocation().distanceSquared(mob.getLocation());
        double close = PetAi.FOLLOW_CLOSE_ENOUGH;
        if (distSq <= close * close) {
            d.body().stop(mob);
        } else if (!d.body().hasPath(mob)) {
            d.body().moveTo(mob, owner, PetAi.FOLLOW_SPEED);
        }
    }

    /** {@code FIGHT}: chase at 1.15, bite every 40 ticks in reach, stop when the target is no longer valid. */
    private void fight(long now) {
        LivingEntity t = brain.target;
        // StopAttackingIfTargetInvalid(e -> false, refreshCommandOnNextTick, false)
        if (!hasTarget() || !canAttack(t)) {
            brain.target = null;
            brain.needsCommandUpdate = true;
            return;
        }
        if (!Bukkit.isOwnedByCurrentRegion(t)) {
            return;
        }
        boolean visible = d.body().sees(mob, t);
        boolean inRange = PredatorMelee.withinMeleeRange(box(mob), box(t));
        if (!(visible && inRange)) {
            d.body().moveTo(mob, t, PetAi.CHASE_SPEED);
        } else if (now >= brain.coolingUntil) {
            brain.coolingUntil = now + PetAi.ATTACK_COOLDOWN_TICKS;
            d.body().attack(mob, t, brain.species == dev.tfp.core.pet.PetSpecies.DOG ? "tfp_animals:entity.dog.attack" : null);
        }
    }

    private static PredatorMelee.Box box(org.bukkit.entity.Entity e) {
        BoundingBox b = e.getBoundingBox();
        return new PredatorMelee.Box(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ());
    }

    /** {@code SIT}: stay put and look around. */
    private void sit(long now) {
        d.body().stop(mob);
        Player owner = brain.ownerHere();
        if (owner != null && owner.getLocation().distanceSquared(mob.getLocation()) <= PetAi.SIT_LOOK_RANGE * PetAi.SIT_LOOK_RANGE
                && d.body().sees(mob, owner)) {
            d.body().lookAt(mob, owner);
        } else {
            lookAtPlayers(now, PetAi.SIT_LOOK_RANGE);
        }
    }

    // ---- targets

    /** {@code TamableMammal.canAttack}: not the owner, and what the species hunts. */
    boolean canAttack(LivingEntity target) {
        return PetAi.canAttack(brain.species, brain.isOwnedBy(target), d.senses().huntedBy(brain.species, target),
                d.senses().monster(target));
    }

    private void startAttacking(LivingEntity target) {
        if (target != null && PetBrain.alive(target) && canAttack(target)) {
            brain.target = target;
        }
    }

    /** {@code TamableAi.getUnwantedAttackTarget}. */
    private LivingEntity unwantedTarget(long now) {
        boolean flee = PetAi.couldFlee((float) mob.getHealth(), mob.getFireTicks() > 0);
        boolean hurt = brain.hurtRecently(now);
        LivingEntity pest = null;
        if (!flee && !hurt) {
            pest = d.body().nearby(mob, SENSE_RANGE).stream().filter(d.senses()::pest).filter(e -> d.body().sees(mob, e))
                    .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
        }
        return switch (PetAi.unwantedTarget(flee, hurt, pest != null)) {
            case 1 -> brain.attacker;
            case 2 -> pest;
            default -> null;
        };
    }

    /** {@code TamableAi.getAttackTarget} (the hunt activity). */
    private LivingEntity huntTarget(long now) {
        boolean flee = PetAi.couldFlee((float) mob.getHealth(), mob.getFireTicks() > 0);
        Player owner = brain.owner();
        LivingEntity attacker = owner == null ? null : d.fights().attackerOf(owner.getUniqueId());
        LivingEntity victim = owner == null ? null : d.fights().victimOf(owner.getUniqueId());
        boolean attackerOk = PetBrain.alive(attacker) && Bukkit.isOwnedByCurrentRegion(attacker) && canAttack(attacker);
        boolean victimOk = PetBrain.alive(victim) && Bukkit.isOwnedByCurrentRegion(victim) && canAttack(victim);
        PetAi.Pick pick = PetAi.huntTarget(flee, owner != null, attackerOk, now,
                owner == null ? 0 : d.fights().attackerStamp(owner.getUniqueId()), victimOk,
                owner == null ? 0 : d.fights().victimStamp(owner.getUniqueId()), brain.hurtRecently(now));
        return switch (pick) {
            case OWNER_ATTACKER -> attacker;
            case OWNER_VICTIM -> victim;
            case HURT_BY -> brain.attacker;
            case NONE -> null;
        };
    }
}
